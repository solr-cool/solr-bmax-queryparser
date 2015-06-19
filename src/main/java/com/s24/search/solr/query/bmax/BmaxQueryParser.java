package com.s24.search.solr.query.bmax;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.DisMaxParams;
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
   public static final String PARAM_BOOST_DOWN_TERM_WITHSYNONYMS = "bmax.boostDownTerm.withsynonyms";
   public static final String PARAM_BOOST_DOWN_TERM_EXTRA = "bmax.boostDownTerm.extra";
   public static final String PARAM_BOOST_UP_TERM_ENABLE = "bmax.boostUpTerm.enable";
   public static final String PARAM_BOOST_UP_TERM_WITHSYNONYMS = "bmax.boostUpTerm.withsynonyms";
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
   private final Analyzer subtopicAnalyzer;
   private final Analyzer queryParsingAnalyzer;

   private final float boostDownTermWeight;
   private final float synonymBoost;
   private final boolean boostDownTermEnabled;
   private final boolean boostDownTermWithSynonyms;
   private final boolean boostUpTermEnabled;
   private final boolean boostUpTermWithSynonyms;
   private final boolean explicitSortEnabled;

   /**
    * Creates a new {@linkplain BmaxQueryParser}.
    * 
    * @param qstr
    *           the original input query string
    * @param queryParsingAnalyzer
    *           the analyzer to parse the query with.
    * @param synonymAnalyzer
    *           the analyzer to parse synonyms out of the outcome of the
    *           <code>queryParsingAnalyzer</code>
    * @param subtopicAnalyzer
    * @param boostUpAnalyzer
    * @param boostDownAnalyzer
    */
   public BmaxQueryParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req,
         Analyzer queryParsingAnalyzer, Analyzer synonymAnalyzer, Analyzer subtopicAnalyzer,
         Analyzer boostUpAnalyzer, Analyzer boostDownAnalyzer) {
      super(qstr, localParams, params, req);

      // mandatory
      checkNotNull(queryParsingAnalyzer, "Pre-condition violated: queryParsingAnalyzer must not be null.");
      this.queryParsingAnalyzer = queryParsingAnalyzer;

      // optional
      this.synonymAnalyzer = synonymAnalyzer;
      this.subtopicAnalyzer = subtopicAnalyzer;
      this.boostDownAnalyzer = boostDownAnalyzer;
      this.boostUpAnalyzer = boostUpAnalyzer;

      this.boostDownTermEnabled = params.getBool(PARAM_BOOST_DOWN_TERM_ENABLE, true);
      this.boostDownTermWithSynonyms = params.getBool(PARAM_BOOST_DOWN_TERM_WITHSYNONYMS, true);
      this.boostUpTermEnabled = params.getBool(PARAM_BOOST_UP_TERM_ENABLE, true);
      this.boostUpTermWithSynonyms = params.getBool(PARAM_BOOST_UP_TERM_WITHSYNONYMS, true);
      this.boostDownTermWeight = -Math.abs(params.getFloat(PARAM_BOOST_DOWN_TERM_WEIGHT, 2.0f));
      this.synonymBoost = params.getFloat(PARAM_SYNONYM_BOOST, 0.01f);

      // check sort
      String sort = params.get(CommonParams.SORT, "score desc").toLowerCase(Locale.US);
      this.explicitSortEnabled = !sort.contains("score");
   }

   @Override
   public Query parse() throws SyntaxError {
      BmaxQuery query = analyzeQuery();

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

            for (CharSequence term : Terms.collect(getString(), queryParsingAnalyzer)) {

               // add term
               query.getTermsAndSynonyms().put(term, new HashSet<CharSequence>());

               // add synonyms and extra synonyms
               if (synonymAnalyzer != null) {
                  query.getTermsAndSynonyms().get(term)
                        .addAll(Sets.newHashSet(Terms.collect(term, synonymAnalyzer)));
               }

               // add extra synonyms from request
               if (extraSynonyms.containsKey(term)) {
                  query.getTermsAndSynonyms().get(term).addAll(extraSynonyms.get(term));
               }

               // add subtopics
               if (subtopicAnalyzer != null) {
                  query.getTermsAndSubtopics().put(term, Sets.newHashSet(Terms.collect(term, subtopicAnalyzer)));
               }
            }
         }

         // evaluate boostterms only if explicit sort is not given
         if (!explicitSortEnabled) {

            // extract separate boost fields and their boosts
            if (boostUpTermEnabled || boostDownTermEnabled) {
               Map<String, Float> boostFieldsAndBoosts = SolrPluginUtils.parseFieldBoosts(getReq().getParams()
                     .getParams(
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
                  String boostInput = getString();

                  if (boostUpTermWithSynonyms) {
                     boostInput += " " + Joiner.on(' ').join(Iterables.concat(query.getTermsAndSynonyms().values()));
                  }

                  query.getBoostUpTerms().addAll(Sets.newHashSet(Terms.collect(boostInput, boostUpAnalyzer)));
               }

               String boostUpTermExtra = getReq().getParams().get(PARAM_BOOST_UP_TERM_EXTRA);
               if (boostUpTermExtra != null) {
                  query.getBoostUpTerms().addAll(
                        Sets.newHashSet(Splitter.on(',').omitEmptyStrings().split(boostUpTermExtra)));
               }
            }
            req.getContext().put("boostUpTerms", query.getBoostUpTerms());
         }

         req.getContext().put("queryTerms", query.getTermsAndSynonyms().keySet());
         req.getContext().put("synonyms", Sets.newHashSet(Iterables.concat(query.getTermsAndSynonyms().values())));
         req.getContext().put("subtopics", Sets.newHashSet(Iterables.concat(query.getTermsAndSubtopics().values())));

         // done
         return query;
      } catch (Exception e) {
         throw new RuntimeException(e);
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
