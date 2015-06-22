package com.s24.search.solr.util.packed;

import static org.junit.Assert.assertEquals;

import java.text.NumberFormat;

import org.apache.commons.lang.math.RandomUtils;
import org.apache.lucene.util.packed.PackedInts;
import org.junit.Test;

public class OffsetGrowableFloatWriterTest {

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
      System.out.println("Float array bytes: " + NumberFormat.getInstance().format(4 * 4096));
      OffsetGrowableFloatWriter writer = new OffsetGrowableFloatWriter(OffsetGrowableFloatWriter.DEFAULT_PRECISION, 2, 4096, PackedInts.DEFAULT);
      
      for (int i = 0; i < 4096; i++) {
         float value = RandomUtils.nextFloat() * RandomUtils.nextFloat();
         writer.setFloat(i, value);
         assertEquals(value, writer.getFloat(i), OffsetGrowableFloatWriter.DEFAULT_PRECISION);
      }
      
      System.out.println("OffsetGrowableFloatWriter bytes: " + NumberFormat.getInstance().format(writer.ramBytesUsed()));
   }

}
