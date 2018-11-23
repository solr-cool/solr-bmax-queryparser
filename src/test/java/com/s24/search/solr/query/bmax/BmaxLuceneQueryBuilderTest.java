package com.s24.search.solr.query.bmax;

import com.s24.search.solr.query.bmax.BmaxQuery.BmaxTerm;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.*;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.FieldParams;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.*;
import java.util.stream.Collectors;

import static com.s24.search.solr.query.bmax.AbstractLuceneQueryTest.*;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class BmaxLuceneQueryBuilderTest {

   IndexSchema schema = Mockito.mock(IndexSchema.class);
   FieldType fieldType = Mockito.mock(FieldType.class);

   SchemaField schemaField1 = new SchemaField("field1", fieldType);
   SchemaField schemaField2 = new SchemaField("field2", fieldType);
   SchemaField schemaField3 = new SchemaField("field3", fieldType);

   @Before
   public void setUp() {
      Mockito.reset(schema, fieldType);
      when(schema.getField("field1")).thenReturn(schemaField1);
      when(schema.getField("field2")).thenReturn(schemaField2);
      when(schema.getField("field3")).thenReturn(schemaField3);
      when(schema.getQueryAnalyzer()).thenReturn(new StandardAnalyzer());
      when(fieldType.getQueryAnalyzer()).thenReturn(new StandardAnalyzer());
   }

   @Test
   public void testBuildingBmaxLuceneQueryWorksOnSimpleBmaxQuery() throws Exception {

      BmaxQuery bmaxQuery = new BmaxQuery();

      Query buildedQuery = new BmaxLuceneQueryBuilder(bmaxQuery)
            .build();

      assertEquals(new MatchAllDocsQuery(), buildedQuery);
   }

   @Test
   public void testMatchNoDocsQueryForNoTerms() {
      // given
      BmaxQuery bmaxQuery = new BmaxQuery();


      // when
      Query result1 = new BmaxLuceneQueryBuilder(bmaxQuery)
              .withNoMatchDocsForNoTermsQuery(true)
              .build();

      Query result2 = new BmaxLuceneQueryBuilder(bmaxQuery)
              .build();

      // then
      assertThat(result1, instanceOf(MatchNoDocsQuery.class));
      assertThat(result2, instanceOf(MatchAllDocsQuery.class));
   }

   @Test
   public void testTermWithSubtopicAndSynonym() throws Exception {
      BmaxQuery bmaxQuery = new BmaxQuery();
      bmaxQuery.getFieldsAndBoosts().put("field1", 10f);
      bmaxQuery.getFieldsAndBoosts().put("field2", 1f);
      BmaxTerm term = new BmaxTerm("foo");
      term.getSynonyms().add("bar");
      term.getSubtopics().add("baz");
      bmaxQuery.getTerms().add(term);
      BmaxTerm term2 = new BmaxTerm("quux");
      bmaxQuery.getTerms().add(term2);
      bmaxQuery.setAllPhraseFields(Collections.emptyList());
      bmaxQuery.getSubtopicFieldsAndBoosts().put("field1", 10f);
      bmaxQuery.getSubtopicFieldsAndBoosts().put("field2", 1f);
      bmaxQuery.setSynonymBoost(0.5f);
      bmaxQuery.setSubtopicBoost(0.25f);

      Query query = new BmaxLuceneQueryBuilder(bmaxQuery).withSchema(schema).build();
      assertThat(query,
            bq(
                  c(BooleanClause.Occur.MUST, dmq(
                          tis(10f, "field1", "foo"),
                          tis(5f, "field1", "bar"),
                          tis(1f, "field2", "foo"),
                          tis(0.5f, "field2", "bar"),
                          tis(2.5f, "field1", "baz"),
                          tis(0.25f, "field2", "baz")
                  )),
                  c(BooleanClause.Occur.MUST, dmq(
                          tis(10f, "field1", "quux"),
                          tis(1f, "field2", "quux")
                  ))));
   }

   @Test
   public void testPhraseBoost() throws Exception {


      BmaxQuery bmaxQuery = new BmaxQuery();
      bmaxQuery.getFieldsAndBoosts().put("field1", 10f);

      bmaxQuery.getTerms().addAll(Arrays.asList(new BmaxTerm("t1"), new BmaxTerm("t2"), new BmaxTerm("t3"),
              new BmaxTerm("t4")));
      bmaxQuery.setTieBreakerMultiplier(0.01f);

      bmaxQuery.setPhraseBoostTieBreaker(0.3f);
      bmaxQuery.setAllPhraseFields(
              Arrays.asList(new FieldParams("field1", 0, 0, 2f), new FieldParams("field2", 2, 2, 1f),
                      new FieldParams("field1", 3, 0, 3f)));

      Query query = new BmaxLuceneQueryBuilder(bmaxQuery)
              .withSchema(schema)
              .build();
      assertTrue(query instanceof BooleanQuery);

      BooleanQuery bq = (BooleanQuery) query;
      
      final List<DisjunctionMaxQuery> pfQueries = bq.clauses().stream()
              .filter(clause -> (clause.getOccur() == BooleanClause.Occur.SHOULD))
              .filter(clause -> clause.getQuery() instanceof DisjunctionMaxQuery)
              .map(clause -> (DisjunctionMaxQuery) clause.getQuery())
              .filter(dmq -> dmq.getTieBreakerMultiplier() == 0.3f)
              .collect(toList());
      assertEquals(1, pfQueries.size());

      assertThat(pfQueries.get(0),
              dmq(1f, 0.3f,
                      phrase(2f, "field1", 0, "t1", "t2", "t3", "t4"),
                      bq(1f, 1,
                              c(BooleanClause.Occur.SHOULD, phrase(1f, "field2", 2, "t1", "t2")),
                              c(BooleanClause.Occur.SHOULD, phrase(1f, "field2", 2, "t2", "t3")),
                              c(BooleanClause.Occur.SHOULD, phrase(1f, "field2", 2, "t3", "t4")))

                      ,
                      bq(3f, 1,
                              c(BooleanClause.Occur.SHOULD, phrase(1f, "field1", 0, "t1", "t2", "t3")),
                              c(BooleanClause.Occur.SHOULD, phrase(1f, "field1", 0, "t2", "t3", "t4"))



              )));



   }

   private static TermInSetQueryMatcher tis(float boost, String field, String... terms) {
      return new TermInSetQueryMatcher(boost, field, terms);
   }

   private static TermInSetQueryMatcher tis(String field, String... terms) {
      return tis(1f, field, terms);
   }

   private static class TermInSetQueryMatcher extends TypeSafeMatcher<Query> {
      private final String field;
      private final Set<String> terms;
      private final float boost;
      private final TermInSetQuery expected;

      public TermInSetQueryMatcher(float boost, String field, String... terms) {
         this.boost = boost;
         this.field = field;
         this.terms = new HashSet<>(Arrays.asList(terms));
         this.expected = new TermInSetQuery(field, Arrays.stream(terms).map(BytesRef::new).collect(toList()));
      }

      @Override
      protected boolean matchesSafely(Query query) {
         Query actualQuery = query;
         float actualBoost = 1f;
         if (query instanceof BoostQuery) {
            BoostQuery boostQuery = (BoostQuery) query;
            actualQuery = boostQuery.getQuery();
            actualBoost = boostQuery.getBoost();
         }
         boolean result = actualQuery.equals(expected) && actualBoost == boost;
         return result;
      }

      @Override
      public void describeTo(Description description) {
         description.appendText("TermInSetQuery field: " + field + ", terms: " + terms + ", boost: " + boost);
      }
   }
}
