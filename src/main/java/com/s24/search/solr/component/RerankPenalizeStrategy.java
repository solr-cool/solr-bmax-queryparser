package com.s24.search.solr.component;

import org.apache.lucene.analysis.Analyzer;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.handler.component.ResponseBuilder;

import java.util.Locale;

public class RerankPenalizeStrategy extends PenalizeStrategy {

    protected final int penalizeDocs;
    protected final float penalizeFactor;

    public RerankPenalizeStrategy(final ResponseBuilder rb, final String q, final String penalizeExtraTerms,
                                  final Analyzer penalizeAnalyzer, final String penalizeFields, final int penalizeDocs,
                                  final float penalizeFactor) {
        super(rb, q, penalizeExtraTerms, penalizeAnalyzer, penalizeFields);
        this.penalizeDocs = penalizeDocs;
        this.penalizeFactor = penalizeFactor;
    }

    @Override
    protected void addToQuery(final String penalizeQueryString, final ModifiableSolrParams queryParams) {
        queryParams.add(CommonParams.RQ,
                String.format(Locale.US, "{!rerank reRankQuery=$rqq reRankDocs=%s reRankWeight=%.6f}",
                penalizeDocs, penalizeFactor));
        queryParams.add("rqq", penalizeQueryString);
    }

}
