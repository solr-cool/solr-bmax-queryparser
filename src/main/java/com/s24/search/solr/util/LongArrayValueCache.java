package com.s24.search.solr.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Arrays;

import org.apache.lucene.util.RamUsageEstimator;

public class LongArrayValueCache implements LongValueCache {

   private final long[] cache;

   public LongArrayValueCache(int maxDocs) {
      this.cache = new long[maxDocs + 1];

      // reset internal
      Arrays.fill(cache, Long.MAX_VALUE);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public long ramBytesUsed() {
      return RamUsageEstimator.sizeOf(cache);
   }
   
   @Override
   public int getBitsPerValue() {
      return 16;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int size() {
      return cache.length;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public long get(int doc) {
      checkArgument(doc >= 0 && doc < cache.length,
            "Pre-condition violated: expression index >=0 && index < cache.length  must be true.");
      
      return cache[doc];
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void set(int doc, long value) {
      checkArgument(doc >= 0 && doc < cache.length,
            "Pre-condition violated: expression index >=0 && index < cache.length  must be true.");

      cache[doc] = value;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean hasValue(int index) {
      checkArgument(index >= 0 && index < cache.length,
            "Pre-condition violated: expression index >=0 && index < cache.length  must be true.");
      
      return cache[index] < Long.MAX_VALUE;
   }

}
