package com.s24.search.solr.query.bmax;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
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
import org.apache.solr.search.SolrIndexSearcher;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * 
 * 
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public class BmaxLuceneQueryBuilder {

   private final static float USER_QUERY_FIELD_BOOST = 1.0f;
   private final static float BOOST_QUERY_FIELD_BOOST = 1.0f;
   private final static int DOCUMENT_FREQUENCY_NO_MANIPULATION = -1;

   private final BmaxQuery bmaxquery;
   private List<ValueSource> multiplicativeBoost;
   private IndexSchema schema;
   private SolrIndexSearcher indexSearcher;
   private int maxDocumentFrequencyInQuery = -1;

   public BmaxLuceneQueryBuilder(BmaxQuery bmaxQuery) {
      checkNotNull(bmaxQuery, "Pre-condition violated: bmaxQuery must not be null.");

      this.bmaxquery = bmaxQuery;
   }

   public BmaxLuceneQueryBuilder withMultiplicativeBoost(List<ValueSource> multiplicativeBoost) {
      this.multiplicativeBoost = multiplicativeBoost;
      return this;
   }

   public BmaxLuceneQueryBuilder withSchema(IndexSchema schema) {
      this.schema = schema;
      return this;
   }

   public BmaxLuceneQueryBuilder withIndexSearcher(SolrIndexSearcher indexSearcher) {
      this.indexSearcher = indexSearcher;
      return this;
   }

   // ---- go build yourself

   public Query build() {
      Query inner = buildWrappingQuery();

      // default
      Query main = inner;

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
         Entry<CharSequence, Set<CharSequence>> term = Maps.immutableEntry(termAndSynonyms.getKey(), (Set<CharSequence>) Sets.newHashSet(termAndSynonyms.getValue()));
         
         // add subtopics
         if (bmaxquery.getTermsAndSubtopics().containsKey(termAndSynonyms.getKey())) {
            term.getValue().addAll(bmaxquery.getTermsAndSubtopics().get(termAndSynonyms.getKey()));
         }
         
         // append dismax query as clause
         bq.add(new BooleanClause(buildDismaxQuery(term), Occur.MUST));
      }

      // build boostings
      if (!bmaxquery.getBoostUpTerms().isEmpty()) {
         Collection<BooleanClause> clauses = buildBoostQueryClauses(bmaxquery.getBoostUpTerms());

         for (BooleanClause bc : clauses) {
            bq.add(bc);
         }
      }

      return bq;
   }

   // ---- boost query

   /**
    * Builds a boost query
    */
   protected Collection<BooleanClause> buildBoostQueryClauses(Collection<CharSequence> boostTerms) {
      checkNotNull(boostTerms, "Pre-condition violated: boostTerms must not be null.");

      Collection<BooleanClause> clauses = Sets.newHashSet();

      // compute a single dismax clause for every term
      for (CharSequence term : boostTerms) {
         clauses.add(new BooleanClause(buildBoostQueryDismaxFieldClause(term, BOOST_QUERY_FIELD_BOOST), Occur.SHOULD));
      }

      return clauses;
   }

   /**
    * Transforms the given input into valid field terms
    */
   protected Query buildBoostQueryDismaxFieldClause(CharSequence term, float extraBoost) {
      checkNotNull(term, "Pre-condition violated: field must not be null.");

      DisjunctionMaxQuery dmq = new DisjunctionMaxQuery(0);

      // iterate fields to place term in
      for (String field : bmaxquery.getBoostFieldsAndBoosts().keySet()) {

         // get analyzer for field
         Analyzer analyzer = schema.getField(field).getType().getQueryAnalyzer();

         // transform terms into Term representation
         Collection<Term> terms = Terms.collectTerms(term, analyzer, field);

         // get max document frequency of terms
         int documentFrequency = -1;
         if (bmaxquery.isManipulateDocumentFrequencies()) {
            documentFrequency = Terms.collectMaximumDocumentFrequency(terms, indexSearcher);
         }

         // norm term queries and make sure their document frequency is always
         // below the main query's doc frequency.
         Collection<Query> termQueries = buildTermQueries(field, terms, BOOST_QUERY_FIELD_BOOST,
               (maxDocumentFrequencyInQuery + documentFrequency - 1));

         // use dismax queries if terms has been split into pieces
         if (termQueries.size() > 0) {
            dmq.add(termQueries);
         }
      }

      // return term query only
      if (dmq.getDisjuncts().size() == 1) {
         return dmq.iterator().next();
      }

      return dmq;
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

            // get max document frequency of main terms
            int documentFrequency = -1;
            if (bmaxquery.isManipulateDocumentFrequencies()) {
               documentFrequency = Terms.collectMaximumDocumentFrequency(originalTerms, indexSearcher);

               // update maximum in query
               this.maxDocumentFrequencyInQuery = Math.max(documentFrequency, maxDocumentFrequencyInQuery);
            }

            // add clauses
            dmq.add(buildTermQueries(field, synonyms, bmaxquery.getSynonymBoost(), documentFrequency));
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

      TermQuery query = null;

      if (bmaxquery.isManipulateTermFrequencies() && documentFrequency > 0) {
         try {

            // build term context
            TermContext termContext = TermContext.build(indexSearcher.getTopReaderContext(), term);

            // this is a fucked up hell
            TermContext manipulated = new TermContext(indexSearcher.getTopReaderContext());
            boolean foundTermInContext = false;

            // iterate leaves and find first matching. Fake doc frequency and
            // term freqency.
            for (int ord = 0; ord < indexSearcher.getTopReaderContext().leaves().size(); ord++) {
               if (termContext.get(ord) != null) {
                  manipulated.register(termContext.get(ord), ord, documentFrequency, -1);
                  foundTermInContext = true;
               }
            }

            if (foundTermInContext) {
               query = new TermQuery(term, manipulated);
            } else {
               query = new TermQuery(term, documentFrequency);
            }
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      }

      // fallback
      if (query == null) {
         query = new TermQuery(term);
      }

      // set boost
      if (boost > 0f) {
         query.setBoost(boost);
      }

      return query;
   }
}
