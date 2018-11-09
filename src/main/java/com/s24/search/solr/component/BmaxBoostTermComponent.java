package com.s24.search.solr.component;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.s24.search.solr.component.BmaxBoostConstants.*;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
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
import org.apache.solr.request.SolrQueryRequest;
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
      final SolrParams requestParams = rb.req.getParams();
      boolean boost = requestParams.getBool(BOOST_ENABLE, true);
      String boostExtraTerms = requestParams.get(BOOST_EXTRA_TERMS);
      float boostFactor = Math.abs(requestParams.getFloat(BOOST_FACTOR, 1f));
      boolean penalize = requestParams.getBool(PENALIZE_ENABLE, true);
      float penalizeFactor = -Math.abs(requestParams.getFloat(PENALIZE_FACTOR, 100.0f));
      int penalizeDocs = requestParams.getInt(PENALIZE_DOC_COUNT, 400);
      String penalizeExtraTerms = requestParams.get(PENALIZE_EXTRA_TERMS);
      String penalizeStrategy = requestParams.get(PENALIZE_STRATEGY, VALUE_PENALIZE_STRATEGY_RERANK);

      // collect analyzers
      final IndexSchema schema = rb.req.getSearcher().getSchema();

      final ModifiableSolrParams params = new ModifiableSolrParams(requestParams);

      final String q = getExpandedQuery(requestParams, schema, requestParams.getBool(SYNONYM_ENABLE, true));

      final boolean hasIncomingBoostQuery = requestParams.get(DisMaxParams.BQ) != null;

      // check boosts
      if (boost && !hasIncomingBoostQuery) {

         final Analyzer boostAnalyzer = schema.getFieldTypeByName(boostTermFieldType).getQueryAnalyzer();
         Collection<CharSequence> terms = Terms.collect(q, boostAnalyzer);

         // add extra terms
         if (boostExtraTerms != null) {
            terms.addAll(Sets.newHashSet(Splitter.on(',').omitEmptyStrings().split(boostExtraTerms)));
         }

         // add boosts
         if (!terms.isEmpty()) {
            params.add("bq", String.format(Locale.US, "{!%s qf='%s' mm=1 bq=''} %s", boostQueryType,
                  computeFactorizedQueryFields(rb.req, boostFactor),
                  Joiner.on(' ').join(terms)));

            // add debug
            if (rb.isDebugQuery()) {
               BmaxDebugInfo.add(rb, COMPONENT_NAME + ".boost.terms", Joiner.on(' ').join(terms));
            }
         }
      }

      // check penalizes
      if (penalize) {

         // query fields for penalize terms
         final String fields = requestParams.get(PENALIZE_FIELDS, requestParams.get(DisMaxParams.QF));
         final Analyzer penalizeAnalyzer = schema.getFieldTypeByName(penalizeTermFieldType).getQueryAnalyzer();

         PenalizeStrategy strategy = null;

         if (penalizeStrategy.equals(VALUE_PENALIZE_STRATEGY_RERANK)) {

            if (requestParams.get(CommonParams.RQ) == null) {
               strategy = new RerankPenalizeStrategy(rb, q, penalizeExtraTerms, penalizeAnalyzer, fields, penalizeDocs,
                       penalizeFactor);
            }

         } else if (penalizeStrategy.equals(VALUE_PENALIZE_STRATEGY_BOOST_QUERY)) {

            if (!hasIncomingBoostQuery) {
               strategy = new BoostQueryPenalizeStrategy(rb, q, penalizeExtraTerms, penalizeAnalyzer, fields,
                       penalizeFactor);
            }

         } else {
            throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,
                    "Unknown value '" + penalizeStrategy + "' for param : " + PENALIZE_STRATEGY);
         }

         if (strategy != null) {
            strategy.apply(params);
         }

      }

      rb.req.setParams(params);
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
   protected String computeFactorizedQueryFields(SolrQueryRequest request, float factor) {
      checkNotNull(request, "Pre-condition violated: request must not be null.");

      StringBuilder qf = new StringBuilder();

      String field = (request.getParams().get(BOOST_FIELDS) != null ? BOOST_FIELDS : DisMaxParams.QF);
      // parse fields and boosts
      Map<String, Float> fieldBoosts = SolrPluginUtils.parseFieldBoosts(request.getParams().getParams(field));

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
