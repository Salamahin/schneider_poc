package schneider_poc.modbus_collector

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import com.ghgande.j2mod.modbus.procimg.SimpleRegister
import com.typesafe.scalalogging.LazyLogging
import zio.{Clock, URIO, ZIO}

import java.time.Instant

sealed trait Measured {
  val timestamp: Instant
}
case class Numeric(override val timestamp: Instant, value: BigDecimal) extends Measured

trait DataCollector {
  def measure(deviceId: Int, gauge: Gauge): URIO[Clock, Measured]
}

object DataCollector extends LazyLogging {
  private implicit class UnsignedInt16Syntax(bi: Int) {
    def uint16: BigInt = BigInt(Array(0: Byte) ++ BigInt(bi).toByteArray)
  }

  private implicit class UnsignedInt32Syntax(bi: Array[Int]) {
    def uint32: BigInt = {
      val ba = bi.take(2).map(v => BigInt(v).toByteArray).foldLeft(Array(0: Byte)) {
        case (acc, next) => acc ++ next
      }

      BigInt(ba)
    }
  }

  class RealDataCollector(master: ModbusTCPMaster) extends DataCollector with LazyLogging {
    private def measureGauge(gauge: Gauge, master: ModbusTCPMaster, deviceId: Int) = {
      logger.debug(s"Measurement started, deviceId=$deviceId, gauge=$gauge")

      gauge match {
        case Int16(offset, scale) =>
          val value = master.readMultipleRegisters(deviceId, offset, 1).head
          BigDecimal(value.asInstanceOf[SimpleRegister].getValue) / scale

        case Uint16(offset, scale) =>
          val value = master.readMultipleRegisters(deviceId, offset, 1).head
          BigDecimal(value.asInstanceOf[SimpleRegister].getValue.uint16) / scale

        case Uint32(offset, scale) =>
          val value = master
            .readMultipleRegisters(deviceId, offset, 2)
            .map(_.asInstanceOf[SimpleRegister])
            .map(_.getValue)
            .uint32
          BigDecimal(value) / scale
      }
    }

    override def measure(deviceId: Int, gauge: Gauge) =
      for {
        measured <- URIO { measureGauge(gauge, master, deviceId) }
        now      <- Clock.instant

        _ = logger.debug(s"Measurement complete, deviceId=$deviceId, gauge=$gauge, result=$measured")

      } yield Numeric(now, measured)
  }

  private def openModbusTcpMaster(host: String, port: Int) = {
    val m = new ModbusTCPMaster(host, port)
    m.connect()
    logger.debug(s"Modbus TCP master connected to host=$host:$port")
    m
  }

  def live(host: String, port: Int) = {
    ZIO
      .acquireReleaseWith[Any, Throwable, ModbusTCPMaster, DataCollector](
        acquire = ZIO.attempt { openModbusTcpMaster(host, port) },
        release = master => ZIO.succeed(master.disconnect()),
        use = master => ZIO.succeed(new RealDataCollector(master))
      )
  }
}
