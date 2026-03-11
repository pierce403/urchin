package guru.urchin.sdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbJsonParserTest {
  @Test
  fun `parses complete ADS-B single aircraft JSON`() {
    val json = """
      {
        "hex": "A00001",
        "flight": "AAL123  ",
        "alt_baro": 35000,
        "alt_geom": 35200,
        "gs": 450.5,
        "track": 270.0,
        "lat": 40.6413,
        "lon": -73.7781,
        "squawk": "1200",
        "rssi": -8.5
      }
    """.trimIndent()

    val result = AdsbJsonParser.parse(json)
    assertNotNull(result)
    assertEquals("A00001", result!!.icao)
    assertEquals("AAL123", result.callsign)
    assertEquals(35000, result.altitude)
    assertEquals(450.5, result.speed!!, 0.01)
    assertEquals(270.0, result.heading!!, 0.01)
    assertEquals(40.6413, result.lat!!, 0.0001)
    assertEquals(-73.7781, result.lon!!, 0.0001)
    assertEquals("1200", result.squawk)
    assertEquals(-8.5, result.rssi!!, 0.01)
    assertEquals(1090.0, result.frequencyMhz!!, 0.01)
  }

  @Test
  fun `returns null for missing hex field`() {
    val json = """{"flight":"AAL123","alt_baro":35000}"""
    assertNull(AdsbJsonParser.parse(json))
  }

  @Test
  fun `returns null for empty hex field`() {
    val json = """{"hex":"","flight":"AAL123"}"""
    assertNull(AdsbJsonParser.parse(json))
  }

  @Test
  fun `returns null for invalid JSON`() {
    assertNull(AdsbJsonParser.parse("not json"))
    assertNull(AdsbJsonParser.parse(""))
    assertNull(AdsbJsonParser.parse("{"))
  }

  @Test
  fun `handles missing optional fields`() {
    val json = """{"hex":"ABCDEF"}"""
    val result = AdsbJsonParser.parse(json)
    assertNotNull(result)
    assertEquals("ABCDEF", result!!.icao)
    assertNull(result.callsign)
    assertNull(result.altitude)
    assertNull(result.speed)
    assertNull(result.heading)
    assertNull(result.lat)
    assertNull(result.lon)
    assertNull(result.squawk)
    assertNull(result.rssi)
  }

  @Test
  fun `uppercases ICAO hex code`() {
    val json = """{"hex":"abcdef"}"""
    val result = AdsbJsonParser.parse(json)
    assertNotNull(result)
    assertEquals("ABCDEF", result!!.icao)
  }

  @Test
  fun `trims whitespace from flight callsign`() {
    val json = """{"hex":"A00001","flight":"UAL456  "}"""
    val result = AdsbJsonParser.parse(json)
    assertEquals("UAL456", result!!.callsign)
  }

  @Test
  fun `prefers alt_baro over alt_geom`() {
    val json = """{"hex":"A00001","alt_baro":35000,"alt_geom":35200}"""
    val result = AdsbJsonParser.parse(json)
    assertEquals(35000, result!!.altitude)
  }

  @Test
  fun `falls back to alt_geom when alt_baro missing`() {
    val json = """{"hex":"A00001","alt_geom":35200}"""
    val result = AdsbJsonParser.parse(json)
    assertEquals(35200, result!!.altitude)
  }

  @Test
  fun `preserves raw JSON in reading`() {
    val json = """{"hex":"A00001","flight":"AAL123"}"""
    val result = AdsbJsonParser.parse(json)
    assertNotNull(result)
    assertEquals(json, result!!.rawJson)
  }

  @Test
  fun `parses aircraft array format`() {
    val json = """
      {
        "now": 1700000000.0,
        "messages": 12345,
        "aircraft": [
          {"hex": "A00001", "flight": "AAL123 ", "alt_baro": 35000, "gs": 450.0},
          {"hex": "A00002", "flight": "UAL456 ", "alt_baro": 28000, "gs": 380.0},
          {"hex": "A00003"}
        ]
      }
    """.trimIndent()

    val results = AdsbJsonParser.parseAircraftArray(json)
    assertEquals(3, results.size)
    assertEquals("A00001", results[0].icao)
    assertEquals("AAL123", results[0].callsign)
    assertEquals(35000, results[0].altitude)
    assertEquals("A00002", results[1].icao)
    assertEquals("A00003", results[2].icao)
    assertNull(results[2].callsign)
  }

  @Test
  fun `aircraft array returns empty for missing aircraft key`() {
    val json = """{"now": 1700000000.0, "messages": 0}"""
    assertEquals(emptyList<SdrReading.Adsb>(), AdsbJsonParser.parseAircraftArray(json))
  }

  @Test
  fun `aircraft array skips entries without hex`() {
    val json = """
      {
        "aircraft": [
          {"hex": "A00001", "flight": "AAL123"},
          {"flight": "NOHEX"},
          {"hex": "A00002"}
        ]
      }
    """.trimIndent()

    val results = AdsbJsonParser.parseAircraftArray(json)
    assertEquals(2, results.size)
    assertEquals("A00001", results[0].icao)
    assertEquals("A00002", results[1].icao)
  }

  @Test
  fun `aircraft array returns empty for invalid JSON`() {
    assertEquals(emptyList<SdrReading.Adsb>(), AdsbJsonParser.parseAircraftArray("not json"))
  }

  @Test
  fun `parses emergency squawk codes`() {
    val squawks = listOf("7500", "7600", "7700")
    for (squawk in squawks) {
      val json = """{"hex":"A00001","squawk":"$squawk"}"""
      val result = AdsbJsonParser.parse(json)
      assertEquals(squawk, result!!.squawk)
    }
  }

  @Test
  fun `prefers gs over tas for speed`() {
    val json = """{"hex":"A00001","gs":450.0,"tas":420.0}"""
    val result = AdsbJsonParser.parse(json)
    assertEquals(450.0, result!!.speed!!, 0.01)
  }

  @Test
  fun `falls back to tas when gs missing`() {
    val json = """{"hex":"A00001","tas":420.0}"""
    val result = AdsbJsonParser.parse(json)
    assertEquals(420.0, result!!.speed!!, 0.01)
  }
}
