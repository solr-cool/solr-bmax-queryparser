package com.s24.search.solr.query.bmax;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.BoostedQuery;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.ProductFloatFunction;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.solr.schema.IndexSchema;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Builds the bmax dismax query
 * 
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public class BmaxLuceneQueryBuilder {

   private final static float USER_QUERY_FIELD_BOOST = 1.0f;
   private final static int DOCUMENT_FREQUENCY_NO_MANIPULATION = -1;

   private final BmaxQuery bmaxquery;
   private List<ValueSource> multiplicativeBoost;
   private List<Query> boostQueries;
   private IndexSchema schema;

   public BmaxLuceneQueryBuilder(BmaxQuery bmaxQuery) {
      checkNotNull(bmaxQuery, "Pre-condition violated: bmaxQuery must not be null.");

      this.bmaxquery = bmaxQuery;
   }

   public BmaxLuceneQueryBuilder withBoostQueries(List<Query> boostQueries) {
      checkNotNull(boostQueries, "Pre-condition violated: boostQueries must not be null.");
      
      this.boostQueries = boostQueries;
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
      if (bmaxquery.getTermsAndSynonyms().isEmpty()) {
         return new MatchAllDocsQuery();
      }

      BooleanQuery bq = new BooleanQuery(true);

      // iterate terms
      for (Entry<CharSequence, Set<CharSequence>> termAndSynonyms : bmaxquery.getTermsAndSynonyms().entrySet()) {

         // create new entry to use in following functions
         Entry<CharSequence, Set<CharSequence>> term = Maps.immutableEntry(termAndSynonyms.getKey(),
               (Set<CharSequence>) Sets.newHashSet(termAndSynonyms.getValue()));

         // add subtopics
         if (bmaxquery.getTermsAndSubtopics().containsKey(termAndSynonyms.getKey())) {
            term.getValue().addAll(bmaxquery.getTermsAndSubtopics().get(termAndSynonyms.getKey()));
         }

         // append dismax query as clause
         bq.add(new BooleanClause(buildDismaxQuery(term), Occur.MUST));
      }

      // add boostings
      if (boostQueries != null) {
         for (Query f : boostQueries) {
            bq.add(f, BooleanClause.Occur.SHOULD);
         }
      }

      // done
      return bq;
   }

   // ---- main query

   /**
    * Builds a dismax query for the given terms and their corresponding
    * synonyms.
    */
   protected Query buildDismaxQuery(Entry<CharSequence, Set<CharSequence>> termWithSynonyms) {
      checkNotNull(termWithSynonyms, "Pre-condition violated: termWithSynonyms must not be null.");

      DisjunctionMaxQuery dmq = new DisjunctionMaxQuery(0);

      // iterate fields and build concrete queries
      for (String field : bmaxquery.getFieldsAndBoosts().keySet()) {

         // collect terms
         Analyzer analyzer = schema.getField(field).getType().getQueryAnalyzer();
         Set<Term> originalTerms = Sets.newHashSet(Terms.collectTerms(termWithSynonyms.getKey(), analyzer, field));

         // add main user clause
         dmq.add(buildTermQueries(field, originalTerms, USER_QUERY_FIELD_BOOST, DOCUMENT_FREQUENCY_NO_MANIPULATION));

         // collect terms
         Set<Term> synonyms = Sets.newHashSet();
         for (CharSequence t : termWithSynonyms.getValue()) {
            synonyms.addAll(Terms.collectTerms(t, analyzer, field));
         }

         // add synonym clauses
         if (!synonyms.isEmpty()) {

            // add clauses
            dmq.add(buildTermQueries(field, synonyms, bmaxquery.getSynonymBoost(), -1));
         }
      }

      return dmq;
   }

   // ---- term queries

   /**
    * Combines the given terms to a valid dismax query for the field given.
    */
   protected Collection<Query> buildTermQueries(String field, Collection<Term> terms, float extraBoost,
         int documentFrequency) {
      checkNotNull(field, "Pre-condition violated: field must not be null.");
      checkNotNull(terms, "Pre-condition violated: terms must not be null.");

      Collection<Query> queries = Sets.newHashSet();

      for (Term term : terms) {
         queries.add(buildTermQuery(term, bmaxquery.getFieldsAndBoosts().get(field) * extraBoost, documentFrequency));
      }

      return queries;
   }

   /**
    * Builds a term query and manipulates the document frequency, if given.
    */
   protected TermQuery buildTermQuery(Term term, float boost, int documentFrequency) {
      checkNotNull(term, "Pre-condition violated: term must not be null.");

      TermQuery query = new TermQuery(term);

      // set boost
      if (boost > 0f) {
         query.setBoost(boost);
      }

      return query;
   }
}
