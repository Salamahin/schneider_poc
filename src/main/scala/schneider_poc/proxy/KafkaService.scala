package schneider_poc.proxy

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.StringSerializer
import zio.Task

import java.util.Properties

trait KafkaService {
  def produce(topic: String, message: String): Task[Unit] = produce(topic, null, message)
  def produce(topic: String, key: String, message: String): Task[Unit]
}

object KafkaService extends LazyLogging {
  def live(bootstrapServer: String): KafkaService = (topic: String, key: String, message: String) => {
    Task {
      val producer = new KafkaProducer[String, String](new Properties() {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer])
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer])
      })

      producer
        .send(
          new ProducerRecord(topic, key, message),
          (metadata: RecordMetadata, exception: Exception) => {
            producer.close()

            if (exception == null)
              logger.debug(s"Kafka message with key=$key and topic=$topic produced, offset=${metadata.offset()}")
            else
              throw exception
          }
        )
        .get()
    }
  }
}
