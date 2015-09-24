package com.s24.search.solr.util.packed;

import java.io.IOException;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BitSetIterator;
import org.apache.lucene.util.SparseFixedBitSet;
import org.apache.lucene.util.packed.GrowableWriter;

import com.s24.search.solr.util.LongValueCache;

/**
 * A growable writer that works with a internal minimum value, that is added as an offset to the values returned. Using
 * this technique, we reduce the cardinality of values stored in the growable writer, which reduces ram usage.
 * 
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public class OffsetGrowableWriter extends GrowableWriter implements LongValueCache {

   // track ids that have been modified
   private final SparseFixedBitSet valuesFilled;

   // initial minimum value
   private long minimumValue = Long.MAX_VALUE;

   public OffsetGrowableWriter(int startBitsPerValue, int valueCount, float acceptableOverheadRatio) {
      this(startBitsPerValue, valueCount, acceptableOverheadRatio, Long.MAX_VALUE);
   }

   public OffsetGrowableWriter(int startBitsPerValue, int valueCount, float acceptableOverheadRatio,
         long minimalValue) {
      super(startBitsPerValue, valueCount, acceptableOverheadRatio);

      this.minimumValue = minimalValue;
      this.valuesFilled = new SparseFixedBitSet(valueCount);

      // reset contents
      fill(0, valueCount, 0l);
   }

   /**
    * Add bitset ram bytes
    */
   @Override
   public long ramBytesUsed() {
      return super.ramBytesUsed() + valuesFilled.ramBytesUsed();
   }

   public long getCurrentMinimumValue() {
      return minimumValue;
   }

   /**
    * Ensures that the value to add is above the current minimum value or reduces the minimum value to the value given.
    * In that case, alle values stored are fixed to meet the new minimium value.
    */
   protected void ensureMinimumBoundary(long value) {

      // we have a new offset: rebalance all values using the diff;
      if (value < minimumValue) {
         long diff = minimumValue - value;

         try {
            
            for (DocIdSetIterator ids = new BitSetIterator(valuesFilled, 0L); ids.nextDoc() != DocIdSetIterator.NO_MORE_DOCS;) {
               long v = super.get(ids.docID());

               super.set(ids.docID(), (v + diff));
            }
         } catch (IOException e) {
            // ignored
         }

         // update new minimum value
         this.minimumValue = value;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void set(int index, long value) {
      ensureMinimumBoundary(value);
      long diff = value - minimumValue;

      // insert value
      super.set(index, diff);

      // mark set
      valuesFilled.set(index);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public long get(int index) {
      if (valuesFilled.get(index)) {
         return super.get(index) + minimumValue;
      }

      // should be zero (filled in constructor)
      return super.get(index);
   }

   /**
    * Checks whether for the given index there has been a value set
    */
   public boolean hasValue(int index) {
      return valuesFilled.get(index);
   }
}
