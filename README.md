The boosting dismax query parser (bmax)
==================

![travis ci build status](https://travis-ci.org/shopping24/solr-bmax-queryparser.png)

A synonym aware edismax query parser for Apache Solr. The bmax query parser relies on
field types and tokenizer chains to parse the user query, discovers synonyms, subtopics, boost 
and penalize terms at query time. Hence it is highly configurable. It is the ideal query
parser for e-commerce searches as it eliminates the usage of term and document frequency.

It does *not accept* any lucene query syntax (`~-+()`). The query composed is a dismax query 
with a minimum must match of 100%.

This document covers Version 1.5.x and onwards. For the old 0.9.9 version, [take a look at
the release branch](https://github.com/shopping24/solr-bmax-queryparser/blob/v0.9.8/README.md).

## Fundamentals

### Terminology

*Synonym* - a (bidirectional) syntactic or semantic equivalent to a origin term. It will expand 
recall and in ranking, matches on these synonyms will be scored almost as high as the origin 
term (default 0.9). Example: `tv -> television`.

*Subtopic* - a unidirectional specification of a origin term that will expand recall and score lower than the
origin term. Example: `bicycle -> mountainbike` or `laptop -> macbook`.

*Penalize term* - a term that semantically describes what should rank _lower_ in a search result 
matching the origin term. These terms will not increase recall, documents matching penalize terms 
will rank lower. Example: `mountainbike -> isbn`

*Boost term* - a term that semantically describes what should rank _higher_ in a search result 
matching the origin term. These terms will not increase recall, documents matching penalize terms 
will rank higher. Example: `television -> hdmi`

### document and term frequency handling

The bmax query parser eliminates the usage of term and document frequency for document 
ranking. With subtopics, synonyms, boost and penalize terms disabled and query fields set
to a single field, all returned documents are s cored `1.0`. 

### synonym and subtopic handling

Query epxansions that increase recall (synonyms and subtopics) are bound to the origin term. 
Given the synonym example _violet_ to _blue_, the query _blue bike_
would be rewriten by the bmax parser to `(violet OR blue) AND bike`. If you add the subtopic
_mountainbike, ebike_ to _bike_, the query would be rewritten to `(violet OR blue) AND (bike OR mountainbike OR ebike)`.

Out of the box synonym handling in Solr (dismax, edisxmax) loses these relationships during
query analysis. As an example, given the synonym _violet_ to _blue_ a regular Solr synonym 
handling would rewrite the query _blue bike_ to `blue violet bike`. Depending on your query
parser (and `mm` setting in dismax) this could lead to higher recall with way less precision.

## Using the Bmax query parser

To take andvantage of the bmax query parser, have it properly installed and configured
as described in the next chapter. The query parser utilizes 2 components, the _booster_
and the _queryparser_. The booster enriches the query with boost and penalize terms,
the query parser transforms a given user query into a Lucene search query.

Use the following url parameters to fine tune your installation.

### Boost component parameters

The Bmax boost component enriches the query with boost and penalize terms.

* `bmax.booster` (boolean) - enable/disable boost term component. Default is `false`.
* `bmax.booster.boost` (boolean) - enable/disable boost term resolution. Default is `true`.
* `bmax.booster.boost.factor` (float) - boost factor that is multiplied to the boosts given in the `qf` or `bmax.booster.boost.qf` parameter for each query field respectivly, default is `1.0`.
* `bmax.booster.boost.strategy` (String) - strategy for combining boost terms with the main query: `rq` - rerank query, `bq`- boost query (additively), `boost`- boost function (multiplicative). Default is `rq`.
* `bmax.booster.boost.docs` (int) - The number of documents to boost from the begin of the result set (rerank query strategy only). Default is `400`.*
* `bmax.booster.boost.extra` (String) - comma separated extra boost terms. Great to check new boost term ideas.
* `bmax.booster.penalize` (boolean) - enable/disable penalize term resolution. Default is `true`.
* `bmax.booster.penalize.factor` (float) - Penalize factor that is used as negative weight in the penalize query. Default is `100.0`.
* `bmax.booster.penalize.strategy` (String) - strategy for combining penalize terms with the main query: `rq` - rerank query, `bq`- boost query (additively), `boost`- boost function (multiplicative). Default is `rq`.
* `bmax.booster.penalize.docs` (int) - The number of documents to penalize from the begin of the result set (rerank query strategy only). Default is `400`.
* `bmax.booster.penalize.extra` (String) - comma separated extra penalize terms. Great to check new ideas.


### Query parser params

The Bmax query parser utilizes a [Solr edismax query parser](https://lucene.apache.org/solr/guide/6_6/the-extended-dismax-query-parser.html)
and the following standard url parameters can be used:

* `q` (string) – the user query. *Lucene query syntax is not supported.*
* `qf` (string) – [the query fields with their weights](https://lucene.apache.org/solr/guide/6_6/the-dismax-query-parser.html#TheDisMaxQueryParser-Theqf_QueryFields_Parameter).
* `bq` (string) – [additive boost query](https://lucene.apache.org/solr/guide/6_6/the-dismax-query-parser.html#TheDisMaxQueryParser-Thebq_BoostQuery_Parameter) 
* `bf` (string) – [additive boost functions](https://lucene.apache.org/solr/guide/6_6/the-dismax-query-parser.html#TheDisMaxQueryParser-Thebf_BoostFunctions_Parameter)
* `tie` (string) – [the dismax tie breaker](https://lucene.apache.org/solr/guide/6_6/the-dismax-query-parser.html#TheDisMaxQueryParser-Thetie_TieBreaker_Parameter), default is `0.0`.
* `boost` (string) – [multiplicative boost functions](https://lucene.apache.org/solr/guide/6_6/the-extended-dismax-query-parser.html#TheExtendedDisMaxQueryParser-TheboostParameter)
* `pf` (string) - [the phrase fields](https://lucene.apache.org/solr/guide/6_6/the-dismax-query-parser.html#TheDisMaxQueryParser-Thepf_PhraseFields_Parameter)
* `ps` (string) - [the phrase slop for pf (default for pf2/pf3)](https://lucene.apache.org/solr/guide/6_6/the-dismax-query-parser.html#TheDisMaxQueryParser-Theps_PhraseSlop_Parameter)
* `pf2` (string) - [the bigram phrase fields](https://lucene.apache.org/solr/guide/6_6/the-extended-dismax-query-parser.html#TheExtendedDisMaxQueryParser-Thepf2Parameter)
* `ps2` (string) - [the phrase slop for pf2](https://lucene.apache.org/solr/guide/6_6/the-extended-dismax-query-parser.html#TheExtendedDisMaxQueryParser-Theps2Parameter)
* `pf3` (string) - [the trigram phrase fields](https://lucene.apache.org/solr/guide/6_6/the-extended-dismax-query-parser.html#TheExtendedDisMaxQueryParser-Theps3Parameter)
* `ps3` (string) - [the phrase slop for pf3](https://lucene.apache.org/solr/guide/6_6/the-extended-dismax-query-parser.html#TheExtendedDisMaxQueryParser-Theps3Parameter)
* `phrase.tie` (float) - A tie breaker that is used when aggregating pf,pf2,pf3 queries. Defaults to the value of `tie`

To fine tune or debug your query, use the following extra arguments:

* `bmax.synonym` (boolean) - Enable / disable synoynm lookup, default is `true`
* `bmax.synonym.boost` (float) – The term boost to be multiplicated for synonym terms with the boost defined in the `qf` parameter for each query field respectively, default is `0.1`. 
* `bmax.subtopic` (boolean) - Enable / disable subtopic lookup, default is `true`
* `bmax.subtopic.boost`  (float) – The term boost to be multiplicated for subtopic terms with the boost defined in the `qf` parameter for each query field respectively, default is `0.01`. 
* `bmax.subtopic.qf` (string) - The query fields in which to search for subtopics, defaults to the ones given in the `qf` parameter.

### Query clause reduction / term inspection

Before adding a term query clause to the main query or the boost query, a _term inspection cache_  can be checked, whether the term exists 
in the field term values. If the term does not exist in the field term values, the term query clause is omitted. If you are using
a lot of query fields, this can reduce the overall query clause count dramatically and speed up query computation.

* `bmax.inspect` (boolean) – Use the local term inspection cache to validate term query clauses. Default is `false`. Set 
  this to `true` in your main query configuration to lookup each term in the local term inspection cache.
* `bmax.inspect.build` (boolean) – Build a local term inspection cache using the given `qf`. Default is `false`. Configure
  a new/first searcher listener in your `solrconfig.xml` and query all documents (`*:*`) once with this parameter set
  to `true`. Supply the fields to inspect in the `qf` parameter. 
   
The _term inspection cache_ is stored in a custom Solr cache named `bmax.fieldTermCache`. Configure and size a cache in
your `solrconfig.xml`. The cache entries will be saved as [Dictomaton FSTs](https://github.com/danieldk/dictomaton) in 
order to consume as less heap as possible.

## Bmax query processing
Query processing in the bmax query parser is split into 2 steps:

1. First is retrieving and supplying boost and penalize terms. This is done in the
    `BmaxBoostTermComponent`
1. Second is parsing the incoming query and building an appropriate Lucene query.
   This is done in the `BmaxQueryParser`.

### 1. Retrieving boost and penalize terms
The incoming user query (`q`) is analyzed and boost terms are supplied in the `bq` parameter.
Penalize terms are added in the `rq` and `rqq` parameter to form a negative rerank query.
Boost and penalize term retrieval is done in 3 steps:

1. Run the incoming query in `q` through the configured `queryParsingFieldType`
1. Expand synonyms for each query token through `synonymFieldType`.
1. Retrieve boost and penalize terms for each token through
   `boostTermFieldType` and `penalizeTermFieldType` respectivly.

![image](./bmax_booster.png)

Given the example above with `q=blue bike cheap` the query parsing field type would
remove noise and leave `blue bike`. The synonym lookup would retrieve `bicycle` as
synonym for `bike` and append it: `blue bike bicylce`. This would be the input for
penalize and boost term discovery.

The discovered boost terms `crossbike bmx pedelec` are appended to the incoming query
as a boost query `bq={!dismax qf='...' mm=1 bq=''} crossbike bmx pedelec`. The discovered
penalize terms are appended as rerank query `rq={!rerank reRankQuery=$rqq reRankDocs=... reRankWeight=...}&rqq=...book OR toys ...`. The rerank query formulated is a boolean OR query.

### 2. Parsing the user query
The bmax query parser utilizes the `edismax` query parser to build it's query. It recognizes the well known `edismax` parameters:

* `q` – the main query
* `qf` – query fields (weighted)
* `bq` – the boost query (additive)
* `bf` – boost functions (additive)
* `boost` – boost functions (multiplicative)
* `pf,ps,pf2,ps2,pf3,ps3` – phrase boosts (additive). Note that the scores from these boosts are added up per type (pf,pf2,pf3) and field but dismax'ed between types and fields.
    Set `phrase.tie=1.0` if you want the standard edismax behaviour and also add up the scores between fields and types.

Rerank queries are realized through the default Solr rerank postfilter. Query parsing
is done in 3 steps:

1. Run the incoming query in `q` through the configured `queryParsingFieldType`
1. Expand synonyms for each query token through `synonymFieldType`. Synoynms treated 
   as sematically equal to the source token.
1. Retrieve subtopic terms for each token and synonym through `subtopicFieldType`. 
   Subtopics are bound to the source token in the main query.

![image](./bmax_queryparser.png)

Given the example above with `q=blue bike cheap` the query parsing field type would
remove noise and leave the tokens `blue,bike`. The synonym lookup would retrieve `bicycle` as
synonym for `bike`: `blue,[bike,bicycle]`. Subtopic retrieval for each token creates:
`[blue,lavendel],[bike,bicycle,bmx,crossbike,roadbike]`.

The query constructed is always a `dismax` query with a minimum must match of `100%`. The
example above would create the following query:

    BooleanQuery(MUST) of
      DismaxQuery(MUST) of blue,lavendel
      DismaxQuery(MUST) of bike,bicycle,bmx,crossbike,roadbike

The boost query (if given) is appended.

## Installing the Bmax query parser

* Place the [`solr-bmax-queryparser-<VERSION>-jar-with-dependencies.jar`](https://github.com/shopping24/solr-bmax-queryparser/releases) in the `/lib` 
  directory of your Solr installation. 
* Configure at least one field type in your `schema.xml` that can be used for query parsing and tokenizing
* Configure the `bmax` query parser in your `solrconfig.xml` (see below)
* Configure the `bmax.booster` search component in your `solrconfig.xml` (see below)
* Enable the `bmax` query parser using the `defType=bmax` parameter in your query.

This project is also vailable from Maven Central:

    <dependency>
        <groupId>com.s24.search.solr</groupId>
        <artifactId>solr-bmax-queryparser</artifactId>
        <version>1.5.0</version>
        <classifier>jar-with-dependencies</classifier>
    </dependency>


## Configuring the query parser

Add the `BmaxQParserPlugin` to the list of query parsers configured in your `solrconfig.xml`. It takes the following configuration parameters:

    <queryParser name="bmax" class="com.s24.search.solr.query.bmax.BmaxQParserPlugin">
        <!-- use this field type's query analyzer to tokenize the query -->
        <str name="queryParsingFieldType">bmax_query</str>

        <!-- further field types for synonyms and subtopics -->
        <str name="synonymFieldType">bmax_synonyms</str>
        <str name="subtopicFieldType">bmax_subtopics</str>
    </queryParser>

Configure the boost term component as follows:

    <searchComponent name="bmax.booster" class="com.s24.search.solr.component.BmaxBoostTermComponent">
        <!-- use the same as in query parser -->
        <str name="queryParsingFieldType">bmax_query</str>
        <str name="synonymFieldType">bmax_synonyms</str>
        
        <!-- boost and penalize term retrieval -->
        <str name="boostTermFieldType">bmax_boostterms</str>
        <str name="penalizeTermFieldType">bmax_penalizeterms</str>
    </searchComponent>
    
and add it to the components of your search handler in front of the query 
component:

    <requestHandler name="/select" class="solr.SearchHandler" default="true">
     <arr name="components">
         ...
         <str>bmax.booster</str>
         ...
      </arr>
    </requestHandler>

## Configuring the fieldTypes needed

A simple example for a field type in your `schema.xml`, that tokenizes a incoming query and removes stopwords might be this:

    <fieldType name="bmax_query" class="solr.TextField" indexed="false" stored="false">
        <analyzer type="query">
            <tokenizer class="solr.PatternTokenizerFactory" 
                       pattern="[+;:,\s©®℗℠™&amp;()/\p{Punct}&lt;&gt;»«]+" />
                                       
            <!-- lower case -->
            <filter class="solr.LowerCaseFilterFactory" />
            
            <!-- Removes stopwords from the query. -->
            <filter class="solr.StopFilterFactory" 
                    words="stopwords.txt" ignoreCase="true"/>
        </analyzer>
    </fieldType>

This is a example of a synonym parser. The input is each token of the query analyzer above, one at a time. So, there's no need for any fancy tokenizing, the keyword tokenizer will do it. This analyzer chain utilizes the `SynonymFilter` and as a last step removes all non-synonyms. With this nifty little trick, no unneeded synonyms get added to your query.

    <fieldType name="bmax_synonyms" class="solr.TextField" indexed="false" stored="false">
         <analyzer type="query">
            <tokenizer class="solr.KeywordTokenizerFactory" />
            
            <!-- synonyms -->
            <filter class="solr.SynonymFilterFactory" synonyms="syn.txt" ignoreCase="true" expand="false"/>

            <!-- remove all non-synonyms -->
            <filter class="solr.TypeTokenFilterFactory" types="list_tokentype_synonym.txt" useWhitelist="true"/>
         </analyzer>
      </fieldType>

For the boostterm field type, the `SynonymFilter` might be handy as well.

## Building the project

This should install the current version into your local repository

    $ mvn clean install
    
### Releasing the project to maven central
    
Define new versions
    
    $ export NEXT_VERSION=<version>
    $ export NEXT_DEVELOPMENT_VERSION=<version>-SNAPSHOT

Then execute the release chain

    $ mvn org.codehaus.mojo:versions-maven-plugin:2.8.1:set -DgenerateBackupPoms=false -DnewVersion=$NEXT_VERSION
    $ git commit -a -m "pushes to release version $NEXT_VERSION"
    $ mvn -P release
    
Then, increment to next development version:
    
    $ git tag -a v$NEXT_VERSION -m "`curl -s http://whatthecommit.com/index.txt`"
    $ mvn org.codehaus.mojo:versions-maven-plugin:2.0:set -DgenerateBackupPoms=false -DnewVersion=$NEXT_DEVELOPMENT_VERSION
    $ git commit -a -m "pushes to development version $NEXT_DEVELOPMENT_VERSION"
    $ git push origin tag v$NEXT_VERSION && git push origin

## Contributing

We're looking forward to your comments, issues and pull requests!

## License

This project is licensed under the [Apache License, Version 2](http://www.apache.org/licenses/LICENSE-2.0.html).
