package com.s24.search.solr.query.bmax;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.ObjectOutputStream;

import com.google.common.base.Objects;
import com.google.common.io.ByteStreams;
import com.google.common.io.CountingOutputStream;

import eu.danieldk.dictomaton.Dictionary;

/**
 * Stores the set of terms that occur in a field in the documents.
 */
public class FieldTermsDictionary {

   private final Dictionary terms;
   private final long estimatedMemorySize;

   /**
    * Creates a term dictionary which does not know the terms of its field. The returned dictionary will return
    * {@code true} for all calls to {@link #fieldMayContainTerm(String)}.
    */
   public FieldTermsDictionary() {
      this.terms = null;
      this.estimatedMemorySize = 0;
   }

   /**
    * Creates a term dictionary with the given terms.
    *
    * @param terms the terms.
    */
   public FieldTermsDictionary(Dictionary terms) {
      this.terms = checkNotNull(terms);

      // A hack to estimate the memory size of the dictionary
      long bytes = -1;
      try (CountingOutputStream cos = new CountingOutputStream(ByteStreams.nullOutputStream());
            ObjectOutputStream oos = new ObjectOutputStream(cos)) {
         oos.writeObject(terms);
         oos.flush();
         bytes = cos.getCount();
      } catch (IOException e) {
         // ignore
      }
      this.estimatedMemorySize = bytes;
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
            .add("termCount", terms == null ? 0 : terms.size())
            .add("estimatedMemorySize", estimatedMemorySize)
            .toString();
   }
}
