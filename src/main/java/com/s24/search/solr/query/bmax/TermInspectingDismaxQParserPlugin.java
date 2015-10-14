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
 * Query parser plugin.
 */
public class TermInspectingDismaxQParserPlugin extends QParserPlugin {
   private String queryParsingFieldType;

   // pre-loaded analyzers to use
   private Analyzer queryParsingAnalyzer;

   @Override
   public void init(@SuppressWarnings("rawtypes") NamedList args) {
      checkNotNull(args, "Pre-condition violated: args must not be null.");

      // mandatory
      queryParsingFieldType = checkNotNull((String) args.get("queryParsingFieldType"), "No queryParsingFieldType given. Aborting.");
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
      }
      checkNotNull(queryParsingAnalyzer, "Pre-condition violated: queryParsingAnalyzer must not be null.");

      return new TermInspectingDismaxQParser(qstr, localParams, req.getParams(), req, queryParsingAnalyzer,
            req.getSearcher().getCache("bmax.fieldTermCache"));
   }
}
