package guru.urchin.sdr

import org.json.JSONObject

/**
 * Parses DMR (Digital Mobile Radio) JSON from a network bridge decoder.
 * Extracts radio ID, color code, slot, talkgroup, data type, and encryption status.
 */
object DmrJsonParser {
  fun parse(line: String): SdrReading.Dmr? {
    if (line.length > 10_000) return null
    return try {
      val json = JSONObject(line)
      if (json.optString("type", "") != "dmr") return null

      val radioId = json.optString("radio_id", "").takeIf { it.isNotEmpty() } ?: return null

      SdrReading.Dmr(
        radioId = radioId,
        colorCode = json.optIntOrNull("color_code"),
        slot = json.optIntOrNull("slot"),
        talkGroup = json.optString("talkgroup", "").takeIf { it.isNotEmpty() },
        dataType = json.optString("data_type", "").takeIf { it.isNotEmpty() },
        encrypted = json.optBooleanOrNull("encrypted"),
        rssi = json.optDoubleOrNull("rssi"),
        snr = json.optDoubleOrNull("snr"),
        frequencyMhz = json.optDoubleOrNull("freq"),
        rawJson = line
      )
    } catch (_: Exception) {
      null
    }
  }

  private fun JSONObject.optIntOrNull(key: String): Int? =
    if (has(key)) optInt(key) else null

  private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key)) optDouble(key).takeIf { !it.isNaN() } else null

  private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
    if (has(key)) optBoolean(key) else null
}
