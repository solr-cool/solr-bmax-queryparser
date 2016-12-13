package com.s24.search.solr.query.bmax;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Locale;
import java.util.Map.Entry;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.Query;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestInfo;
import org.apache.solr.search.ExtendedDismaxQParser;
import org.apache.solr.search.SolrCache;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.util.SolrPluginUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.s24.search.solr.query.bmax.BmaxQuery.BmaxTerm;
import com.s24.search.solr.util.BmaxDebugInfo;

import eu.danieldk.dictomaton.DictionaryBuilder;
import eu.danieldk.dictomaton.DictionaryBuilderException;

public class BmaxQueryParser extends ExtendedDismaxQParser {

   private static final Logger log = LoggerFactory.getLogger(BmaxQueryParser.class);

   public static final String PARAM_SYNONYM_ENABLE = "bmax.synonym";
   public static final String PARAM_SYNONYM_BOOST = "bmax.synonym.boost";
   public static final String PARAM_SUBTOPIC_ENABLE = "bmax.subtopic";
   public static final String PARAM_SUBTOPIC_BOOST = "bmax.subtopic.boost";
   public static final String PARAM_SUBTOPIC_FIELDS = "bmax.subtopic.qf";
   public static final String PARAM_TIE = DisMaxParams.TIE;
   public static final String PARAM_INSPECT_TERMS = "bmax.inspect";
   public static final String PARAM_BUILD_INSPECT_TERMS = "bmax.inspect.build";

   private static final String WILDCARD = "*:*";

   private final Analyzer synonymAnalyzer;
   private final Analyzer subtopicAnalyzer;
   private final Analyzer queryParsingAnalyzer;
   private final SolrCache<String, FieldTermsDictionary> fieldTermCache;
   private final SolrParams params;
   private final boolean debugQuery;

   /**
    * Creates a new {@linkplain BmaxQueryParser}.
    *
    * @param qstr
    *           the original input query string
    * @param queryParsingAnalyzer
    *           the analyzer to parse the query with.
    * @param synonymAnalyzer
    *           the analyzer to parse synonyms out of the outcome of the <code>queryParsingAnalyzer</code>
    * @param subtopicAnalyzer
    */
   public BmaxQueryParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req,
         Analyzer queryParsingAnalyzer, Analyzer synonymAnalyzer, Analyzer subtopicAnalyzer,
         SolrCache<String, FieldTermsDictionary> fieldTermCache) {
      super(qstr, localParams, params, req);
      this.params = SolrParams.wrapDefaults(localParams, params);
      this.debugQuery = isDebugQuery();

      // mandatory
      checkNotNull(queryParsingAnalyzer, "Pre-condition violated: queryParsingAnalyzer must not be null.");
      this.queryParsingAnalyzer = queryParsingAnalyzer;

      // optional args
      this.synonymAnalyzer = synonymAnalyzer;
      this.subtopicAnalyzer = subtopicAnalyzer;
      this.fieldTermCache = fieldTermCache;
   }

   /**
    * Returns true if query information should be included in the debug output.
    */
   private boolean isDebugQuery() {
      // debugQuery=true is a legacy alternative for debug=all
      if (params.getBool(CommonParams.DEBUG_QUERY, false)) {
         return true;
      }

      // This code is adapted from SolrPluginUtils#getDebugInterests, but that code requires a ResponseBuilder to write
      // its result into.
      String[] debugParams = params.getParams(CommonParams.DEBUG);
      if (debugParams != null) {
         for (int i = 0; i < debugParams.length; i++) {
            if (debugParams[i].equalsIgnoreCase("all")
                  || debugParams[i].equalsIgnoreCase("true")
                  || debugParams[i].equalsIgnoreCase("query")) {
               return true;
            }
         }
      }

      return false;
   }

   @Override
   public Query parse() throws SyntaxError {
      // parse query
      BmaxQuery query = analyzeQuery();

      // analyze terms
      if (query.isBuildTermsInspectionCache() && fieldTermCache != null) {
         buildFieldTermCache(query);
      }

      // build query
      BmaxLuceneQueryBuilder queryBuilder = new BmaxLuceneQueryBuilder(query);
      Query result = queryBuilder
            .withMultiplicativeBoost(getMultiplicativeBoosts())
            .withBoostFunctions(getBoostFunctions())
            .withBoostQueries(getBoostQueries())
            .withSchema(getReq().getSchema())
            .withFieldTermCache(fieldTermCache)
            .build();

      // save debug stuff
      if (SolrRequestInfo.getRequestInfo() != null && debugQuery) {
         ResponseBuilder rb = SolrRequestInfo.getRequestInfo().getResponseBuilder();

         BmaxDebugInfo.add(rb, "bmax.query",
               Joiner.on(' ').join(Collections2.transform(query.getTerms(), BmaxQuery.toQueryTerm)));
         BmaxDebugInfo.add(rb, "bmax.synonyms",
               Joiner.on(' ').join(Iterables.concat(Iterables.transform(query.getTerms(), BmaxQuery.toSynonyms))));
         BmaxDebugInfo.add(rb, "bmax.subtopics",
               Joiner.on(' ').join(Iterables.concat(Iterables.transform(query.getTerms(), BmaxQuery.toSubtopics))));
         BmaxDebugInfo.add(rb, "bmax.queryClauseCount", String.valueOf(queryBuilder.getQueryClauseCount()));
      }

      // done
      return result;
   }

   protected void buildFieldTermCache(BmaxQuery query) {
      checkNotNull(query, "Pre-condition violated: query must not be null.");

      long start = System.currentTimeMillis();
      try {
         // iterate query fields
         for (Entry<String, Float> field : query.getFieldsAndBoosts().entrySet()) {

            // fill on cache miss
            FieldTermsDictionary fieldTerms = fieldTermCache.get(field.getKey());
            if (fieldTerms == null) {
               DictionaryBuilder builder = new DictionaryBuilder();
               org.apache.lucene.index.Terms terms = getReq().getSearcher().getSlowAtomicReader().terms(field.getKey());
               if (terms != null) {
                  for (TermsEnum termsEnum = terms.iterator(); termsEnum.next() != null;) {
                     String term = termsEnum.term().utf8ToString();
                     try {
                        builder.add(term);
                     } catch (DictionaryBuilderException e) {
                        // In rare cases there are entries like unicode signs that are not in lexicographical order.
                        // Dictomaton will throw this exception, but we just want to ignore this entry.
                        log.warn("Term {} not added to the dictionary (may no in lexicographical order).", term,
                              e.getMessage());
                     }
                  }
               }

               fieldTermCache.put(field.getKey(), new FieldTermsDictionary(builder.build()));
            }
         }

         if (SolrRequestInfo.getRequestInfo() != null && debugQuery) {
            ResponseBuilder rb = SolrRequestInfo.getRequestInfo().getResponseBuilder();
            BmaxDebugInfo.add(rb, "bmax.inspect", String.format(Locale.US, "Built term inspection cache in %sms",
                  (System.currentTimeMillis() - start)));
         }
      } catch (Exception e) {
         log.warn("Failed to build fieldTermCache", e);
      }
   }

   protected BmaxQuery analyzeQuery() {
      BmaxQuery query = new BmaxQuery();

      // get parameters
      query.setSynonymEnabled(params.getBool(PARAM_SYNONYM_ENABLE, true));
      query.setSynonymBoost(params.getFloat(PARAM_SYNONYM_BOOST, 0.1f));
      query.setSubtopicEnabled(params.getBool(PARAM_SUBTOPIC_ENABLE, true));
      query.setSubtopicBoost(params.getFloat(PARAM_SUBTOPIC_BOOST, 0.01f));
      query.setTieBreakerMultiplier(params.getFloat(PARAM_TIE, 0.00f));
      query.setInspectTerms(params.getBool(PARAM_INSPECT_TERMS, false));
      query.setBuildTermsInspectionCache(params.getBool(PARAM_BUILD_INSPECT_TERMS, false));

      try {
         // extract fields and boost
         query.getFieldsAndBoosts().putAll(
               SolrPluginUtils.parseFieldBoosts(params.getParams(DisMaxParams.QF)));

         // get subtopic fields and boost, fallback to defaults
         query.getSubtopicFieldsAndBoosts().putAll(
               SolrPluginUtils.parseFieldBoosts(params.get(PARAM_SUBTOPIC_FIELDS, params.get(DisMaxParams.QF))));

         // set default field boost for the unboosted ones
         for (String fieldname : query.getFieldsAndBoosts().keySet()) {
            if (query.getFieldsAndBoosts().get(fieldname) == null) {
               query.getFieldsAndBoosts().put(fieldname, 1.0f);
            }
         }
         for (String fieldname : query.getSubtopicFieldsAndBoosts().keySet()) {
            if (query.getSubtopicFieldsAndBoosts().get(fieldname) == null) {
               query.getSubtopicFieldsAndBoosts().put(fieldname, 1.0f);
            }
         }

         // iterate terms
         if (!WILDCARD.equals(getString())) {
            for (final CharSequence term : Terms.collect(getString(), queryParsingAnalyzer)) {

               // create bmax representation
               BmaxTerm bt = new BmaxTerm(term);

               // add synonyms and extra synonyms
               if (query.isSynonymEnabled() && synonymAnalyzer != null) {
                  bt.getSynonyms().addAll(Collections2.filter(
                        Terms.collect(term, synonymAnalyzer),
                        Predicates.not(new Predicate<CharSequence>() {
                           @Override
                           public boolean apply(CharSequence t) {
                              return t.toString().equals(term.toString());
                           }
                        })));
               }

               // add subtopics.
               if (query.isSubtopicEnabled() && subtopicAnalyzer != null) {
                  bt.getSubtopics().addAll(Terms.collect(term, subtopicAnalyzer));

                  // run synonyms through subtopics as well
                  if (!bt.getSynonyms().isEmpty() && synonymAnalyzer != null) {
                     for (CharSequence synonym : bt.getSynonyms()) {
                        bt.getSubtopics().addAll(Terms.collect(synonym, subtopicAnalyzer));
                     }
                  }
               }

               // add term
               query.getTerms().add(bt);
            }
         }

         // done
         return query;
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }
}
