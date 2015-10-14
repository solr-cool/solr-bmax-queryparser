package com.s24.search.solr.component;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.request.SolrQueryRequest;
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

   public static final String COMPONENT_NAME = "bmax.booster";

   // params
   private static final String PENALIZE_EXTRA_TERMS = COMPONENT_NAME + ".penalize.extra";
   private static final String PENALIZE_DOC_COUNT = COMPONENT_NAME + ".penalize.docs";
   private static final String PENALIZE_FACTOR = COMPONENT_NAME + ".penalize.factor";
   private static final String PENALIZE_ENABLE = COMPONENT_NAME + ".penalize";
   private static final String BOOST_EXTRA_TERMS = COMPONENT_NAME + ".boost.extra";
   private static final String BOOST_ENABLE = COMPONENT_NAME + ".boost";
   private static final String BOOST_FACTOR = COMPONENT_NAME + ".boost.factor";
   private static final String BOOST_FIELDS = COMPONENT_NAME + ".boost.qf";
   private static final String SYNONYM_ENABLE = COMPONENT_NAME + ".synonyms";

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

      // check component is activated
      if (rb.req.getParams().getBool(COMPONENT_NAME, false)) {

         // check preconditions
         String q = rb.req.getParams().get(CommonParams.Q);
         String sort = rb.req.getParams().get(CommonParams.SORT);

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
      String q = rb.req.getParams().get(CommonParams.Q);
      boolean boost = rb.req.getParams().getBool(BOOST_ENABLE, true);
      String boostExtraTerms = rb.req.getParams().get(BOOST_EXTRA_TERMS);
      float boostFactor = Math.abs(rb.req.getParams().getFloat(BOOST_FACTOR, 1f));
      boolean penalize = rb.req.getParams().getBool(PENALIZE_ENABLE, true);
      float penalizeFactor = -Math.abs(rb.req.getParams().getFloat(PENALIZE_FACTOR, 100.0f));
      int penalizeDocs = rb.req.getParams().getInt(PENALIZE_DOC_COUNT, 400);
      String penalizeExtraTerms = rb.req.getParams().get(PENALIZE_EXTRA_TERMS);
      boolean synonyms = rb.req.getParams().getBool(SYNONYM_ENABLE, true);

      // collect analyzers
      Analyzer queryParsingAnalyzer = rb.req.getSearcher().getSchema()
            .getFieldTypeByName(queryParsingFieldType).getQueryAnalyzer();
      Analyzer synonymAnalyzer = rb.req.getSearcher().getSchema()
            .getFieldTypeByName(synonymFieldType).getQueryAnalyzer();
      Analyzer boostAnalyzer = rb.req.getSearcher().getSchema()
            .getFieldTypeByName(boostTermFieldType).getQueryAnalyzer();
      Analyzer penalizeAnalyzer = rb.req.getSearcher().getSchema()
            .getFieldTypeByName(penalizeTermFieldType).getQueryAnalyzer();
      ModifiableSolrParams params = new ModifiableSolrParams(rb.req.getParams());

      // do a first query parsing approach
      q = Joiner.on(' ').skipNulls().join(Terms.collect(q, queryParsingAnalyzer));

      // collect synonyms
      if (synonyms) {
         q += " " + Joiner.on(' ').skipNulls().join(Terms.collect(q, synonymAnalyzer));
      }

      // check boosts
      if (boost && rb.req.getParams().get("bq") == null) {
         Collection<CharSequence> terms = Terms.collect(q, boostAnalyzer);

         // add extra terms
         if (boostExtraTerms != null) {
            terms.addAll(Sets.newHashSet(Splitter.on(',').omitEmptyStrings().split(boostExtraTerms)));
         }

         // add boosts
         if (!terms.isEmpty()) {
            params.add("bq", String.format(Locale.US, "{!%s qf='%s' mm=1 bq=''} %s", boostQueryType,
                  rb.req.getParams().get(BOOST_FIELDS, computeFactorizedQueryFields(rb.req, boostFactor)),
                  Joiner.on(' ').join(terms)));

            // add debug
            if (rb.isDebugQuery()) {
               BmaxDebugInfo.add(rb, COMPONENT_NAME + ".boost.terms", Joiner.on(' ').join(terms));
            }
         }
      }

      // check penalizes
      if (penalize && rb.req.getParams().get("rq") == null) {
         Collection<CharSequence> terms = Terms.collect(q, penalizeAnalyzer);

         // add extra terms
         if (penalizeExtraTerms != null) {
            terms.addAll(Sets.newHashSet(Splitter.on(',').omitEmptyStrings().split(penalizeExtraTerms)));
         }

         // add boosts
         if (!terms.isEmpty()) {
            params.add("rq", String.format(Locale.US, "{!rerank reRankQuery=$rqq reRankDocs=%s reRankWeight=%.1f}",
                  penalizeDocs, penalizeFactor));

            // join terms once. save cpu.
            String joinedTerms = Joiner.on(" OR ").join(terms);

            // iterate query fields
            StringBuilder rerank = new StringBuilder();
            Map<String, Float> queryFields = SolrPluginUtils.parseFieldBoosts(rb.req.getParams().getParams(
                  DisMaxParams.QF));
            for (Entry<String, Float> field : queryFields.entrySet()) {
               if (rerank.length() > 0) {
                  rerank.append(" OR ");
               }

               rerank.append(field.getKey());
               rerank.append(":(");
               rerank.append(joinedTerms);
               rerank.append(')');
            }

            // append rerank query
            params.add("rqq", rerank.toString());

            // add debug
            if (rb.isDebugQuery()) {
               BmaxDebugInfo.add(rb, COMPONENT_NAME + ".penalize.terms", Joiner.on(' ').join(terms));
            }
         }
      }

      rb.req.setParams(params);
   }

   /**
    * Computes
    */
   protected String computeFactorizedQueryFields(SolrQueryRequest request, float factor) {
      checkNotNull(request, "Pre-condition violated: request must not be null.");

      StringBuilder qf = new StringBuilder();

      // parse fields and boosts
      Map<String, Float> fieldBoosts = SolrPluginUtils.parseFieldBoosts(request.getParams().getParams(DisMaxParams.QF));

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

   @Override
   public String getSource() {
      return "https://github.com/shopping24/solr-bmax-queryparser";
   }

}
