package com.s24.search.solr.component;

public interface BmaxBoostConstants {

    String COMPONENT_NAME = "bmax.booster";

    // params
    String PENALIZE_EXTRA_TERMS = COMPONENT_NAME + ".penalize.extra";
    String PENALIZE_DOC_COUNT = COMPONENT_NAME + ".penalize.docs";
    String PENALIZE_FACTOR = COMPONENT_NAME + ".penalize.factor";
    String PENALIZE_ENABLE = COMPONENT_NAME + ".penalize";
    String PENALIZE_FIELDS = COMPONENT_NAME + ".penalize.qf";
    String PENALIZE_STRATEGY = COMPONENT_NAME + ".penalize.strategy";
    String VALUE_PENALIZE_STRATEGY_RERANK = "rq";
    String VALUE_PENALIZE_STRATEGY_BOOST_QUERY = "bq";

    String BOOST_EXTRA_TERMS = COMPONENT_NAME + ".boost.extra";
    String BOOST_ENABLE = COMPONENT_NAME + ".boost";
    String BOOST_FACTOR = COMPONENT_NAME + ".boost.factor";
    String BOOST_FIELDS = COMPONENT_NAME + ".boost.qf";
    String BOOST_STRATEGY = COMPONENT_NAME + ".boost.strategy";
    String VALUE_BOOST_STRATEGY_ADDITIVELY = "bq";
    String VALUE_BOOST_STRATEGY_MULTIPLICATIVE = "boost";

    String SYNONYM_ENABLE = COMPONENT_NAME + ".synonyms";

}
