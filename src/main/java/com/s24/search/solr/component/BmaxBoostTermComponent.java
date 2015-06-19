package com.s24.search.solr.component;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.lucene.analysis.Analyzer;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.search.ReRankQParserPlugin;
import org.apache.solr.util.SolrPluginUtils;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.s24.search.solr.query.bmax.Terms;

/**
 * Adds boost and penalize terms to a query. Boost terms are filled into a
 * <code>bq</code> boost query parameter that can be picked up by the bmax
 * and/or edismax query parser. Penalize terms are filled into a rerank query
 * that will be executed by the {@linkplain ReRankQParserPlugin}.
 * 
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public class BmaxBoostTermComponent extends SearchComponent {

   public static final String COMPONENT_NAME = "bmax.booster";

   // produces boost terms
   private String boostTermFieldType;

   // produces penalize terms
   private String penalizeTermFieldType;

   @Override
   public void init(@SuppressWarnings("rawtypes") NamedList args) {
      super.init(args);

      SolrParams configuration = SolrParams.toSolrParams(args);
      boostTermFieldType = configuration.get("boostTermFieldType");
      penalizeTermFieldType = configuration.get("penalizeTermFieldType");
   }

   @Override
   public void prepare(ResponseBuilder rb) throws IOException {
      checkNotNull(rb, "Pre-condition violated: rb must not be null.");

      // check component is activated
      if (rb.req.getParams().getBool(COMPONENT_NAME, false)) {
         String q = rb.req.getParams().get(CommonParams.Q);

         if (q != null && !"*:*".equals(q)) {
            
            // debug map exists?
            if (rb.getDebugInfo() == null) {
               rb.setDebugInfo(new SimpleOrderedMap<Object>());
            }
            
            // bmax debug map exists?
            if (rb.getDebugInfo().get("bmax") == null) {
               rb.getDebugInfo().add("bmax", new SimpleOrderedMap<String>());
            }
            
            prepareInternal(rb);
         }
      }
   }

   @SuppressWarnings("unchecked")
   protected void prepareInternal(ResponseBuilder rb) throws IOException {
      checkNotNull(rb, "Pre-condition violated: rb must not be null.");

      // collect preconditions
      String q = rb.req.getParams().get(CommonParams.Q);
      boolean boost = rb.req.getParams().getBool(COMPONENT_NAME + ".boost", true);
      String boostExtraTerms = rb.req.getParams().get(COMPONENT_NAME + ".boost.extra");
      boolean penalize = rb.req.getParams().getBool(COMPONENT_NAME + ".penalize", true);
      float penalizeFactor = -Math.abs(rb.req.getParams().getFloat(COMPONENT_NAME + ".penalize.factor", 100.0f));
      int penalizeDocs = rb.req.getParams().getInt(COMPONENT_NAME + ".penalize.docs", 400);
      String penalizeExtraTerms = rb.req.getParams().get(COMPONENT_NAME + ".penalize.extra");
      Analyzer boostAnalyzer = rb.req.getSearcher().getSchema()
            .getFieldTypeByName(boostTermFieldType).getQueryAnalyzer();
      Analyzer penalizeAnalyzer = rb.req.getSearcher().getSchema()
            .getFieldTypeByName(penalizeTermFieldType).getQueryAnalyzer();
      ModifiableSolrParams params = new ModifiableSolrParams(rb.req.getParams());

      // check boosts
      if (boost) {
         Collection<CharSequence> terms = Terms.collect(q, boostAnalyzer);

         // add extra terms
         if (boostExtraTerms != null) {
            terms.addAll(Sets.newHashSet(Splitter.on(',').omitEmptyStrings().split(boostExtraTerms)));
         }

         // add boosts
         if (!terms.isEmpty()) {
            params.add("bq", String.format(Locale.US, "{!dismax qf='%s' mm=1} %s",
                  rb.req.getParams().get(DisMaxParams.QF),
                  Joiner.on(' ').join(terms)));
            
            // add debug
            ((NamedList<String>) rb.getDebugInfo().get("bmax")).add("boost.terms", Joiner.on(' ').join(terms));
         }
      }

      // check penalizes
      if (penalize) {
         Collection<CharSequence> terms = Terms.collect(q, penalizeAnalyzer);

         // add extra terms
         if (penalizeExtraTerms != null) {
            terms.addAll(Sets.newHashSet(Splitter.on(',').omitEmptyStrings().split(penalizeExtraTerms)));
         }

         // add boosts
         if (!terms.isEmpty()) {
            params.add("rq", String.format(Locale.US, "{!rerank reRankQuery=$rqq reRankDocs=%s reRankWeight=%.1f}",
                  penalizeDocs, penalizeFactor));

            // join terms once. save cpu.
            String joinedTerms = Joiner.on(" OR ").join(terms);

            // iterate query fields
            StringBuilder rerank = new StringBuilder();
            Map<String, Float> queryFields = SolrPluginUtils.parseFieldBoosts(rb.req.getParams().getParams(
                  DisMaxParams.QF));
            for (Entry<String, Float> field : queryFields.entrySet()) {
               if (rerank.length() > 0) {
                  rerank.append(" OR ");
               }

               rerank.append(field.getKey());
               rerank.append(":(");
               rerank.append(joinedTerms);
               rerank.append(')');
            }
            
            // append rerank query
            params.add("rqq", rerank.toString());
            
            // add debug
            ((NamedList<String>) rb.getDebugInfo().get("bmax")).add("penalize.terms", joinedTerms);
         }
      }

      rb.req.setParams(params);
   }

   @Override
   public void process(ResponseBuilder rb) throws IOException {
      // noop
   }

   @Override
   public String getDescription() {
      return "Adds boost and penalize terms to a query";
   }

   @Override
   public String getSource() {
      return "https://github.com/shopping24/solr-bmax-queryparser";
   }

}
