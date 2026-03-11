package guru.urchin.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DeviceKeyProtocolTest {

  private fun baseInput() = ObservationInput(
    name = null,
    address = null,
    rssi = -50,
    timestamp = 1000L,
    serviceUuids = emptyList(),
    manufacturerData = emptyMap(),
    source = "SDR"
  )

  @Test
  fun `ADS-B ICAO produces stable key across observations`() {
    val first = baseInput().copy(adsbIcao = "A00001", adsbCallsign = "AAL123")
    val second = first.copy(timestamp = 2000L, rssi = -60, adsbCallsign = "AAL456")
    assertEquals(DeviceKey.from(first), DeviceKey.from(second))
  }

  @Test
  fun `different ADS-B ICAO produce different keys`() {
    val a = baseInput().copy(adsbIcao = "A00001")
    val b = baseInput().copy(adsbIcao = "A00002")
    assertNotEquals(DeviceKey.from(a), DeviceKey.from(b))
  }

  @Test
  fun `ADS-B ICAO is case-insensitive`() {
    val lower = baseInput().copy(adsbIcao = "abcdef")
    val upper = baseInput().copy(adsbIcao = "ABCDEF")
    assertEquals(DeviceKey.from(lower), DeviceKey.from(upper))
  }

  @Test
  fun `POCSAG CAP code produces stable key`() {
    val first = baseInput().copy(pocsagCapCode = "1234567", pocsagFunctionCode = 1)
    val second = first.copy(timestamp = 2000L, rssi = -70)
    assertEquals(DeviceKey.from(first), DeviceKey.from(second))
  }

  @Test
  fun `different POCSAG CAP codes produce different keys`() {
    val a = baseInput().copy(pocsagCapCode = "1234567", pocsagFunctionCode = 1)
    val b = baseInput().copy(pocsagCapCode = "7654321", pocsagFunctionCode = 1)
    assertNotEquals(DeviceKey.from(a), DeviceKey.from(b))
  }

  @Test
  fun `same POCSAG CAP code with different function codes produce different keys`() {
    val a = baseInput().copy(pocsagCapCode = "1234567", pocsagFunctionCode = 0)
    val b = baseInput().copy(pocsagCapCode = "1234567", pocsagFunctionCode = 2)
    assertNotEquals(DeviceKey.from(a), DeviceKey.from(b))
  }

  @Test
  fun `P25 unit ID produces stable key`() {
    val first = baseInput().copy(p25UnitId = "12345", p25Wacn = "BEE00", p25SystemId = "001")
    val second = first.copy(timestamp = 2000L, p25TalkGroupId = "200")
    assertEquals(DeviceKey.from(first), DeviceKey.from(second))
  }

  @Test
  fun `different P25 unit IDs produce different keys`() {
    val a = baseInput().copy(p25UnitId = "12345", p25Wacn = "BEE00", p25SystemId = "001")
    val b = baseInput().copy(p25UnitId = "67890", p25Wacn = "BEE00", p25SystemId = "001")
    assertNotEquals(DeviceKey.from(a), DeviceKey.from(b))
  }

  @Test
  fun `TPMS sensor ID with model produces stable key`() {
    val first = baseInput().copy(tpmsSensorId = "0x00ABCDEF", tpmsModel = "Toyota")
    val second = first.copy(timestamp = 2000L, rssi = -80)
    assertEquals(DeviceKey.from(first), DeviceKey.from(second))
  }

  @Test
  fun `TPMS sensor ID is case-insensitive`() {
    val lower = baseInput().copy(tpmsSensorId = "0x00abcdef", tpmsModel = "Toyota")
    val upper = baseInput().copy(tpmsSensorId = "0x00ABCDEF", tpmsModel = "Toyota")
    assertEquals(DeviceKey.from(lower), DeviceKey.from(upper))
  }

  @Test
  fun `priority order - ADS-B takes precedence over POCSAG`() {
    val withBoth = baseInput().copy(adsbIcao = "A00001", pocsagCapCode = "1234567")
    val adsbOnly = baseInput().copy(adsbIcao = "A00001")
    assertEquals(DeviceKey.from(withBoth), DeviceKey.from(adsbOnly))
  }

  @Test
  fun `priority order - POCSAG takes precedence over P25`() {
    val withBoth = baseInput().copy(pocsagCapCode = "1234567", pocsagFunctionCode = 1, p25UnitId = "12345")
    val pocsagOnly = baseInput().copy(pocsagCapCode = "1234567", pocsagFunctionCode = 1)
    assertEquals(DeviceKey.from(withBoth), DeviceKey.from(pocsagOnly))
  }

  @Test
  fun `priority order - P25 takes precedence over TPMS`() {
    val withBoth = baseInput().copy(p25UnitId = "12345", tpmsSensorId = "0x00ABCDEF")
    val p25Only = baseInput().copy(p25UnitId = "12345")
    assertEquals(DeviceKey.from(withBoth), DeviceKey.from(p25Only))
  }
}
