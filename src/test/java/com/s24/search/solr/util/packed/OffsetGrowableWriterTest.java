package com.s24.search.solr.util.packed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.text.NumberFormat;

import org.apache.commons.lang.math.RandomUtils;
import org.apache.lucene.codecs.compressing.GrowableByteArrayDataOutput;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.util.packed.GrowableWriter;
import org.apache.lucene.util.packed.PackedInts;
import org.apache.lucene.util.packed.PackedInts.Reader;
import org.apache.lucene.util.packed.PackedInts.Writer;
import org.junit.Test;

public class OffsetGrowableWriterTest {

   private static final int MAX_DOCS = 8600000;

   
   @Test
   public void testDefaultValues() throws Exception {
      
   }
   
   @Test
   public void testSizing() throws Exception {
      System.out.println("Float array: " + NumberFormat.getInstance().format(4 * MAX_DOCS));

      OffsetGrowableWriter offset = new OffsetGrowableWriter(8, MAX_DOCS, PackedInts.DEFAULT);

      // assure reset
      for (int i = 0; i < offset.size(); i++) {
         assertEquals(0, offset.get(i));
      }

      // fill offset writer
      long maxvalue = 0;
      for (int i = 0; i < offset.size(); i++) {
         long next = (long) RandomUtils.nextInt(1024) + 100000;
         offset.set(i, next);
         assertEquals(next, offset.get(i));
         
         if (next > maxvalue) {
            maxvalue = next;
         }
      }

      System.out.println("OffsetGrowableWriter minimum value: " + offset.getCurrentMinimumValue() + ", Bytes: "
            + NumberFormat.getInstance().format(offset.ramBytesUsed()));

      // compare to linear writer/reader
      GrowableByteArrayDataOutput out = new GrowableByteArrayDataOutput(offset.size());
      Writer compare = PackedInts.getWriter(out, offset.size(), PackedInts.bitsRequired(maxvalue), PackedInts.DEFAULT);
      for (int i = 0; i < offset.size(); i++) {
         compare.add(offset.get(i));
      }

      Reader reader = PackedInts.getReader(new ByteArrayDataInput(out.bytes));
      System.out.println("writer/reader bytes: " + NumberFormat.getInstance().format(reader.ramBytesUsed()));

      // compare to simple growable writer
      GrowableWriter growable = new GrowableWriter(2, MAX_DOCS, PackedInts.DEFAULT);
      for (int i = 0; i < offset.size(); i++) {
         growable.set(i, offset.get(i));
      }

      System.out.println("GrowableWriter bytes: " + NumberFormat.getInstance().format(growable.ramBytesUsed()));

      // assure things are alright
      assertTrue(offset.getBitsPerValue() < compare.bitsPerValue());
      assertTrue(offset.getBitsPerValue() < growable.getBitsPerValue());

      for (int i = 0; i < offset.size(); i++) {
         // assertEquals(offset.get(i), reader.get(i));
         assertEquals(offset.get(i), growable.get(i));
      }
   }

}
