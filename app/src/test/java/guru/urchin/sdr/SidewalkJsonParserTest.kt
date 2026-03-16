package guru.urchin.sdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SidewalkJsonParserTest {
  @Test
  fun `parse valid sidewalk reading`() {
    val json = """{"type":"sidewalk","smsn":"0A1B2C3D4E","frame_type":"data","rssi":-80.0,"freq":903.0}"""
    val result = SidewalkJsonParser.parse(json)
    assertNotNull(result)
    assertEquals("0A1B2C3D4E", result!!.smsn)
    assertEquals("data", result.frameType)
    assertEquals(-80.0, result.rssi!!, 0.01)
  }

  @Test
  fun `returns null for wrong type`() {
    val json = """{"type":"zwave","smsn":"0A1B2C3D4E"}"""
    assertNull(SidewalkJsonParser.parse(json))
  }

  @Test
  fun `returns null for missing smsn`() {
    val json = """{"type":"sidewalk","frame_type":"data"}"""
    assertNull(SidewalkJsonParser.parse(json))
  }

  @Test
  fun `rejects smsn wrong length`() {
    val json = """{"type":"sidewalk","smsn":"0A1B2C"}"""
    assertNull(SidewalkJsonParser.parse(json))
  }

  @Test
  fun `rejects smsn with non-hex chars`() {
    val json = """{"type":"sidewalk","smsn":"0A1B2CXYZ0"}"""
    assertNull(SidewalkJsonParser.parse(json))
  }

  @Test
  fun `accepts lowercase hex smsn`() {
    val json = """{"type":"sidewalk","smsn":"0a1b2c3d4e"}"""
    val result = SidewalkJsonParser.parse(json)
    assertNotNull(result)
    assertEquals("0a1b2c3d4e", result!!.smsn)
  }

  @Test
  fun `returns null for malformed json`() {
    assertNull(SidewalkJsonParser.parse("not json"))
  }

  @Test
  fun `returns null for empty string`() {
    assertNull(SidewalkJsonParser.parse(""))
  }

  @Test
  fun `rejects oversized json`() {
    val json = """{"type":"sidewalk","smsn":"0A1B2C3D4E","padding":"${"x".repeat(10_001)}"}"""
    assertNull(SidewalkJsonParser.parse(json))
  }

  @Test
  fun `handles optional fields missing`() {
    val json = """{"type":"sidewalk","smsn":"0A1B2C3D4E"}"""
    val result = SidewalkJsonParser.parse(json)
    assertNotNull(result)
    assertNull(result!!.frameType)
    assertNull(result.rssi)
    assertNull(result.frequencyMhz)
  }
}
