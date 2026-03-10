package guru.urchin.alerts

import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import guru.urchin.data.AlertRuleEntity

enum class AlertRuleType(
  val storageValue: String,
  val label: String,
  val inputHint: String
) {
  NAME("name", "Name", "Device name, model, or callsign"),
  ID("id", "Sensor ID", "TPMS ID, ICAO hex, CAP code, or unit ID"),
  PROTOCOL("protocol", "Protocol", "tpms, pocsag, adsb, or p25");

  companion object {
    fun fromStorageValue(value: String?): AlertRuleType? {
      return entries.firstOrNull { it.storageValue == value }
    }
  }
}

enum class AlertEmojiPreset(
  val emoji: String,
  val label: String
) {
  EYES("\uD83D\uDC40", "Eyes"),
  BELL("\uD83D\uDD14", "Bell"),
  WARNING("\u26A0\uFE0F", "Warning"),
  RADAR("\uD83D\uDCE1", "Radar"),
  SIREN("\uD83D\uDEA8", "Siren"),
  NINJA("\uD83E\uDD77", "Ninja");

  companion object {
    fun fromEmoji(value: String?): AlertEmojiPreset? {
      return entries.firstOrNull { it.emoji == value }
    }
  }
}

enum class AlertSoundPreset(
  val storageValue: String,
  val label: String,
  val stopAfterMs: Long
) {
  PING("ping", "Ping", 900),
  CHIME("chime", "Chime", 1500),
  ALARM("alarm", "Alarm", 2400);

  fun resolveUri(): Uri {
    val primary = when (this) {
      PING -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
      CHIME -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
      ALARM -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    }
    return primary ?: Settings.System.DEFAULT_NOTIFICATION_URI
  }

  companion object {
    fun fromStorageValue(value: String?): AlertSoundPreset? {
      return entries.firstOrNull { it.storageValue == value }
    }
  }
}

data class AlertRuleDraft(
  val type: AlertRuleType,
  val input: String,
  val emoji: AlertEmojiPreset,
  val soundPreset: AlertSoundPreset
)

data class NormalizedAlertRuleInput(
  val pattern: String,
  val displayValue: String
)

data class AlertObservation(
  val deviceKey: String,
  val displayName: String?,
  val sensorId: String?,
  val protocolType: String?,
  val source: String
)

data class AlertMatch(
  val rule: AlertRuleEntity,
  val reason: String
)

object AlertRuleInputNormalizer {
  private val KNOWN_PROTOCOLS = setOf("tpms", "pocsag", "adsb", "p25")

  fun normalize(type: AlertRuleType, rawInput: String): NormalizedAlertRuleInput? {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) {
      return null
    }

    return when (type) {
      AlertRuleType.NAME -> {
        NormalizedAlertRuleInput(pattern = trimmed.lowercase(), displayValue = trimmed)
      }
      AlertRuleType.ID -> {
        val upper = trimmed.uppercase()
        NormalizedAlertRuleInput(pattern = upper, displayValue = upper)
      }
      AlertRuleType.PROTOCOL -> {
        val lower = trimmed.lowercase()
        if (lower !in KNOWN_PROTOCOLS) return null
        NormalizedAlertRuleInput(pattern = lower, displayValue = lower)
      }
    }
  }
}

object DeviceAlertMatcher {
  fun findMatches(
    rules: List<AlertRuleEntity>,
    observation: AlertObservation
  ): List<AlertMatch> {
    if (rules.isEmpty()) {
      return emptyList()
    }

    return rules.filter { it.enabled }.mapNotNull { rule ->
      val type = AlertRuleType.fromStorageValue(rule.matchType) ?: return@mapNotNull null
      val matched = when (type) {
        AlertRuleType.NAME -> {
          val name = observation.displayName?.lowercase()
          name != null && name.contains(rule.matchPattern)
        }
        AlertRuleType.ID -> {
          observation.sensorId?.uppercase() == rule.matchPattern
        }
        AlertRuleType.PROTOCOL -> {
          observation.protocolType?.lowercase() == rule.matchPattern
        }
      }

      if (!matched) {
        null
      } else {
        AlertMatch(rule = rule, reason = "Matched ${type.label} ${rule.displayValue}")
      }
    }
  }
}

fun AlertRuleEntity.displayTitle(): String {
  val typeLabel = AlertRuleType.fromStorageValue(matchType)?.label ?: matchType
  return "$emoji $typeLabel $displayValue"
}

fun AlertRuleEntity.displayMeta(): String {
  val soundLabel = AlertSoundPreset.fromStorageValue(soundPreset)?.label ?: soundPreset
  val stateLabel = if (enabled) "Enabled" else "Disabled"
  return "Sound: $soundLabel \u2022 $stateLabel"
}
