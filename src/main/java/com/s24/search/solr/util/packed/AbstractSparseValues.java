package com.s24.search.solr.util.packed;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.lucene.util.FixedBitSet;
import org.apache.lucene.util.RamUsageEstimator;

import com.s24.search.solr.util.LongValueCache;

/**
 * A sparse packed data structure
 * 
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public abstract class AbstractSparseValues implements LongValueCache {

   // holds the per-bucket bits
   private final long[] words;

   // each word bucket is associated with a writer holding the bucket's values.
   private final LongValueCache[] values;

   public AbstractSparseValues(int maxValueCount) {
      checkArgument(maxValueCount > 0, "Pre-condition violated: expression maxValueCount > 0 must be true.");

      // compute number of words
      int wordCount = FixedBitSet.bits2words(maxValueCount);

      this.words = new long[wordCount];
      this.values = new LongValueCache[wordCount];
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public long ramBytesUsed() {
      long bytes = RamUsageEstimator.sizeOf(words);

      for (int i = 0; i < values.length; i++) {
         if (values[i] != null) {
            bytes += values[i].ramBytesUsed();
         }
      }

      return bytes;
   }

   @Override
   public boolean hasValue(int index) {
      checkArgument(index >= 0, "Pre-condition violated: expression index >= 0 must be true.");

      // get word by div 64
      int i = index >> 6;

      return ((words[i] & (1L << index)) != 0);
   }

   /**
    * Returns the maxmimum bits per value used.
    */
   @Override
   public int getBitsPerValue() {
      int bits = 0;

      // iterate writers and find maximum
      for (int i = 0; i < values.length; i++) {
         if (values[i] != null) {
            bits = Math.max(bits, values[i].getBitsPerValue());
         }
      }

      return bits;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void set(int index, long value) {
      checkArgument(index >= 0, "Pre-condition violated: expression index >= 0 must be true.");
      checkArgument(value >= 0, "Pre-condition violated: expression value >= 0 must be true.");

      // get index to work on
      int i = index >> 6;

      // check preconditions
      if (values[i] == null) {

         // ensure growable writer is present
         values[i] = createNewValues();
      }

      // flip index
      words[i] ^= (1L << index);

      // set value in writer
      values[i].set(index % 64, value);
   }

   protected abstract LongValueCache createNewValues();

   /**
    * {@inheritDoc}. Returns the number of bits set in words.
    */
   @Override
   public int size() {
      int size = 0;

      for (int i = 0; i < words.length; i++) {
         size += Long.bitCount(words[i]);
      }

      return size;
   }

   @Override
   public long get(int index) {
      checkArgument(index >= 0, "Pre-condition violated: expression index >= 0 must be true.");

      // check for value
      if (hasValue(index)) {
         // get word by div 64
         int i = index >> 6;

         // get value in writer
         return values[i].get(index % 64);
      }

      return Long.MAX_VALUE;
   }
   
}
