package guru.urchin.sdr

import org.json.JSONObject

/**
 * Parses JSON lines from the sidewalk_json_bridge TCP stream.
 *
 * Expected format:
 * ```
 * {"type":"sidewalk","smsn":"0A1B2C3D4E","frame_type":"data","rssi":-80,"freq":903.0}
 * ```
 */
object SidewalkJsonParser {
  private const val MAX_JSON_LENGTH = 10_000
  private val SMSN_RE = Regex("[0-9A-Fa-f]{10}")

  fun parse(jsonLine: String): SdrReading.Sidewalk? {
    if (jsonLine.length > MAX_JSON_LENGTH) return null
    return try {
      val json = JSONObject(jsonLine)
      if (json.optString("type") != "sidewalk") return null

      val smsn = json.optStringOrNull("smsn")
        ?.takeIf { it.matches(SMSN_RE) } ?: return null

      SdrReading.Sidewalk(
        smsn = smsn,
        frameType = json.optStringOrNull("frame_type"),
        rssi = json.optDoubleOrNull("rssi"),
        snr = json.optDoubleOrNull("snr"),
        frequencyMhz = json.optDoubleOrNull("freq"),
        rawJson = jsonLine
      )
    } catch (_: Exception) {
      null
    }
  }
}
