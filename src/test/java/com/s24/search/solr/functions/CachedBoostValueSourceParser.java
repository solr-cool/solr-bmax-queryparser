package com.s24.search.solr.functions;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.lucene.queries.function.ValueSource;
import org.apache.solr.search.FunctionQParser;
import org.apache.solr.search.SolrCache;
import org.apache.solr.search.SyntaxError;
import org.apache.solr.search.ValueSourceParser;

/**
 * Generates a ValueSource for invocations of the cached boost function. The returned ValueSource returns values from
 * the cache if possible.
 * 
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public class CachedBoostValueSourceParser extends ValueSourceParser {

   @Override
   public ValueSource parse(FunctionQParser fp) throws SyntaxError {

      // get cache key
      String cacheName = checkNotNull(fp).parseArg();
      String cacheKey = checkNotNull(fp).parseArg();

      // get cache from searcher
      @SuppressWarnings("unchecked")
      SolrCache<String, FloatCachingValueSource> cache = fp.getReq().getSearcher().getCache(cacheName);
      checkNotNull(cache, "Could not get boostCache from Searcher.");

      // get cached functions from cache
      FloatCachingValueSource valueSource = cache.get(cacheKey);

      return checkNotNull(valueSource, "Could not load pre-cached values for cache key " + cacheKey);
   }
}
