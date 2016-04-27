package com.s24.search.solr.query.bmax;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.BoostedQuery;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.ConstValueSource;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QParser;
import org.apache.solr.search.SolrCache;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.util.SolrPluginUtils;

/**
 * A very basic dismax query parser which inspects the search terms to avoid creating query clauses for terms that are
 * known to not occur in any document in the index. The parser does not support boost queries or similar features. It is
 * mainly intended to be used as the query parser for a boost term query in combination with the bmax query parser for
 * the main query.
 */
public class TermInspectingDismaxQParser extends QParser {

   private static final String WILDCARD = "*:*";

   private final Analyzer queryParsingAnalyzer;
   private final SolrCache<String, FieldTermsDictionary> fieldTermCache;
   private final TermInspectingDismaxQueryConfiguration config;

   /**
    * Creates a query parser.
    * 
    * @param qstr
    *           the query string to parse.
    * @param localParams
    *           the local parameters.
    * @param params
    *           the global or default parameters.
    * @param req
    *           the request.
    * @param queryParsingAnalyzer
    *           the analyzer that will be used to split the query string into query terms.
    * @param fieldTermCache
    *           the field term cache that is used to inspect the search terms. This parameter is optional and may be
    *           {@code null}.
    */
   public TermInspectingDismaxQParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req,
         Analyzer queryParsingAnalyzer, SolrCache<String, FieldTermsDictionary> fieldTermCache) {
      super(qstr, localParams, params, req);
      this.params = SolrParams.wrapDefaults(localParams, params);
      this.queryParsingAnalyzer = checkNotNull(queryParsingAnalyzer);
      this.fieldTermCache = fieldTermCache;
      this.config = new TermInspectingDismaxQueryConfiguration(localParams, params);
   }

   @Override
   public Query parse() throws SyntaxError {
      try {
         Set<CharSequence> terms = null;
         if (!WILDCARD.equals(getString())) {
            terms = Terms.collect(getString(), queryParsingAnalyzer);
         } else {
            return new MatchAllDocsQuery();
         }

         BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
         for (CharSequence term : terms) {
            queryBuilder.add(new BooleanClause(buildDismaxQuery(term), BooleanClause.Occur.SHOULD));
         }
         return queryBuilder.build();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   /**
    * Builds a dismax query for the given term in all query fields.
    */
   private Query buildDismaxQuery(CharSequence term) {
      List<Query> disjuncts = new ArrayList<>();

      for (String field : config.queryFields.keySet()) {
         Analyzer analyzer = getReq().getSchema().getField(field).getType().getQueryAnalyzer();
         disjuncts.addAll(buildTermQueries(field, Terms.collectTerms(term, analyzer, field)));
      }
      return new DisjunctionMaxQuery(disjuncts, config.tiebreaker);
   }

   /**
    * Creates the term queries for the given terms in the given field.
    */
   private Collection<Query> buildTermQueries(String field, Collection<Term> terms) {
      Collection<Query> result = new HashSet<>();
      for (Term term : terms) {
         // Check if term inspection is enabled and we have a cached term dictionary for the field
         FieldTermsDictionary fieldTerms = null;
         if (config.inspectTerms && fieldTermCache != null) {
            fieldTerms = fieldTermCache.get(field);
         }

         // Add a term query to the result unless we have a field terms dictionary and we know, based on that
         // dictionary, that the term does not occur in the field
         if (fieldTerms == null || fieldTerms.fieldMayContainTerm(term.text())) {
            Query termQuery = new TermQuery(term);
            if (config.queryFields.get(field) != null) {
               // We have a non-default boost value
               ValueSource bosstedValueSource = new ConstValueSource(config.queryFields.get(field));
               termQuery = new BoostedQuery(query, bosstedValueSource);
            }
            result.add(termQuery);
         }
      }
      return result;
   }

   /**
    * Simple container for configuration information used when parsing queries.
    */
   class TermInspectingDismaxQueryConfiguration {
      Map<String, Float> queryFields;
      SolrParams solrParams;
      float tiebreaker;
      boolean inspectTerms;

      TermInspectingDismaxQueryConfiguration(SolrParams localParams, SolrParams params) {
         solrParams = SolrParams.wrapDefaults(localParams, params);
         queryFields = SolrPluginUtils.parseFieldBoosts(solrParams.get(DisMaxParams.QF));
         tiebreaker = solrParams.getFloat(DisMaxParams.TIE, 0.0f);
         inspectTerms = solrParams.getBool(BmaxQueryParser.PARAM_INSPECT_TERMS, false);
      }
   }
}
