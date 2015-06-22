package com.s24.search.solr.component;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;

import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.search.ReRankQParserPlugin;

import com.google.common.base.Joiner;
import com.s24.search.solr.query.bmax.BmaxQuery;
import com.s24.search.solr.util.BmaxDebugInfo;

/**
 * Adds boost and penalize terms to a query. Boost terms are filled into a
 * <code>bq</code> boost query parameter that can be picked up by the bmax
 * and/or edismax query parser. Penalize terms are filled into a rerank query
 * that will be executed by the {@linkplain ReRankQParserPlugin}.
 * 
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public class BmaxDebugComponent extends SearchComponent {

   public static final String COMPONENT_NAME = "bmax.debug";

   @Override
   public void prepare(ResponseBuilder rb) throws IOException {
      checkNotNull(rb, "Pre-condition violated: rb must not be null.");

      // check component is activated
      if (rb.req.getParams().getBool(COMPONENT_NAME, false)) {
         BmaxDebugInfo.ensureBmaxDebugContainerPresent(rb);
      }
   }

   @Override
   public void process(ResponseBuilder rb) throws IOException {
      checkNotNull(rb, "Pre-condition violated: rb must not be null.");

      if (rb.req.getParams().getBool(COMPONENT_NAME, false)
            && rb.isDebug()) {

         // transfer context information (if present)
         if (rb.req.getContext().get("bmaxQuery") != null
               && rb.req.getContext().get("bmaxQuery") instanceof BmaxQuery) {

            // get query
            BmaxQuery query = (BmaxQuery) rb.req.getContext().get("bmaxQuery");

            // add stuff
            BmaxDebugInfo.add(rb, "query", Joiner.on(' ').join(query.getTermsAndSynonyms().keySet()));
            BmaxDebugInfo.add(rb, "synonyms", Joiner.on(' ').join(query.getTermsAndSynonyms().values()));
            BmaxDebugInfo.add(rb, "subtocpics", Joiner.on(' ').join(query.getTermsAndSubtopics().values()));

            // remove query
            rb.req.getContext().remove("bmaxQuery");
         }
      }
   }

   @Override
   public String getDescription() {
      return "Adds bmax query information to debug info";
   }

   @Override
   public String getSource() {
      return "https://github.com/shopping24/solr-bmax-queryparser";
   }

}
