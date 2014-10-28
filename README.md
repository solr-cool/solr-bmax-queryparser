solr-bmax-queryparser
==================

![travis ci build status](https://travis-ci.org/shopping24/solr-bmax-queryparser.png)

A boosting dismax query parser for Apache Solr. The bmax query parser relies on
field types and tokenizer chains to parse the user query, discover synonyms, boost 
and penalize terms at query time. Hence it is highly configurable. The lucene query
composed is a dismax query with a minimum must match of 100%.

## Query processing

### Parsing the user query

### Discovering synonyms (optional)

### Discovering boost and penalize terms (optional)

### Building the query

## Installing the component

* Place the [`solr-bmax-queryparser-<VERSION>-jar-with-dependencies.jar`](https://github.com/shopping24/solr-bmax-queryparser/releases) in the `/lib` 
  directory of your Solr installation.

## Configuring the query parser

    <!-- bmax parser -->
    <queryParser name="bmax" class="com.s24.search.solr.query.bmax.BmaxQParserPlugin">
        <str name="synonymFieldType">fieldType_synonyms</str>
        <str name="queryParsingFieldType"fieldType_query</str>
        <str name="boostUpFieldType"fieldType_boostterms</str>
        <str name="boostDownFieldType">fieldType_penalizeterms</str>
    </queryParser>

    bmax.manipulateDocumentFrequencies
    bmax.manipulateTermFrequencies
    

### URL parameters

## Building the project

This should install the current version into your local repository

    $ export MAVEN_OPTS="-Dmaven.wagon.http.ssl.insecure=true -Dmaven.wagon.http.ssl.allowall=true -Dmaven.wagon.http.ssl.ignore.validity.dates=true"
    $ mvn clean install
    
### Releasing the project to maven central
    
Define new versions
    
    $ export NEXT_VERSION=<version>
    $ export NEXT_DEVELOPMENT_VERSION=<version>-SNAPSHOT

Then execute the release chain

    $ mvn org.codehaus.mojo:versions-maven-plugin:2.0:set -DgenerateBackupPoms=false -DnewVersion=$NEXT_VERSION
    $ git commit -a -m "pushes to release version $NEXT_VERSION"
    $ mvn -P release
    
Then, increment to next development version:
    
    $ git tag -a v$NEXT_VERSION -m "`curl -s http://whatthecommit.com/index.txt`"
    $ mvn org.codehaus.mojo:versions-maven-plugin:2.0:set -DgenerateBackupPoms=false -DnewVersion=$NEXT_DEVELOPMENT_VERSION
    $ git commit -a -m "pushes to development version $NEXT_DEVELOPMENT_VERSION"
    $ git push origin tag v$NEXT_VERSION && git push origin

## License

This project is licensed under the [Apache License, Version 2](http://www.apache.org/licenses/LICENSE-2.0.html).
