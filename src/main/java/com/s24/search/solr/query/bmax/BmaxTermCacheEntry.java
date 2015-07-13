package com.s24.search.solr.query.bmax;

import com.google.common.base.Objects;

import eu.danieldk.dictomaton.Dictionary;

public class BmaxTermCacheEntry {

   private final Dictionary terms;
   private final int termCount;
   private final boolean cache;

   public BmaxTermCacheEntry(int termCount) {
      this(null, termCount, false);
   }

   public BmaxTermCacheEntry(Dictionary terms, int termCount, boolean cache) {
      this.terms = terms;
      this.termCount = termCount;
      this.cache = cache;
   }

   public Dictionary getTerms() {
      return terms;
   }

   public int getTermCount() {
      return termCount;
   }

   public boolean isCache() {
      return cache;
   }
   
   @Override
   public String toString() {
      return Objects.toStringHelper(this)
            .add("cache", cache)
            .add("termCount", termCount)
            .toString();
   }
   
}
