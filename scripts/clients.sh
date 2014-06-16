export MAVEN_OPTS=-Xmx512m
for port in {61616,61617}
do
for i in {1..6}
do
 mvn -o -Ppair -Dbroker.host=$brokerHost -Dcount=100000 -Dport=$port -Did=$i  &
 mvn -o -Ppair -Dbroker.host=$brokerHost -Dcount=100000 -Dport=$port -Did=$i  &
 mvn -o -Ppair -Dbroker.host=$brokerHost -Dcount=100000 -Dport=$port -Did=$i  &
done
done
