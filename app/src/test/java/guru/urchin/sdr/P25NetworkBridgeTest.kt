package guru.urchin.sdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P25NetworkBridgeTest {

  @Test
  fun `parses complete P25 JSON event`() {
    val json = """{"unit_id":"12345","nac":"293","wacn":"BEE00","system_id":"001","talkgroup":"100","rssi":-15.5,"freq":851.0}"""
    val result = P25NetworkBridge.parseP25Json(json)
    assertNotNull(result)
    assertEquals("12345", result!!.unitId)
    assertEquals("293", result.nac)
    assertEquals("BEE00", result.wacn)
    assertEquals("001", result.systemId)
    assertEquals("100", result.talkGroupId)
    assertEquals(-15.5, result.rssi!!, 0.01)
    assertEquals(851.0, result.frequencyMhz!!, 0.01)
  }

  @Test
  fun `parses with alt field names src and sysid and tgid`() {
    val json = """{"src":"67890","sysid":"002","tgid":"200"}"""
    val result = P25NetworkBridge.parseP25Json(json)
    assertNotNull(result)
    assertEquals("67890", result!!.unitId)
    assertEquals("002", result.systemId)
    assertEquals("200", result.talkGroupId)
  }

  @Test
  fun `parses with srcaddr field`() {
    val json = """{"srcaddr":"99999","nac":"100"}"""
    val result = P25NetworkBridge.parseP25Json(json)
    assertNotNull(result)
    assertEquals("99999", result!!.unitId)
    assertEquals("100", result.nac)
  }

  @Test
  fun `returns null when no unit ID field present`() {
    val json = """{"nac":"293","talkgroup":"100"}"""
    assertNull(P25NetworkBridge.parseP25Json(json))
  }

  @Test
  fun `returns null for invalid JSON`() {
    assertNull(P25NetworkBridge.parseP25Json("not json"))
    assertNull(P25NetworkBridge.parseP25Json(""))
    assertNull(P25NetworkBridge.parseP25Json("{"))
  }

  @Test
  fun `handles missing optional fields`() {
    val json = """{"unit_id":"12345"}"""
    val result = P25NetworkBridge.parseP25Json(json)
    assertNotNull(result)
    assertEquals("12345", result!!.unitId)
    assertNull(result.nac)
    assertNull(result.wacn)
    assertNull(result.systemId)
    assertNull(result.talkGroupId)
    assertNull(result.rssi)
    assertNull(result.frequencyMhz)
  }

  @Test
  fun `preserves raw JSON`() {
    val json = """{"unit_id":"12345","nac":"293"}"""
    val result = P25NetworkBridge.parseP25Json(json)
    assertNotNull(result)
    assertEquals(json, result!!.rawJson)
  }

  @Test
  fun `parseP25StatusJson parses units array`() {
    val json = """
      {
        "units": [
          {"unit_id": "12345", "nac": "293", "talkgroup": "100"},
          {"unit_id": "67890", "nac": "293", "talkgroup": "200"},
          {"unit_id": "11111"}
        ]
      }
    """.trimIndent()

    val results = P25NetworkBridge.parseP25StatusJson(json)
    assertEquals(3, results.size)
    assertEquals("12345", results[0].unitId)
    assertEquals("100", results[0].talkGroupId)
    assertEquals("67890", results[1].unitId)
    assertEquals("11111", results[2].unitId)
  }

  @Test
  fun `parseP25StatusJson skips entries without unit ID`() {
    val json = """{"units":[{"unit_id":"12345"},{"nac":"293"},{"unit_id":"67890"}]}"""
    val results = P25NetworkBridge.parseP25StatusJson(json)
    assertEquals(2, results.size)
    assertEquals("12345", results[0].unitId)
    assertEquals("67890", results[1].unitId)
  }

  @Test
  fun `parseP25StatusJson returns empty for missing units key`() {
    val json = """{"status": "ok"}"""
    assertTrue(P25NetworkBridge.parseP25StatusJson(json).isEmpty())
  }

  @Test
  fun `parseP25StatusJson returns empty for invalid JSON`() {
    assertTrue(P25NetworkBridge.parseP25StatusJson("not json").isEmpty())
  }
}
