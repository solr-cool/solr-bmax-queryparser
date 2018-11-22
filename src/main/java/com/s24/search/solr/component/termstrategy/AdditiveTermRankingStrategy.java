package com.s24.search.solr.component.termstrategy;

import com.s24.search.solr.component.AbstractTermRankingStrategy;
import org.apache.solr.common.params.ModifiableSolrParams;

public class AdditiveTermRankingStrategy extends AbstractTermRankingStrategy {

    @Override
    protected void addToQuery(String termRankingQueryString, ModifiableSolrParams params) {
        params.add("bq", termRankingQueryString);
    }

}
