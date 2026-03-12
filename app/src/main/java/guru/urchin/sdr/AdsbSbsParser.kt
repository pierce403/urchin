package guru.urchin.sdr

object AdsbSbsParser {
  fun parse(line: String): SdrReading.Adsb? {
    val fields = line.split(',')
    if (fields.size < 22) return null
    if (!fields[0].equals("MSG", ignoreCase = true)) return null

    val icao = fields[4].trim().uppercase()
    if (icao.isBlank()) return null

    val callsign = fields[10].trim().ifBlank { null }
    val altitude = fields[11].trim().toIntOrNull()
    val speed = fields[12].trim().toDoubleOrNull()
    val heading = fields[13].trim().toDoubleOrNull()
    val lat = fields[14].trim().toDoubleOrNull()
    val lon = fields[15].trim().toDoubleOrNull()
    val squawk = fields[17].trim().ifBlank { null }

    return SdrReading.Adsb(
      icao = icao,
      callsign = callsign,
      altitude = altitude,
      speed = speed,
      heading = heading,
      lat = lat,
      lon = lon,
      squawk = squawk,
      rssi = null,
      snr = null,
      frequencyMhz = 1090.0,
      rawJson = line
    )
  }
}
