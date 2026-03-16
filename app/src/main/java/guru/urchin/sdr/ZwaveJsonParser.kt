package guru.urchin.sdr

import org.json.JSONObject

/**
 * Parses JSON lines from the zwave_json_bridge TCP stream.
 *
 * Expected format:
 * ```
 * {"type":"zwave","home_id":"AABBCCDD","node_id":5,"frame_type":"singlecast","rssi":-72,"freq":908.42}
 * ```
 */
object ZwaveJsonParser {
  private const val MAX_JSON_LENGTH = 10_000
  private val HOME_ID_RE = Regex("[0-9A-Fa-f]{8}")

  fun parse(jsonLine: String): SdrReading.Zwave? {
    if (jsonLine.length > MAX_JSON_LENGTH) return null
    return try {
      val json = JSONObject(jsonLine)
      if (json.optString("type") != "zwave") return null

      val homeId = json.optStringOrNull("home_id")
        ?.takeIf { it.matches(HOME_ID_RE) } ?: return null
      val nodeId = json.optIntOrNull("node_id")
        ?.takeIf { it in 1..232 } ?: return null

      SdrReading.Zwave(
        homeId = homeId,
        nodeId = nodeId,
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
