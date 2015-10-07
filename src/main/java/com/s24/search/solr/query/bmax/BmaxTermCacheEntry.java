package com.s24.search.solr.query.bmax;

import com.google.common.base.Objects;

import eu.danieldk.dictomaton.Dictionary;

public class BmaxTermCacheEntry {

   private final Dictionary terms;
   private final int termCount;
   private final boolean hasTerms;

   public BmaxTermCacheEntry(int termCount) {
      this(null, termCount, false);
   }

   public BmaxTermCacheEntry(Dictionary terms, int termCount, boolean hasTerms) {
      this.terms = terms;
      this.termCount = termCount;
      this.hasTerms = hasTerms;
   }

   public Dictionary getTerms() {
      return terms;
   }

   public int getTermCount() {
      return termCount;
   }

   /**
    * Returns true if this entry contains the cached terms, false otherwise.
    */
   public boolean hasTerms() {
      return hasTerms;
   }
   
   @Override
   public String toString() {
      return Objects.toStringHelper(this)
            .add("hasTerms", hasTerms)
            .add("termCount", termCount)
            .toString();
   }
   
}
