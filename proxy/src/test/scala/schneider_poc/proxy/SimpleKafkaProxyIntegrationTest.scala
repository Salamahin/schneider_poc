package schneider_poc.proxy

import com.typesafe.scalalogging.LazyLogging
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.serialization.StringDeserializer
import schneider_poc.proxy.{KafkaService, SimpleKafkaProxy}
import zhttp.http.{HttpData, Method}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.test.Assertion.hasSameElements
import zio.{Schedule, Task, UIO, ZManaged}
import zio.test.{DefaultRunnableSpec, TestEnvironment, ZSpec}

import java.net.{ServerSocket, URLEncoder}
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.{Collections, Properties, UUID}
import scala.util.Using
import zio.test._

object SimpleKafkaProxyIntegrationTest extends DefaultRunnableSpec with LazyLogging {
  case class ServicesInfo(zkPort: Int, kafkaPort: Int, servicePort: Int)

  def findFreePorts = {
    def nextFreePort = Using(new ServerSocket(0))(ss => ss.getLocalPort)

    Task.fromTry {
      for {
        zk      <- nextFreePort
        kafka   <- nextFreePort
        service <- nextFreePort
      } yield ServicesInfo(zk, kafka, service)
    }
  }

  private def startKafka(zkPort: Int, kafkaPort: Int) =
    ZManaged.acquireReleaseWith(
      UIO {
        val conf = EmbeddedKafkaConfig(kafkaPort = kafkaPort, zooKeeperPort = zkPort)
        EmbeddedKafka.start()(conf)
        logger.debug(s"Kafka started on kafkaPort=$kafkaPort, zkPort=$zkPort")
      }
    )(_ =>
      UIO {
        EmbeddedKafka.stop()
        logger.debug(s"Kafka stopped")
      }
    )

  private def startService(servicePort: Int, kafkaPort: Int) =
    SimpleKafkaProxy.start(servicePort).provideLayer(KafkaService.live(s"localhost:$kafkaPort"))

  private val runServices = (for {
    si @ ServicesInfo(zkPort, kafkaPort, servicePort) <- findFreePorts
    _                                                 <- startKafka(zkPort, kafkaPort).zio
    service                                           <- startService(servicePort, kafkaPort).fork
  } yield (si, service)).retry(Schedule.forever)

  private def postRequest(servicePort: Int, topic: String, body: String, keys: String*) = {
    def encodeValue(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8.toString)

    val queryString =
      if (keys.isEmpty) ""
      else
        keys
          .map(encodeValue)
          .map(k => s"key=$k")
          .mkString("?", "&", "")

    Client
      .request(
        s"http://localhost:$servicePort/$topic$queryString",
        Method.POST,
        content = HttpData.fromString(body)
      )
      .provideCustomLayer(
        ChannelFactory.auto ++ EventLoopGroup.auto()
      )
  }

  private def readTopic(kafkaPort: Int, topic: String) = {
    import scala.jdk.CollectionConverters._

    val consumer = new KafkaConsumer[String, String](new Properties() {

      put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, s"localhost:$kafkaPort")
      put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString)
      put(ConsumerConfig.CLIENT_ID_CONFIG, "client-id")
      put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer])
      put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer])
    })

    consumer.subscribe(Collections.singletonList(topic))
    val messages = consumer
      .poll(Duration.ofSeconds(200))
      .iterator()
      .asScala
      .map(r => (r.key(), r.value()))
      .toList

    logger.debug(s"Poll from topic=$topic complete, ${messages.size} consumed")
    messages
  }

  val singleKey = test("when no key specified pushes a single message to the topic") {
    for {
      (ServicesInfo(_, kafkaPort, servicePort), srv) <- runServices
      _                                              <- postRequest(servicePort, "topicname", """{"hello": "world"}""")
      storedMessages                                 = readTopic(kafkaPort, "topicname")
      _                                              <- srv.interrupt
    } yield assert(storedMessages.map(_._2))(hasSameElements("""{"hello": "world"}""" :: Nil))
  }

  val multipleKeys = test("when several keys specified pushes a several messages to the topic") {
    for {
      (ServicesInfo(_, kafkaPort, servicePort), srv) <- runServices
      _                                              <- postRequest(servicePort, "topicname", """{"hello": "world"}""", "k1", "k2")
      storedMessages                                 = readTopic(kafkaPort, "topicname")
      _                                              <- srv.interrupt
    } yield assert(storedMessages)(
      hasSameElements(
        ("k1"   -> """{"hello": "world"}""") ::
          ("k2" -> """{"hello": "world"}""") ::
          Nil
      )
    )
  }

  override def spec: ZSpec[TestEnvironment, Any] = suite("kafka proxy: integration")(singleKey, multipleKeys)
}
