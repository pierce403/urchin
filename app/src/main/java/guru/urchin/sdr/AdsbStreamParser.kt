package guru.urchin.sdr

object AdsbStreamParser {
  fun parse(line: String): SdrReading.Adsb? =
    AdsbJsonParser.parse(line) ?: AdsbSbsParser.parse(line)
}
