package guru.urchin.sdr

import org.junit.Assert.assertEquals
import org.junit.Test

class ObservationBuilderRegistryTest {
  @Test
  fun `dispatches TPMS reading to TPMS builder`() {
    val reading = SdrReading.Tpms(
      model = "PMV-107J",
      sensorId = "0x00ABCDEF",
      pressureKpa = 220.5,
      temperatureC = 28.0,
      batteryOk = true,
      status = 0,
      rssi = -12.0,
      snr = 15.0,
      frequencyMhz = 433.92,
      rawJson = "{}"
    )
    val input = ObservationBuilderRegistry.build(reading)
    assertEquals("tpms", input.protocolType)
    assertEquals("tpms_sensor", input.classificationCategory)
    assertEquals("0x00ABCDEF", input.tpmsSensorId)
  }

  @Test
  fun `dispatches POCSAG reading to POCSAG builder`() {
    val reading = SdrReading.Pocsag(
      address = "1234567",
      functionCode = 1,
      message = "Test",
      model = "Flex",
      rssi = -10.0,
      snr = null,
      frequencyMhz = 929.6125,
      rawJson = "{}"
    )
    val input = ObservationBuilderRegistry.build(reading)
    assertEquals("pocsag", input.protocolType)
    assertEquals("pager", input.classificationCategory)
    assertEquals("1234567", input.pocsagCapCode)
  }

  @Test
  fun `dispatches ADS-B reading to ADS-B builder`() {
    val reading = SdrReading.Adsb(
      icao = "A00001",
      callsign = "AAL123",
      altitude = 35000,
      speed = 450.0,
      heading = 270.0,
      lat = 40.64,
      lon = -73.78,
      squawk = "1200",
      rssi = -8.0,
      snr = null,
      frequencyMhz = 1090.0,
      rawJson = "{}"
    )
    val input = ObservationBuilderRegistry.build(reading)
    assertEquals("adsb", input.protocolType)
    assertEquals("aircraft", input.classificationCategory)
    assertEquals("A00001", input.adsbIcao)
  }

  @Test
  fun `dispatches P25 reading to P25 builder`() {
    val reading = SdrReading.P25(
      unitId = "12345",
      nac = "293",
      wacn = "BEE00",
      systemId = "001",
      talkGroupId = "100",
      rssi = -15.0,
      snr = null,
      frequencyMhz = 851.0,
      rawJson = "{}"
    )
    val input = ObservationBuilderRegistry.build(reading)
    assertEquals("p25", input.protocolType)
    assertEquals("radio_unit", input.classificationCategory)
    assertEquals("12345", input.p25UnitId)
  }
}
