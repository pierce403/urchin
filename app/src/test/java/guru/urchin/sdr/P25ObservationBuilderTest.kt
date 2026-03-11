package guru.urchin.sdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P25ObservationBuilderTest {
  @Test
  fun `builds display name with unit ID and talk group`() {
    val input = P25ObservationBuilder.build(sampleReading())
    assertEquals("Unit 12345 on TG 100", input.name)
  }

  @Test
  fun `builds display name without talk group`() {
    val reading = sampleReading(talkGroupId = null)
    val input = P25ObservationBuilder.build(reading)
    assertEquals("Unit 12345", input.name)
  }

  @Test
  fun `sets SDR source and transport`() {
    val input = P25ObservationBuilder.build(sampleReading())
    assertEquals("SDR", input.source)
    assertEquals("sdr", input.transport)
  }

  @Test
  fun `sets radio_unit classification with high confidence`() {
    val input = P25ObservationBuilder.build(sampleReading())
    assertEquals("radio_unit", input.classificationCategory)
    assertEquals("P25 radio unit", input.classificationLabel)
    assertEquals("high", input.classificationConfidence)
  }

  @Test
  fun `sets protocol type to p25`() {
    val input = P25ObservationBuilder.build(sampleReading())
    assertEquals("p25", input.protocolType)
  }

  @Test
  fun `populates all P25 fields`() {
    val input = P25ObservationBuilder.build(sampleReading())
    assertEquals("12345", input.p25UnitId)
    assertEquals("293", input.p25Nac)
    assertEquals("BEE00", input.p25Wacn)
    assertEquals("001", input.p25SystemId)
    assertEquals("100", input.p25TalkGroupId)
  }

  @Test
  fun `handles null optional fields`() {
    val reading = SdrReading.P25(
      unitId = "99999",
      nac = null, wacn = null, systemId = null, talkGroupId = null,
      rssi = null, snr = null, frequencyMhz = null, rawJson = "{}"
    )
    val input = P25ObservationBuilder.build(reading)
    assertEquals("99999", input.p25UnitId)
    assertNull(input.p25Nac)
    assertNull(input.p25Wacn)
    assertNull(input.p25SystemId)
    assertNull(input.p25TalkGroupId)
  }

  @Test
  fun `uses rssi from reading or defaults to -100`() {
    val withRssi = P25ObservationBuilder.build(sampleReading(rssi = -20.0))
    assertEquals(-20, withRssi.rssi)

    val withoutRssi = P25ObservationBuilder.build(sampleReading(rssi = null))
    assertEquals(-100, withoutRssi.rssi)
  }

  @Test
  fun `includes classification evidence`() {
    val input = P25ObservationBuilder.build(sampleReading())
    assertTrue(input.classificationEvidence.any { it.contains("sdr") })
    assertTrue(input.classificationEvidence.any { it.contains("p25") })
  }

  @Test
  fun `has no address`() {
    val input = P25ObservationBuilder.build(sampleReading())
    assertNull(input.address)
  }

  private fun sampleReading(
    rssi: Double? = -15.0,
    talkGroupId: String? = "100"
  ) = SdrReading.P25(
    unitId = "12345",
    nac = "293",
    wacn = "BEE00",
    systemId = "001",
    talkGroupId = talkGroupId,
    rssi = rssi,
    snr = null,
    frequencyMhz = 851.0,
    rawJson = "{}"
  )
}
