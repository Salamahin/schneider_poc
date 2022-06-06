package schneider_poc

import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.common.serialization.StringDeserializer
import zhttp.http.{HttpData, Method}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zio.test.Assertion.hasSameElements
import zio.test._
import zio.{UIO, ZEnv, ZManaged}

import java.time.Duration
import java.util.{Collections, Properties, UUID}

object SimpleKafkaProxyIntegrationTest extends DefaultRunnableSpec {
  private val zkPort      = 9092
  private val kafkaPort   = 9093
  private val servicePort = 18080

  private val kafkaBootstrap = s"localhost:$kafkaPort"

  private val managedKafka = ZManaged.acquireReleaseWith {
    val conf = EmbeddedKafkaConfig(kafkaPort = kafkaPort, zooKeeperPort = zkPort)
    UIO(EmbeddedKafka.start()(conf))
  }(_ => UIO(EmbeddedKafka.stop()))

  private val webService = SimpleKafkaProxy.start(servicePort).provideService(KafkaService.live(kafkaBootstrap))

  private def postRequest(topic: String, body: String) =
    Client
      .request(
        s"http://localhost:$servicePort/$topic",
        Method.POST,
        content = HttpData.fromString(body)
      )
      .provideCustomLayer(
        ChannelFactory.auto ++ EventLoopGroup.auto()
      )

  private def readTopic(topic: String) = {
    val consumer = new KafkaConsumer[String, String](new Properties() {

      put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrap)
      put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString)
      put(ConsumerConfig.CLIENT_ID_CONFIG, "client-id")
      put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer])
      put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, classOf[StringDeserializer])
    })

    consumer.subscribe(Collections.singletonList(topic))

    import scala.jdk.CollectionConverters._
    consumer
      .poll(Duration.ofSeconds(200))
      .iterator()
      .asScala
      .map(_.value())
      .toList
  }

  val integration: ZSpec[ZEnv, Any] = test("pushes request body to the kafka topic") {
    for {
      ws <- webService.fork
      _  <- managedKafka.zio

      _              <- postRequest("topicname", """{"hello": "world"}""")
      storedMessages = readTopic("topicname")

      _ <- ws.interrupt
    } yield assert(storedMessages)(hasSameElements("""{"hello": "world"}""" :: Nil))
  }

  override def spec: ZSpec[TestEnvironment, Any] = suite("kafka proxy: ingegration")(integration)
}
