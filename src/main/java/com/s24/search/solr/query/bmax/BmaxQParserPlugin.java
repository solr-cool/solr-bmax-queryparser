package com.s24.search.solr.query.bmax;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.lucene.analysis.Analyzer;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;

/**
 * 
 * @author Shopping24 GmbH, Torsten Bøgh Köster (@tboeghk)
 */
public class BmaxQParserPlugin extends QParserPlugin {
   /**
    * Field type with query analyzer for penalize terms.
    */
   private String queryParsingFieldType;
   private String synonymFieldType;
   private String subtopicFieldType;

   // pre-loaded analyzers to use
   private Analyzer synonymAnalyzer;
   private Analyzer subtopicAnalyzer;
   private Analyzer queryParsingAnalyzer;

   @Override
   public void init(@SuppressWarnings("rawtypes") NamedList args) {
      checkNotNull(args, "Pre-condition violated: args must not be null.");
      
      // mandatory
      queryParsingFieldType = checkNotNull((String) args.get("queryParsingFieldType"), "No queryParsingFieldType given. Aborting.");

      // optional
      synonymFieldType = (String) args.get("synonymFieldType");
      subtopicFieldType = (String) args.get("subtopicFieldType");
   }

   @SuppressWarnings("unchecked")
   @Override
   public QParser createParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
      checkNotNull(req, "Pre-condition violated: req must not be null.");

      // get query parsers if not available
      if (queryParsingAnalyzer == null) {
         this.queryParsingAnalyzer = (queryParsingFieldType != null) ? req.getSchema()
               .getFieldTypeByName(queryParsingFieldType)
               .getQueryAnalyzer() : null;
         this.synonymAnalyzer = (synonymFieldType != null) ? req.getSchema().getFieldTypeByName(synonymFieldType)
               .getQueryAnalyzer() : null;
         this.subtopicAnalyzer = (subtopicFieldType != null) ? req.getSchema().getFieldTypeByName(subtopicFieldType)
                     .getQueryAnalyzer() : null;
      }
      checkNotNull(queryParsingAnalyzer, "Pre-condition violated: queryParsingAnalyzer must not be null.");

      // check for modifiable solr params
      if (!(req.getParams() instanceof ModifiableSolrParams)) {
         
         // and force them modifiable
         req.setParams(new ModifiableSolrParams(req.getParams()));
      } 
      
      return new BmaxQueryParser(qstr, localParams, req.getParams(), req, queryParsingAnalyzer, 
            synonymAnalyzer, subtopicAnalyzer, req.getSearcher().getCache("bmax.fieldTermCache"));
   }
}
