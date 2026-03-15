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
    val encryptionAlgorithm: String? = null,
    val encryptionKeyId: String? = null,
    val emergency: Boolean? = null,
    val voiceOrData: String? = null,
    override val rssi: Double?,
    override val snr: Double?,
    override val frequencyMhz: Double?,
    override val rawJson: String
  ) : SdrReading()

  data class LoRaWan(
    val devAddr: String,
    val spreadingFactor: String?,
    val codingRate: String?,
    val payloadSize: Int?,
    val crcOk: Boolean?,
    val fPort: Int? = null,
    val frameCounter: Int? = null,
    val mType: String? = null,
    override val rssi: Double?,
    override val snr: Double?,
    override val frequencyMhz: Double?,
    override val rawJson: String
  ) : SdrReading()

  data class Meshtastic(
    val nodeId: String,
    val destId: String?,
    val packetId: Int?,
    val hopLimit: Int?,
    val hopStart: Int?,
    val channelHash: String?,
    val portNum: String? = null,
    val payloadText: String? = null,
    override val rssi: Double?,
    override val snr: Double?,
    override val frequencyMhz: Double?,
    override val rawJson: String
  ) : SdrReading()

  data class WmBus(
    val manufacturer: String,
    val serialNumber: String,
    val meterVersion: Int?,
    val meterType: String?,
    override val rssi: Double?,
    override val snr: Double?,
    override val frequencyMhz: Double?,
    override val rawJson: String
  ) : SdrReading()

  data class Zwave(
    val homeId: String,
    val nodeId: Int,
    val frameType: String?,
    val commandClass: String? = null,
    val nodeRole: String? = null,
    val securityLevel: String? = null,
    override val rssi: Double?,
    override val snr: Double?,
    override val frequencyMhz: Double?,
    override val rawJson: String
  ) : SdrReading()

  data class Sidewalk(
    val smsn: String,
    val frameType: String?,
    override val rssi: Double?,
    override val snr: Double?,
    override val frequencyMhz: Double?,
    override val rawJson: String
  ) : SdrReading()

  data class Dmr(
    val radioId: String,
    val colorCode: Int?,
    val slot: Int?,
    val talkGroup: String?,
    val dataType: String? = null,
    val encrypted: Boolean? = null,
    override val rssi: Double?,
    override val snr: Double?,
    override val frequencyMhz: Double?,
    override val rawJson: String
  ) : SdrReading()

  data class Nxdn(
    val unitId: String,
    val ran: Int?,
    val talkGroup: String?,
    val messageType: String? = null,
    override val rssi: Double?,
    override val snr: Double?,
    override val frequencyMhz: Double?,
    override val rawJson: String
  ) : SdrReading()
}
