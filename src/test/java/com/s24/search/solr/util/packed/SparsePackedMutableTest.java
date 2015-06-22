package com.s24.search.solr.util.packed;

import static org.junit.Assert.assertEquals;

import java.text.NumberFormat;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.math.RandomUtils;
import org.apache.lucene.util.packed.PackedInts;
import org.apache.solr.util.stats.Clock;
import org.apache.solr.util.stats.Timer;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class SparsePackedMutableTest {

   private static final int MAX_DOCS = 8600000;
   private static final int FILL_DOCS = RandomUtils.nextInt(MAX_DOCS);

   @Test
   public void testSimpleAppendWriteGet() throws Exception {
      SparsePackedMutable sparse = new SparsePackedMutable(0l, 4, PackedInts.bitsRequired(1024), PackedInts.DEFAULT);
      long next = (long) RandomUtils.nextInt(1024 * 1014);
      sparse.set(0, next);
      sparse.set(1, next);
      assertEquals(next, sparse.get(0));
      assertEquals(next, sparse.get(1));

      // grow
      sparse.set(2, next);
      sparse.set(3, next);
      assertEquals(next, sparse.get(0));
      assertEquals(next, sparse.get(1));
      assertEquals(next, sparse.get(2));
      assertEquals(next, sparse.get(3));
   }

   @Test
   public void testRandomWriteGet() throws Exception {
      SparsePackedMutable sparse = new SparsePackedMutable(0l, 8, PackedInts.bitsRequired(1024), PackedInts.DEFAULT);
      sparse.set(0, 39);
      sparse.set(4, 44);
      sparse.set(5, 45);
      sparse.set(2, 42);
      sparse.set(1, 41);
      assertEquals(39, sparse.get(0));
      assertEquals(41, sparse.get(1));
      assertEquals(42, sparse.get(2));
      assertEquals(44, sparse.get(4));
      assertEquals(45, sparse.get(5));
   }

   @Test
   public void testInternalIndex() throws Exception {
      SparsePackedMutable sparse = new SparsePackedMutable(0l, MAX_DOCS, 2, PackedInts.DEFAULT);
      assertEquals(0, sparse.internalIndexOf(0));
      sparse.set(0, 42);
      assertEquals(0, sparse.internalIndexOf(0));
      assertEquals(42, sparse.get(0));
      assertEquals(1, sparse.internalIndexOf(1));
      sparse.set(1, 43);
      assertEquals(1, sparse.internalIndexOf(1));
      assertEquals(43, sparse.get(1));
      assertEquals(2, sparse.internalIndexOf(2));
   }

   @Test
   public void testInternalIndexWithAdvancing() throws Exception {
      SparsePackedMutable sparse = new SparsePackedMutable(0l, MAX_DOCS, 16, PackedInts.DEFAULT);
      for (int i = 0; i < 4096; i++) {
         assertEquals(i, sparse.internalIndexOf(i));
         sparse.set(i, 42 + i);
         assertEquals(i, sparse.internalIndexOf(i));
      }
   }

   @Test
   public void testInternalIndexWithRandomAdvancing() throws Exception {
      SparsePackedMutable sparse = new SparsePackedMutable(0l, MAX_DOCS, 16, PackedInts.DEFAULT);
      for (int i = 0; i < 4096; i++) {
         int idx = RandomUtils.nextInt(4096);

         sparse.set(idx, 42 + i);
         assertEquals(42 + i, sparse.get(idx));
      }
   }

   @Test
   public void testBitSetStuff() throws Exception {
      assertEquals(64, Long.bitCount(-1));
      assertEquals(0, Long.bitCount(0));
   }

   @Test
   @Ignore("Performance > 1mio entries is problematic")
   public void testAppendingWritePerformance() throws Exception {
      System.out.println("Float array: " + NumberFormat.getInstance().format(4 * MAX_DOCS));
      System.out.println("Filling with: " + NumberFormat.getInstance().format(FILL_DOCS) + " values ...");

      // growable writer
      long start = System.currentTimeMillis();
      OffsetGrowableWriter offset = new OffsetGrowableWriter(2, MAX_DOCS, PackedInts.DEFAULT);
      for (int i = 0; i < FILL_DOCS; i++) {
         long next = (long) RandomUtils.nextInt(1024) + 100000;
         offset.set(i, next);
      }

      System.out.println(String.format("Growable writer bytes: %s in %sms",
            NumberFormat.getInstance().format(offset.ramBytesUsed()), (System.currentTimeMillis() - start)));

      // sparse packed
      Timer setter = new Timer(TimeUnit.MILLISECONDS, TimeUnit.MILLISECONDS, Clock.defaultClock());
      Timer getter = new Timer(TimeUnit.MILLISECONDS, TimeUnit.MILLISECONDS, Clock.defaultClock());
      start = System.currentTimeMillis();
      final SparsePackedMutable sparse = new SparsePackedMutable(0l, MAX_DOCS, 1024, PackedInts.DEFAULT);
      for (int i = 0; i < FILL_DOCS; i++) {
         final int index = i;
         final long value = offset.get(index);

         // time setter
         setter.time(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
               sparse.set(index, value);
               return null;
            }
         });

         Long sparseValue = getter.time(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
               return Long.valueOf(sparse.get(index));
            }
         });

         assertEquals("doc: " + i, value, sparseValue.longValue());

         if (i % (1024 * 128) == 0) {
            System.out.println(String.format("%s --> sr: %s, gr: %s", i,
                  NumberFormat.getNumberInstance().format(setter.getMean()), NumberFormat.getNumberInstance()
                        .format(getter.getMean())));
         }
      }

      System.out.println(String.format("Sparse packed bytes: %s in %sms",
            NumberFormat.getInstance().format(sparse.ramBytesUsed()), (System.currentTimeMillis() - start)));

      // assure things are alright
      assertEquals(offset.getBitsPerValue(), sparse.getBitsPerValue());

      for (int i = 0; i < FILL_DOCS; i++) {
         assertEquals(offset.get(i), sparse.get(i));
      }
   }
}
