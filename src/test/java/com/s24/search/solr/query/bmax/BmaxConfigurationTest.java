package com.s24.search.solr.query.bmax;

import org.apache.log4j.BasicConfigurator;
import org.apache.solr.SolrTestCaseJ4.SuppressSSL;
import org.apache.solr.util.AbstractSolrTestCase;
import org.junit.BeforeClass;
import org.junit.Test;

@SuppressSSL
public class BmaxConfigurationTest extends AbstractSolrTestCase {

   @BeforeClass
   public static void setupBmaxCore() throws Exception {
      BasicConfigurator.resetConfiguration();
      BasicConfigurator.configure();
      
      // setup core
      initCore("bmax-simple-solrconfig.xml", "bmax-simple-schema.xml");
   }

   @Test
   public void testRunning() throws Exception {
      assertTrue(true);
   }

}
