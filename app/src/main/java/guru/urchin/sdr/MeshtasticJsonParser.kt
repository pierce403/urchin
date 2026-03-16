package guru.urchin.sdr

import android.util.Base64
import android.util.Log
import org.json.JSONObject

/**
 * Parses JSON lines from the lora_json_bridge TCP stream tagged as `"type":"meshtastic"`.
 *
 * Meshtastic header (from base64 `data` field):
 * - Bytes 0-3: destination NodeNum (little-endian)
 * - Bytes 4-7: sender NodeNum (little-endian)
 * - Byte 8: packet ID
 * - Byte 9: flags (hop limit in bits 0-2, hop start in bits 3-5)
 */
object MeshtasticJsonParser {
  private const val MAX_JSON_LENGTH = 10_000

  fun parse(jsonLine: String): SdrReading.Meshtastic? {
    if (jsonLine.length > MAX_JSON_LENGTH) return null
    return try {
      val json = JSONObject(jsonLine)
      if (json.optString("type") != "meshtastic") return null

      val data = json.optStringOrNull("data") ?: return null
      val bytes = try {
        Base64.decode(data, Base64.DEFAULT)
      } catch (e: Exception) {
        Log.d("MeshtasticJsonParser", "Failed to decode base64 payload: ${e.message}")
        return null
      }
      if (bytes.size < 10) return null

      val destId = formatLeU32(bytes, 0)
      val nodeId = formatLeU32(bytes, 4)
      val packetId = bytes[8].toInt() and 0xFF
      val flags = bytes[9].toInt() and 0xFF
      val hopLimit = flags and 0x07
      val hopStart = (flags shr 3) and 0x07

      SdrReading.Meshtastic(
        nodeId = nodeId,
        destId = destId,
        packetId = packetId,
        hopLimit = hopLimit,
        hopStart = hopStart,
        channelHash = json.optStringOrNull("chan")?.let { "ch$it" },
        rssi = json.optDoubleOrNull("rssi"),
        snr = json.optDoubleOrNull("lsnr"),
        frequencyMhz = json.optDoubleOrNull("freq"),
        rawJson = jsonLine
      )
    } catch (_: Exception) {
      null
    }
  }

  private fun formatLeU32(bytes: ByteArray, offset: Int): String {
    if (offset + 3 >= bytes.size) return "00000000"
    return String.format(
      "%02X%02X%02X%02X",
      bytes[offset + 3].toInt() and 0xFF,
      bytes[offset + 2].toInt() and 0xFF,
      bytes[offset + 1].toInt() and 0xFF,
      bytes[offset].toInt() and 0xFF
    )
  }
}
