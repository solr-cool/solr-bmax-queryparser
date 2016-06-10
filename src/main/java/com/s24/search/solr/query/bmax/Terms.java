package com.s24.search.solr.query.bmax;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.input.CharSequenceReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.util.SolrPluginUtils;

import com.google.common.collect.Lists;

/**
 * Utility methods that yet cannot be found in {@linkplain SolrPluginUtils}
 *
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public class Terms {

   /**
    * Analyzes the given string using the given {@link Analyzer} (-chain).
    *
    * @param input
    *           Input string.
    * @param analyzer
    *           Analyzer.
    * @return All terms from the resulting token stream.
    */
   public static Set<CharSequence> collect(CharSequence input, Analyzer analyzer) {
      checkNotNull(input, "Pre-condition violated: input must not be null.");
      checkNotNull(analyzer, "Pre-condition violated: analyzer must not be null.");

      Set<CharSequence> result = new HashSet<>();
      TokenStream tokenStream = null;
      try {
         tokenStream = analyzer.tokenStream("bmax", new CharSequenceReader(input));
         tokenStream.reset();
         CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);

         while (tokenStream.incrementToken()) {
            // Needs to converted to string, because on tokenStream.end()
            // the charTermAttribute will be flushed.
            result.add(charTermAttribute.toString());
         }

      } catch (IOException e) {
         throw new RuntimeException(e);
      } finally {
         TokenStreams.endQuietly(tokenStream);
         TokenStreams.resetQuietly(tokenStream);
         TokenStreams.closeQuietly(tokenStream);
      }

      return result;
   }

   /**
    * Collects terms from the given analyzer relying on {@linkplain BytesRef}s and not strings.
    */
   public static Collection<Term> collectTerms(CharSequence input, Analyzer analyzer, String field) {
      checkNotNull(input, "Pre-condition violated: input must not be null.");
      checkNotNull(analyzer, "Pre-condition violated: analyzer must not be null.");
      checkNotNull(field, "Pre-condition violated: field must not be null.");

      Collection<Term> result = Lists.newArrayList();
      TokenStream tokenStream = null;
      try {
         tokenStream = analyzer.tokenStream(field, new CharSequenceReader(input));
         tokenStream.reset();
         TermToBytesRefAttribute termAttribute = tokenStream.addAttribute(TermToBytesRefAttribute.class);

         while (tokenStream.incrementToken()) {
            // Needs to converted to a deep copy of byte ref, because on
            // tokenStream.end()
            // the termAttribute will be flushed.
            termAttribute.getBytesRef();
            result.add(new Term(field, BytesRef.deepCopyOf(termAttribute.getBytesRef())));
         }
      } catch (IOException e) {
         throw new RuntimeException(e);
      } finally {
         TokenStreams.endQuietly(tokenStream);
         TokenStreams.resetQuietly(tokenStream);
         TokenStreams.closeQuietly(tokenStream);
      }

      return result;
   }

   /**
    * Collects terms from the given analyzer relying on {@linkplain BytesRef}s and not strings.
    */
   public static Collection<Term> collectTerms(Set<CharSequence> input, Analyzer analyzer, String field) {
      return input.stream()
            .map(s -> collectTerms(s, analyzer, field))
            .flatMap(e -> e.stream())
            .collect(Collectors.toList());
   }

   /**
    * Collects the maximum document frequency for the terms given.
    */
   public static int collectMaximumDocumentFrequency(Collection<Term> terms,
         SolrIndexSearcher indexSearcher) {
      checkNotNull(terms, "Pre-condition violated: terms must not be null.");
      checkNotNull(indexSearcher, "Pre-condition violated: indexSearcher must not be null.");

      int df = -1;

      try {
         for (Term term : terms) {
            df = Math.max(df, indexSearcher.docFreq(term));
         }
      } catch (IOException e) {
         throw new RuntimeException(e);
      }

      return df;
   }
}
