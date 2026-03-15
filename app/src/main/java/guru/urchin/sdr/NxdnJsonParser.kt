package guru.urchin.sdr

import org.json.JSONObject

/**
 * Parses NXDN (Kenwood/Icom) JSON from a network bridge decoder.
 * Extracts unit ID, RAN, talkgroup, and message type.
 */
object NxdnJsonParser {
  fun parse(line: String): SdrReading.Nxdn? {
    if (line.length > 10_000) return null
    return try {
      val json = JSONObject(line)
      if (json.optString("type", "") != "nxdn") return null

      val unitId = json.optString("unit_id", "").takeIf { it.isNotEmpty() } ?: return null

      SdrReading.Nxdn(
        unitId = unitId,
        ran = if (json.has("ran")) json.optInt("ran") else null,
        talkGroup = json.optString("talkgroup", "").takeIf { it.isNotEmpty() },
        messageType = json.optString("message_type", "").takeIf { it.isNotEmpty() },
        rssi = if (json.has("rssi")) json.optDouble("rssi").takeIf { !it.isNaN() } else null,
        snr = if (json.has("snr")) json.optDouble("snr").takeIf { !it.isNaN() } else null,
        frequencyMhz = if (json.has("freq")) json.optDouble("freq").takeIf { !it.isNaN() } else null,
        rawJson = line
      )
    } catch (_: Exception) {
      null
    }
  }
}
