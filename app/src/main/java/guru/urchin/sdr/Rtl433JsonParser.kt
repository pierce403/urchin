package guru.urchin.sdr

import org.json.JSONObject

object Rtl433JsonParser {
  private val POCSAG_MODELS = setOf("flex", "pocsag")

  fun parse(jsonLine: String): SdrReading? {
    return try {
      val json = JSONObject(jsonLine)
      val type = json.optString("type", "").lowercase()
      val model = json.optString("model", "").lowercase()

      when {
        type == "tpms" -> parseTpms(json, jsonLine)
        POCSAG_MODELS.any { model.contains(it) } -> parsePocsag(json, jsonLine)
        else -> null
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun parseTpms(json: JSONObject, rawJson: String): SdrReading.Tpms {
    return SdrReading.Tpms(
      model = json.optString("model", "Unknown"),
      sensorId = json.optString("id", "0x00000000"),
      pressureKpa = json.optDoubleOrNull("pressure_kPa")
        ?: json.optDoubleOrNull("pressure_PSI")?.let { it * 6.89476 }
        ?: json.optDoubleOrNull("pressure_bar")?.let { it * 100.0 },
      temperatureC = json.optDoubleOrNull("temperature_C"),
      batteryOk = json.optBooleanOrNull("battery_ok"),
      status = json.optIntOrNull("status"),
      rssi = json.optDoubleOrNull("rssi"),
      snr = json.optDoubleOrNull("snr"),
      frequencyMhz = json.optDoubleOrNull("freq"),
      rawJson = rawJson
    )
  }

  private fun parsePocsag(json: JSONObject, rawJson: String): SdrReading.Pocsag {
    return SdrReading.Pocsag(
      address = json.optString("address", "0"),
      functionCode = json.optInt("function", 0),
      message = json.optStringOrNull("alpha") ?: json.optStringOrNull("message"),
      model = json.optString("model", "POCSAG"),
      rssi = json.optDoubleOrNull("rssi"),
      snr = json.optDoubleOrNull("snr"),
      frequencyMhz = json.optDoubleOrNull("freq"),
      rawJson = rawJson
    )
  }

}
