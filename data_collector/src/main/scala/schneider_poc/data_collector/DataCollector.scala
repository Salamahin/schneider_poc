package schneider_poc.data_collector

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster
import com.ghgande.j2mod.modbus.procimg.SimpleRegister
import com.typesafe.scalalogging.LazyLogging
import zio.{Clock, Task, ZIO, ZManaged}

trait DataCollector {
  def measure(m: Measurement): Task[MeasuredGauge]
}

trait DataCollectorFactory {
  def make(host: String, port: Int): ZManaged[Any, Throwable, DataCollector]
}

object DataCollectorFactory {
  val live: ZIO[Clock, Nothing, DataCollectorFactory] = for {
    clock <- ZIO.service[Clock]
    factory = new DataCollectorFactory {
      override def make(host: String, port: Int): ZManaged[Any, Throwable, DataCollector] = {
        val connectedDC = ZIO.attempt {
          val dc = new RealDataCollector(host, port, clock)
          dc.connect()
          dc
        }

        ZManaged.acquireReleaseWith(connectedDC)(dc => ZIO.succeed(dc.disconnect()))
      }
    }
  } yield factory
}

class RealDataCollector(host: String, port: Int, clock: Clock) extends DataCollector with LazyLogging {
  private val master = new ModbusTCPMaster(host, port)

  def connect() = {
    master.connect()
  }

  def disconnect() = {
    master.disconnect()
  }

  private def measureGauge(measurement: Measurement) = {
    import TypeConversions._

    measurement.gauge match {
      case Int16(offset, scale) =>
        val value = master.readMultipleRegisters(measurement.deviceId, offset, 1).head
        BigDecimal(value.asInstanceOf[SimpleRegister].getValue) / scale

      case Uint16(offset, scale) =>
        val value = master.readMultipleRegisters(measurement.deviceId, offset, 1).head
        BigDecimal(value.asInstanceOf[SimpleRegister].getValue.uint16) / scale

      case Uint32(offset, scale) =>
        val value = master
          .readMultipleRegisters(measurement.deviceId, offset, 2)
          .map(_.asInstanceOf[SimpleRegister])
          .map(_.getValue)
          .uint32
        BigDecimal(value) / scale

      case Float32(offset) =>
        master
          .readMultipleRegisters(measurement.deviceId, offset, 2)
          .map(_.asInstanceOf[SimpleRegister])
          .map(_.getValue)
          .float32

      case Int64(offset) =>
        val measured = master
          .readMultipleRegisters(measurement.deviceId, offset, 4)
          .map(_.asInstanceOf[SimpleRegister])
          .map(_.getValue)
          .int64

        BigDecimal(measured)
    }
  }

  override def measure(m: Measurement) =
    for {
      (measured, now) <- ZIO.attempt { measureGauge(m) } zipPar clock.instant
      _               = logger.debug(s"Measurement complete, details=$m, result=$measured")
    } yield MeasuredGauge(m.gatewayId, m.deviceId, m.gaugeDescription, now.toEpochMilli, measured)
}
