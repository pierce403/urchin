package guru.urchin.sdr

import org.json.JSONObject

object AdsbJsonParser {
  fun parse(jsonLine: String, frequencyMhz: Double = 1090.0): SdrReading.Adsb? {
    return try {
      val json = JSONObject(jsonLine)
      val icao = json.optStringOrNull("hex") ?: return null

      SdrReading.Adsb(
        icao = icao.uppercase().trim(),
        callsign = json.optStringOrNull("flight")?.trim(),
        altitude = json.optIntOrNull("alt_baro") ?: json.optIntOrNull("alt_geom"),
        speed = json.optDoubleOrNull("gs") ?: json.optDoubleOrNull("tas"),
        heading = json.optDoubleOrNull("track") ?: json.optDoubleOrNull("mag_heading"),
        lat = json.optDoubleOrNull("lat"),
        lon = json.optDoubleOrNull("lon"),
        squawk = json.optStringOrNull("squawk"),
        rssi = json.optDoubleOrNull("rssi"),
        snr = null,
        frequencyMhz = frequencyMhz,
        rawJson = jsonLine
      )
    } catch (_: Exception) {
      null
    }
  }

  fun parseAircraftArray(jsonText: String, frequencyMhz: Double = 1090.0): List<SdrReading.Adsb> {
    return try {
      val json = JSONObject(jsonText)
      val aircraft = json.optJSONArray("aircraft") ?: return emptyList()
      (0 until aircraft.length()).mapNotNull { i ->
        val obj = aircraft.optJSONObject(i) ?: return@mapNotNull null
        val icao = obj.optStringOrNull("hex") ?: return@mapNotNull null
        SdrReading.Adsb(
          icao = icao.uppercase().trim(),
          callsign = obj.optStringOrNull("flight")?.trim(),
          altitude = obj.optIntOrNull("alt_baro") ?: obj.optIntOrNull("alt_geom"),
          speed = obj.optDoubleOrNull("gs"),
          heading = obj.optDoubleOrNull("track"),
          lat = obj.optDoubleOrNull("lat"),
          lon = obj.optDoubleOrNull("lon"),
          squawk = obj.optStringOrNull("squawk"),
          rssi = obj.optDoubleOrNull("rssi"),
          snr = null,
          frequencyMhz = frequencyMhz,
          rawJson = obj.toString()
        )
      }
    } catch (_: Exception) {
      emptyList()
    }
  }

}
