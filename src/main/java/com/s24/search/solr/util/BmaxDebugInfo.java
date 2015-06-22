package com.s24.search.solr.util;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.handler.component.ResponseBuilder;

public class BmaxDebugInfo {

   private BmaxDebugInfo() {
   }

   public static void add(ResponseBuilder rb, String key, String value) {
      checkNotNull(rb, "Pre-condition violated: rb must not be null.");
      checkNotNull(key, "Pre-condition violated: key must not be null.");
      checkNotNull(value, "Pre-condition violated: value must not be null.");

      if (rb.isDebug()) {
         // bmax debug map exists?
         if (rb.getDebugInfo().get("bmax") == null) {
            rb.getDebugInfo().add("bmax", new SimpleOrderedMap<String>());
         }
         
         // add info
      }
   }
}
