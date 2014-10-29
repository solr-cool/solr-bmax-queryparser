package com.s24.search.solr.query.bmax;

import static org.junit.Assert.assertEquals;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.junit.Test;

public class BmaxLuceneQueryBuilderTest {

   @Test
   public void testBuildingBmaxLuceneQueryWorksOnSimpleBmaxQuery() throws Exception {

      BmaxQuery bmaxQuery = new BmaxQuery();

      Query buildedQuery = new BmaxLuceneQueryBuilder(bmaxQuery)
            .build();

      assertEquals(new MatchAllDocsQuery(), buildedQuery);

   }

}
