package guru.urchin.sdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ZwaveJsonParserTest {
  @Test
  fun `parse valid zwave reading`() {
    val json = """{"type":"zwave","home_id":"AABBCCDD","node_id":5,"frame_type":"singlecast","rssi":-72.0,"freq":908.42}"""
    val result = ZwaveJsonParser.parse(json)
    assertNotNull(result)
    assertEquals("AABBCCDD", result!!.homeId)
    assertEquals(5, result.nodeId)
    assertEquals("singlecast", result.frameType)
    assertEquals(-72.0, result.rssi!!, 0.01)
  }

  @Test
  fun `returns null for wrong type`() {
    val json = """{"type":"wmbus","home_id":"AABBCCDD","node_id":5}"""
    assertNull(ZwaveJsonParser.parse(json))
  }

  @Test
  fun `returns null for missing home_id`() {
    val json = """{"type":"zwave","node_id":5}"""
    assertNull(ZwaveJsonParser.parse(json))
  }

  @Test
  fun `returns null for missing node_id`() {
    val json = """{"type":"zwave","home_id":"AABBCCDD"}"""
    assertNull(ZwaveJsonParser.parse(json))
  }

  @Test
  fun `rejects invalid home_id format`() {
    val json = """{"type":"zwave","home_id":"XYZ","node_id":5}"""
    assertNull(ZwaveJsonParser.parse(json))
  }

  @Test
  fun `rejects home_id wrong length`() {
    val json = """{"type":"zwave","home_id":"AABB","node_id":5}"""
    assertNull(ZwaveJsonParser.parse(json))
  }

  @Test
  fun `rejects node_id zero`() {
    val json = """{"type":"zwave","home_id":"AABBCCDD","node_id":0}"""
    assertNull(ZwaveJsonParser.parse(json))
  }

  @Test
  fun `rejects node_id above 232`() {
    val json = """{"type":"zwave","home_id":"AABBCCDD","node_id":233}"""
    assertNull(ZwaveJsonParser.parse(json))
  }

  @Test
  fun `accepts node_id at boundary 232`() {
    val json = """{"type":"zwave","home_id":"AABBCCDD","node_id":232}"""
    val result = ZwaveJsonParser.parse(json)
    assertNotNull(result)
    assertEquals(232, result!!.nodeId)
  }

  @Test
  fun `accepts node_id at boundary 1`() {
    val json = """{"type":"zwave","home_id":"AABBCCDD","node_id":1}"""
    val result = ZwaveJsonParser.parse(json)
    assertNotNull(result)
    assertEquals(1, result!!.nodeId)
  }

  @Test
  fun `accepts lowercase hex home_id`() {
    val json = """{"type":"zwave","home_id":"aabbccdd","node_id":5}"""
    val result = ZwaveJsonParser.parse(json)
    assertNotNull(result)
    assertEquals("aabbccdd", result!!.homeId)
  }

  @Test
  fun `returns null for malformed json`() {
    assertNull(ZwaveJsonParser.parse("not json"))
  }

  @Test
  fun `rejects oversized json`() {
    val json = """{"type":"zwave","home_id":"AABBCCDD","node_id":5,"padding":"${"x".repeat(10_001)}"}"""
    assertNull(ZwaveJsonParser.parse(json))
  }
}
