package guru.urchin.sdr

import android.content.Context

object SdrPreferences {
  private const val PREFS_NAME = "urchin_sdr"
  private const val KEY_ENABLED = "sdr_enabled"
  private const val KEY_SOURCE = "sdr_source"
  private const val KEY_FREQUENCY = "sdr_frequency"
  private const val KEY_GAIN = "sdr_gain"
  private const val KEY_NETWORK_HOST = "sdr_network_host"
  private const val KEY_NETWORK_PORT = "sdr_network_port"
  private const val KEY_PROTOCOLS_ENABLED = "sdr_protocols_enabled"
  private const val KEY_ADSB_NETWORK_PORT = "sdr_adsb_network_port"
  private const val KEY_UAT_NETWORK_PORT = "sdr_uat_network_port"
  private const val KEY_P25_NETWORK_PORT = "sdr_p25_network_port"
  private const val KEY_LORAWAN_NETWORK_PORT = "sdr_lorawan_network_port"
  private const val KEY_WMBUS_NETWORK_PORT = "sdr_wmbus_network_port"
  private const val KEY_ZWAVE_NETWORK_PORT = "sdr_zwave_network_port"
  private const val KEY_SIDEWALK_NETWORK_PORT = "sdr_sidewalk_network_port"

  private fun prefs(context: Context) =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun isEnabled(context: Context): Boolean =
    prefs(context).getBoolean(KEY_ENABLED, false)

  fun setEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
  }

  fun source(context: Context): SdrSource =
    SdrSource.fromValue(prefs(context).getString(KEY_SOURCE, null))

  fun setSource(context: Context, source: SdrSource) {
    prefs(context).edit().putString(KEY_SOURCE, source.value).apply()
  }

  fun frequencyMhz(context: Context): Int =
    prefs(context).getInt(KEY_FREQUENCY, 433)

  fun setFrequencyMhz(context: Context, mhz: Int) {
    prefs(context).edit().putInt(KEY_FREQUENCY, mhz).apply()
  }

  fun frequencyHz(context: Context): Int = when (frequencyMhz(context)) {
    315 -> 315_000_000
    1090 -> 1_090_000_000
    978 -> 978_000_000
    152 -> 152_480_000
    157 -> 157_450_000
    454 -> 454_000_000
    929 -> 929_612_500
    931 -> 931_937_500
    else -> 433_920_000
  }

  fun enabledProtocols(context: Context): Set<String> {
    val raw = prefs(context).getString(KEY_PROTOCOLS_ENABLED, null)
    return raw?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet()
      ?: setOf("tpms")
  }

  fun setEnabledProtocols(context: Context, protocols: Set<String>) {
    prefs(context).edit().putString(KEY_PROTOCOLS_ENABLED, protocols.joinToString(",")).apply()
  }

  fun gain(context: Context): Int? {
    val value = prefs(context).getInt(KEY_GAIN, -1)
    return if (value < 0) null else value
  }

  fun setGain(context: Context, gain: Int?) {
    prefs(context).edit().putInt(KEY_GAIN, gain ?: -1).apply()
  }

  fun networkHost(context: Context): String =
    prefs(context).getString(KEY_NETWORK_HOST, "192.168.1.100") ?: "192.168.1.100"

  fun setNetworkHost(context: Context, host: String) {
    prefs(context).edit().putString(KEY_NETWORK_HOST, host).apply()
  }

  fun networkPort(context: Context): Int =
    prefs(context).getInt(KEY_NETWORK_PORT, 1234)

  fun setNetworkPort(context: Context, port: Int) {
    prefs(context).edit().putInt(KEY_NETWORK_PORT, port).apply()
  }

  fun adsbNetworkPort(context: Context): Int =
    prefs(context).getInt(KEY_ADSB_NETWORK_PORT, 30003)

  fun setAdsbNetworkPort(context: Context, port: Int) {
    prefs(context).edit().putInt(KEY_ADSB_NETWORK_PORT, port).apply()
  }

  fun uatNetworkPort(context: Context): Int =
    prefs(context).getInt(KEY_UAT_NETWORK_PORT, 30978)

  fun setUatNetworkPort(context: Context, port: Int) {
    prefs(context).edit().putInt(KEY_UAT_NETWORK_PORT, port).apply()
  }

  fun p25NetworkPort(context: Context): Int =
    prefs(context).getInt(KEY_P25_NETWORK_PORT, 23456)

  fun setP25NetworkPort(context: Context, port: Int) {
    prefs(context).edit().putInt(KEY_P25_NETWORK_PORT, port).apply()
  }

  fun lorawanNetworkPort(context: Context): Int =
    prefs(context).getInt(KEY_LORAWAN_NETWORK_PORT, 1680)

  fun setLorawanNetworkPort(context: Context, port: Int) {
    prefs(context).edit().putInt(KEY_LORAWAN_NETWORK_PORT, port).apply()
  }

  fun wmbusNetworkPort(context: Context): Int =
    prefs(context).getInt(KEY_WMBUS_NETWORK_PORT, 1681)

  fun setWmbusNetworkPort(context: Context, port: Int) {
    prefs(context).edit().putInt(KEY_WMBUS_NETWORK_PORT, port).apply()
  }

  fun zwaveNetworkPort(context: Context): Int =
    prefs(context).getInt(KEY_ZWAVE_NETWORK_PORT, 1682)

  fun setZwaveNetworkPort(context: Context, port: Int) {
    prefs(context).edit().putInt(KEY_ZWAVE_NETWORK_PORT, port).apply()
  }

  fun sidewalkNetworkPort(context: Context): Int =
    prefs(context).getInt(KEY_SIDEWALK_NETWORK_PORT, 1683)

  fun setSidewalkNetworkPort(context: Context, port: Int) {
    prefs(context).edit().putInt(KEY_SIDEWALK_NETWORK_PORT, port).apply()
  }

  enum class SdrSource(val value: String) {
    USB("usb"),
    NETWORK("network");

    companion object {
      fun fromValue(value: String?): SdrSource =
        entries.firstOrNull { it.value == value } ?: USB
    }
  }
}
