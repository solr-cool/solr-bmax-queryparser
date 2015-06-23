package com.s24.search.solr.util.packed;

import static com.google.common.base.Preconditions.checkArgument;

import com.s24.search.solr.util.FloatValueCache;

/**
 * A growable writer that stores floats.
 * 
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public class OffsetGrowableFloatWriter extends OffsetGrowableWriter implements FloatValueCache {

   public static final int DEFAULT_PRECISION = 10000;

   private final float precision;

   public OffsetGrowableFloatWriter(int precision, int startBitsPerValue, int valueCount, float acceptableOverheadRatio) {
      super(startBitsPerValue, valueCount, acceptableOverheadRatio);

      this.precision = precision;
   }

   /**
    * sets the value for the given document id
    */
   public void setFloat(int doc, float value) {
      checkArgument(doc >= 0, "Pre-condition violated: expression doc >= 0 must be true.");
      checkArgument(value >= 0, "Pre-condition violated: expression value >= 0 must be true.");

      set(doc, Math.round(value * precision));
   }

   /**
    * returns the stored value for the given document or Float.NaN if not set
    * yet.
    */
   public float getFloat(int doc) {
      if (hasValue(doc)) {
         return (float) get(doc) / precision;
      }

      return Float.NaN;
   }
}
