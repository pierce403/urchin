package guru.urchin.sdr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PocsagObservationBuilderTest {
  @Test
  fun `builds observation with correct display name`() {
    val input = PocsagObservationBuilder.build(sampleReading())
    assertEquals("Pager 1234567", input.name)
  }

  @Test
  fun `sets SDR source and transport`() {
    val input = PocsagObservationBuilder.build(sampleReading())
    assertEquals("SDR", input.source)
    assertEquals("sdr", input.transport)
  }

  @Test
  fun `sets pager classification with high confidence`() {
    val input = PocsagObservationBuilder.build(sampleReading())
    assertEquals("pager", input.classificationCategory)
    assertEquals("POCSAG pager", input.classificationLabel)
    assertEquals("high", input.classificationConfidence)
  }

  @Test
  fun `sets protocol type to pocsag`() {
    val input = PocsagObservationBuilder.build(sampleReading())
    assertEquals("pocsag", input.protocolType)
  }

  @Test
  fun `populates POCSAG fields`() {
    val reading = sampleReading()
    val input = PocsagObservationBuilder.build(reading)
    assertEquals("1234567", input.pocsagCapCode)
    assertEquals(1, input.pocsagFunctionCode)
    assertEquals("Test dispatch message", input.pocsagMessage)
  }

  @Test
  fun `handles null message`() {
    val reading = sampleReading(message = null)
    val input = PocsagObservationBuilder.build(reading)
    assertEquals("1234567", input.pocsagCapCode)
    assertNull(input.pocsagMessage)
  }

  @Test
  fun `uses rssi from reading or defaults to -100`() {
    val withRssi = PocsagObservationBuilder.build(sampleReading(rssi = -15.0))
    assertEquals(-15, withRssi.rssi)

    val withoutRssi = PocsagObservationBuilder.build(sampleReading(rssi = null))
    assertEquals(-100, withoutRssi.rssi)
  }

  @Test
  fun `includes classification evidence`() {
    val input = PocsagObservationBuilder.build(sampleReading())
    assertTrue(input.classificationEvidence.any { it.contains("sdr") })
    assertTrue(input.classificationEvidence.any { it.contains("Flex") })
  }

  @Test
  fun `sets vendor to model name`() {
    val input = PocsagObservationBuilder.build(sampleReading())
    assertEquals("Flex", input.vendorName)
    assertEquals("rtl_433 protocol", input.vendorSource)
  }

  @Test
  fun `has no address`() {
    val input = PocsagObservationBuilder.build(sampleReading())
    assertNull(input.address)
  }

  private fun sampleReading(
    rssi: Double? = -12.0,
    message: String? = "Test dispatch message"
  ) = SdrReading.Pocsag(
    address = "1234567",
    functionCode = 1,
    message = message,
    model = "Flex",
    rssi = rssi,
    snr = null,
    frequencyMhz = 929.6125,
    rawJson = "{}"
  )
}
