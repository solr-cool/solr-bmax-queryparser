package com.s24.search.solr.component;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.lucene.queries.function.FunctionQuery;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.ProductFloatFunction;
import org.apache.lucene.search.Query;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.search.FunctionQParserPlugin;
import org.apache.solr.search.QParser;

/**
 * Replaces given boost parameters with a single cached boost function.
 * 
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public class BmaxCacheBoostComponent extends AbstractCachingComponent {

   public BmaxCacheBoostComponent() {
      super("boost");
   }

   /**
    * {@inheritDoc}
    */
   protected ValueSource compileValueFunctions(ResponseBuilder rb, String[] boosts)
         throws Exception {
      checkNotNull(rb, "Pre-condition violated: rb must not be null.");
      checkNotNull(boosts, "Pre-condition violated: boosts must not be null.");

      ValueSource[] functions = new ValueSource[boosts.length];

      // iterate boost params
      for (int i = 0; i < boosts.length; i++) {
         Query q = QParser.getParser(boosts[i], FunctionQParserPlugin.NAME, rb.req).getQuery();
         if (q instanceof FunctionQuery) {
            functions[i] = (((FunctionQuery) q).getValueSource());
         } else {
            return null;
         }
      }

      // compute a product of all value sources function.
      return new ProductFloatFunction(functions);
   }

   @Override
   public String getDescription() {
      return "Replaces configured boost functions with a single cached function.";
   }

}
