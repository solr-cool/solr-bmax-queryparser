package com.s24.search.solr.component.termstrategy;

import org.apache.solr.common.params.ModifiableSolrParams;

public interface TermRankingStrategy {

    String apply(ModifiableSolrParams params);
}
