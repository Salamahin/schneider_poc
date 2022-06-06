package schneider_poc

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer
import zio.Task

import java.util.Properties

trait KafkaService {
  def produce(topic: String, message: String): Task[Unit]
}

object KafkaService {
  def live(bootstrapServer: String): KafkaService = new KafkaService {
    val producer = new KafkaProducer[String, String](new Properties() {
      put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer)
      put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer])
      put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer])
    })

    override def produce(topic: String, message: String): Task[Unit] =
      Task {
        producer.send(new ProducerRecord(topic, null, message)).get()
      }
  }
}
