package com.s24.search.solr.query.bmax;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestInfo;
import org.apache.solr.search.ExtendedDismaxQParser;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.util.SolrPluginUtils;

import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.s24.search.solr.query.bmax.BmaxQuery.BmaxTerm;
import com.s24.search.solr.util.BmaxDebugInfo;

public class BmaxQueryParser extends ExtendedDismaxQParser {

   public static final String PARAM_SYNONYM_BOOST = "bmax.synonym.boost";
   public static final String PARAM_SUBTOPIC_BOOST = "bmax.subtopic.boost";
   public static final String PARAM_TIE = "bmax.term.tie";

   private static final String WILDCARD = "*:*";

   private final Analyzer synonymAnalyzer;
   private final Analyzer subtopicAnalyzer;
   private final Analyzer queryParsingAnalyzer;

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
   }

   @Override
   public Query parse() throws SyntaxError {
      BmaxQuery query = analyzeQuery();

      // save debug stuff
      if (SolrRequestInfo.getRequestInfo() != null) {
         ResponseBuilder rb = SolrRequestInfo.getRequestInfo().getResponseBuilder();

         BmaxDebugInfo.add(rb, "bmax.query",
               Joiner.on(' ').join(Collections2.transform(query.getTerms(), BmaxQuery.toQueryTerm)));
         BmaxDebugInfo.add(rb, "bmax.synonyms",
               Joiner.on(' ').join(Iterables.concat(Iterables.transform(query.getTerms(), BmaxQuery.toSynonyms))));
         BmaxDebugInfo.add(rb, "bmax.subtocpics",
               Joiner.on(' ').join(Iterables.concat(Iterables.transform(query.getTerms(), BmaxQuery.toSubtopics))));
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

      // get parameters
      query.setSynonymBoost(getReq().getParams().getFloat(PARAM_SYNONYM_BOOST, 0.1f));
      query.setSubtopicBoost(getReq().getParams().getFloat(PARAM_SUBTOPIC_BOOST, 0.01f));
      query.setTieBreakerMultiplier(getReq().getParams().getFloat(PARAM_TIE, 0.00f));

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
                  bt.getSynonyms().addAll(Terms.collect(term, synonymAnalyzer));
               }

               // add subtopics. Effectivly, synonyms and subtopics are treated
               // the same.
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
