package guru.urchin.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAlertRulesTest {
  @Test
  fun `builds expected seeded default alerts`() {
    val rules = DefaultAlertRules.buildEntities(nowMs = 100L)

    assertEquals(3, rules.size)
    assertTrue(rules.any { it.matchPattern == "tpms" && it.matchType == AlertRuleType.PROTOCOL.storageValue })
    assertTrue(rules.any { it.matchPattern == "adsb" && it.matchType == AlertRuleType.PROTOCOL.storageValue })
    assertTrue(rules.any { it.matchType == AlertRuleType.SURVEILLANCE.storageValue })
  }

  @Test
  fun `all default rules use known alert types`() {
    val rules = DefaultAlertRules.buildEntities()

    assertTrue(rules.all { AlertRuleType.fromStorageValue(it.matchType) != null })
  }

  @Test
  fun `all default rules are enabled`() {
    val rules = DefaultAlertRules.buildEntities()

    assertTrue(rules.all { it.enabled })
  }
}
