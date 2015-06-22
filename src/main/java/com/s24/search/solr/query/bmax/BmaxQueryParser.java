package com.s24.search.solr.query.bmax;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.ExtendedDismaxQParser;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.util.SolrPluginUtils;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.s24.search.solr.util.BmaxDebugInfo;

public class BmaxQueryParser extends ExtendedDismaxQParser {

   public static final String PARAM_SYNONYM_BOOST = "bmax.synonym.boost";
   public static final String PARAM_SYNONYM_EXTRA = "bmax.synonym.extra";

   private static final String WILDCARD = "*:*";

   private final Analyzer synonymAnalyzer;
   private final Analyzer subtopicAnalyzer;
   private final Analyzer queryParsingAnalyzer;

   private final float synonymBoost;

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
         Analyzer queryParsingAnalyzer, Analyzer synonymAnalyzer, Analyzer subtopicAnalyzer) {
      super(qstr, localParams, params, req);

      // mandatory
      checkNotNull(queryParsingAnalyzer, "Pre-condition violated: queryParsingAnalyzer must not be null.");
      this.queryParsingAnalyzer = queryParsingAnalyzer;

      // optional args
      this.synonymAnalyzer = synonymAnalyzer;
      this.subtopicAnalyzer = subtopicAnalyzer;

      // collect params
      this.synonymBoost = params.getFloat(PARAM_SYNONYM_BOOST, 0.01f);
   }

   @Override
   public Query parse() throws SyntaxError {
      BmaxQuery query = analyzeQuery();

      // save debug stuff
      if (BmaxDebugInfo.isDebug(getReq())) {
         getReq().getContext().put("bmaxQuery", query);
      }

      // create query
      return new BmaxLuceneQueryBuilder(query)
            .withMultiplicativeBoost(getMultiplicativeBoosts())
            .withBoostQueries(getBoostQueries())
            .withSchema(getReq().getSchema())
            .build();
   }

   protected BmaxQuery analyzeQuery() {
      BmaxQuery query = new BmaxQuery();

      // transfer parameters
      query.setSynonymBoost(synonymBoost);

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

               // add subtopics. Effectivly, synonyms and subtopics are treated
               // the same.
               if (subtopicAnalyzer != null) {
                  query.getTermsAndSubtopics().put(term, Sets.newHashSet(Terms.collect(term, subtopicAnalyzer)));
               }
            }
         }

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
