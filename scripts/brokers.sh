for port in {61617,61616}
do 
mvn -o -Pbroker -Dbroker.host=$brokerHost -Dbroker.port=$port &
done
