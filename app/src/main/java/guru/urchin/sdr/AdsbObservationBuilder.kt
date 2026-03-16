package guru.urchin.sdr

import guru.urchin.scan.ObservationInput

object AdsbObservationBuilder {
  fun build(reading: SdrReading.Adsb): ObservationInput {
    val isUat = reading.frequencyMhz != null && reading.frequencyMhz < 1000.0
    val linkLabel = if (isUat) "UAT" else "ADS-B"
    val protocol = if (isUat) "uat" else "adsb"
    val callsignPart = reading.callsign?.takeIf { it.isNotBlank() }
    val displayName = if (callsignPart != null) {
      "Aircraft $callsignPart (${reading.icao})"
    } else {
      "Aircraft ${reading.icao}"
    }
    return ObservationInput(
      name = displayName,
      address = null,
      rssi = reading.rssi?.toInt() ?: -100,
      timestamp = System.currentTimeMillis(),
      serviceUuids = emptyList(),
      manufacturerData = emptyMap(),
      source = "SDR",
      transport = "sdr",
      nameSource = "adsb_transponder",
      protocolType = protocol,
      classificationCategory = "aircraft",
      classificationLabel = "$linkLabel transponder",
      classificationConfidence = "high",
      classificationEvidence = listOf("source:sdr", "protocol:$protocol", "icao:${reading.icao}"),
      adsbIcao = reading.icao,
      adsbCallsign = reading.callsign,
      adsbAltitude = reading.altitude,
      adsbSpeed = reading.speed,
      adsbHeading = reading.heading,
      adsbLat = reading.lat,
      adsbLon = reading.lon,
      adsbSquawk = reading.squawk,
      rawJson = reading.rawJson
    )
  }
}
