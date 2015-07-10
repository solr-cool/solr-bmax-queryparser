package com.s24.search.solr.util.packed;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.lucene.util.OpenBitSet;
import org.apache.lucene.util.RamUsageEstimator;
import org.apache.lucene.util.packed.PackedInts;

import com.s24.search.solr.util.LongValueCache;

/**
 * A sparse packed data structure
 * 
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public abstract class AbstractSparseMutable extends PackedInts.Mutable {

   // holds the per-bucket bits
   private final long[] words;

   // each word bucket is associated with a writer holding the bucket's values.
   private final LongValueCache[] values;

   public AbstractSparseMutable(int maxValueCount) {
      checkArgument(maxValueCount > 0, "Pre-condition violated: expression maxValueCount > 0 must be true.");

      // compute number of words
      int wordCount = OpenBitSet.bits2words(maxValueCount);

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

      // get word by div 64
      int i = index >> 6;
      checkNotNull(values[i], "Pre-condition violated: values[i] must not be null.");

      // get value in writer
      return values[i].get(index % 64);
   }

}
