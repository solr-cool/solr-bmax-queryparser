package com.s24.search.solr.util;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.ResponseBuilder;

/**
 * Utility class for debugging
 * 
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public class BmaxDebugInfo {

   private BmaxDebugInfo() {
   }

   /**
    * Add debug info to the response. Values with the same key will be concatenated.
    * 
    * @param rb
    *           Where the debug info will be attached.
    * @param key
    *           Debug info key
    * @param value
    *           Debug info value
    */
   public static void add(ResponseBuilder rb, String key, String value) {
      checkNotNull(rb, "Pre-condition violated: rb must not be null.");
      checkNotNull(key, "Pre-condition violated: key must not be null.");
      checkNotNull(value, "Pre-condition violated: value must not be null.");

      if (rb.isDebug()) {
         String debugKey = key.replace('.', '_');
         String debugValue = value;
         NamedList<Object> debugInfo = rb.getDebugInfo();
         if (debugInfo != null) {
            Object previousValue = debugInfo.remove(debugKey);
            if (previousValue != null) {
               // just add the new value if its valid (not empty and no duplicate)
               debugValue = !((String) previousValue).contains(value) && value.length() > 0
                     ? (String) previousValue + "; " + value : (String) previousValue;
            }
         }
         rb.addDebugInfo(debugKey, debugValue);
      }
   }
}
