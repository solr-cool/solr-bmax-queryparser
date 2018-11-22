package com.s24.search.solr.component;

import static com.s24.search.solr.component.BmaxBoostConstants.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.solr.common.params.DisMaxParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.search.SolrIndexSearcher;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;

public class BmaxBoostTermComponentTest {

    SolrQueryRequest request = mock(SolrQueryRequest.class);
    SolrQueryResponse response = mock(SolrQueryResponse.class);
    SolrIndexSearcher solrIndexSearcher = mock(SolrIndexSearcher.class);
    IndexSchema schema = mock(IndexSchema.class);
    FieldType queryParsingFieldType = mock(FieldType.class);
    FieldType penalizeTermFieldType = mock(FieldType.class);

    ResponseBuilder responseBuilder = new ResponseBuilder(request, response, Collections.emptyList());

    NamedList<String> initArgs = new NamedList<>();

    @Before
    public void setUp() {

        reset(request);
        reset(response);
        reset(solrIndexSearcher);

        when(request.getSearcher()).thenReturn(solrIndexSearcher);
        when(solrIndexSearcher.getSchema()).thenReturn(schema);

        when(schema.getFieldTypeByName("queryParsingFieldType")).thenReturn(queryParsingFieldType);
        when(queryParsingFieldType.getQueryAnalyzer()).thenReturn(new StandardAnalyzer());

        when(schema.getFieldTypeByName("penalizeTermFieldType")).thenReturn(penalizeTermFieldType);
        when(penalizeTermFieldType.getQueryAnalyzer()).thenReturn(new StandardAnalyzer());

        initArgs.add("queryParsingFieldType", "queryParsingFieldType");
        initArgs.add("synonymFieldType", "synonymFieldType");
        initArgs.add("boostTermFieldType", "boostTermFieldType");
        initArgs.add("penalizeTermFieldType", "penalizeTermFieldType");
        initArgs.add("boostQueryType", "tidismax");
        initArgs.add("penalizeQueryType", "tidismax");
    }

    @Test
    public void testThatRerankStrategyIsNotAppliedIfRqAlreadyExists() throws Exception {

        BmaxBoostTermComponent component = new BmaxBoostTermComponent();
        component.init(initArgs);
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "a b c");
        params.set("rq", "dummy");
        params.set(PENALIZE_ENABLE, true);
        params.set(PENALIZE_STRATEGY, VALUE_PENALIZE_STRATEGY_RERANK);
        params.set(SYNONYM_ENABLE, false);
        params.set(BOOST_ENABLE, false);
        params.set(DisMaxParams.QF, "field1 field2^3");
        when(request.getParams()).thenReturn(params);

        component.prepareInternal(responseBuilder);
        ArgumentCaptor<SolrParams> argument = ArgumentCaptor.forClass(SolrParams.class);
        verify(request).setParams(argument.capture());

        Assert.assertThat(argument.getValue().getParams("rq"),
                CoreMatchers.equalTo(new String[] {"dummy"}) );


    }

    @Test
    public void testThatRerankStrategyIsApplied() throws Exception {

        BmaxBoostTermComponent component = new BmaxBoostTermComponent();
        component.init(initArgs);
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "a b c");
        params.set(PENALIZE_ENABLE, true);
        params.set(PENALIZE_STRATEGY, VALUE_PENALIZE_STRATEGY_RERANK);
        params.set(SYNONYM_ENABLE, false);
        params.set(BOOST_ENABLE, false);
        params.set(DisMaxParams.QF, "field1 field2^3");
        when(request.getParams()).thenReturn(params);

        component.prepareInternal(responseBuilder);

        ArgumentCaptor<SolrParams> argument = ArgumentCaptor.forClass(SolrParams.class);
        verify(request).setParams(argument.capture());

        Assert.assertThat(argument.getValue().getParams("rq"),
                CoreMatchers.equalTo(
                        new String[] {"{!rerank reRankQuery=$rqq reRankDocs=400}"}) );

        // 'a' was removed from q as it is a stopword in StandardAnalyzer
        Assert.assertThat(argument.getValue().getParams("rqq"),
                CoreMatchers.equalTo(new String[] {"{!tidismax qf='field1^-100.0 field2^-300.0 ' mm=1 bq=''} b c"}) );
    }

    @Test
    public void testThatBoostQueryStrategyIsApplied() throws Exception {

        BmaxBoostTermComponent component = new BmaxBoostTermComponent();
        component.init(initArgs);
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "a b c");
        params.set(PENALIZE_ENABLE, true);
        params.set(PENALIZE_STRATEGY, VALUE_PENALIZE_STRATEGY_BOOST_QUERY);
        params.set(PENALIZE_FACTOR, 1000000);
        params.set(SYNONYM_ENABLE, false);
        params.set(BOOST_ENABLE, false);
        params.set(DisMaxParams.QF, "field1 field2^3");
        when(request.getParams()).thenReturn(params);

        component.prepareInternal(responseBuilder);

        ArgumentCaptor<SolrParams> argument = ArgumentCaptor.forClass(SolrParams.class);
        verify(request).setParams(argument.capture());

        Assert.assertThat(argument.getValue().getParams("bq"),
                CoreMatchers.equalTo(new String[] {"{!tidismax qf='field1^-1000000.0 field2^-3000000.0 ' mm=1 bq=''} b c"}) );

    }
}
