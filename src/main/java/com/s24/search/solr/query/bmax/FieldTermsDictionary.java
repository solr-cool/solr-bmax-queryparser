package com.s24.search.solr.query.bmax;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Objects;

import eu.danieldk.dictomaton.Dictionary;

/**
 * Stores the set of terms that occur in a field in the documents.
 */
public class FieldTermsDictionary {

   private final Dictionary terms;

   /**
    * Creates a term dictionary which does not know the terms of its field. The returned dictionary will return
    * {@code true} for all calls to {@link #fieldMayContainTerm(String)}.
    */
   public FieldTermsDictionary() {
      this.terms = null;
   }

   /**
    * Creates a term dictionary with the given terms.
    *
    * @param terms the terms.
    */
   public FieldTermsDictionary(Dictionary terms) {
      this.terms = checkNotNull(terms);
   }

   /**
    * Returns true if the field for which this entry contains the terms may contain the given term.
    */
   public boolean fieldMayContainTerm(String term) {
      // The term may be contained if we don't know the terms; otherwise, it is contained if it is contained ;)
      return terms == null || terms.contains(term);
   }

   @Override
   public String toString() {
      return Objects.toStringHelper(this)
            .add("hasTerms", terms != null)
            .add("size", terms == null ? 0 : terms.size())
            .toString();
   }
}
