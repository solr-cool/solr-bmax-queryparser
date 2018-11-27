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
    FieldType boostTermFieldType = mock(FieldType.class);

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

        when(schema.getFieldTypeByName("boostTermFieldType")).thenReturn(boostTermFieldType);
        when(boostTermFieldType.getQueryAnalyzer()).thenReturn(new StandardAnalyzer());

        initArgs.add("queryParsingFieldType", "queryParsingFieldType");
        initArgs.add("synonymFieldType", "synonymFieldType");
        initArgs.add("boostTermFieldType", "boostTermFieldType");
        initArgs.add("penalizeTermFieldType", "penalizeTermFieldType");
    }

    ////////Basic tests that test if a RankingStrategy is applied for Boosts and Penalizing independantly////////
    @Test
    public void testThatRerankStrategyForPenalizingIsApplied() throws Exception {

        BmaxBoostTermComponent component = new BmaxBoostTermComponent();
        component.init(initArgs);
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "a b c");
        params.set(SYNONYM_ENABLE, false);
        params.set(BOOST_ENABLE, false);
        params.set(PENALIZE_ENABLE, true);
        params.set(PENALIZE_STRATEGY, VALUE_STRATEGY_RERANK);
        params.set(PENALIZE_FACTOR,1000000);
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
                CoreMatchers.equalTo(new String[] {"{!dismax qf='field1^-1000000.0 field2^-3000000.0 ' mm=1 bq=''} b c"}) );
    }

    @Test
    public void testThatRerankStrategyForBoostingIsApplied() throws Exception {

        BmaxBoostTermComponent component = new BmaxBoostTermComponent();
        component.init(initArgs);
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "a b c");
        params.set(PENALIZE_ENABLE, false);
        params.set(SYNONYM_ENABLE, false);
        params.set(BOOST_ENABLE, true);
        params.set(BOOST_STRATEGY, VALUE_STRATEGY_RERANK);
        params.set(BOOST_FACTOR,100);
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
                CoreMatchers.equalTo(new String[] {"{!dismax qf='field1^100.0 field2^300.0 ' mm=1 bq=''} b c"}) );
    }

    @Test
    public void testThatAdditiveStrategyForPenalizingIsApplied() throws Exception {

        BmaxBoostTermComponent component = new BmaxBoostTermComponent();
        component.init(initArgs);
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "a b c");

        params.set(SYNONYM_ENABLE, false);
        params.set(BOOST_ENABLE, false);
        params.set(PENALIZE_ENABLE, true);
        params.set(PENALIZE_STRATEGY, VALUE_STRATEGY_ADDITIVELY);
        params.set(PENALIZE_FACTOR, 1000000);
        params.set(DisMaxParams.QF, "field1 field2^3");
        when(request.getParams()).thenReturn(params);

        component.prepareInternal(responseBuilder);

        ArgumentCaptor<SolrParams> argument = ArgumentCaptor.forClass(SolrParams.class);
        verify(request).setParams(argument.capture());

        Assert.assertThat(argument.getValue().getParams("bq"),
                CoreMatchers.equalTo(new String[] {"{!dismax qf='field1^-1000000.0 field2^-3000000.0 ' mm=1 bq=''} b c"}) );
    }

    @Test
    public void testThatAdditiveStrategyForBoostingIsApplied() throws Exception {

        BmaxBoostTermComponent component = new BmaxBoostTermComponent();
        component.init(initArgs);
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "a b c");

        params.set(PENALIZE_ENABLE, false);
        params.set(SYNONYM_ENABLE, false);
        params.set(BOOST_ENABLE, true);
        params.set(BOOST_STRATEGY, VALUE_STRATEGY_ADDITIVELY);
        params.set(BOOST_FACTOR,100);
        params.set(DisMaxParams.QF, "field1 field2^3");
        when(request.getParams()).thenReturn(params);

        component.prepareInternal(responseBuilder);

        ArgumentCaptor<SolrParams> argument = ArgumentCaptor.forClass(SolrParams.class);
        verify(request).setParams(argument.capture());

        Assert.assertThat(argument.getValue().getParams("bq"),
                CoreMatchers.equalTo(new String[] {"{!dismax qf='field1^100.0 field2^300.0 ' mm=1 bq=''} b c"}) );
    }

    @Test
    public void testThatMultiplicativeStrategyForPenalizingIsApplied() throws Exception {

        BmaxBoostTermComponent component = new BmaxBoostTermComponent();
        component.init(initArgs);
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "a b c");

        params.set(SYNONYM_ENABLE, false);
        params.set(BOOST_ENABLE, false);
        params.set(PENALIZE_ENABLE, true);
        params.set(PENALIZE_STRATEGY, VALUE_STRATEGY_MULTIPLICATIVE);
        params.set(PENALIZE_FACTOR, 1000000);
        params.set(DisMaxParams.QF, "field1 field2^3");
        when(request.getParams()).thenReturn(params);

        component.prepareInternal(responseBuilder);

        ArgumentCaptor<SolrParams> argument = ArgumentCaptor.forClass(SolrParams.class);
        verify(request).setParams(argument.capture());

        Assert.assertThat(argument.getValue().getParams("boost"),
                CoreMatchers.equalTo(new String[] {"{!dismax qf='field1^-1000000.0 field2^-3000000.0 ' mm=1 bq=''} b c"}) );
    }

    @Test
    public void testThatMultiplicativeStrategyForBoostingIsApplied() throws Exception {

        BmaxBoostTermComponent component = new BmaxBoostTermComponent();
        component.init(initArgs);
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "a b c");

        params.set(SYNONYM_ENABLE, false);
        params.set(PENALIZE_ENABLE, false);
        params.set(BOOST_ENABLE, true);
        params.set(BOOST_STRATEGY, VALUE_STRATEGY_MULTIPLICATIVE);
        params.set(BOOST_FACTOR,100);
        params.set(DisMaxParams.QF, "field1 field2^3");
        when(request.getParams()).thenReturn(params);

        component.prepareInternal(responseBuilder);

        ArgumentCaptor<SolrParams> argument = ArgumentCaptor.forClass(SolrParams.class);
        verify(request).setParams(argument.capture());

        Assert.assertThat(argument.getValue().getParams("boost"),
                CoreMatchers.equalTo(new String[] {"{!dismax qf='field1^100.0 field2^300.0 ' mm=1 bq=''} b c"}) );
    }

    //////////////////////////////////////////Tests for potential edgecases//////////////////////////////////////

    /**
     * RerankStrategy mustn't be applied if rq is already set to avoid overwriting
     * @throws Exception
     */
    @Test
    public void testThatRerankStrategyForPenalizingIsNotAppliedIfRqAlreadyExists() throws Exception {

        BmaxBoostTermComponent component = new BmaxBoostTermComponent();
        component.init(initArgs);
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "a b c");
        params.set("rq", "dummy");
        params.set(SYNONYM_ENABLE, false);
        params.set(BOOST_ENABLE, false);
        params.set(PENALIZE_ENABLE, true);
        params.set(PENALIZE_STRATEGY, VALUE_STRATEGY_RERANK);
        params.set(DisMaxParams.QF, "field1 field2^3");
        when(request.getParams()).thenReturn(params);

        component.prepareInternal(responseBuilder);
        ArgumentCaptor<SolrParams> argument = ArgumentCaptor.forClass(SolrParams.class);
        verify(request).setParams(argument.capture());

        Assert.assertThat(argument.getValue().getParams("rq"),
                CoreMatchers.equalTo(new String[] {"dummy"}) );
    }

    /**
     * RerankStrategy mustn't be applied if rq is already set to avoid overwriting
     * @throws Exception
     */
    @Test
    public void testThatRerankStrategyForBoostingIsNotAppliedIfRqAlreadyExists() throws Exception {

        BmaxBoostTermComponent component = new BmaxBoostTermComponent();
        component.init(initArgs);
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "a b c");
        params.set("rq", "dummy");
        params.set(SYNONYM_ENABLE, false);
        params.set(PENALIZE_ENABLE, false);
        params.set(BOOST_ENABLE, true);
        params.set(BOOST_STRATEGY, VALUE_STRATEGY_RERANK);
        params.set(DisMaxParams.QF, "field1 field2^3");
        when(request.getParams()).thenReturn(params);

        component.prepareInternal(responseBuilder);
        ArgumentCaptor<SolrParams> argument = ArgumentCaptor.forClass(SolrParams.class);
        verify(request).setParams(argument.capture());

        Assert.assertThat(argument.getValue().getParams("rq"),
                CoreMatchers.equalTo(new String[] {"dummy"}) );
    }

    /**
     * Boost applies first and only one RerankQuery can be applied
     * @throws Exception
     */
    @Test
    public void testThatRerankStrategyForPenalizingIsNotAppliedIfBoostAlsoUsesRQ() throws Exception {

        BmaxBoostTermComponent component = new BmaxBoostTermComponent();
        component.init(initArgs);
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "a b c");
        params.set(SYNONYM_ENABLE, false);
        params.set(BOOST_ENABLE, true);
        params.set(PENALIZE_ENABLE, true);
        params.set(BOOST_STRATEGY, VALUE_STRATEGY_RERANK);
        params.set(PENALIZE_STRATEGY, VALUE_STRATEGY_RERANK);
        params.set(DisMaxParams.QF, "field1 field2^3");
        when(request.getParams()).thenReturn(params);

        component.prepareInternal(responseBuilder);
        ArgumentCaptor<SolrParams> argument = ArgumentCaptor.forClass(SolrParams.class);
        verify(request).setParams(argument.capture());

        Assert.assertThat(argument.getValue().getParams("rq"),
                CoreMatchers.equalTo(new String[] {"{!rerank reRankQuery=$rqq reRankDocs=400}"}) );
        Assert.assertThat(argument.getValue().getParams("rqq"),
                CoreMatchers.equalTo(new String[] {"{!dismax qf='field1^1.0 field2^3.0 ' mm=1 bq=''} b c"}) );
    }

    /**
     * Tests that both boosting and penalizing may use the same Strategy if it is multiplicative
     * @throws Exception
     */
    @Test
    public void testThatMultiplicativeStrategyForBoostAndPenalizingIsApplied() throws Exception {

        BmaxBoostTermComponent component = new BmaxBoostTermComponent();
        component.init(initArgs);
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "a b c");

        params.set(SYNONYM_ENABLE, false);
        params.set(BOOST_ENABLE, true);
        params.set(PENALIZE_ENABLE, true);
        params.set(BOOST_STRATEGY, VALUE_STRATEGY_MULTIPLICATIVE);
        params.set(PENALIZE_STRATEGY, VALUE_STRATEGY_MULTIPLICATIVE);
        params.set(DisMaxParams.QF, "field1 field2^3");
        when(request.getParams()).thenReturn(params);

        component.prepareInternal(responseBuilder);

        ArgumentCaptor<SolrParams> argument = ArgumentCaptor.forClass(SolrParams.class);
        verify(request).setParams(argument.capture());

        Assert.assertThat(argument.getValue().getParams("boost"),
                CoreMatchers.equalTo(new String[] {"{!dismax qf='field1^1.0 field2^3.0 ' mm=1 bq=''} b c", "{!dismax qf='field1^-100.0 field2^-300.0 ' mm=1 bq=''} b c"}) );
    }

    /**
     * Tests that both boosting and penalizing may use the same Strategy if it is additive
     * @throws Exception
     */
    @Test
    public void testThatAdditiveStrategyForBoostAndPenalizingIsApplied() throws Exception {

        BmaxBoostTermComponent component = new BmaxBoostTermComponent();
        component.init(initArgs);
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.set("q", "a b c");

        params.set(SYNONYM_ENABLE, false);
        params.set(BOOST_ENABLE, true);
        params.set(PENALIZE_ENABLE, true);
        params.set(BOOST_STRATEGY, VALUE_STRATEGY_ADDITIVELY);
        params.set(PENALIZE_STRATEGY, VALUE_STRATEGY_ADDITIVELY);
        params.set(DisMaxParams.QF, "field1 field2^3");
        when(request.getParams()).thenReturn(params);

        component.prepareInternal(responseBuilder);

        ArgumentCaptor<SolrParams> argument = ArgumentCaptor.forClass(SolrParams.class);
        verify(request).setParams(argument.capture());

        Assert.assertThat(argument.getValue().getParams("bq"),
                CoreMatchers.equalTo(new String[] {"{!dismax qf='field1^1.0 field2^3.0 ' mm=1 bq=''} b c", "{!dismax qf='field1^-100.0 field2^-300.0 ' mm=1 bq=''} b c"}) );
    }
}
