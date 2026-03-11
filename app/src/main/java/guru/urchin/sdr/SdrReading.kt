package guru.urchin.sdr

/**
 * Parsed output from an SDR source. Each variant corresponds to one radio protocol
 * and carries the protocol-specific fields extracted by the relevant JSON parser.
 * Common fields ([rssi], [snr], [frequencyMhz], [rawJson]) are shared across all variants.
 */
sealed class SdrReading {
  abstract val rssi: Double?
  abstract val snr: Double?
  abstract val frequencyMhz: Double?
  abstract val rawJson: String

  data class Tpms(
    val model: String,
    val sensorId: String,
    val pressureKpa: Double?,
    val temperatureC: Double?,
    val batteryOk: Boolean?,
    val status: Int?,
    override val rssi: Double?,
    override val snr: Double?,
    override val frequencyMhz: Double?,
    override val rawJson: String
  ) : SdrReading()

  data class Pocsag(
    val address: String,
    val functionCode: Int,
    val message: String?,
    val model: String,
    override val rssi: Double?,
    override val snr: Double?,
    override val frequencyMhz: Double?,
    override val rawJson: String
  ) : SdrReading()

  data class Adsb(
    val icao: String,
    val callsign: String?,
    val altitude: Int?,
    val speed: Double?,
    val heading: Double?,
    val lat: Double?,
    val lon: Double?,
    val squawk: String?,
    override val rssi: Double?,
    override val snr: Double?,
    override val frequencyMhz: Double?,
    override val rawJson: String
  ) : SdrReading()

  data class P25(
    val unitId: String,
    val nac: String?,
    val wacn: String?,
    val systemId: String?,
    val talkGroupId: String?,
    override val rssi: Double?,
    override val snr: Double?,
    override val frequencyMhz: Double?,
    override val rawJson: String
  ) : SdrReading()
}
