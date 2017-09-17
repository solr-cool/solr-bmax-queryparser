package com.s24.search.solr.query.bmax;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.TermsQuery;
import org.apache.lucene.queries.function.BoostedQuery;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.ProductFloatFunction;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.QueryBuilder;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.search.FieldParams;
import org.apache.solr.search.SolrCache;

import com.s24.search.solr.query.bmax.BmaxQuery.BmaxTerm;

/**
 * Builds the bmax dismax query
 *
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public class BmaxLuceneQueryBuilder {

   private final static float USER_QUERY_FIELD_BOOST = 1.0f;

   private final BmaxQuery bmaxquery;
   private List<ValueSource> multiplicativeBoost;
   private List<Query> boostQueries;
   private List<Query> additiveBoostFunctions;
   private IndexSchema schema;
   private SolrCache<String, FieldTermsDictionary> fieldTermCache;
   private int queryClauseCount = 0;

   public BmaxLuceneQueryBuilder(BmaxQuery bmaxQuery) {
      checkNotNull(bmaxQuery, "Pre-condition violated: bmaxQuery must not be null.");

      this.bmaxquery = bmaxQuery;
   }

   public int getQueryClauseCount() {
      return queryClauseCount;
   }

   public BmaxLuceneQueryBuilder withFieldTermCache(SolrCache<String, FieldTermsDictionary> fieldTermCache) {
      this.fieldTermCache = fieldTermCache;
      return this;
   }

   public BmaxLuceneQueryBuilder withBoostQueries(List<Query> boostQueries) {
      checkNotNull(boostQueries, "Pre-condition violated: boostQueries must not be null.");

      this.boostQueries = boostQueries;
      return this;
   }

   public BmaxLuceneQueryBuilder withBoostFunctions(List<Query> boostFunctions) {
      checkNotNull(boostFunctions, "Pre-condition violated: boostFunctions must not be null.");

      this.additiveBoostFunctions = boostFunctions;
      return this;
   }

   public BmaxLuceneQueryBuilder withMultiplicativeBoost(List<ValueSource> multiplicativeBoost) {
      checkNotNull(multiplicativeBoost, "Pre-condition violated: multiplicativeBoost must not be null.");

      this.multiplicativeBoost = multiplicativeBoost;
      return this;
   }

   public BmaxLuceneQueryBuilder withSchema(IndexSchema schema) {
      checkNotNull(schema, "Pre-condition violated: schema must not be null.");

      this.schema = schema;
      return this;
   }

   // ---- go build yourself

   public Query build() {
      Query inner = buildWrappingQuery();

      // default
      Query main = inner;

      // add boost params
      if (multiplicativeBoost != null) {
         if (multiplicativeBoost.size() > 1) {
            ValueSource prod = new ProductFloatFunction(
                  multiplicativeBoost.toArray(new ValueSource[multiplicativeBoost.size()]));
            main = new BoostedQuery(inner, prod);
         }
         if (multiplicativeBoost.size() == 1) {
            main = new BoostedQuery(inner, multiplicativeBoost.get(0));
         }
      }

      return main;
   }

   /**
    * Builds the wrapping, unboosted query.
    */
   protected Query buildWrappingQuery() {
      if (bmaxquery.getTerms().isEmpty()) {
         return new MatchAllDocsQuery();
      }

      Builder bq = new Builder();
      bq.setDisableCoord(true);

      // iterate terms
      for (BmaxTerm term : bmaxquery.getTerms()) {

         // append dismax query as clause
         bq.add(new BooleanClause(buildDismaxQuery(term), Occur.MUST));
      }

      // add boost queries
      if (boostQueries != null) {
         for (Query f : boostQueries) {
            bq.add(f, Occur.SHOULD);
         }
      }

      // add additive boost function
      if (additiveBoostFunctions != null) {
         for (Query f : additiveBoostFunctions) {
            bq.add(f, Occur.SHOULD);
         }
      }

      // add phrase boost
      getPhraseFieldQueries().ifPresent(pfQuery -> bq.add(pfQuery, Occur.SHOULD));

      // done
      return bq.build();
   }

   // ---- main query

   /**
    * Builds a dismax query for the given terms and their corresponding synonyms.
    */
   protected Query buildDismaxQuery(BmaxTerm term) {
      checkNotNull(term, "Pre-condition violated: term must not be null.");

      List<Query> dismaxQueries = new ArrayList<>();

      // iterate fields and build concrete queries
      for (Entry<String, Float> field : bmaxquery.getFieldsAndBoosts().entrySet()) {

         // get analyzer to work with
         Analyzer analyzer = schema.getField(field.getKey()).getType().getQueryAnalyzer();

         // add main term clause
         Query queries = buildTermQueries(field.getKey(), field.getValue().floatValue(),
               Terms.collectTerms(term.getTerm(), analyzer, field.getKey()),
               USER_QUERY_FIELD_BOOST);
         if (queries != null) {
            dismaxQueries.add(queries);
         }

         // add synonym clause
         if (!term.getSynonyms().isEmpty()) {
            for (CharSequence synonym : term.getSynonyms()) {
               Query termQueries = buildTermQueries(field.getKey(), field.getValue().floatValue(),
                     Terms.collectTerms(synonym, analyzer, field.getKey()),
                     bmaxquery.getSynonymBoost());
               if (termQueries != null) {
                  dismaxQueries.add(termQueries);
               }
            }
         }
      }

      // if we have subtopics for the current term
      if (!term.getSubtopics().isEmpty()) {

         // iterate subtopic fields and build concrete queries
         for (Entry<String, Float> field : bmaxquery.getSubtopicFieldsAndBoosts().entrySet()) {

            // get analyzer to work with
            Analyzer analyzer = schema.getField(field.getKey()).getType().getQueryAnalyzer();

            // add subtopic clause
            for (CharSequence subtopic : term.getSubtopics()) {
               Query termQueries = buildTermQueries(field.getKey(), field.getValue().floatValue(),
                     Terms.collectTerms(subtopic, analyzer, field.getKey()),
                     bmaxquery.getSubtopicBoost());
               if (termQueries != null) {
                  dismaxQueries.add(termQueries);
               }
            }
         }
      }

      return new DisjunctionMaxQuery(dismaxQueries, bmaxquery.getTieBreakerMultiplier());
   }

   // ---- term queries

   /**
    * Combines the given terms to a valid dismax query for the field given.
    */
   protected Query buildTermQueries(String field, float fieldBoost, Collection<Term> terms,
         float extraBoost) {
      checkNotNull(field, "Pre-condition violated: field must not be null.");
      checkNotNull(terms, "Pre-condition violated: terms must not be null.");

      FieldTermsDictionary fieldTerms = null;

      // check for term inspection && available term cache
      if (bmaxquery.isInspectTerms() && fieldTermCache != null) {

         // or on activated term inspection
         fieldTerms = fieldTermCache.get(field);
      }

      Collection<Term> filteredTerms = new ArrayList<>();

      for (Term term : terms) {
         // Add the term to the query if we don't have a cache, or if the cache
         // says that the field may contain the term
         if (fieldTerms == null || fieldTerms.fieldMayContainTerm(term.text())) {
            filteredTerms.add(term);
         }
      }

      return filteredTerms.isEmpty() ? null : buildTermQuery(filteredTerms, fieldBoost * extraBoost);
   }

   /**
    * Builds a term query and manipulates the document frequency, if given.
    */
   protected Query buildTermQuery(Collection<Term> terms, float boost) {
      checkNotNull(terms, "Pre-condition violated: term must not be null.");

      Query termsquery = new TermsQuery(terms);
      queryClauseCount++;

      // set boost
      if (boost > 0f) {
         return withBoostFactor(termsquery, boost);
      }

      return termsquery;
   }

   protected Optional<Query> getPhraseFieldQueries()  {

      // sloppy phrase queries for proximity
      final List<FieldParams> allPhraseFields = bmaxquery.getAllPhraseFields();

      if (allPhraseFields.size() > 0) {

         final List<BmaxTerm> bmaxTerms = bmaxquery.getTerms();

         if (bmaxTerms.size() > 1) { // it's a phrase

            final List<CharSequence> terms = bmaxTerms.stream().map(BmaxTerm::getTerm).collect(Collectors.toList());
            final List<Query> disjuncts = new LinkedList<>();

            final QueryBuilder queryBuilder = new QueryBuilder(schema.getQueryAnalyzer());

            final Map<Integer, List<String>> shingles = new HashMap<>(2);
            String queryStringAsPhrase = null;

            for (final FieldParams fieldParams : allPhraseFields) {

               final int n = fieldParams.getWordGrams();
               final int slop = fieldParams.getSlop();
               final String fieldname = fieldParams.getField();

               if (n == 0) { // entire phrase


                  if (queryStringAsPhrase == null) {
                     // We don't have the entire query string as a phrase yet
                     // (= this is the first field in the allPhraseFields loop)
                     queryStringAsPhrase = terms.stream().collect(Collectors.joining(" "));

                  }
                  final Query pq = queryBuilder.createPhraseQuery(fieldname, queryStringAsPhrase, slop);
                  if (pq != null) {
                     disjuncts.add(withBoostFactor(pq, fieldParams.getBoost()));
                  }

               } else if (n <= terms.size()) { // pf2 or pf3

                  // get/create field-independent bi-gram or tri-gram strings
                  final List<String> shinglesN = shingles.computeIfAbsent(n, nGramSize -> {

                     final List<String> newShingles = new LinkedList<>();

                     for (int i = 0, lenI = terms.size() - nGramSize + 1; i < lenI; i++) {


                        final StringBuilder sb = new StringBuilder();

                        for (int j = i, lenJ = j + n; j < lenJ; j++) {
                           if (sb.length() > 0) {
                              sb.append(' ');
                           }
                           sb.append(terms.get(j));
                        }
                        newShingles.add(sb.toString());
                     }

                     return newShingles;
                  });

                  // map bi-gram/tri-gram strings to phrase queries
                  final List<Query> nGramQueries = shinglesN.stream()
                          .map(nGram ->  queryBuilder.createPhraseQuery(fieldname, nGram, slop))
                          .filter(q -> q != null)
                          .collect(Collectors.toList());


                  switch (nGramQueries.size()) {
                     case 0: break;
                     case 1: {
                        disjuncts.add(withBoostFactor(nGramQueries.get(0), fieldParams.getBoost()));
                        break;

                     }
                     default:
                        // If we have > 1 n-gram phrase for this field, aggregate their scores using
                        // a BooleanQuery with all clauses being optional
                        final BooleanQuery.Builder builder = new BooleanQuery.Builder();
                        builder.setDisableCoord(true);
                        builder.setMinimumNumberShouldMatch(1);

                        for (final Query nGramQuery : nGramQueries) {
                           builder.add(nGramQuery, BooleanClause.Occur.SHOULD);
                        }

                        disjuncts.add(withBoostFactor(builder.build(), fieldParams.getBoost()));
                  }
               }
            }

            switch (disjuncts.size()) {
               case 0: break;
               case 1: return Optional.of(disjuncts.get(0));
               default :
                  return Optional.of(new DisjunctionMaxQuery(disjuncts, bmaxquery.getPhraseBoostTieBreaker()));
            }
         }


      }

      return Optional.empty();
   }

   public static Query withBoostFactor(final Query query, float boostFactor) {
      return boostFactor == 1f ? query : new BoostQuery(query, boostFactor);
   }


}
