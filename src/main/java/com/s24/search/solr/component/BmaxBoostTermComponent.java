package com.s24.search.solr.component;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.s24.search.solr.component.BmaxBoostConstants.*;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.apache.lucene.analysis.Analyzer;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.ShardParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.search.ReRankQParserPlugin;
import org.apache.solr.util.SolrPluginUtils;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.s24.search.solr.query.bmax.Terms;
import com.s24.search.solr.util.BmaxDebugInfo;

/**
 * Adds boost and penalize terms to a query. Boost terms are filled into a
 * <code>bq</code> boost query parameter that can be picked up by the bmax
 * and/or edismax query parser. Penalize terms are filled into a rerank query
 * that will be executed by the {@linkplain ReRankQParserPlugin}.
 *
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public class BmaxBoostTermComponent extends SearchComponent {

   // (optional) synonym field
   private String synonymFieldType;

   // produces boost terms
   private String boostTermFieldType;

   // produces penalize terms
   private String penalizeTermFieldType;

   // parses query
   private String queryParsingFieldType;

   // the defType for the boost query generated for the boost terms
   private String boostQueryType;

   @Override
   public void init(@SuppressWarnings("rawtypes") NamedList args) {
      super.init(args);

      SolrParams configuration = SolrParams.toSolrParams(args);
      queryParsingFieldType = configuration.get("queryParsingFieldType");
      synonymFieldType = configuration.get("synonymFieldType");
      boostTermFieldType = configuration.get("boostTermFieldType");
      penalizeTermFieldType = configuration.get("penalizeTermFieldType");
      boostQueryType = configuration.get("boostQueryType", "dismax");
   }

   @Override
   public void prepare(ResponseBuilder rb) throws IOException {
      checkNotNull(rb, "Pre-condition violated: rb must not be null.");
      SolrParams solrParams = rb.req.getParams();

      // Only run this component if it is active and this is not a subrequest (we want to add the boost terms
      // only on the initial request that Solr receives from the outside).
      if (solrParams.getBool(COMPONENT_NAME, false) && !solrParams.getBool(ShardParams.IS_SHARD, false)) {

         // check preconditions
         String q = solrParams.get(CommonParams.Q);
         String sort = solrParams.get(CommonParams.SORT);

         if (q != null
               && !"*:*".equals(q)
               && (sort == null || (StringUtils.containsIgnoreCase(sort, "score") && StringUtils.containsIgnoreCase(
                     sort, "desc")))) {
            prepareInternal(rb);
         }
      }
   }

   protected void prepareInternal(ResponseBuilder rb) throws IOException {
      checkNotNull(rb, "Pre-condition violated: rb must not be null.");

      // collect configuration
      final ModifiableSolrParams params = new ModifiableSolrParams(rb.req.getParams());
      final IndexSchema schema = rb.req.getSearcher().getSchema();
      final boolean boost = params.getBool(BOOST_ENABLE, true);
      final boolean penalize = params.getBool(PENALIZE_ENABLE, true);

      final String q = getExpandedQuery(params, schema, params.getBool(SYNONYM_ENABLE, true));

      // check boosts
      if (boost) {
         String debugMessage = applyBoosts(params,schema,q);
         if(rb.isDebugQuery()) {
            BmaxDebugInfo.add(rb, COMPONENT_NAME + ".boost.terms", debugMessage);
         }
      }

      // check penalizes
      if (penalize) {
         String debugMessage = applyPenalizing(params,schema,q,rb.isDebugQuery());
         if(rb.isDebugQuery()) {
            BmaxDebugInfo.add(rb, COMPONENT_NAME + ".penalize.terms", debugMessage);
         }
      }

      rb.req.setParams(params);
   }

   /**
    *
    * @param params
    * @param schema
    * @param q
    * @return the debug String
    */
   private String applyBoosts(final ModifiableSolrParams params, final IndexSchema schema, String q) {
      final String boostExtraTerms = params.get(BOOST_EXTRA_TERMS);
      final float boostFactor = Math.abs(params.getFloat(BOOST_FACTOR, 1f));
      final String boostStrategy = params.get(BOOST_STRATEGY, VALUE_BOOST_STRATEGY_ADDITIVELY);

      final Analyzer boostAnalyzer = schema.getFieldTypeByName(boostTermFieldType).getQueryAnalyzer();
      Collection<CharSequence> terms = Terms.collect(q, boostAnalyzer);

      // add extra terms
      if (boostExtraTerms != null) {
         terms.addAll(Sets.newHashSet(Splitter.on(',').omitEmptyStrings().split(boostExtraTerms)));
      }

      // add boosts
      if (!terms.isEmpty()) {
         params.add(boostStrategy, String.format(Locale.US, "{!%s qf='%s' mm=1 bq=''} %s", boostQueryType,
                 computeFactorizedQueryFields(params, boostFactor),
                 Joiner.on(' ').join(terms)));
      }
      return Joiner.on(' ').join(terms);

   }

   private String applyPenalizing(final ModifiableSolrParams params, final IndexSchema schema, String q, boolean debug) {
      final float penalizeFactor = -Math.abs(params.getFloat(PENALIZE_FACTOR, 100.0f));
      final int penalizeDocs = params.getInt(PENALIZE_DOC_COUNT, 400);
      final String penalizeExtraTerms = params.get(PENALIZE_EXTRA_TERMS);
      final String penalizeStrategy = params.get(PENALIZE_STRATEGY, VALUE_PENALIZE_STRATEGY_RERANK);


      // query fields for penalize terms
      final Analyzer penalizeAnalyzer = schema.getFieldTypeByName(penalizeTermFieldType).getQueryAnalyzer();
      final String fields = params.get(PENALIZE_FIELDS, params.get(DisMaxParams.QF));

      PenalizeStrategy strategy = null;

      if (penalizeStrategy.equals(VALUE_PENALIZE_STRATEGY_RERANK)) {

         // there can only be one Rerankquery in solr (as of 22.11.18) , so we can only use RQ if it is empty
         if (params.get(CommonParams.RQ) == null) {
            strategy = new RerankPenalizeStrategy(q, penalizeExtraTerms, penalizeAnalyzer, fields, penalizeDocs,
                    penalizeFactor);
         }

      } else if (penalizeStrategy.equals(VALUE_PENALIZE_STRATEGY_BOOST_QUERY)) {
         strategy = new BoostQueryPenalizeStrategy(q, penalizeExtraTerms, penalizeAnalyzer, fields, penalizeFactor);

      } else {
         throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                 "Unknown value '" + penalizeStrategy + "' for param : " + PENALIZE_STRATEGY);
      }

      if (strategy != null) {
         return strategy.apply(params);
      }
      return Strings.EMPTY;
   }

   /**
    * Join the query terms and their synonym terms into a single query string
    * @param requestParams
    * @param schema
    * @param synonyms
    * @return
    */
   protected String getExpandedQuery(final SolrParams requestParams, final IndexSchema schema, boolean synonyms) {

      String q = requestParams.get(CommonParams.Q);

      final Analyzer queryParsingAnalyzer = schema.getFieldTypeByName(queryParsingFieldType).getQueryAnalyzer();

      // do a first query parsing approach
      q = Joiner.on(' ').skipNulls().join(Terms.collect(q, queryParsingAnalyzer));

      // collect synonyms
      if (synonyms) {
         final Analyzer synonymAnalyzer = schema.getFieldTypeByName(synonymFieldType).getQueryAnalyzer();
         q += " " + Joiner.on(' ').skipNulls().join(Terms.collect(q, synonymAnalyzer));
      }

      return q;
   }

   /**
    * Computes
    */
   protected String computeFactorizedQueryFields(SolrParams params, float factor) {
      checkNotNull(params, "Pre-condition violated: params must not be null.");

      StringBuilder qf = new StringBuilder();

      String field = (params.get(BOOST_FIELDS) != null ? BOOST_FIELDS : DisMaxParams.QF);
      // parse fields and boosts
      Map<String, Float> fieldBoosts = SolrPluginUtils.parseFieldBoosts(params.getParams(field));

      // iterate, add factor and add to result qf
      for (Entry<String, Float> f : fieldBoosts.entrySet()) {
         qf.append(f.getKey());
         qf.append('^');

         if (f.getValue() != null) {
            qf.append(f.getValue() * factor);
         } else {
            qf.append(factor);
         }

         qf.append(' ');
      }

      return qf.toString();
   }

   @Override
   public void process(ResponseBuilder rb) throws IOException {
      // noop
   }

   @Override
   public String getDescription() {
      return "Adds boost and penalize terms to a query";
   }

}
