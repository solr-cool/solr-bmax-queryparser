package com.s24.search.solr.component;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.lucene.queries.function.ValueSource;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.search.SolrCache;

import com.google.common.base.Preconditions;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.s24.search.solr.functions.FloatCachingValueSource;

/**
 * Replaces given boost parameters with a single cached boost function.
 * 
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public abstract class AbstractCachingComponent extends SearchComponent {

   // the param to cache. This will be use as cache prefix
   private final String param;

   private final String componentName;
   private final String functionName;
   private final String debugParamName;

   public AbstractCachingComponent(String param) {
      checkNotNull(param, "Pre-condition violated: param must not be null.");

      this.param = param;
      this.componentName = param + ".cache";
      this.debugParamName = param + ".cached";
      this.functionName = "cached" + param;
   }

   /**
    * If the query contains more than one boost, computes a replacement boost
    * function which caches boost values and replaces the boosts in the query
    * with a call of the caching function.
    */
   @Override
   public void prepare(ResponseBuilder rb) throws IOException {
      Preconditions.checkNotNull(rb);

      // check component is activated
      if (rb.req.getParams().getBool(componentName, false)) {
         String[] boosts = rb.req.getParams().getParams(param);

         @SuppressWarnings("unchecked")
         SolrCache<String, ValueSource> cache = rb.req.getSearcher().getCache(componentName);

         // more than one boost given
         if (cache != null && boosts != null && boosts.length > 1) {
            try {
               // replace all boosts with the new, cached boost function
               ModifiableSolrParams params = new ModifiableSolrParams(rb.req.getParams());
               params.remove(param);
               params.add("boost", computeBoostFunction(rb, boosts, cache));
               params.set(debugParamName, boosts);
               rb.req.setParams(params);
            } catch (Exception e) {
               throw new IOException(e);
            }
         }
      }
   }

   /**
    * Creates a cache entry for the given boosts and returns the
    * <code>cachedboost</code> function that replaces the original boosts in the
    * query.
    */
   protected String[] computeBoostFunction(
         ResponseBuilder rb,
         String[] boosts,
         SolrCache<String, ValueSource> cache) throws Exception {
      checkNotNull(rb, "Pre-condition violated: rb must not be null.");
      checkNotNull(boosts, "Pre-condition violated: boosts must not be null.");
      checkNotNull(cache, "Pre-condition violated: cache must not be null.");

      // compile boost functions
      ValueSource function = compileValueFunctions(rb, boosts);

      // check that we got a function query or we're doomed.
      if (function != null) {

         // compute hash key for the boosts given
         String key = computeHashKey(boosts);

         // place compiled functions into cache if not already present
         if (cache.get(key) == null) {
            cache.put(key, wrapInCachingValueSource(function, rb.req.getSearcher().maxDoc()));
         }

         // replace boost functions with a cached one
         boosts = new String[] { String.format(Locale.US, "%s(%s,%s)", functionName, componentName, key) };
      }

      return boosts;
   }

   /**
    * Compute a hash key for the given boosts.
    */
   private String computeHashKey(String[] boosts) {
      Hasher hasher = Hashing.murmur3_32().newHasher();
      for (int i = 0; i < boosts.length; i++) {
         hasher.putString(boosts[i], StandardCharsets.UTF_8);
      }
      return hasher.hash().toString();
   }

   /**
    * Wraps the compiled value function into a caching function. By default,
    * this uses a {@linkplain FloatCachingValueSource}.
    */
   protected ValueSource wrapInCachingValueSource(ValueSource function, int maxDocs) {
      return new FloatCachingValueSource(function, maxDocs);
   }

   /**
    * Compiles the boost params given into a Value Source representation or
    * <code>null</code> if the compiled boost functions cannot be cached in any
    * way.
    */
   protected abstract ValueSource compileValueFunctions(ResponseBuilder rb, String[] boosts)
         throws Exception;

   @Override
   public void process(ResponseBuilder rb) throws IOException {
      // noop
   }

   @Override
   public String getSource() {
      return "https://github.com/shopping24/com.s24.api";
   }
}
