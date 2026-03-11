package guru.urchin.sdr

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import guru.urchin.util.DebugLog

data class SdrChannelConfig(
  val id: String,
  val source: SdrPreferences.SdrSource,
  val frequencyHz: Int,
  val gain: Int?,
  val networkHost: String? = null,
  val networkPort: Int? = null,
  val usbDeviceId: Int? = null,
  val hardwareProfile: SdrHardwareProfile? = null
)

/**
 * Abstraction for a single SDR stream. Wraps either a USB [Rtl433Process] subprocess
 * or a [TcpStreamBridge] TCP connection depending on the configured source.
 * Used by [SdrController] for multi-dongle assignment.
 */
class SdrChannel(
  private val config: SdrChannelConfig,
  private val context: Context
) {
  private var rtl433Process: Rtl433Process? = null
  private var networkBridge: TcpStreamBridge<SdrReading>? = null

  val id: String get() = config.id
  val isRunning: Boolean
    get() = rtl433Process?.isRunning == true || networkBridge?.isConnected == true

  fun start(
    scope: CoroutineScope,
    onReading: (SdrReading) -> Unit,
    onError: (String) -> Unit
  ) {
    stop()
    DebugLog.log("SdrChannel[${config.id}] starting on ${config.frequencyHz} Hz via ${config.source.value}")

    when (config.source) {
      SdrPreferences.SdrSource.USB -> {
        val profile = config.hardwareProfile ?: run {
          onError("No hardware profile for USB channel ${config.id}")
          return
        }
        val process = Rtl433Process(context)
        rtl433Process = process
        process.start(
          scope = scope,
          hardwareProfile = profile,
          usbDeviceId = config.usbDeviceId,
          frequencyHz = config.frequencyHz,
          gain = config.gain,
          onReading = onReading,
          onError = onError
        )
      }
      SdrPreferences.SdrSource.NETWORK -> {
        val host = config.networkHost ?: "192.168.1.100"
        val port = config.networkPort ?: 1234
        val bridge = TcpStreamBridge<SdrReading>("SdrChannel[${config.id}]", Rtl433JsonParser::parse)
        networkBridge = bridge
        bridge.connect(
          scope = scope,
          host = host,
          port = port,
          onReading = onReading,
          onError = onError
        )
      }
    }
  }

  fun stop() {
    rtl433Process?.stop()
    rtl433Process = null
    networkBridge?.disconnect()
    networkBridge = null
  }
}
