package com.s24.search.solr.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Arrays;

/**
 * Very simple, float array based implementation.
 * 
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public class FloatArrayValueCache implements FloatValueCache {

   private final float[] cache;

   public FloatArrayValueCache(int maxDocs) {
      this.cache = new float[maxDocs + 1];

      // reset internal
      Arrays.fill(cache, Float.NaN);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public long ramBytesUsed() {
      return 4 * cache.length;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public float getFloat(int index) {
      checkArgument(index >= 0 && index < cache.length,
            "Pre-condition violated: expression index >=0 && index < cache.length  must be true.");

      return cache[index];
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setFloat(int index, float value) {
      checkArgument(index >= 0 && index < cache.length,
            "Pre-condition violated: expression index >=0 && index < cache.length  must be true.");

      cache[index] = value;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean hasValue(int index) {
      checkArgument(index >= 0 && index < cache.length,
            "Pre-condition violated: expression index >=0 && index < cache.length  must be true.");

      return !Float.isNaN(cache[index]);
   }

   @Override
   public int size() {
      return cache.length;
   }

}
