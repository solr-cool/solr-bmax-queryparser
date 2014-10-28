package com.s24.search.solr.query.bmax;

import java.io.Closeable;
import java.io.IOException;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.analysis.TokenStream;

public class TokenStreams {

   public static void resetQuietly(TokenStream tokenStream) {
      if (tokenStream != null) {
         try {
            tokenStream.reset();
         } catch (Exception e) {
            // ignored
         }
      }
   }

   public static void endQuietly(TokenStream tokenStream) {
      if (tokenStream != null) {
         try {
            tokenStream.end();
         } catch (IOException e) {
            // ignored
         }
      }
   }

   /**
    * Unconditionally close a <code>Closeable</code>.
    * <p>
    * Equivalent to {@link Closeable#close()}, except any exceptions will be
    * ignored. This is typically used in finally blocks.
    * <p>
    * Example code:
    * 
    * <pre>
    * Closeable closeable = null;
    * try {
    *    closeable = new FileReader(&quot;foo.txt&quot;);
    *    // process closeable
    *    closeable.close();
    * } catch (Exception e) {
    *    // error handling
    * } finally {
    *    IOUtils.closeQuietly(closeable);
    * }
    * </pre>
    * 
    * @param closeable
    *           the object to close, may be null or already closed
    * @since 2.0
    */
   public static void closeQuietly(Closeable closeable) {
      IOUtils.closeQuietly(closeable);
   }

}
