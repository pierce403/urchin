package guru.urchin.sdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AdsbSbsParserTest {
  @Test
  fun `parses BaseStation MSG line`() {
    val line = "MSG,3,1,1,A00001,1,2026/03/11,16:30:00.000,2026/03/11,16:30:00.000,AAL123 ,35000,450,270,40.6413,-73.7781,0,1200,0,0,0,0"

    val reading = AdsbSbsParser.parse(line)

    assertNotNull(reading)
    assertEquals("A00001", reading!!.icao)
    assertEquals("AAL123", reading.callsign)
    assertEquals(35000, reading.altitude)
    assertEquals(450.0, reading.speed!!, 0.01)
    assertEquals(270.0, reading.heading!!, 0.01)
    assertEquals(40.6413, reading.lat!!, 0.0001)
    assertEquals(-73.7781, reading.lon!!, 0.0001)
    assertEquals("1200", reading.squawk)
  }

  @Test
  fun `returns null for non MSG lines`() {
    assertNull(AdsbSbsParser.parse("SEL,1,1,1,A00001"))
  }

  @Test
  fun `returns null when ICAO is missing`() {
    val line = "MSG,3,1,1,,1,2026/03/11,16:30:00.000,2026/03/11,16:30:00.000,AAL123 ,35000,450,270,40.6413,-73.7781,0,1200,0,0,0,0"
    assertNull(AdsbSbsParser.parse(line))
  }
}
