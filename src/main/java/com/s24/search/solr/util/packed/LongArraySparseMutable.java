package com.s24.search.solr.util.packed;

import com.s24.search.solr.util.LongArrayValueCache;
import com.s24.search.solr.util.LongValueCache;

public class LongArraySparseMutable extends AbstractSparseValues {

   public LongArraySparseMutable(int maxValueCount) {
      super(maxValueCount);
   }

   @Override
   protected LongValueCache createNewValues() {
      return new LongArrayValueCache(Long.SIZE);
   }

}
