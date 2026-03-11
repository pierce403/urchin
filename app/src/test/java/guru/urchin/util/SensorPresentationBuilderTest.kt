package guru.urchin.util

import guru.urchin.data.DeviceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorPresentationBuilderTest {

  private fun device(
    metadataJson: String?,
    displayName: String? = null,
    userCustomName: String? = null
  ) = DeviceEntity(
    deviceKey = "testkey",
    displayName = displayName,
    lastAddress = null,
    firstSeen = 1000L,
    lastSeen = 2000L,
    lastSightingAt = 2000L,
    sightingsCount = 5,
    observationCount = 10,
    lastRssi = -12,
    rssiMin = -20,
    rssiMax = -5,
    rssiAvg = -12.0,
    lastMetadataJson = metadataJson,
    starred = false,
    userCustomName = userCustomName
  )

  // ── TPMS ──

  @Test
  fun `TPMS shows sensor ID in title`() {
    val json = """{"protocolType":"tpms","tpmsSensorId":"0x00ABCDEF","tpmsModel":"Toyota"}"""
    val presentation = SensorPresentationBuilder.build(device(json))
    assertEquals("tpms", presentation.protocolType)
    assertTrue(presentation.title.contains("0x00ABCDEF"))
  }

  @Test
  fun `TPMS shows pressure and temp in summary`() {
    val json = """{"protocolType":"tpms","tpmsSensorId":"0x00ABCDEF","tpmsPressureKpa":220.5,"tpmsTemperatureC":28.0}"""
    val presentation = SensorPresentationBuilder.build(device(json))
    assertTrue(presentation.listSummary.contains("220"))
    assertTrue(presentation.listSummary.contains("28"))
  }

  @Test
  fun `TPMS uses userCustomName when set`() {
    val json = """{"protocolType":"tpms","tpmsSensorId":"0x00ABCDEF"}"""
    val presentation = SensorPresentationBuilder.build(device(json, userCustomName = "Front Left"))
    assertEquals("Front Left", presentation.title)
  }

  // ── POCSAG ──

  @Test
  fun `POCSAG shows CAP code in title`() {
    val json = """{"protocolType":"pocsag","pocsagCapCode":"1234567","pocsagFunctionCode":1}"""
    val presentation = SensorPresentationBuilder.build(device(json))
    assertEquals("pocsag", presentation.protocolType)
    assertTrue(presentation.title.contains("1234567"))
  }

  @Test
  fun `POCSAG shows message in summary`() {
    val json = """{"protocolType":"pocsag","pocsagCapCode":"1234567","pocsagMessage":"STRUCTURE FIRE AT 456 OAK AVE"}"""
    val presentation = SensorPresentationBuilder.build(device(json))
    assertTrue(presentation.listSummary.contains("STRUCTURE FIRE"))
  }

  @Test
  fun `POCSAG truncates long messages in summary`() {
    val longMsg = "A".repeat(100)
    val json = """{"protocolType":"pocsag","pocsagCapCode":"1234567","pocsagMessage":"$longMsg"}"""
    val presentation = SensorPresentationBuilder.build(device(json))
    assertTrue(presentation.listSummary.contains("..."))
  }

  // ── ADS-B ──

  @Test
  fun `ADS-B shows callsign and ICAO in title`() {
    val json = """{"protocolType":"adsb","adsbIcao":"A00001","adsbCallsign":"AAL123"}"""
    val presentation = SensorPresentationBuilder.build(device(json))
    assertEquals("adsb", presentation.protocolType)
    assertTrue(presentation.title.contains("AAL123"))
    assertTrue(presentation.title.contains("A00001"))
  }

  @Test
  fun `ADS-B shows ICAO only when no callsign`() {
    val json = """{"protocolType":"adsb","adsbIcao":"A00001"}"""
    val presentation = SensorPresentationBuilder.build(device(json))
    assertTrue(presentation.title.contains("A00001"))
  }

  @Test
  fun `ADS-B shows altitude and speed in summary`() {
    val json = """{"protocolType":"adsb","adsbIcao":"A00001","adsbAltitude":35000,"adsbSpeed":450.0}"""
    val presentation = SensorPresentationBuilder.build(device(json))
    assertTrue(presentation.listSummary.contains("35000"))
    assertTrue(presentation.listSummary.contains("450"))
  }

  @Test
  fun `ADS-B includes squawk in detail lines`() {
    val json = """{"protocolType":"adsb","adsbIcao":"A00001","adsbSquawk":"7700"}"""
    val presentation = SensorPresentationBuilder.build(device(json))
    assertTrue(presentation.detailLines.any { it.contains("7700") })
  }

  @Test
  fun `ADS-B includes position in detail lines`() {
    val json = """{"protocolType":"adsb","adsbIcao":"A00001","adsbLat":40.6413,"adsbLon":-73.7781}"""
    val presentation = SensorPresentationBuilder.build(device(json))
    assertTrue(presentation.detailLines.any { it.contains("40.6413") })
  }

  // ── P25 ──

  @Test
  fun `P25 shows unit ID and talk group in title`() {
    val json = """{"protocolType":"p25","p25UnitId":"12345","p25TalkGroupId":"100"}"""
    val presentation = SensorPresentationBuilder.build(device(json))
    assertEquals("p25", presentation.protocolType)
    assertTrue(presentation.title.contains("12345"))
    assertTrue(presentation.title.contains("TG 100"))
  }

  @Test
  fun `P25 shows NAC and WACN in detail lines`() {
    val json = """{"protocolType":"p25","p25UnitId":"12345","p25Nac":"293","p25Wacn":"BEE00"}"""
    val presentation = SensorPresentationBuilder.build(device(json))
    assertTrue(presentation.detailLines.any { it.contains("293") })
    assertTrue(presentation.detailLines.any { it.contains("BEE00") })
  }

  // ── Search ──

  @Test
  fun `search text includes all protocol identifiers`() {
    val json = """{"protocolType":"adsb","adsbIcao":"A00001","adsbCallsign":"AAL123","adsbSquawk":"7700"}"""
    val presentation = SensorPresentationBuilder.build(device(json))
    assertTrue(presentation.searchText.contains("A00001"))
    assertTrue(presentation.searchText.contains("AAL123"))
    assertTrue(presentation.searchText.contains("7700"))
  }

  // ── Default ──

  @Test
  fun `null metadata defaults to TPMS protocol`() {
    val presentation = SensorPresentationBuilder.build(device(null))
    assertEquals("tpms", presentation.protocolType)
  }

  @Test
  fun `unknown protocol type defaults to TPMS`() {
    val json = """{"protocolType":"unknown"}"""
    val presentation = SensorPresentationBuilder.build(device(json))
    assertEquals("tpms", presentation.protocolType)
  }
}
