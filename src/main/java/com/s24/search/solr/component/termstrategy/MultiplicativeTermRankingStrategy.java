package com.s24.search.solr.component.termstrategy;

import org.apache.solr.common.params.ModifiableSolrParams;

public class MultiplicativeTermRankingStrategy extends AbstractTermRankingStrategy {

    @Override
    protected void addToQuery(String termRankingQueryString, ModifiableSolrParams params) {
        params.add("boost", termRankingQueryString);
    }

}
