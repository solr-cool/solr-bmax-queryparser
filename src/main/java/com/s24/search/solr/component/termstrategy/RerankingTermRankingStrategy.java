package com.s24.search.solr.component.termstrategy;

import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;

import java.util.Locale;

public class RerankingTermRankingStrategy extends AbstractTermRankingStrategy {

    private int reRankDocs;

    public RerankingTermRankingStrategy(int reRankDocs) {
        this.reRankDocs = reRankDocs;
    }

    @Override
    protected void addToQuery(String termRankingQueryString, ModifiableSolrParams params) {
        //currently (22.11.2018) only one reRankquery is possible so only apply if we don't overwrite something
        if (params.get(CommonParams.RQ) == null) {
            params.add(CommonParams.RQ,
                    String.format(Locale.US, "{!rerank reRankQuery=$rqq reRankDocs=%s}",
                            reRankDocs));
            params.add("rqq", termRankingQueryString);
        }
    }
}
