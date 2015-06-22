package com.s24.search.solr.util;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;

public class BmaxDebugInfo {

   private BmaxDebugInfo() {
   }

   public static void ensureBmaxDebugContainerPresent(ResponseBuilder rb) {
      checkNotNull(rb, "Pre-condition violated: rb must not be null.");

      // debug map
      if (rb.getDebugInfo() == null) {
         rb.setDebugInfo(new SimpleOrderedMap<Object>());
      }

      // bmax debug map
      if (rb.getDebugInfo().get("bmax") == null) {
         rb.getDebugInfo().add("bmax", new SimpleOrderedMap<String>());
      }
   }

   @SuppressWarnings("unchecked")
   public static void add(ResponseBuilder rb, String key, String value) {
      checkNotNull(rb, "Pre-condition violated: rb must not be null.");
      checkNotNull(key, "Pre-condition violated: key must not be null.");
      checkNotNull(value, "Pre-condition violated: value must not be null.");

      if (rb.isDebug()) {
         ensureBmaxDebugContainerPresent(rb);

         // add info
         ((NamedList<String>) rb.getDebugInfo().get("bmax")).add(key, value);
      }
   }

   public static boolean isDebug(SolrQueryRequest request) {
      checkNotNull(request, "Pre-condition violated: request must not be null.");

      String debug = request.getParams().get(CommonParams.DEBUG);
      
      return debug != null && !"false".equalsIgnoreCase(debug);
   }
}
