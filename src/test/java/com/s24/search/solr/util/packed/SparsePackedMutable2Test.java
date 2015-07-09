package com.s24.search.solr.util.packed;

import static org.junit.Assert.assertEquals;

import java.text.NumberFormat;

import org.apache.commons.lang.math.RandomUtils;
import org.apache.lucene.util.packed.PackedInts;
import org.junit.Ignore;
import org.junit.Test;

public class SparsePackedMutable2Test {

   private static final int MAX_DOCS = 8600000;
   private static final int FILL_DOCS = RandomUtils.nextInt(MAX_DOCS);

   @Test
   public void testSize() {
      SparsePackedMutable2 mutable = new SparsePackedMutable2(1024, PackedInts.DEFAULT);
      assertEquals(0, mutable.size());
   }

   @Test(expected = NullPointerException.class)
   public void testPreconditions() throws Exception {
      SparsePackedMutable2 mutable = new SparsePackedMutable2(1024, PackedInts.DEFAULT);
      mutable.get(1);
   }

   @Test
   public void testSequentialGetSet() throws Exception {
      SparsePackedMutable2 mutable = new SparsePackedMutable2(1024, PackedInts.DEFAULT);

      for (int i = 0; i < mutable.size(); i++) {
         long value = RandomUtils.nextLong();
         mutable.set(i, value);
         assertEquals(value, mutable.get(i));
      }
   }

   @Test
   @Ignore
   public void testAppendingWritePerformance() throws Exception {
      System.out.println("int array: " + NumberFormat.getInstance().format(4 * MAX_DOCS));

      // sparse packed
      long start = System.currentTimeMillis();
      final SparsePackedMutable sparse = new SparsePackedMutable(0l, MAX_DOCS, 1024, PackedInts.DEFAULT);
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
