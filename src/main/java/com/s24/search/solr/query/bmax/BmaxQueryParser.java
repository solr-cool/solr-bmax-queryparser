package com.s24.search.solr.query.bmax;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.ExtendedDismaxQParser;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.util.SolrPluginUtils;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public class BmaxQueryParser extends ExtendedDismaxQParser {

   public static final String PARAM_BOOST_DOWN_TERM_WEIGHT = "bmax.boostDownTerm.weight";
   public static final String PARAM_BOOST_DOWN_TERM_ENABLE = "bmax.boostDownTerm.enable";
   public static final String PARAM_BOOST_UP_TERM_ENABLE = "bmax.boostUpTerm.enable";
   public static final String PARAM_BOOST_UP_TERM_QF = "bmax.boostUpTerm.qf";
   public static final String PARAM_SYNONYM_BOOST = "bmax.synonym.boost";
   public static final String PARAM_MANIPULATE_DOCUMENT_FREQUENCIES = "bmax.manipulateDocumentFrequencies";
   public static final String PARAM_MANIPULATE_TERM_FREQUENCIES = "bmax.manipulateTermFrequencies";

   private final Analyzer boostUpAnalyzer;
   private final Analyzer boostDownAnalyzer;
   private final Analyzer synonymAnalyzer;
   private final Analyzer queryParsingAnalyzer;

   private final float boostDownTermWeight;
   private final float synonymBoost;
   private final boolean boostDownTermEnabled;
   private final boolean boostUpTermEnabled;

   public BmaxQueryParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req,
         Analyzer queryParsingAnalyzer, Analyzer synonymAnalyzer, Analyzer boostUpAnalyzer, Analyzer boostDownAnalyzer) {
      super(qstr, localParams, params, req);

      checkNotNull(queryParsingAnalyzer, "Pre-condition violated: queryParsingAnalyzer must not be null.");

      this.queryParsingAnalyzer = queryParsingAnalyzer;
      this.synonymAnalyzer = synonymAnalyzer;
      this.boostDownAnalyzer = boostDownAnalyzer;
      this.boostUpAnalyzer = boostUpAnalyzer;

      this.boostDownTermEnabled = params.getBool(PARAM_BOOST_DOWN_TERM_ENABLE, true);
      this.boostUpTermEnabled = params.getBool(PARAM_BOOST_UP_TERM_ENABLE, true);
      this.boostDownTermWeight = -Math.abs(params.getFloat(PARAM_BOOST_DOWN_TERM_WEIGHT, 2.0f));
      this.synonymBoost = params.getFloat(PARAM_SYNONYM_BOOST, 0.01f);
   }

   @Override
   public Query parse() throws SyntaxError {
      BmaxQuery query = analyzeQuery();

      // handle boost down via rerank plugin
      appendRerankParameters(query);

      // create query
      return new BmaxLuceneQueryBuilder(query)
            .withMultiplicativeBoost(getMultiplicativeBoosts())
            .withSchema(getReq().getSchema())
            .withIndexSearcher(getReq().getSearcher())
            .build();
   }

   protected BmaxQuery analyzeQuery() {
      BmaxQuery query = new BmaxQuery();

      // transfer parameters
      query.setSynonymBoost(synonymBoost);
      query.setManipulateTermFrequencies(getReq().getParams().getBool(PARAM_MANIPULATE_TERM_FREQUENCIES, true));
      query.setManipulateDocumentFrequencies(getReq().getParams().getBool(PARAM_MANIPULATE_DOCUMENT_FREQUENCIES, true));

      try {
         // extract fields and boost
         query.getFieldsAndBoosts().putAll(
               SolrPluginUtils.parseFieldBoosts(getReq().getParams().getParams(DisMaxParams.QF)));

         // extract separate boost fields and their boosts
         Map<String, Float> boostFieldsAndBoosts = SolrPluginUtils.parseFieldBoosts(getReq().getParams().getParams(
               PARAM_BOOST_UP_TERM_QF));
         if (boostFieldsAndBoosts.isEmpty()) {
            query.getBoostFieldsAndBoosts().putAll(query.getFieldsAndBoosts());
         } else {
            query.getBoostFieldsAndBoosts().putAll(boostFieldsAndBoosts);
         }

         // iterate terms
         for (String term : Terms.collect(getString(), queryParsingAnalyzer)) {

            // add term
            query.getTermsAndSynonyms().put(term, new HashSet<String>());

            if (synonymAnalyzer != null) {
               query.getTermsAndSynonyms().get(term)
                     .addAll(Sets.newHashSet(Terms.collect(qstr, synonymAnalyzer)));
            }
         }

         // boost up and down terms
         if (boostUpAnalyzer != null && boostUpTermEnabled) {
            query.getBoostUpTerms().addAll(Sets.newHashSet(Terms.collect(qstr, boostUpAnalyzer)));
         }
         if (boostDownAnalyzer != null && boostDownTermEnabled) {
            query.getBoostDownTerms().addAll(Sets.newHashSet(Terms.collect(qstr, boostDownAnalyzer)));
         }

         req.getContext().put("boostUpTerms", query.getBoostUpTerms());
         req.getContext().put("boostDownTerms", query.getBoostDownTerms());
         req.getContext().put("synonyms", Sets.newHashSet(Iterables.concat(query.getTermsAndSynonyms().values())));

         // done
         return query;
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   protected void appendRerankParameters(BmaxQuery query) {
      checkNotNull(query, "Pre-condition violated: query must not be null.");

      // collect penalize terms
      Collection<String> terms = query.getBoostDownTerms();

      if (!terms.isEmpty() && getParams() instanceof ModifiableSolrParams) {

         // join terms once. save cpu.
         String joinedTerms = Joiner.on(" OR ").join(terms);

         // add control local params
         ((ModifiableSolrParams) getParams()).add("rq", String.format(Locale.US,
               "{!rerank reRankQuery=$rqq reRankDocs=400 reRankWeight=%.1f}", boostDownTermWeight));

         // create rerank query
         StringBuilder rerank = new StringBuilder();
         for (String field : query.getFieldsAndBoosts().keySet()) {
            if (rerank.length() > 0) {
               rerank.append(" OR ");
            }

            rerank.append(field);
            rerank.append(":(");
            rerank.append(joinedTerms);
            rerank.append(')');
         }

         ((ModifiableSolrParams) getParams()).add("rqq", rerank.toString());
      }
   }
}
