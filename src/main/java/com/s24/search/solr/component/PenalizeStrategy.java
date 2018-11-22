package com.s24.search.solr.component;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.s24.search.solr.query.bmax.Terms;
import org.apache.logging.log4j.util.Strings;
import org.apache.lucene.analysis.Analyzer;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.util.SolrPluginUtils;

import java.util.Collection;
import java.util.Map;

public abstract class PenalizeStrategy {

    private final String q;
    private final String penalizeExtraTerms;
    private final Analyzer penalizeAnalyzer;
    private final String penalizeFields;

    public PenalizeStrategy(final String q, final String penalizeExtraTerms,
                            final Analyzer penalizeAnalyzer, final String penalizeFields) {
        this.q = q;
        this.penalizeExtraTerms = penalizeExtraTerms;
        this.penalizeAnalyzer = penalizeAnalyzer;
        this.penalizeFields = penalizeFields;
    }

    public String apply(final ModifiableSolrParams queryParams) {
        String joinedTerms = getTerms();
        if (!joinedTerms.isEmpty()){
            addToQuery(makePenalizeQueryString(joinedTerms), queryParams);
            return joinedTerms;
        }
        return Strings.EMPTY;

    }

    protected abstract void addToQuery(String penalizeQueryString, ModifiableSolrParams queryParams);

    protected String getTerms() {
        final Collection<CharSequence> terms = Terms.collect(q, penalizeAnalyzer);
        // add extra terms
        if (penalizeExtraTerms != null) {
            terms.addAll(Sets.newHashSet(Splitter.on(',').omitEmptyStrings().split(penalizeExtraTerms)));
        }

        return Joiner.on(" OR ").join(terms);
    }

    protected String makePenalizeQueryString(String joinedTerms) {
            // iterate query fields
            StringBuilder penalizeQueryString = new StringBuilder();
            Map<String, Float> queryFields = SolrPluginUtils.parseFieldBoosts(penalizeFields);
            for (Map.Entry<String, Float> field : queryFields.entrySet()) {
                if (penalizeQueryString.length() > 0) {
                    penalizeQueryString.append(" OR ");
                }

                penalizeQueryString.append(field.getKey());
                penalizeQueryString.append(":(");
                penalizeQueryString.append(joinedTerms);
                penalizeQueryString.append(')');
            }

            // append penalizeQueryString query
            return penalizeQueryString.toString();
    }
}
