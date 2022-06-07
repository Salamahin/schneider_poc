package schneider_poc.generator

import io.circe.{Encoder, Json}
import zio.{Clock, Random, ZIO}

import java.sql.Timestamp

case class Record(id: String, timestamp: Timestamp, value: Double)

object Record {
  import io.circe.generic.semiauto._
  implicit val timestampEncoder: Encoder[Timestamp] = Encoder.instance(ts => Json.fromLong(ts.getTime))
  implicit val recordEncoder: Encoder[Record]       = deriveEncoder[Record]

  def next(id: String) = {
    for {
      random <- ZIO.environment[Random].map(_.get)
      clock  <- ZIO.environment[Clock].map(_.get)
      now    <- clock.instant
      value  <- random.nextDouble
    } yield Record(id, new Timestamp(now.toEpochMilli), value * 100)
  }
}
