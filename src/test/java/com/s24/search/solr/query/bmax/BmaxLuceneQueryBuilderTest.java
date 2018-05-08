package com.s24.search.solr.query.bmax;

import com.s24.search.solr.query.bmax.BmaxQuery.BmaxTerm;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.*;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.FieldParams;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.s24.search.solr.query.bmax.AbstractLuceneQueryTest.*;
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
              .collect(Collectors.toList());
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
}
