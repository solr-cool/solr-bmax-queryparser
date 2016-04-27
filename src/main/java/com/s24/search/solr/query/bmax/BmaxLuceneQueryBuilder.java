package com.s24.search.solr.query.bmax;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.BoostedQuery;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.ConstValueSource;
import org.apache.lucene.queries.function.valuesource.ProductFloatFunction;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.search.SolrCache;

import com.google.common.collect.Sets;
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
            bq.add(f, BooleanClause.Occur.SHOULD);
         }
      }

      // add additive boost function
      if (additiveBoostFunctions != null) {
         for (Query f : additiveBoostFunctions) {
            bq.add(f, BooleanClause.Occur.SHOULD);
         }
      }

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
         dismaxQueries.addAll(
               buildTermQueries(field.getKey(), field.getValue().floatValue(),
                     Terms.collectTerms(term.getTerm(), analyzer, field.getKey()),
                     USER_QUERY_FIELD_BOOST));

         // add synonym clause
         if (!term.getSynonyms().isEmpty()) {
            for (CharSequence synonym : term.getSynonyms()) {
               dismaxQueries.addAll(
                     buildTermQueries(field.getKey(), field.getValue().floatValue(),
                           Terms.collectTerms(synonym, analyzer, field.getKey()),
                           bmaxquery.getSynonymBoost()));
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
               dismaxQueries.addAll(
                     buildTermQueries(field.getKey(), field.getValue().floatValue(),
                           Terms.collectTerms(subtopic, analyzer, field.getKey()),
                           bmaxquery.getSubtopicBoost()));
            }
         }
      }

      return new DisjunctionMaxQuery(dismaxQueries, bmaxquery.getTieBreakerMultiplier());
   }

   // ---- term queries

   /**
    * Combines the given terms to a valid dismax query for the field given.
    */
   protected Collection<Query> buildTermQueries(String field, float fieldBoost, Collection<Term> terms,
         float extraBoost) {
      checkNotNull(field, "Pre-condition violated: field must not be null.");
      checkNotNull(terms, "Pre-condition violated: terms must not be null.");

      Collection<Query> queries = Sets.newHashSet();

      for (Term term : terms) {
         FieldTermsDictionary fieldTerms = null;

         // check for term inspection && available term cache
         if (bmaxquery.isInspectTerms() && fieldTermCache != null) {

            // or on activated term inspection
            fieldTerms = fieldTermCache.get(field);
         }

         // Add the term to the query if we don't have a cache, or if the cache
         // says that the field may contain the term
         if (fieldTerms == null || fieldTerms.fieldMayContainTerm(term.text())) {
            queries.add(buildTermQuery(term, fieldBoost * extraBoost));
         }
      }

      return queries;
   }

   /**
    * Builds a term query and manipulates the document frequency, if given.
    */
   protected Query buildTermQuery(Term term, float boost) {
      checkNotNull(term, "Pre-condition violated: term must not be null.");

      Query query = new TermQuery(term);
      queryClauseCount++;

      // set boost
      if (boost > 0f) {
         ValueSource bosstedValueSource = new ConstValueSource(boost);
         query = new BoostedQuery(query, bosstedValueSource);
      }

      return query;
   }
}
