package guru.urchin.sdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsbObservationBuilderTest {
  @Test
  fun `builds display name with callsign and ICAO`() {
    val input = AdsbObservationBuilder.build(sampleReading())
    assertEquals("Aircraft AAL123 (A00001)", input.name)
  }

  @Test
  fun `builds display name with ICAO only when no callsign`() {
    val reading = sampleReading(callsign = null)
    val input = AdsbObservationBuilder.build(reading)
    assertEquals("Aircraft A00001", input.name)
  }

  @Test
  fun `builds display name with ICAO only when blank callsign`() {
    val reading = sampleReading(callsign = "  ")
    val input = AdsbObservationBuilder.build(reading)
    assertEquals("Aircraft A00001", input.name)
  }

  @Test
  fun `sets SDR source and transport`() {
    val input = AdsbObservationBuilder.build(sampleReading())
    assertEquals("SDR", input.source)
    assertEquals("sdr", input.transport)
  }

  @Test
  fun `sets aircraft classification with high confidence`() {
    val input = AdsbObservationBuilder.build(sampleReading())
    assertEquals("aircraft", input.classificationCategory)
    assertEquals("ADS-B transponder", input.classificationLabel)
    assertEquals("high", input.classificationConfidence)
  }

  @Test
  fun `sets protocol type to adsb`() {
    val input = AdsbObservationBuilder.build(sampleReading())
    assertEquals("adsb", input.protocolType)
  }

  @Test
  fun `populates all ADS-B fields`() {
    val reading = sampleReading()
    val input = AdsbObservationBuilder.build(reading)
    assertEquals("A00001", input.adsbIcao)
    assertEquals("AAL123", input.adsbCallsign)
    assertEquals(35000, input.adsbAltitude)
    assertEquals(450.5, input.adsbSpeed!!, 0.01)
    assertEquals(270.0, input.adsbHeading!!, 0.01)
    assertEquals(40.6413, input.adsbLat!!, 0.0001)
    assertEquals(-73.7781, input.adsbLon!!, 0.0001)
    assertEquals("1200", input.adsbSquawk)
  }

  @Test
  fun `handles null optional fields`() {
    val reading = SdrReading.Adsb(
      icao = "ABCDEF",
      callsign = null,
      altitude = null,
      speed = null,
      heading = null,
      lat = null,
      lon = null,
      squawk = null,
      rssi = null,
      snr = null,
      frequencyMhz = 1090.0,
      rawJson = "{}"
    )
    val input = AdsbObservationBuilder.build(reading)
    assertEquals("ABCDEF", input.adsbIcao)
    assertNull(input.adsbCallsign)
    assertNull(input.adsbAltitude)
    assertNull(input.adsbSpeed)
    assertNull(input.adsbSquawk)
  }

  @Test
  fun `uses rssi from reading or defaults to -100`() {
    val withRssi = AdsbObservationBuilder.build(sampleReading(rssi = -8.5))
    assertEquals(-8, withRssi.rssi)

    val withoutRssi = AdsbObservationBuilder.build(sampleReading(rssi = null))
    assertEquals(-100, withoutRssi.rssi)
  }

  @Test
  fun `includes classification evidence with ICAO`() {
    val input = AdsbObservationBuilder.build(sampleReading())
    assertTrue(input.classificationEvidence.any { it.contains("sdr") })
    assertTrue(input.classificationEvidence.any { it.contains("adsb") })
    assertTrue(input.classificationEvidence.any { it.contains("A00001") })
  }

  @Test
  fun `has no address`() {
    val input = AdsbObservationBuilder.build(sampleReading())
    assertNull(input.address)
  }

  private fun sampleReading(
    callsign: String? = "AAL123",
    rssi: Double? = -8.5
  ) = SdrReading.Adsb(
    icao = "A00001",
    callsign = callsign,
    altitude = 35000,
    speed = 450.5,
    heading = 270.0,
    lat = 40.6413,
    lon = -73.7781,
    squawk = "1200",
    rssi = rssi,
    snr = null,
    frequencyMhz = 1090.0,
    rawJson = "{}"
  )
}
