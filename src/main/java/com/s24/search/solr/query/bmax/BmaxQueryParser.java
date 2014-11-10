package com.s24.search.solr.query.bmax;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class BmaxQueryParser extends ExtendedDismaxQParser {

   public static final String PARAM_BOOST_DOWN_TERM_WEIGHT = "bmax.boostDownTerm.weight";
   public static final String PARAM_BOOST_DOWN_TERM_ENABLE = "bmax.boostDownTerm.enable";
   public static final String PARAM_BOOST_DOWN_TERM_EXTRA = "bmax.boostDownTerm.extra";
   public static final String PARAM_BOOST_UP_TERM_ENABLE = "bmax.boostUpTerm.enable";
   public static final String PARAM_BOOST_UP_TERM_QF = "bmax.boostUpTerm.qf";
   public static final String PARAM_BOOST_UP_TERM_EXTRA = "bmax.boostUpTerm.extra";
   public static final String PARAM_SYNONYM_BOOST = "bmax.synonym.boost";
   public static final String PARAM_SYNONYM_EXTRA = "bmax.synonym.extra";
   public static final String PARAM_MANIPULATE_DOCUMENT_FREQUENCIES = "bmax.manipulateDocumentFrequencies";
   public static final String PARAM_MANIPULATE_TERM_FREQUENCIES = "bmax.manipulateTermFrequencies";

   private static final String WILDCARD = "*:*";

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
      query.setManipulateTermFrequencies(getReq().getParams().getBool(PARAM_MANIPULATE_TERM_FREQUENCIES, false));
      query.setManipulateDocumentFrequencies(getReq().getParams().getBool(PARAM_MANIPULATE_DOCUMENT_FREQUENCIES, false));

      try {
         // extract fields and boost
         query.getFieldsAndBoosts().putAll(
               SolrPluginUtils.parseFieldBoosts(getReq().getParams().getParams(DisMaxParams.QF)));

         // set default field boost for the unboosted ones
         for (String fieldname : query.getFieldsAndBoosts().keySet()) {
            if (query.getFieldsAndBoosts().get(fieldname) == null) {
               query.getFieldsAndBoosts().put(fieldname, 1.0f);
            }
         }

         // iterate terms
         if (!WILDCARD.equals(getString())) {

            // get extra synonyms
            Map<String, Set<String>> extraSynonyms = parseExtraSynonyms(getReq().getParams().get(PARAM_SYNONYM_EXTRA));

            for (String term : Terms.collect(getString(), queryParsingAnalyzer)) {

               // add term
               query.getTermsAndSynonyms().put(term, new HashSet<String>());

               // add synonyms and extra synonyms
               if (synonymAnalyzer != null) {
                  query.getTermsAndSynonyms().get(term)
                        .addAll(Sets.newHashSet(Terms.collect(term, synonymAnalyzer)));
               }
               if (extraSynonyms.containsKey(term)) {
                  query.getTermsAndSynonyms().get(term).addAll(extraSynonyms.get(term));
               }
            }
         }

         // extract separate boost fields and their boosts
         if (boostUpTermEnabled || boostDownTermEnabled) {
            Map<String, Float> boostFieldsAndBoosts = SolrPluginUtils.parseFieldBoosts(getReq().getParams().getParams(
                  PARAM_BOOST_UP_TERM_QF));
            if (boostFieldsAndBoosts.isEmpty()) {
               query.getBoostFieldsAndBoosts().putAll(query.getFieldsAndBoosts());
            } else {
               query.getBoostFieldsAndBoosts().putAll(boostFieldsAndBoosts);
            }
         }

         // boost up and down terms
         if (boostUpTermEnabled) {
            if (boostUpAnalyzer != null) {
               query.getBoostUpTerms().addAll(Sets.newHashSet(Terms.collect(getString(), boostUpAnalyzer)));
            }

            String boostUpTermExtra = getReq().getParams().get(PARAM_BOOST_UP_TERM_EXTRA);
            if (boostUpTermExtra != null) {
               query.getBoostUpTerms().addAll(
                     Sets.newHashSet(Splitter.on(',').omitEmptyStrings().split(boostUpTermExtra)));
            }
         }
         if (boostDownTermEnabled) {
            if (boostDownAnalyzer != null) {
               query.getBoostDownTerms().addAll(Sets.newHashSet(Terms.collect(getString(), boostDownAnalyzer)));
            }

            String boostDownTermExtra = getReq().getParams().get(PARAM_BOOST_DOWN_TERM_EXTRA);
            if (boostDownTermExtra != null) {
               query.getBoostDownTerms().addAll(
                     Sets.newHashSet(Splitter.on(',').omitEmptyStrings().split(boostDownTermExtra)));
            }
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
      checkArgument(getParams() instanceof ModifiableSolrParams,
            "Pre-condition violated: expression getParams() instanceof ModifiableSolrParams must be true.");

      // collect penalize terms
      Collection<String> terms = query.getBoostDownTerms();

      if (!terms.isEmpty()) {

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

   protected Map<String, Set<String>> parseExtraSynonyms(String synonyms) {
      final Map<String, Set<String>> result = Maps.newHashMap();

      // extras available?
      String extraSynonmDefinition = getReq().getParams().get(PARAM_SYNONYM_EXTRA);
      if (extraSynonmDefinition != null) {

         // split synonym definition
         Iterable<String> definitions = Splitter.on('|').omitEmptyStrings().trimResults().split(extraSynonmDefinition);
         for (String definition : definitions) {
            String[] args = definition.split("=>");
            if (args.length == 2) {
               result.put(args[0], Sets.newHashSet(Splitter.on(',').omitEmptyStrings().trimResults().split(args[1])));
            }
         }
      }

      return result;
   }
}
