package com.s24.search.solr.query.bmax;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;

/**
 * POJO for query data used in bmax
 * 
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public class BmaxQuery {

   // terms with their collected synonyms
   private final Map<CharSequence, Set<CharSequence>> termsAndSynonyms = Maps.newHashMap();

   // terms with their collected subtopics
   private final Map<CharSequence, Set<CharSequence>> termsAndSubtopics = Maps.newHashMap();

   // fields to query and their boosts
   private final Map<String, Float> fieldsAndBoosts = Maps.newHashMap();

   // boost for synonyms
   private float synonymBoost = 0.01f;

   public Map<CharSequence, Set<CharSequence>> getTermsAndSynonyms() {
      return termsAndSynonyms;
   }

   public Map<CharSequence, Set<CharSequence>> getTermsAndSubtopics() {
      return termsAndSubtopics;
   }

   public Map<String, Float> getFieldsAndBoosts() {
      return fieldsAndBoosts;
   }

   public float getSynonymBoost() {
      return synonymBoost;
   }

   public void setSynonymBoost(float synonymBoost) {
      this.synonymBoost = synonymBoost;
   }
}
