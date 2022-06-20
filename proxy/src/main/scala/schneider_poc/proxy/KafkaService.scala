package schneider_poc.proxy

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.StringSerializer
import zio.{IO, UIO, ZManaged}

import java.util.Properties

sealed trait KafkaError
case object UnknownKafkaError extends KafkaError

case class MessageLocation(partition: Int, offset: Long)

trait KafkaService {
  def produce(topic: String, message: String): IO[KafkaError, MessageLocation] = produce(topic, null, message)
  def produce(topic: String, key: String, message: String): IO[KafkaError, MessageLocation]
}

object KafkaService extends LazyLogging {
  def live(bootstrapServer: String) =
    ZManaged
      .acquireReleaseWith(
        UIO.succeed(
          new KafkaProducer[String, String](new Properties() {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer])
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer])
          })
        )
      ) { pr => UIO(pr.close()) }
      .map { pr =>
        new KafkaService {
          override def produce(topic: String, key: String, message: String): IO[KafkaError, MessageLocation] = IO.fromEither {
            val meta = pr
              .send(
                new ProducerRecord(topic, key, message),
                (metadata: RecordMetadata, exception: Exception) => {
                  if (exception == null) {
                    logger.debug(s"Kafka message with key=$key and value=$message is stored in the topic=$topic, offset=${metadata.offset()}, partition=${metadata.partition()}")
                  } else
                    logger.error(s"Failed to store the message with key=$key and value=$message is stored in the topic=$topic}", exception)
                }
              )
              .get()

            if (meta.hasOffset) Right(MessageLocation(meta.partition(), meta.offset()))
            else Left(UnknownKafkaError)
          }
        }
      }
      .toLayer
}
