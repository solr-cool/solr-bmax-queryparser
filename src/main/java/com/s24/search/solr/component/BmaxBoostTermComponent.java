package com.s24.search.solr.component;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.s24.search.solr.component.BmaxBoostConstants.*;

import java.io.IOException;

import com.s24.search.solr.component.termstrategy.TermRankingStrategy;
import com.s24.search.solr.component.termstrategy.TermRankingStrategyBuilder;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.apache.lucene.analysis.Analyzer;
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

import com.google.common.base.Joiner;
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

   // the defType for the boost query generated for the boost terms
   private String penalizeQueryType;

   @Override
   public void init(@SuppressWarnings("rawtypes") NamedList args) {
      super.init(args);

      SolrParams configuration = SolrParams.toSolrParams(args);
      queryParsingFieldType = configuration.get("queryParsingFieldType");
      synonymFieldType = configuration.get("synonymFieldType");
      boostTermFieldType = configuration.get("boostTermFieldType");
      penalizeTermFieldType = configuration.get("penalizeTermFieldType");
      boostQueryType = configuration.get("boostQueryType", "dismax");
      penalizeQueryType = configuration.get("penalizeQueryType", "dismax");
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
         String debugMessage = applyPenalizing(params,schema,q);
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
      final String queryFields = (params.get(BOOST_FIELDS) != null ? BOOST_FIELDS : DisMaxParams.QF);
      final Analyzer boostAnalyzer = schema.getFieldTypeByName(boostTermFieldType).getQueryAnalyzer();
      final int boostDocCount = params.getInt(BOOST_DOC_COUNT, 400);

      TermRankingStrategyBuilder termRankingStrategyBuilder = new TermRankingStrategyBuilder();
      switch(boostStrategy) {
         case "bq": termRankingStrategyBuilder.additiveTermRankingStrategy(); break;
         case "boost": termRankingStrategyBuilder.multiplicativeTermRankingStrategy(); break;
         case "rq": termRankingStrategyBuilder.rerankTermRankingStrategy(boostDocCount); break;
      }

      TermRankingStrategy strategy = termRankingStrategyBuilder
              .forQuery(q)
              .withAnalyzer(boostAnalyzer)
              .withQueryField(queryFields)
              .withExtraTerms(boostExtraTerms)
              .withQueryType(boostQueryType)
              .withFactor(boostFactor)
              .build();

      return strategy.apply(params);
   }

   private String applyPenalizing(final ModifiableSolrParams params, final IndexSchema schema, String q) {
      final String penalizeExtraTerms = params.get(PENALIZE_EXTRA_TERMS);
      final float penalizeFactor = Math.abs(params.getFloat(PENALIZE_FACTOR, 100.0f));
      final String penalizeStrategy = params.get(PENALIZE_STRATEGY, VALUE_PENALIZE_STRATEGY_RERANK);
      final String queryFields = (params.get(PENALIZE_FIELDS) != null ? PENALIZE_FIELDS : DisMaxParams.QF);
      final Analyzer penalizeAnalyzer = schema.getFieldTypeByName(penalizeTermFieldType).getQueryAnalyzer();
      final int penalizeDocCount = params.getInt(PENALIZE_DOC_COUNT, 400);

      TermRankingStrategyBuilder termRankingStrategyBuilder = new TermRankingStrategyBuilder();
      switch(penalizeStrategy) {
         case "bq": termRankingStrategyBuilder.additiveTermRankingStrategy(); break;
         case "boost": termRankingStrategyBuilder.multiplicativeTermRankingStrategy(); break;
         case "rq": termRankingStrategyBuilder.rerankTermRankingStrategy(penalizeDocCount); break;
      }

      TermRankingStrategy strategy = termRankingStrategyBuilder
              .forQuery(q)
              .withAnalyzer(penalizeAnalyzer)
              .withQueryField(queryFields)
              .withExtraTerms(penalizeExtraTerms)
              .withQueryType(penalizeQueryType)
              .withFactor(penalizeFactor)
              .asPenalizer()
              .build();

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

   @Override
   public void process(ResponseBuilder rb) throws IOException {
      // noop
   }

   @Override
   public String getDescription() {
      return "Adds boost and penalize terms to a query";
   }

}
