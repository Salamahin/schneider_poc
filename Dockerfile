FROM sbtscala/scala-sbt:8u332_1.6.2_3.1.2 as sbt
COPY . /usr/src/schneider_poc
WORKDIR /usr/src/schneider_poc
RUN sbt assembly

# DATA COLLECTOR ==================================================================
FROM sbt as data_collector

ENV PERIODICITY "5s"
ENV REGISTRY_FILE "device_registry.conf"

CMD java -jar data_collector/target/scala-2.13/data_collector.jar \
  --periodicity $PERIODICITY \
  --registry-file $REGISTRY_FILE \
  --service-url $SERVICE_ENDPOINT/$TOPIC

# PROXY          ==================================================================
FROM sbt as proxy

ENV SERVICE_PORT 18080

CMD java -jar proxy/target/scala-2.13/proxy.jar \
  --bootstrap-server $KAFKA_BOOTSTRAP_SERVER \
  --port $SERVICE_PORT