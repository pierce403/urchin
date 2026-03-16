package guru.urchin.sdr

import android.util.Base64
import android.util.Log
import org.json.JSONObject

/**
 * Parses JSON lines from the lora_json_bridge TCP stream into [SdrReading.LoRaWan] events.
 *
 * Each line is a flattened Semtech rxpk object with a `"type":"lorawan"` prefix:
 * ```
 * {"type":"lorawan","freq":904.3,"rssi":-65,"lsnr":7.2,"datr":"SF7BW125","codr":"4/5","size":23,"stat":1,"data":"<base64>"}
 * ```
 *
 * The DevAddr is extracted from the base64 PHY payload: for uplink data frames
 * (MHDR types 0x40 and 0x80) bytes 1–4 are the 4-byte DevAddr in little-endian order.
 */
object LoRaWanJsonParser {
  private const val MAX_JSON_LENGTH = 10_000

  fun parse(jsonLine: String): SdrReading.LoRaWan? {
    if (jsonLine.length > MAX_JSON_LENGTH) return null
    return try {
      val json = JSONObject(jsonLine)
      if (json.optString("type") != "lorawan") return null

      val devAddr = extractDevAddr(json.optStringOrNull("data")) ?: return null

      SdrReading.LoRaWan(
        devAddr = devAddr,
        spreadingFactor = json.optStringOrNull("datr"),
        codingRate = json.optStringOrNull("codr"),
        payloadSize = json.optIntOrNull("size"),
        crcOk = json.optIntOrNull("stat")?.let { it == 1 },
        rssi = json.optDoubleOrNull("rssi"),
        snr = json.optDoubleOrNull("lsnr"),
        frequencyMhz = json.optDoubleOrNull("freq"),
        rawJson = jsonLine
      )
    } catch (_: Exception) {
      null
    }
  }

  /**
   * Extracts the 4-byte DevAddr from a base64-encoded LoRaWAN PHY payload.
   * Returns an uppercase hex string (e.g. "01ABCDEF") or null if the frame
   * is not a data uplink or the payload is too short.
   */
  private fun extractDevAddr(base64Data: String?): String? {
    if (base64Data.isNullOrBlank()) return null
    val bytes = try {
      Base64.decode(base64Data, Base64.DEFAULT)
    } catch (e: Exception) {
      Log.d("LoRaWanJsonParser", "Failed to decode base64 payload: ${e.message}")
      return null
    }
    if (bytes.size < 5) return null

    val mhdr = bytes[0].toInt() and 0xFF
    val mType = (mhdr shr 5) and 0x07
    // MType 0x02 = Unconfirmed Data Up, 0x04 = Confirmed Data Up
    if (mType != 0x02 && mType != 0x04) return null

    // DevAddr is bytes 1–4 in little-endian order
    return String.format(
      "%02X%02X%02X%02X",
      bytes[4].toInt() and 0xFF,
      bytes[3].toInt() and 0xFF,
      bytes[2].toInt() and 0xFF,
      bytes[1].toInt() and 0xFF
    )
  }
}
