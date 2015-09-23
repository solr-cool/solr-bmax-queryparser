package com.s24.search.solr.util.packed;

import java.util.Collection;
import java.util.Collections;

import org.apache.lucene.util.Accountable;

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

   @Override
   public Collection<Accountable> getChildResources() {
      return Collections.emptyList();
   }

}
