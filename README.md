solr-thymeleaf-responsewriter
==================

![travis ci build status](https://travis-ci.org/shopping24/solr-thymeleaf.png)

A Solr component to use the [Thymeleaf template engine](http://www.thymeleaf.org/).

## Installing the component

* Place the [`solr-thymeleaf-<VERSION>-jar-with-dependencies.jar`](https://github.com/shopping24/solr-thymeleaf/releases) in the `/lib` 
  directory of your Solr installation.
* Configure the component in your `solrconfig.xml`:

    <!-- html / thymeleaf response writer -->
    <queryResponseWriter name="html" class="com.s24.search.solr.response.ThymeleafResponseWriter" />

### Configuring template resolving

Pass the template to render in the `tl.template` request parameter. You can configure Thymeleaf template resolving:

    <queryResponseWriter name="html" class="com.s24.search.solr.response.ThymeleafResponseWriter">
         <str name="tl.templateMode">XHTML</str>
         <str name="tl.locale">de_de</str>
         <str name="tl.cacheTTLMs">3600000</str>
         <str name="tl.prefix">.html</str>
         <str name="tl.suffix">${solr.core.config}/templates/</str>
    </queryResponseWriter>

The template context is prefilled with the current `request`, the request `params` and a solr `response`.

## Building the project

This should install the current version into your local repository

    $ export JAVA_HOME=$(/usr/libexec/java_home -v 1.7)
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
