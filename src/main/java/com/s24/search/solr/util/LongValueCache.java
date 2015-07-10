package com.s24.search.solr.util;

import org.apache.lucene.util.Accountable;

public interface LongValueCache extends Accountable {

   int size();

   long get(int doc);

   void set(int doc, long value);

   boolean hasValue(int index);
   
   public int getBitsPerValue();

}
