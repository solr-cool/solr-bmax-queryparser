package com.s24.search.solr.util;

import org.apache.lucene.util.Accountable;

/**
 * A interface for a per-doc float value cache.
 * 
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public interface FloatValueCache extends Accountable {

   int size();
   
   float getFloat(int doc);

   void setFloat(int doc, float value);

   boolean hasValue(int index);
}
