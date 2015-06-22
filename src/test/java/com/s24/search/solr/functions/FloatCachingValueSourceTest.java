package com.s24.search.solr.functions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class FloatCachingValueSourceTest {

   private static final int MAX_DOC = 42;

   @Mock
   private ValueSource mockSource;

   @Mock
   private FunctionValues mockValues;

   private FloatCachingValueSource valueSource;

   @Before
   public void setUp() throws Exception {
      MockitoAnnotations.initMocks(this);
      when(mockSource.getValues(any(Map.class), any(AtomicReaderContext.class))).thenReturn(mockValues);
      valueSource = new FloatCachingValueSource(mockSource, MAX_DOC);
   }

   @Test
   public void testReturnsValueOfUnderlyingSource() throws Exception {
      when(mockValues.floatVal(12)).thenReturn(1.23f);
      when(mockValues.floatVal(23)).thenReturn(3.45f);
      assertEquals(1.23f, valueSource.getValues(null, null).floatVal(12), 0.0);
      assertEquals(3.45f, valueSource.getValues(null, null).floatVal(23), 0.0);
   }

   @Test
   public void testEqualsHashCode() throws Exception {
      assertFalse(valueSource.equals(null));
      assertFalse(valueSource.equals(new Object()));
      assertTrue(valueSource.equals(valueSource));
      assertTrue(valueSource.equals(new FloatCachingValueSource(mockSource, MAX_DOC)));
      assertEquals(valueSource.hashCode(), new FloatCachingValueSource(mockSource, MAX_DOC).hashCode());
   }

   @Test
   public void testDescription() throws Exception {
      assertNotNull(valueSource.description());
   }

   @Test
   public void testToString() throws Exception {
      assertNotNull(valueSource.toString());
   }
}
