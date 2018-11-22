package com.s24.search.solr.component;

import org.apache.lucene.analysis.Analyzer;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.common.params.ModifiableSolrParams;

import java.util.Locale;

public class BoostQueryPenalizeStrategy extends PenalizeStrategy {

    protected final float penalizeFactor;

    public BoostQueryPenalizeStrategy(final String q, final String penalizeExtraTerms,
                                      final Analyzer penalizeAnalyzer, final String penalizeFields,
                                      final float penalizeFactor) {
        super(q, penalizeExtraTerms, penalizeAnalyzer, penalizeFields);
        this.penalizeFactor = penalizeFactor;
    }

    @Override
    protected void addToQuery(final String penalizeQueryString, final ModifiableSolrParams queryParams) {
        queryParams.add(DisMaxParams.BQ, "(" + penalizeQueryString + String.format(Locale.US, ")^%.6f", penalizeFactor));
    }
}
