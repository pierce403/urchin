package guru.urchin.alerts

import android.content.Context
import guru.urchin.data.AlertRuleEntity
import guru.urchin.data.AlertRuleRepository

data class DefaultAlertRule(
  val type: AlertRuleType,
  val rawInput: String,
  val emoji: String,
  val soundPreset: AlertSoundPreset
)

object DefaultAlertRules {
  private val rules = listOf(
    DefaultAlertRule(
      type = AlertRuleType.PROTOCOL,
      rawInput = "tpms",
      emoji = "\uD83D\uDCE1",
      soundPreset = AlertSoundPreset.PING
    ),
    DefaultAlertRule(
      type = AlertRuleType.PROTOCOL,
      rawInput = "adsb",
      emoji = "\u2708\uFE0F",
      soundPreset = AlertSoundPreset.CHIME
    )
  )

  fun buildEntities(nowMs: Long = System.currentTimeMillis()): List<AlertRuleEntity> {
    return rules.mapIndexedNotNull { index, rule ->
      val normalized = AlertRuleInputNormalizer.normalize(rule.type, rule.rawInput)
        ?: return@mapIndexedNotNull null
      AlertRuleEntity(
        matchType = rule.type.storageValue,
        matchPattern = normalized.pattern,
        displayValue = normalized.displayValue,
        emoji = rule.emoji,
        soundPreset = rule.soundPreset.storageValue,
        enabled = true,
        createdAt = nowMs + index
      )
    }
  }
}

object DefaultAlertSeeder {
  private const val PREFS_NAME = "urchin_alert_defaults"
  private const val KEY_DEFAULTS_SEEDED_V1 = "defaults_seeded_v1"

  suspend fun seedIfNeeded(
    context: Context,
    repository: AlertRuleRepository
  ) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    if (prefs.getBoolean(KEY_DEFAULTS_SEEDED_V1, false)) {
      return
    }

    val existingRuleKeys = repository.getRules()
      .map { it.matchType to it.matchPattern }
      .toSet()

    DefaultAlertRules.buildEntities().forEach { rule ->
      val key = rule.matchType to rule.matchPattern
      if (key !in existingRuleKeys) {
        repository.addRule(rule)
      }
    }

    prefs.edit().putBoolean(KEY_DEFAULTS_SEEDED_V1, true).apply()
  }
}
