package com.s24.search.solr.functions;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.Map;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.queries.function.FunctionValues;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.docvalues.FloatDocValues;
import org.apache.lucene.util.packed.PackedInts;

import com.google.common.base.Objects;
import com.s24.search.solr.util.packed.OffsetGrowableFloatWriter;

/**
 * A ValueSource for FloatDocValues that caches the values retrieved from an
 * underlying ValueSource.
 */
public class FloatCachingValueSource extends ValueSource {

   private final ValueSource source;
   private final OffsetGrowableFloatWriter cache;

   /**
    * Creates a caching value source for the given underlying value source.
    * 
    * @param source
    *           the source of the uncached values.
    * @param maxDoc
    *           the maxDoc of the index.
    */
   public FloatCachingValueSource(ValueSource source, int maxDoc) {
      this.source = checkNotNull(source);
      this.cache = new OffsetGrowableFloatWriter(
            OffsetGrowableFloatWriter.DEFAULT_PRECISION,
            4, maxDoc,
            PackedInts.DEFAULT);
   }

   /**
    * Returns true if the value for the given document is cached.
    */
   private boolean isCached(int doc) {
      return cache.hasValue(doc);
   }

   /**
    * Checks whether this atomic reader context is valid. Cache for valid reader
    * contexts only.
    */
   private boolean isValidAtomicReaderContext(AtomicReaderContext readerContext) {
      return readerContext != null && readerContext.docBase > 0;
   }

   @Override
   public FunctionValues getValues(@SuppressWarnings("rawtypes") Map context, final AtomicReaderContext readerContext)
         throws IOException {
      final FunctionValues sourceValues = source.getValues(context, readerContext);

      return new FloatDocValues(this) {
         @Override
         public float floatVal(int doc) {
            if (isValidAtomicReaderContext(readerContext)) {
               if (!isCached(doc)) {
                  cache.setFloat(doc, sourceValues.floatVal(doc));
               }

               return cache.getFloat(doc);
            }

            return sourceValues.floatVal(doc);
         }
      };
   }

   @Override
   public boolean equals(Object o) {
      if (o == null || this.getClass() != o.getClass()) {
         return false;
      }
      FloatCachingValueSource other = (FloatCachingValueSource) o;
      return this.source.equals(other.source) && (this.cache.size() == other.cache.size());
   }

   @Override
   public int hashCode() {
      return source.hashCode();
   }

   @Override
   public String description() {
      return "cached(" + source.description() + ")";
   }

   @Override
   public String toString() {
      return Objects.toStringHelper(this).add("source", source).toString();
   }
}
