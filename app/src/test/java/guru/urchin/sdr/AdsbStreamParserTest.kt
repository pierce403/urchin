package guru.urchin.sdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AdsbStreamParserTest {
  @Test
  fun `accepts json stream lines`() {
    val line = """{"hex":"a00001","flight":"AAL123 ","alt_baro":35000}"""

    val reading = AdsbStreamParser.parse(line)

    assertNotNull(reading)
    assertEquals("A00001", reading!!.icao)
    assertEquals("AAL123", reading.callsign)
  }

  @Test
  fun `accepts sbs stream lines`() {
    val line = "MSG,3,1,1,A00002,1,2026/03/11,16:30:00.000,2026/03/11,16:30:00.000,UAL456 ,28000,380,180,37.6188,-122.3754,0,2200,0,0,0,0"

    val reading = AdsbStreamParser.parse(line)

    assertNotNull(reading)
    assertEquals("A00002", reading!!.icao)
    assertEquals("UAL456", reading.callsign)
  }
}
