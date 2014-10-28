package com.s24.search.solr.query.bmax;

import static com.google.common.base.Preconditions.checkNotNull;

import org.apache.lucene.analysis.Analyzer;
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
    * Field type with query analyzer for boost terms.
    */
   private String boostUpFieldType;
   private Analyzer boostUpAnalyzer;

   /**
    * Field type with query analyzer for penalize terms.
    */
   private String boostDownFieldType;
   private Analyzer boostDownAnalyzer;

   private String synonymFieldType;
   private Analyzer synonymAnalyzer;

   private String queryParsingFieldType;
   private Analyzer queryParsingAnalyzer;

   @Override
   public void init(@SuppressWarnings("rawtypes") NamedList args) {

      boostUpFieldType = checkNotNull((String) args.get("boostUpFieldType"));
      boostDownFieldType = checkNotNull((String) args.get("boostDownFieldType"));
      queryParsingFieldType = checkNotNull((String) args.get("queryParsingFieldType"));
      synonymFieldType = checkNotNull((String) args.get("synonymFieldType"));
   }

   @Override
   public QParser createParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
      checkNotNull(req, "Pre-condition violated: req must not be null.");

      if (queryParsingAnalyzer == null) {
         this.boostUpAnalyzer = (boostUpFieldType != null) ? req.getSchema().getFieldTypeByName(boostUpFieldType)
               .getQueryAnalyzer() : null;
         this.boostDownAnalyzer = (boostDownFieldType != null) ? req.getSchema().getFieldTypeByName(boostDownFieldType)
               .getQueryAnalyzer() : null;
         this.queryParsingAnalyzer = (queryParsingFieldType != null) ? req.getSchema()
               .getFieldTypeByName(queryParsingFieldType)
               .getQueryAnalyzer() : null;
         this.synonymAnalyzer = (synonymFieldType != null) ? req.getSchema().getFieldTypeByName(synonymFieldType)
               .getQueryAnalyzer() : null;
      }
      checkNotNull(queryParsingAnalyzer, "Pre-condition violated: queryParsingAnalyzer must not be null.");

      return new BmaxQueryParser(qstr, localParams, params, req, queryParsingAnalyzer, synonymAnalyzer,
            boostUpAnalyzer, boostDownAnalyzer);
   }
}
