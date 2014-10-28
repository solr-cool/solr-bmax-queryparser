package com.s24.search.solr.query.bmax;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class BmaxQuery {

   private final Map<String, Set<String>> termsAndSynonyms = new HashMap<String, Set<String>>();
   private final Set<String> boostUpTerms = new HashSet<String>();
   private final Set<String> boostDownTerms = new HashSet<String>();
   private final Map<String, Float> fieldsAndBoosts = new HashMap<String, Float>();
   private final Map<String, Float> boostFieldsAndBoosts = new HashMap<String, Float>();
   private float synonymBoost = 0.01f;
   private boolean manipulateDocumentFrequencies = true;
   private boolean manipulateTermFrequencies = true;

   public Map<String, Set<String>> getTermsAndSynonyms() {
      return termsAndSynonyms;
   }

   public Set<String> getBoostUpTerms() {
      return boostUpTerms;
   }

   public Set<String> getBoostDownTerms() {
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
