package schneider_poc.data_collector

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class TypeConversionsTest extends AnyFunSuite with Matchers {
  import TypeConversions._

  test("uint16 conversion") {
    0x8001.uint16 should be(BigInt(32769))
  }

  test("uint32 conversion") {
    Array(0x8001, 0x8001).uint32 should be(BigInt(2147581953L))
  }

  test("int64 conversion") {
    Array(0x7fff, 0xffff, 0xffff, 0xffff).int64 should be(BigInt(Long.MaxValue))
    Array(0x8000, 0x0000, 0x0000, 0x0000).int64 should be(BigInt(Long.MinValue))
  }

  test("float32 conversion") {
    Array(0x4049, 0x0e56).float32 should be(BigDecimal("3.1415") +- 0.01)
  }
}
