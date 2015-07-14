package com.s24.search.solr.query.bmax;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Locale;
import java.util.Map.Entry;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.search.Query;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestInfo;
import org.apache.solr.search.ExtendedDismaxQParser;
import org.apache.solr.search.SolrCache;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.util.SolrPluginUtils;

import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.s24.search.solr.query.bmax.BmaxQuery.BmaxTerm;
import com.s24.search.solr.util.BmaxDebugInfo;

import eu.danieldk.dictomaton.DictionaryBuilder;

public class BmaxQueryParser extends ExtendedDismaxQParser {

   public static final String PARAM_SYNONYM_BOOST = "bmax.synonym.boost";
   public static final String PARAM_SUBTOPIC_BOOST = "bmax.subtopic.boost";
   public static final String PARAM_TIE = DisMaxParams.TIE;
   public static final String PARAM_INSPECT_TERMS = "bmax.inspect";
   public static final String PARAM_BUILD_INSPECT_TERMS = "bmax.inspect.build";
   public static final String PARAM_INSPECT_MAX_TERMS = "bmax.inspect.maxterms";

   private static final String WILDCARD = "*:*";

   private final Analyzer synonymAnalyzer;
   private final Analyzer subtopicAnalyzer;
   private final Analyzer queryParsingAnalyzer;
   private final SolrCache<String, BmaxTermCacheEntry> fieldTermCache;

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
         SolrCache<String, BmaxTermCacheEntry> fieldTermCache) {
      super(qstr, localParams, params, req);

      // mandatory
      checkNotNull(queryParsingAnalyzer, "Pre-condition violated: queryParsingAnalyzer must not be null.");
      this.queryParsingAnalyzer = queryParsingAnalyzer;

      // optional args
      this.synonymAnalyzer = synonymAnalyzer;
      this.subtopicAnalyzer = subtopicAnalyzer;
      this.fieldTermCache = fieldTermCache;
   }

   @Override
   public Query parse() throws SyntaxError {
      // parse query
      BmaxQuery query = analyzeQuery();

      // analyze terms
      if (query.isBuildTermsInspectionCache() && fieldTermCache != null) {
         try {
            long start = System.currentTimeMillis();
            analyzeQueryFields(query);
            
            // add debug
            if (SolrRequestInfo.getRequestInfo() != null) {
               ResponseBuilder rb = SolrRequestInfo.getRequestInfo().getResponseBuilder();
               BmaxDebugInfo.add(rb, "bmax.inspect", String.format(Locale.US, "Built term inspection cache in %sms", (System.currentTimeMillis() - start)));
            }
         } catch (Exception e) {
            throw new SyntaxError(e);
         }
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
      if (SolrRequestInfo.getRequestInfo() != null) {
         ResponseBuilder rb = SolrRequestInfo.getRequestInfo().getResponseBuilder();
         
         BmaxDebugInfo.add(rb, "bmax.query",
               Joiner.on(' ').join(Collections2.transform(query.getTerms(), BmaxQuery.toQueryTerm)));
         BmaxDebugInfo.add(rb, "bmax.synonyms",
               Joiner.on(' ').join(Iterables.concat(Iterables.transform(query.getTerms(), BmaxQuery.toSynonyms))));
         BmaxDebugInfo.add(rb, "bmax.subtocpics",
               Joiner.on(' ').join(Iterables.concat(Iterables.transform(query.getTerms(), BmaxQuery.toSubtopics))));
         BmaxDebugInfo.add(rb, "bmax.queryClauseCount", String.valueOf(queryBuilder.getQueryClauseCount()));
      }
      
      // done
      return result;
   }

   protected void analyzeQueryFields(BmaxQuery query) throws Exception {
      checkNotNull(query, "Pre-condition violated: query must not be null.");

      // iterate query fields
      for (Entry<String, Float> field : query.getFieldsAndBoosts().entrySet()) {

         // fill on cache miss
         BmaxTermCacheEntry cache = fieldTermCache.get(field.getKey());
         if (cache == null) {

            // check the number of terms for the field. If below the configured
            // threshold, build a dictomaton lookup
            SortedDocValues values = FieldCache.DEFAULT.getTermsIndex(getReq().getSearcher().getAtomicReader(),
                  field.getKey());

            // inspect and precache terms
            if (values.getValueCount() < query.getMaxInspectTerms()) {
               DictionaryBuilder builder = new DictionaryBuilder();

               // iterate values and add to dictionary
               TermsEnum terms = values.termsEnum();
               while (terms.next() != null) {
                  builder.add(terms.term().utf8ToString());
               }

               // add computed dictionary
               fieldTermCache
                     .put(field.getKey(), new BmaxTermCacheEntry(builder.build(), values.getValueCount(), true));
            } else {
               fieldTermCache.put(field.getKey(), new BmaxTermCacheEntry(values.getValueCount()));
            }
         }
      }
   }

   protected BmaxQuery analyzeQuery() {
      BmaxQuery query = new BmaxQuery();

      // get parameters
      query.setSynonymBoost(getReq().getParams().getFloat(PARAM_SYNONYM_BOOST, 0.1f));
      query.setSubtopicBoost(getReq().getParams().getFloat(PARAM_SUBTOPIC_BOOST, 0.01f));
      query.setTieBreakerMultiplier(getReq().getParams().getFloat(PARAM_TIE, 0.00f));
      query.setInspectTerms(getReq().getParams().getBool(PARAM_INSPECT_TERMS, false));
      query.setMaxInspectTerms(getReq().getParams().getInt(PARAM_INSPECT_MAX_TERMS, 1024 * 8));
      query.setBuildTermsInspectionCache(getReq().getParams().getBool(PARAM_BUILD_INSPECT_TERMS, false));

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
            for (CharSequence term : Terms.collect(getString(), queryParsingAnalyzer)) {

               // create bmax representation
               BmaxTerm bt = new BmaxTerm(term);

               // add synonyms and extra synonyms
               if (synonymAnalyzer != null) {
                  bt.getSynonyms().addAll(Collections2.filter(
                        Terms.collect(term, synonymAnalyzer), 
                        Predicates.not(Predicates.equalTo(term))));
               }

               // add subtopics.
               if (subtopicAnalyzer != null) {
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
