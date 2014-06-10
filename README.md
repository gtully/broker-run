broker-run
==========

A maven pom that will start an activemq instance in vm.
 *MAVEN_OPTS define the jvm env*
 $> export MAVEN_OPTS="-Xmx2g -Xms2g --XX:+UseLargePages"

 **Start a broker**
    $> mvn activemq:run -Dactivemq.version=5.11-SNAPSHOT

 **run a client to consume 10000 messages**
    $> mvn exec:java -Drole=consumer

 **produce**
    $> mvn exec:java -Drole=producer

