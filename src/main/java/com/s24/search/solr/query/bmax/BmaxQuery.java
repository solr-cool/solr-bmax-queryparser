package com.s24.search.solr.query.bmax;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * POJO for query data used in bmax
 * 
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public class BmaxQuery {

   // terms with their collected synonyms and subopics
   private final Collection<BmaxTerm> terms = Lists.newArrayList();

   // fields to query and their boosts
   private final Map<String, Float> fieldsAndBoosts = Maps.newHashMap();

   // boost for synonyms
   private float synonymBoost = 0.1f;
   private float subtopicBoost = 0.01f;
   private float tieBreakerMultiplier = 0.0f;

   public Map<String, Float> getFieldsAndBoosts() {
      return fieldsAndBoosts;
   }

   public float getSynonymBoost() {
      return synonymBoost;
   }

   public void setSynonymBoost(float synonymBoost) {
      this.synonymBoost = synonymBoost;
   }

   public float getSubtopicBoost() {
      return subtopicBoost;
   }

   public void setSubtopicBoost(float subtopicBoost) {
      this.subtopicBoost = subtopicBoost;
   }

   public Collection<BmaxTerm> getTerms() {
      return terms;
   }

   public float getTieBreakerMultiplier() {
      return tieBreakerMultiplier;
   }

   public void setTieBreakerMultiplier(float tieBreakerMultiplier) {
      this.tieBreakerMultiplier = tieBreakerMultiplier;
   }

   public static final Function<BmaxTerm, CharSequence> toQueryTerm = new Function<BmaxQuery.BmaxTerm, CharSequence>() {
      @Override
      public CharSequence apply(BmaxTerm bt) {
         return bt.getTerm();
      }
   };
   public static final Function<BmaxTerm, Set<CharSequence>> toSynonyms = new Function<BmaxQuery.BmaxTerm, Set<CharSequence>>() {
      @Override
      public Set<CharSequence> apply(BmaxTerm bt) {
         return bt.getSynonyms();
      }
   };
   public static final Function<BmaxTerm, Set<CharSequence>> toSubtopics = new Function<BmaxQuery.BmaxTerm, Set<CharSequence>>() {
      @Override
      public Set<CharSequence> apply(BmaxTerm bt) {
         return bt.getSubtopics();
      }
   };
   
   public static class BmaxTerm {
      private final CharSequence term;
      private final Set<CharSequence> synonyms = Sets.newHashSet();
      private final Set<CharSequence> subtopics = Sets.newHashSet();

      public BmaxTerm(CharSequence term) {
         this.term = term;
      }

      public CharSequence getTerm() {
         return term;
      }

      public Set<CharSequence> getSynonyms() {
         return synonyms;
      }

      public Set<CharSequence> getSubtopics() {
         return subtopics;
      }
   }

}
