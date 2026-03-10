package guru.urchin.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAlertRulesTest {
  @Test
  fun `builds expected seeded default alerts`() {
    val rules = DefaultAlertRules.buildEntities(nowMs = 100L)

    assertEquals(2, rules.size)
    assertTrue(rules.any { it.matchPattern == "tpms" && it.soundPreset == AlertSoundPreset.PING.storageValue })
    assertTrue(rules.any { it.matchPattern == "adsb" && it.soundPreset == AlertSoundPreset.CHIME.storageValue })
  }

  @Test
  fun `all default rules are protocol type`() {
    val rules = DefaultAlertRules.buildEntities()

    assertTrue(rules.all { it.matchType == AlertRuleType.PROTOCOL.storageValue })
  }

  @Test
  fun `all default rules are enabled`() {
    val rules = DefaultAlertRules.buildEntities()

    assertTrue(rules.all { it.enabled })
  }
}
