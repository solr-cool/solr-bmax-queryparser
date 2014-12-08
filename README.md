The boosting dismax query parser (bmax)
==================

![travis ci build status](https://travis-ci.org/shopping24/solr-bmax-queryparser.png)

A boosting dismax query parser for Apache Solr. The bmax query parser relies on
field types and tokenizer chains to parse the user query, discover synonyms, boost 
and penalize terms at query time. Hence it is highly configurable. It does *not accept* any lucene query syntax (`~-+()`). The query composed is a dismax query with a minimum must match of 100%.

## bmax query processing

Query processing in the bmax query parser is split into 2 steps. First is parsing and tokenizing the input query. Second is synonym and boost term lookup.

![image](./bmax_queryparsing.png)

Query processing is done via Solr query `Analyzer`s configured in `FieldTypes` in your `schema.xml`. Die field types to use for query processing are part of the query parser configuration in your `solrconfig.xml`.

### 1. Parsing the user query

The contents of the `q` parameter are stuffed into the query analyzer of the configured `queryParsingFieldType`. The configured tokenizer tokenizes the input string and the configured analyzer could be used to remove stopwords (see example below).

### 2. Discovering synonyms (optional)

Each token emitted from the `queryParsingFieldType`s query analyzer is placed in the `synonymFieldType` (if configured). Use the field's query analyzer to look up synonyms (see below). 

### 3. Discovering boost and penalize terms (optional)

The tokens emitted from the `queryParsingFieldType`s query analyzer are also put into the `boostUpFieldType` and the `boostDownFieldType` to discover boost terms. In contrast to synonyms, boost terms are not used to widen the search result, they are used to do a boosting or penalizing inside the result documents.

### Building the query

The tokens extracted from the configured field types are composed into a single lucene query. If we take the example above, the user query `blue pants cheap` is transformed into the tokens `blue` and `pants` (stopword *cheap* is removed). `pants` matches the synonym `jeans`, the boost up terms `denim` and `straight` and the penalize term `shoe`.

The following lucene query will be constructed:

* `BooleanQuery` (`MUST` match) of
   * `DismaxQuery` of (`blue`)
   * `DismaxQuery` of (`pants`, `jeans`)
* `BooleanQuery` (`SHOULD` match) of
   * `DismaxQuery` of (`denim`, `straight`)
* `ReReankQuery` of (`shoe`) for the first 400 docs with a configurable negative weight.

## Installing the component

* Place the [`solr-bmax-queryparser-<VERSION>-jar-with-dependencies.jar`](https://github.com/shopping24/solr-bmax-queryparser/releases) in the `/lib` 
  directory of your Solr installation. 
* Configure at least one field type in your `schema.xml` that can be used for query parsing and tokenizing
* Configure the `bmax` query parser in your `solrconfig.xml` (see below)
* Enable the `bmax` query parser using the `defType=bmax` parameter in your query.

This project is also vailable from Maven Central:

    <dependency>
        <groupId>com.s24.search.solr</groupId>
        <artifactId>solr-bmax-queryparser</artifactId>
        <version>0.9.2</version>
        <classifier>jar-with-dependencies</classifier>
        <scope>provided</scope>
    </dependency>


## Configuring the query parser

Add the `BmaxQParserPlugin` to the list of query parsers configured in your `solrconfig.xml`. It takes the following configuration parameters:

    <queryParser name="bmax" class="com.s24.search.solr.query.bmax.BmaxQParserPlugin">
        <!-- use this field type's query analyzer to tokenize the query -->
        <str name="queryParsingFieldType">bmax_query</str>

        <!-- further field types for synonyms and boostterms -->
        <str name="synonymFieldType">bmax_synonyms</str>
        <str name="boostUpFieldType">bmax_boostterms</str>
        <str name="boostDownFieldType">bmax_penalizeterms</str>
    </queryParser>
 
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

## Using the query parser

The following url parameters are recognized:

* `q` (string) – the user query. *Lucene query syntax is not supported.* The input is passed into the tokenizer as is: `q=blue pants cheap`.

* `qf` (string) – the query fields with their weights: `qf=title^10.0 description^2`.

* `bmax.boostDownTerm.enable` (boolean) – Enables penalize a.k.a boost down term lookup and application.

* `bmax.boostDownTerm.weight` (float) – The factor to apply the rerank query with, always negative. Defaults to `-2.0`

* `bmax.boostDownTerm.extra` (string) - A comma separated list of extra penalize terms to apply. Use this for testing.

* `bmax.boostUpTerm.enable` (boolean) – Enables boost up term lookup and application. 

* `bmax.boostUpTerm.extra`(string) – A comma separated list of extra boost terms to apply. Use this for testing.
 
* `bmax.boostUpTerm.qf` (string) — if supplied, boost terms will be applied on these query fields and not on the query fields supplied in the `qf` parameter. Lucene field weights are supported. Defaults to values in the `qf` parameter.

* `bmax.synonym.boost` (float) – When constructing the lucene queries, field weights are multiplicated with this synonym boost factor. Defaults to `0.01f`.

* `bmax.synonym.extra` (string) – Supply extra synonyms in lucene synonym format, e.g. `shoe=>sneaker|clog,blue=>azure`. Use this for testing.

Advanced users might like to experiment with some experimental features:

* `bmax.manipulateDocumentFrequencies` (boolean, experimental) – Enables advanced manipulation of document frequencies. See [`BmaxLuceneQueryBuilder`](./src/main/java/com/s24/search/solr/query/bmax/BmaxLuceneQueryBuilder.java) for details. This will increase query parsing time by about factor 3! Defaults to `false`.

* `bmax.manipulateTermFrequencies` (boolean, experimental) – Enables advanced manipulation of term frequencies. See [`BmaxLuceneQueryBuilder`](./src/main/java/com/s24/search/solr/query/bmax/BmaxLuceneQueryBuilder.java) for details. This will increase query parsing time by about factor 3! Defaults to `false`.


## Building the project

This should install the current version into your local repository

    $ mvn clean install
    
### Releasing the project to maven central
    
Define new versions
    
    $ export NEXT_VERSION=<version>
    $ export NEXT_DEVELOPMENT_VERSION=<version>-SNAPSHOT
    $ export JAVA_HOME="$(/usr/libexec/java_home -v 1.7.0)" && java -version

Then execute the release chain

    $ mvn org.codehaus.mojo:versions-maven-plugin:2.0:set -DgenerateBackupPoms=false -DnewVersion=$NEXT_VERSION
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
