package guru.urchin.alerts

import guru.urchin.data.AlertRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRuleEngineTest {

  @Test
  fun `normalize NAME lowercases input`() {
    val normalized = AlertRuleInputNormalizer.normalize(AlertRuleType.NAME, "PMV-107J")

    assertEquals("pmv-107j", normalized?.pattern)
    assertEquals("PMV-107J", normalized?.displayValue)
  }

  @Test
  fun `normalize ID uppercases input`() {
    val normalized = AlertRuleInputNormalizer.normalize(AlertRuleType.ID, "0xabcdef")

    assertEquals("0XABCDEF", normalized?.pattern)
    assertEquals("0XABCDEF", normalized?.displayValue)
  }

  @Test
  fun `normalize PROTOCOL accepts known protocols`() {
    assertEquals("tpms", AlertRuleInputNormalizer.normalize(AlertRuleType.PROTOCOL, "TPMS")?.pattern)
    assertEquals("adsb", AlertRuleInputNormalizer.normalize(AlertRuleType.PROTOCOL, "adsb")?.pattern)
    assertEquals("pocsag", AlertRuleInputNormalizer.normalize(AlertRuleType.PROTOCOL, "Pocsag")?.pattern)
    assertEquals("p25", AlertRuleInputNormalizer.normalize(AlertRuleType.PROTOCOL, "P25")?.pattern)
  }

  @Test
  fun `normalize PROTOCOL rejects unknown protocol`() {
    assertNull(AlertRuleInputNormalizer.normalize(AlertRuleType.PROTOCOL, "lora"))
  }

  @Test
  fun `normalize rejects empty input`() {
    assertNull(AlertRuleInputNormalizer.normalize(AlertRuleType.NAME, ""))
    assertNull(AlertRuleInputNormalizer.normalize(AlertRuleType.NAME, "  "))
  }

  @Test
  fun `NAME rule matches display name substring`() {
    val rule = rule(AlertRuleType.NAME, "pmv-107j", displayValue = "PMV-107J")
    val observation = observation(displayName = "PMV-107J Sensor")

    val matches = DeviceAlertMatcher.findMatches(listOf(rule), observation)

    assertEquals(1, matches.size)
    assertEquals("Matched Name PMV-107J", matches.first().reason)
  }

  @Test
  fun `NAME rule does not match when name is null`() {
    val rule = rule(AlertRuleType.NAME, "test")
    val observation = observation(displayName = null)

    val matches = DeviceAlertMatcher.findMatches(listOf(rule), observation)

    assertTrue(matches.isEmpty())
  }

  @Test
  fun `ID rule matches exact sensor ID`() {
    val rule = rule(AlertRuleType.ID, "0XABCDEF", displayValue = "0XABCDEF")
    val observation = observation(sensorId = "0xABCDEF")

    val matches = DeviceAlertMatcher.findMatches(listOf(rule), observation)

    assertEquals(1, matches.size)
  }

  @Test
  fun `ID rule does not match different ID`() {
    val rule = rule(AlertRuleType.ID, "0XABCDEF", displayValue = "0XABCDEF")
    val observation = observation(sensorId = "0x000001")

    val matches = DeviceAlertMatcher.findMatches(listOf(rule), observation)

    assertTrue(matches.isEmpty())
  }

  @Test
  fun `PROTOCOL rule matches protocol type`() {
    val rule = rule(AlertRuleType.PROTOCOL, "tpms", displayValue = "tpms")
    val observation = observation(protocolType = "tpms")

    val matches = DeviceAlertMatcher.findMatches(listOf(rule), observation)

    assertEquals(1, matches.size)
    assertEquals("Matched Protocol tpms", matches.first().reason)
  }

  @Test
  fun `PROTOCOL rule does not match different protocol`() {
    val rule = rule(AlertRuleType.PROTOCOL, "adsb", displayValue = "adsb")
    val observation = observation(protocolType = "tpms")

    val matches = DeviceAlertMatcher.findMatches(listOf(rule), observation)

    assertTrue(matches.isEmpty())
  }

  @Test
  fun `disabled rules are skipped`() {
    val rule = rule(AlertRuleType.PROTOCOL, "tpms", enabled = false)
    val observation = observation(protocolType = "tpms")

    val matches = DeviceAlertMatcher.findMatches(listOf(rule), observation)

    assertTrue(matches.isEmpty())
  }

  @Test
  fun `empty rules returns empty matches`() {
    val observation = observation()

    val matches = DeviceAlertMatcher.findMatches(emptyList(), observation)

    assertTrue(matches.isEmpty())
  }

  private fun rule(
    type: AlertRuleType,
    pattern: String,
    displayValue: String = pattern,
    enabled: Boolean = true
  ) = AlertRuleEntity(
    id = 1,
    matchType = type.storageValue,
    matchPattern = pattern,
    displayValue = displayValue,
    emoji = "\uD83D\uDCE1",
    soundPreset = AlertSoundPreset.PING.storageValue,
    enabled = enabled,
    createdAt = 1
  )

  private fun observation(
    displayName: String? = "Test Device",
    sensorId: String? = "0x00ABCDEF",
    protocolType: String? = "tpms"
  ) = AlertObservation(
    deviceKey = "test-key",
    displayName = displayName,
    sensorId = sensorId,
    protocolType = protocolType,
    source = "SDR"
  )
}
