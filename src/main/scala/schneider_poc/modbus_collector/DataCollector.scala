package schneider_poc.modbus_collector

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import com.ghgande.j2mod.modbus.procimg.SimpleRegister
import com.typesafe.scalalogging.LazyLogging
import zio.{Clock, URIO}

import java.time.Instant

sealed trait Measured {
  val timestamp: Instant
}
case class Numeric(override val timestamp: Instant, value: BigDecimal) extends Measured

trait DataCollector {
  def measure(gaugeId: String, deviceId: Int, gauge: Gauge): URIO[Clock, Measured]
}

class RealDataCollector(host: String, port: Int) extends DataCollector with LazyLogging with AutoCloseable {
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

  private val master = new ModbusTCPMaster(host, port)
  master.connect()

  private def measureGauge(gaugeId: String, gauge: Gauge, master: ModbusTCPMaster, deviceId: Int) = {
    logger.debug(s"Measurement started, gaugeId=${gaugeId}, deviceId=$deviceId, gauge=$gauge")

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

  override def measure(gaugeId: String, deviceId: Int, gauge: Gauge) =
    for {
      measured <- URIO {
                   measureGauge(gaugeId, gauge, master, deviceId)
                 }
      now <- Clock.instant

      _ = logger.debug(s"Measurement complete, gaugeId=$gaugeId deviceId=$deviceId, gauge=$gauge, result=$measured")

    } yield Numeric(now, measured)

  override def close(): Unit =
    master.disconnect()
}
