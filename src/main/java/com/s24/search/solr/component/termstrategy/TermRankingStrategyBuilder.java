package com.s24.search.solr.component.termstrategy;

import com.s24.search.solr.component.AbstractTermRankingStrategy;
import org.apache.lucene.analysis.Analyzer;

public class TermRankingStrategyBuilder {

    private AbstractTermRankingStrategy toBuild;

    private boolean asPenalizer;
    private String q;
    private String extraTerms;
    private Analyzer analyzer;
    private String fields;
    private float boostFactor;
    private String queryType;

      public TermRankingStrategyBuilder multiplicativeTermRankingStrategy() {
        toBuild = new MultiplicativeTermRankingStrategy();
        return this;
    }

    public TermRankingStrategyBuilder rerankTermRankingStrategy(int rerankDocCount) {
        toBuild = new RerankingTermRankingStrategy(rerankDocCount);
        return this;
    }

    public TermRankingStrategyBuilder additiveTermRankingStrategy() {
        toBuild = new AdditiveTermRankingStrategy();
        return this;
    }

    public TermRankingStrategyBuilder forQuery(String q) {
        this.q = q;
        return this;
    }

    public TermRankingStrategyBuilder withExtraTerms(String extraTerms) {
        this.extraTerms = extraTerms;
        return this;
    }

    public TermRankingStrategyBuilder withQueryType(String queryType) {
        this.queryType = queryType;
        return this;
    }

    public TermRankingStrategyBuilder withAnalyzer(Analyzer analyzer) {
        this.analyzer = analyzer;
        return this;
    }

    public TermRankingStrategyBuilder withQueryField(String fields) {
        this.fields = fields;
        return this;
    }

    public TermRankingStrategyBuilder withFactor(float boostFactor) {
        this.boostFactor = boostFactor;
        return this;
    }

    public TermRankingStrategyBuilder asPenalizer() {
        this.asPenalizer = true;
        return this;
    }

    public TermRankingStrategy build() {
        toBuild.setAnalyzer(analyzer);
        toBuild.setExtraTerms(extraTerms);
        toBuild.setFields(fields);
        toBuild.setQ(q);
        toBuild.setQueryType(queryType);
        if (asPenalizer) {
            toBuild.setBoostFactor(-boostFactor);
        } else {
            toBuild.setBoostFactor(boostFactor);
        }

        return toBuild;
    }
}
