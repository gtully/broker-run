broker-run
==========

A maven pom that will start an activemq instance in vm

 *MAVEN_OPTS define the jvm env*

 $> export MAVEN_OPTS="-Xmx2g -Xms2g -XX:+UseLargePages"

 **Start a broker**

    $> mvn -Pbroker

 **run a producer-consumer pair**

    $> mvn -Ppair

 **run a client to consume 10000 messages**

    $> mvn -Pclient -Drole=consumer

 **produce**

    $> mvn -Pclient -Drole=producer

