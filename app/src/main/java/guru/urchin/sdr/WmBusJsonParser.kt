package guru.urchin.sdr

import org.json.JSONObject

/**
 * Parses JSON lines from the wmbus_json_bridge TCP stream.
 *
 * Expected format:
 * ```
 * {"type":"wmbus","manufacturer":"KAM","serial":"12345678","version":1,"device_type":"water","rssi":-65,"freq":868.95}
 * ```
 */
object WmBusJsonParser {
  private const val MAX_JSON_LENGTH = 10_000
  private val MANUFACTURER_RE = Regex("[A-Z]{3}")
  private val SERIAL_RE = Regex("[0-9A-Fa-f]{8}")

  fun parse(jsonLine: String): SdrReading.WmBus? {
    if (jsonLine.length > MAX_JSON_LENGTH) return null
    return try {
      val json = JSONObject(jsonLine)
      if (json.optString("type") != "wmbus") return null

      val manufacturer = json.optStringOrNull("manufacturer")
        ?.takeIf { it.matches(MANUFACTURER_RE) } ?: return null
      val serialNumber = json.optStringOrNull("serial")
        ?.takeIf { it.matches(SERIAL_RE) } ?: return null

      SdrReading.WmBus(
        manufacturer = manufacturer,
        serialNumber = serialNumber,
        meterVersion = json.optIntOrNull("version"),
        meterType = json.optStringOrNull("device_type"),
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
