package com.s24.search.solr.query.bmax;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BmaxQuery {

   private final Map<CharSequence, Set<CharSequence>> termsAndSynonyms = new HashMap<CharSequence, Set<CharSequence>>();
   private final Map<CharSequence, Set<CharSequence>> termsAndSubtopics = new HashMap<CharSequence, Set<CharSequence>>();
   private final Set<CharSequence> boostUpTerms = new HashSet<CharSequence>();
   private final Set<CharSequence> boostDownTerms = new HashSet<CharSequence>();
   private final Map<String, Float> fieldsAndBoosts = new HashMap<String, Float>();
   private final Map<String, Float> boostFieldsAndBoosts = new HashMap<String, Float>();
   private float synonymBoost = 0.01f;
   private boolean manipulateDocumentFrequencies = true;
   private boolean manipulateTermFrequencies = true;

   public Map<CharSequence, Set<CharSequence>> getTermsAndSynonyms() {
      return termsAndSynonyms;
   }

   public Map<CharSequence, Set<CharSequence>> getTermsAndSubtopics() {
      return termsAndSubtopics;
   }

   public Set<CharSequence> getBoostUpTerms() {
      return boostUpTerms;
   }

   public Set<CharSequence> getBoostDownTerms() {
      return boostDownTerms;
   }

   public Map<String, Float> getFieldsAndBoosts() {
      return fieldsAndBoosts;
   }

   public Map<String, Float> getBoostFieldsAndBoosts() {
      return boostFieldsAndBoosts;
   }

   public float getSynonymBoost() {
      return synonymBoost;
   }

   public void setSynonymBoost(float synonymBoost) {
      this.synonymBoost = synonymBoost;
   }

   public boolean isManipulateDocumentFrequencies() {
      return manipulateDocumentFrequencies;
   }

   public void setManipulateDocumentFrequencies(boolean manipulateDocumentFrequencies) {
      this.manipulateDocumentFrequencies = manipulateDocumentFrequencies;
   }

   public boolean isManipulateTermFrequencies() {
      return manipulateTermFrequencies;
   }

   public void setManipulateTermFrequencies(boolean manipulateTermFrequencies) {
      this.manipulateTermFrequencies = manipulateTermFrequencies;
   }
}
