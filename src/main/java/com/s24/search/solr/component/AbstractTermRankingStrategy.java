package com.s24.search.solr.component;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.s24.search.solr.component.termstrategy.TermRankingStrategy;
import com.s24.search.solr.query.bmax.Terms;
import org.apache.logging.log4j.util.Strings;
import org.apache.lucene.analysis.Analyzer;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.util.SolrPluginUtils;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class AbstractTermRankingStrategy implements TermRankingStrategy {

    private String q;
    private String extraTerms;
    private Analyzer analyzer;
    private String fields;
    private float boostFactor;
    private String queryType;

    @Override
    public String apply(ModifiableSolrParams params) {
        String joinedTerms = getTerms();
        if (!joinedTerms.isEmpty()){
            String termRankingQueryString = makeQueryString(params, joinedTerms);

            addToQuery(termRankingQueryString, params);
            return joinedTerms;
        }
        return Strings.EMPTY;
    }

    protected abstract void addToQuery(String termRankingQueryString, ModifiableSolrParams params);

    private String getTerms() {
        final Collection<CharSequence> terms = Terms.collect(q, analyzer);
        // add extra terms
        if (extraTerms != null) {
            terms.addAll(Sets.newHashSet(Splitter.on(',').omitEmptyStrings().split(extraTerms)));
        }

        return Joiner.on(" ").join(terms);
    }

    private String makeQueryString(SolrParams params, String joinedTerms) {
            return String.format(Locale.US, "{!%s qf='%s' mm=1 bq=''} %s", queryType,
                    computeFactorizedQueryFields(params),
                    joinedTerms);
    }

    private String computeFactorizedQueryFields(SolrParams params) {
        checkNotNull(params, "Pre-condition violated: params must not be null.");
        StringBuilder qf = new StringBuilder();
        // parse fields and boosts
        Map<String, Float> fieldBoosts = SolrPluginUtils.parseFieldBoosts(params.getParams(fields));
        // iterate, add factor and add to result qf
        for (Map.Entry<String, Float> f : fieldBoosts.entrySet()) {
            qf.append(f.getKey());
            qf.append('^');

            if (f.getValue() != null) {
                qf.append(f.getValue() * boostFactor);
            } else {
                qf.append(boostFactor);
            }
            qf.append(' ');
        }
        return qf.toString();
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public String getExtraTerms() {
        return extraTerms;
    }

    public void setExtraTerms(String extraTerms) {
        this.extraTerms = extraTerms;
    }

    public Analyzer getAnalyzer() {
        return analyzer;
    }

    public void setAnalyzer(Analyzer analyzer) {
        this.analyzer = analyzer;
    }

    public String getFields() {
        return fields;
    }

    public void setFields(String fields) {
        this.fields = fields;
    }

    public float getBoostFactor() {
        return boostFactor;
    }

    public void setBoostFactor(float boostFactor) {
        this.boostFactor = boostFactor;
    }

    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }
}
