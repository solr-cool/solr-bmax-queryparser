package com.s24.search.solr.util.packed;

import static org.junit.Assert.assertEquals;

import java.text.NumberFormat;

import org.apache.commons.lang.math.RandomUtils;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.lucene.util.packed.PackedInts;
import org.junit.Test;

public class OffsetGrowableFloatWriterTest {
   
   private static final int MAX_DOCS = 8600000;

   @Test
   public void testFloatConversion() throws Exception {
      OffsetGrowableFloatWriter writer = new OffsetGrowableFloatWriter(OffsetGrowableFloatWriter.DEFAULT_PRECISION, 2, 4096, PackedInts.DEFAULT);

      for (int i = 0; i < 4096; i++) {
         float value = RandomUtils.nextFloat() * RandomUtils.nextFloat();
         writer.setFloat(i, value);
         assertEquals(value, writer.getFloat(i), OffsetGrowableFloatWriter.DEFAULT_PRECISION);
      }
   }

   @Test
   public void testSize() throws Exception {
      OffsetGrowableFloatWriter writer = new OffsetGrowableFloatWriter(OffsetGrowableFloatWriter.DEFAULT_PRECISION, 2, 4096, PackedInts.DEFAULT);
      
      for (int i = 0; i < 4096; i++) {
         float value = RandomUtils.nextFloat() * RandomUtils.nextFloat();
         writer.setFloat(i, value);
         assertEquals(value, writer.getFloat(i), OffsetGrowableFloatWriter.DEFAULT_PRECISION);
      }
   }

   @Test
   public void testWritePerformance() throws Exception {
      System.out.println("Writing " + NumberFormat.getInstance().format(MAX_DOCS) + " values.");
      System.out.println("Float array bytes: " + NumberFormat.getInstance().format(RamUsageEstimator.sizeOf(new float[MAX_DOCS])));
      OffsetGrowableFloatWriter writer = new OffsetGrowableFloatWriter(OffsetGrowableFloatWriter.DEFAULT_PRECISION, 2, MAX_DOCS, PackedInts.DEFAULT);
      long start = System.currentTimeMillis();
      
      for (int i = 0; i < MAX_DOCS; i++) {
         float value = RandomUtils.nextFloat() * RandomUtils.nextFloat();
         int j = RandomUtils.nextInt(MAX_DOCS);
         
         writer.setFloat(j, value);
         assertEquals(value, writer.getFloat(j), OffsetGrowableFloatWriter.DEFAULT_PRECISION);
      }
      
      System.out.println("OffsetGrowableFloatWriter bytes: " + NumberFormat.getInstance().format(writer.ramBytesUsed()) + " in " + (System.currentTimeMillis() - start) + "ms ...");
   }

}
