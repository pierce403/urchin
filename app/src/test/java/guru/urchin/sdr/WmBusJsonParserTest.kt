package guru.urchin.sdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WmBusJsonParserTest {
  @Test
  fun `parse valid wmbus reading`() {
    val json = """{"type":"wmbus","manufacturer":"KAM","serial":"12345678","version":1,"device_type":"water","rssi":-65.0,"freq":868.95}"""
    val result = WmBusJsonParser.parse(json)
    assertNotNull(result)
    assertEquals("KAM", result!!.manufacturer)
    assertEquals("12345678", result.serialNumber)
    assertEquals(1, result.meterVersion)
    assertEquals("water", result.meterType)
    assertEquals(-65.0, result.rssi!!, 0.01)
    assertEquals(868.95, result.frequencyMhz!!, 0.01)
  }

  @Test
  fun `returns null for wrong type`() {
    val json = """{"type":"zwave","manufacturer":"KAM","serial":"12345678"}"""
    assertNull(WmBusJsonParser.parse(json))
  }

  @Test
  fun `returns null for missing manufacturer`() {
    val json = """{"type":"wmbus","serial":"12345678"}"""
    assertNull(WmBusJsonParser.parse(json))
  }

  @Test
  fun `returns null for missing serial`() {
    val json = """{"type":"wmbus","manufacturer":"KAM"}"""
    assertNull(WmBusJsonParser.parse(json))
  }

  @Test
  fun `rejects invalid manufacturer format`() {
    val json = """{"type":"wmbus","manufacturer":"KAMM","serial":"12345678"}"""
    assertNull(WmBusJsonParser.parse(json))
  }

  @Test
  fun `rejects lowercase manufacturer`() {
    val json = """{"type":"wmbus","manufacturer":"kam","serial":"12345678"}"""
    assertNull(WmBusJsonParser.parse(json))
  }

  @Test
  fun `rejects invalid serial format`() {
    val json = """{"type":"wmbus","manufacturer":"KAM","serial":"123"}"""
    assertNull(WmBusJsonParser.parse(json))
  }

  @Test
  fun `rejects serial with non-hex chars`() {
    val json = """{"type":"wmbus","manufacturer":"KAM","serial":"1234GHIJ"}"""
    assertNull(WmBusJsonParser.parse(json))
  }

  @Test
  fun `returns null for malformed json`() {
    assertNull(WmBusJsonParser.parse("not json"))
  }

  @Test
  fun `returns null for empty string`() {
    assertNull(WmBusJsonParser.parse(""))
  }

  @Test
  fun `rejects oversized json`() {
    val json = """{"type":"wmbus","manufacturer":"KAM","serial":"12345678","padding":"${"x".repeat(10_001)}"}"""
    assertNull(WmBusJsonParser.parse(json))
  }

  @Test
  fun `handles optional fields missing`() {
    val json = """{"type":"wmbus","manufacturer":"KAM","serial":"12345678"}"""
    val result = WmBusJsonParser.parse(json)
    assertNotNull(result)
    assertNull(result!!.meterVersion)
    assertNull(result.meterType)
    assertNull(result.rssi)
    assertNull(result.frequencyMhz)
  }
}
