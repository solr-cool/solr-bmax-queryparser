package com.s24.search.solr.util.packed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.text.NumberFormat;

import org.apache.commons.lang.math.RandomUtils;
import org.apache.lucene.util.RamUsageEstimator;
import org.junit.Test;

public class LongArraySparseMutableTest {

   private static final int MAX_DOCS = 8600000;
   private static final int FILL_DOCS = RandomUtils.nextInt(MAX_DOCS);

   @Test
   public void testSize() {
      AbstractSparseValues mutable = new LongArraySparseMutable(1024);
      assertEquals(0, mutable.size());
   }

   @Test
   public void testSequentialGetSet() throws Exception {
      AbstractSparseValues mutable = new LongArraySparseMutable(1024);

      for (int i = 0; i < mutable.size(); i++) {
         long value = RandomUtils.nextLong();
         assertFalse(mutable.hasValue(i));
         mutable.set(i, value);
         assertTrue(mutable.hasValue(i));
         assertEquals(value, mutable.get(i));
      }
   }

   @Test
   public void testAppendingWritePerformance() throws Exception {
      System.out.println("long array: "
            + NumberFormat.getInstance().format(RamUsageEstimator.sizeOf(new long[MAX_DOCS])));

      // sparse packed
      long start = System.currentTimeMillis();
      final AbstractSparseValues sparse = new LongArraySparseMutable(FILL_DOCS);
      for (int i = 0; i < FILL_DOCS; i++) {
         final int index = i;
         final long value = RandomUtils.nextInt(FILL_DOCS);
         sparse.set(index, value);
         assertEquals("doc: " + i, value, sparse.get(index));
      }

      System.out.println(String.format("Sparse packed bytes: %s in %sms",
            NumberFormat.getInstance().format(sparse.ramBytesUsed()), (System.currentTimeMillis() - start)));
   }
}
