package guru.urchin.scan

import java.security.MessageDigest

/**
 * Generates a stable device identity from an [ObservationInput].
 *
 * Checks protocol-specific identifiers in priority order:
 * ADS-B (ICAO) → POCSAG (CAP+function) → P25 (WACN+system+unit) → TPMS (model+sensor)
 * → address → name → volatile fallback.
 *
 * The resulting token is SHA-256 hashed to a 64-character hex string used as the
 * primary key in the Room database.
 */
object DeviceKey {
  fun from(input: ObservationInput): String {
    val token = when {
      !input.adsbIcao.isNullOrBlank() ->
        "adsb:${input.adsbIcao.uppercase()}"
      !input.pocsagCapCode.isNullOrBlank() ->
        "pocsag:${input.pocsagCapCode}:${input.pocsagFunctionCode ?: 0}"
      !input.p25UnitId.isNullOrBlank() ->
        "p25:${input.p25Wacn.orEmpty()}:${input.p25SystemId.orEmpty()}:${input.p25UnitId}"
      !input.tpmsSensorId.isNullOrBlank() ->
        "tpms:${input.tpmsModel.orEmpty().lowercase()}:${input.tpmsSensorId.uppercase()}"
      !input.normalizedAddress.isNullOrBlank() -> "a:${input.normalizedAddress}"
      !input.address.isNullOrBlank() -> "a:${input.address}"
      !input.name.isNullOrBlank() -> "n:${input.name}"
      else -> buildFallbackToken(input)
    }

    return sha256(token)
  }

  private fun buildFallbackToken(input: ObservationInput): String {
    return buildString {
      append("volatile:")
      append(input.source.lowercase())
      append(':')
      append(input.timestamp)
      append(':')
      append(input.rssi)
      append(':')
      append(input.classificationFingerprint ?: "none")
      append(':')
      append(input.serviceUuids.size)
      append(':')
      append(input.manufacturerData.size)
    }
  }

  private val HEX = "0123456789abcdef".toCharArray()

  private fun sha256(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    val hex = CharArray(bytes.size * 2)
    for (i in bytes.indices) {
      val v = bytes[i].toInt() and 0xFF
      hex[i * 2] = HEX[v ushr 4]
      hex[i * 2 + 1] = HEX[v and 0x0F]
    }
    return String(hex)
  }
}
