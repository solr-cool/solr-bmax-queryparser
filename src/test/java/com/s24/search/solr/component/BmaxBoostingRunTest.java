package com.s24.search.solr.component;

import org.apache.log4j.BasicConfigurator;
import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.junit.Test;

import static com.s24.search.solr.component.BmaxBoostConstants.COMPONENT_NAME;

public class BmaxBoostingRunTest extends SolrTestCaseJ4 {

    /**
     * This test currently fails because of a bug in solr 7.2 (https://issues.apache.org/jira/browse/SOLR-11809).
     */
    @Test
    public void testQueryReturns200() throws Exception {
        BasicConfigurator.resetConfiguration();
        BasicConfigurator.configure();

        // setup core
        initCore("bmax-simple-solrconfig.xml", "bmax-simple-schema.xml");

        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "hose");
        params.set("defType", "bmax");
        params.set("qf", "id");
        params.set(COMPONENT_NAME, "true");

        assertQ(req(params));
    }
}
