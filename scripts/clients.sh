export MAVEN_OPTS=-Xmx512m
for port in 61616
do
for i in {1..3}
do
 mvn -o -Ppair -DbrokerHost=127.0.0.1 -DnumProducers=3 -DnumConsumers=3 -Dcount=100000 -Did=$i -DbrokerPort=$port &
done
done
