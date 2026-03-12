package guru.urchin.sdr

import android.content.Context
import android.os.Looper
import androidx.annotation.MainThread
import guru.urchin.scan.ObservationRecorder
import guru.urchin.scan.ScanDiagnosticsStore
import guru.urchin.scan.ScanDiagnosticsSnapshot
import guru.urchin.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Top-level SDR orchestrator. Manages USB and network capture across all protocols.
 *
 * In network mode, starts per-protocol TCP bridges (rtl_433 for TPMS/POCSAG,
 * dump1090 for ADS-B, OP25 for P25) on separate ports. In USB mode, detects
 * connected dongles and either assigns one per frequency (multi-dongle) or
 * uses [FrequencyHopper] to time-share a single dongle.
 *
 * All parsed readings flow through [handleSdrReading] → [ObservationBuilderRegistry]
 * → [ObservationRecorder] into the Room database.
 */
class SdrController(
  private val context: Context,
  private val scope: CoroutineScope,
  private val observationRecorder: ObservationRecorder,
  private val usbDetector: UsbDetector = RealUsbDetector(context)
) {
  private val _sdrState = MutableStateFlow<SdrState>(SdrState.Idle)
  val sdrState: StateFlow<SdrState> = _sdrState.asStateFlow()

  private val networkBridge = TcpStreamBridge<SdrReading>("SDR network bridge", Rtl433JsonParser::parse)
  private val rtl433Process by lazy { Rtl433Process(context) }
  private var adsbProcess: Dump1090Process? = null
  private var usbReceiverHandle: Any? = null

  private val channels = mutableListOf<SdrChannel>()
  private var frequencyHopper: FrequencyHopper? = null
  private var adsbBridge: TcpStreamBridge<SdrReading.Adsb>? = null
  private var p25Bridge: TcpStreamBridge<SdrReading.P25>? = null
  private var p25Process: P25Process? = null

  @MainThread
  fun startSdr() {
    check(Looper.myLooper() == Looper.getMainLooper()) { "startSdr() must be called on the main thread" }
    if (!SdrPreferences.isEnabled(context)) {
      _sdrState.value = SdrState.Idle
      return
    }
    if (_sdrState.value is SdrState.Scanning) return

    ScanDiagnosticsStore.reset(
      ScanDiagnosticsSnapshot(
        startTimeMs = System.currentTimeMillis(),
        sourceLabel = SdrPreferences.source(context).value,
        networkHost = SdrPreferences.networkHost(context),
        networkPort = SdrPreferences.networkPort(context),
        frequencyHz = SdrPreferences.frequencyHz(context),
        gain = SdrPreferences.gain(context)
      )
    )

    when (SdrPreferences.source(context)) {
      SdrPreferences.SdrSource.NETWORK -> startNetworkBridge()
      SdrPreferences.SdrSource.USB -> startUsbSdr()
    }
  }

  @MainThread
  fun stopSdr() {
    check(Looper.myLooper() == Looper.getMainLooper()) { "stopSdr() must be called on the main thread" }
    networkBridge.disconnect()
    rtl433Process.stop()
    channels.forEach { it.stop() }
    channels.clear()
    frequencyHopper?.stop()
    frequencyHopper = null
    adsbBridge?.disconnect()
    adsbBridge = null
    adsbProcess?.stop()
    adsbProcess = null
    p25Bridge?.disconnect()
    p25Bridge = null
    p25Process?.stop()
    p25Process = null
    _sdrState.value = SdrState.Idle
    DebugLog.log("SDR scanning stopped")
  }

  fun registerUsbDetection() {
    if (usbReceiverHandle != null) return
    usbReceiverHandle = usbDetector.registerReceiver(
      onAttached = {
        if (SdrPreferences.isEnabled(context) &&
          SdrPreferences.source(context) == SdrPreferences.SdrSource.USB &&
          _sdrState.value !is SdrState.Scanning
        ) {
          startUsbSdr()
        }
      },
      onDetached = {
        if (_sdrState.value is SdrState.Scanning &&
          SdrPreferences.source(context) == SdrPreferences.SdrSource.USB
        ) {
          rtl433Process.stop()
          channels.forEach { it.stop() }
          channels.clear()
          frequencyHopper?.stop()
          frequencyHopper = null
          adsbBridge?.disconnect()
          adsbBridge = null
          adsbProcess?.stop()
          adsbProcess = null
          p25Process?.stop()
          p25Process = null
          _sdrState.value = SdrState.UsbNotConnected
          ScanDiagnosticsStore.update { it.copy(lastError = "USB SDR disconnected.") }
        }
      },
      onPermissionResult = { granted ->
        if (granted) {
          startUsbSdr()
        } else {
          _sdrState.value = SdrState.UsbPermissionDenied
          ScanDiagnosticsStore.update { it.copy(lastError = "USB permission denied.") }
        }
      }
    )
  }

  fun unregisterUsbDetection() {
    usbReceiverHandle?.let {
      usbDetector.unregisterReceiver(it)
      usbReceiverHandle = null
    }
  }

  private fun startNetworkBridge() {
    val host = SdrPreferences.networkHost(context)
    val port = SdrPreferences.networkPort(context)
    val enabledProtocols = SdrPreferences.enabledProtocols(context)
    DebugLog.log("SDR starting network bridge to $host:$port protocols=$enabledProtocols")
    _sdrState.value = SdrState.Scanning
    ScanDiagnosticsStore.update {
      it.copy(sourceLabel = SdrPreferences.SdrSource.NETWORK.value)
    }

    // rtl_433 bridge handles TPMS + POCSAG
    val rtl433Protocols = enabledProtocols.intersect(setOf("tpms", "pocsag"))
    if (rtl433Protocols.isNotEmpty()) {
      networkBridge.connect(
        scope = scope,
        host = host,
        port = port,
        onReading = ::handleSdrReading,
        onError = { message ->
          _sdrState.value = SdrState.Error(message)
          ScanDiagnosticsStore.update { snapshot -> snapshot.copy(lastError = message) }
        }
      )
    }

    // ADS-B bridge on separate port
    if ("adsb" in enabledProtocols) {
      startAdsbNetworkBridge(host)
    }

    // P25 bridge on separate port (OP25 metadata)
    if ("p25" in enabledProtocols) {
      startP25NetworkBridge(host)
    }
  }

  private fun startAdsbNetworkBridge(host: String) {
    val adsbPort = SdrPreferences.adsbNetworkPort(context)
    DebugLog.log("ADS-B starting network bridge to $host:$adsbPort")
    adsbBridge?.disconnect()
    val bridge = TcpStreamBridge<SdrReading.Adsb>("ADS-B network bridge", AdsbStreamParser::parse)
    adsbBridge = bridge
    bridge.connect(
      scope = scope,
      host = host,
      port = adsbPort,
      onReading = { reading -> handleSdrReading(reading) },
      onError = { message ->
        DebugLog.log("ADS-B network bridge error: $message")
        ScanDiagnosticsStore.update { snapshot -> snapshot.copy(lastError = "ADS-B: $message") }
      }
    )
  }

  private fun startP25NetworkBridge(host: String) {
    val p25Port = SdrPreferences.p25NetworkPort(context)
    DebugLog.log("P25 starting network bridge to $host:$p25Port")
    val bridge = TcpStreamBridge<SdrReading.P25>("P25 network bridge", P25NetworkBridge::parseP25Json)
    p25Bridge = bridge
    bridge.connect(
      scope = scope,
      host = host,
      port = p25Port,
      onReading = { reading -> handleSdrReading(reading) },
      onError = { message ->
        DebugLog.log("P25 network bridge error: $message")
        ScanDiagnosticsStore.update { snapshot -> snapshot.copy(lastError = "P25: $message") }
      }
    )
  }

  private fun startUsbSdr() {
    val allDevices = usbDetector.findAllDevices()
    if (allDevices.isEmpty()) {
      _sdrState.value = SdrState.UsbNotConnected
      DebugLog.log("No SDR USB device found")
      ScanDiagnosticsStore.update { it.copy(lastError = "No RTL-SDR or HackRF detected over USB.") }
      return
    }

    if (!usbDetector.allPermitted()) {
      usbDetector.requestNextPermission()
      return
    }

    val enabledProtocols = SdrPreferences.enabledProtocols(context)
    val adsbEnabled = "adsb" in enabledProtocols
    val p25Enabled = "p25" in enabledProtocols
    val rtl433Frequencies = buildRtl433FrequencyList(enabledProtocols)
    val remainingDevices = allDevices.toMutableList()
    val reservedDedicatedSlots = listOfNotNull(
      "adsb".takeIf { adsbEnabled },
      "p25".takeIf { p25Enabled }
    )
    var startupWarning: String? = null

    val rtl433Devices = mutableListOf<SdrDeviceHandle>()
    if (rtl433Frequencies.isNotEmpty() && remainingDevices.isNotEmpty()) {
      rtl433Devices.add(remainingDevices.removeAt(0))
      while (rtl433Devices.size < rtl433Frequencies.size && remainingDevices.size > reservedDedicatedSlots.size) {
        rtl433Devices.add(remainingDevices.removeAt(0))
      }
    }

    val adsbDongle = if (adsbEnabled && remainingDevices.isNotEmpty()) {
      remainingDevices.removeAt(0)
    } else {
      null
    }

    val p25Dongle = if (p25Enabled && remainingDevices.isNotEmpty()) {
      remainingDevices.removeAt(0)
    } else {
      null
    }

    if (rtl433Devices.size >= rtl433Frequencies.size && rtl433Frequencies.isNotEmpty()) {
      // Multiple dongles: assign one per frequency
      DebugLog.log("SDR multi-dongle mode: ${rtl433Devices.size} devices for ${rtl433Frequencies.size} frequencies")
      rtl433Frequencies.forEachIndexed { index, freq ->
        if (index < rtl433Devices.size) {
          val device = rtl433Devices[index]
          val channel = SdrChannel(
            config = SdrChannelConfig(
              id = "usb-$index",
              source = SdrPreferences.SdrSource.USB,
              frequencyHz = freq,
              gain = SdrPreferences.gain(context),
              usbDeviceId = device.id,
              hardwareProfile = device.profile
            ),
            context = context
          )
          channels.add(channel)
          channel.start(scope, onReading = ::handleSdrReading, onError = { message ->
            DebugLog.log("SdrChannel usb-$index error: $message")
            ScanDiagnosticsStore.update { snapshot -> snapshot.copy(lastError = message) }
          })
        }
      }
    } else if (rtl433Frequencies.isNotEmpty()) {
      // Single dongle: use frequency hopping if multiple frequencies
      val device = rtl433Devices.first()
      DebugLog.log("SDR starting on-device: ${usbDetector.deviceDescription() ?: device.profile.label}")

      if (rtl433Frequencies.size > 1) {
        DebugLog.log("SDR frequency hopping mode: ${rtl433Frequencies.size} frequencies")
        val hopper = FrequencyHopper(
          context = context,
          frequencies = rtl433Frequencies,
          usbDeviceId = device.id,
          hardwareProfile = device.profile,
          gain = SdrPreferences.gain(context),
          onReading = ::handleSdrReading,
          onError = { message ->
            _sdrState.value = SdrState.Error(message)
            ScanDiagnosticsStore.update { snapshot -> snapshot.copy(lastError = message) }
          }
        )
        frequencyHopper = hopper
        hopper.start(scope)
      } else {
        rtl433Process.start(
          scope = scope,
          hardwareProfile = device.profile,
          usbDeviceId = device.id,
          frequencyHz = rtl433Frequencies.firstOrNull() ?: SdrPreferences.frequencyHz(context),
          gain = SdrPreferences.gain(context),
          onReading = ::handleSdrReading,
          onError = { message ->
            _sdrState.value = SdrState.Error(message)
            ScanDiagnosticsStore.update { snapshot -> snapshot.copy(lastError = message) }
          }
        )
      }
    }

    if (adsbDongle != null) {
      startAdsbUsbBridge(adsbDongle)
    } else if (adsbEnabled) {
      val message = if (rtl433Frequencies.isNotEmpty() || p25Enabled) {
        "USB ADS-B needs its own RTL-SDR dongle when TPMS/POCSAG or P25 are also enabled."
      } else {
        "USB ADS-B requires an attached RTL-SDR dongle."
      }
      DebugLog.log(message)
      startupWarning = message
      ScanDiagnosticsStore.update { snapshot -> snapshot.copy(lastError = message) }
    }

    // Start P25 on dedicated dongle if available
    if (p25Dongle != null) {
      DebugLog.log("P25 starting on dedicated dongle: ${p25Dongle.profile.label}")
      val process = P25Process(context)
      p25Process = process
      process.start(
        scope = scope,
        hardwareProfile = p25Dongle.profile,
        usbDeviceId = p25Dongle.id,
        frequencyHz = 851_000_000,
        gain = SdrPreferences.gain(context),
        onReading = { reading -> handleSdrReading(reading) },
        onError = { message ->
          DebugLog.log("P25 USB process error: $message")
          ScanDiagnosticsStore.update { snapshot -> snapshot.copy(lastError = "P25: $message") }
        }
      )
    } else if (p25Enabled) {
      val message = "P25 USB mode requires a dedicated dongle; no spare dongle available"
      DebugLog.log(message)
      startupWarning = startupWarning ?: message
      ScanDiagnosticsStore.update { snapshot -> snapshot.copy(lastError = message) }
    }

    _sdrState.value = SdrState.Scanning
    ScanDiagnosticsStore.update {
      it.copy(
        sourceLabel = SdrPreferences.SdrSource.USB.value,
        hardwareLabel = allDevices.first().profile.label,
        lastError = startupWarning
      )
    }
  }

  private fun startAdsbUsbBridge(device: SdrDeviceHandle) {
    val process = Dump1090Process(context)
    val adsbPort = SdrPreferences.adsbNetworkPort(context)
    adsbProcess = process
    process.start(
      scope = scope,
      hardwareProfile = device.profile,
      usbDeviceId = device.id,
      frequencyHz = 1_090_000_000,
      gain = SdrPreferences.gain(context),
      sbsPort = adsbPort,
      onReady = {
        DebugLog.log("ADS-B USB stream ready on 127.0.0.1:$adsbPort")
        startAdsbNetworkBridge("127.0.0.1")
      },
      onError = { message ->
        DebugLog.log("ADS-B USB process error: $message")
        ScanDiagnosticsStore.update { snapshot -> snapshot.copy(lastError = "ADS-B: $message") }
      }
    )
  }

  private fun buildRtl433FrequencyList(enabledProtocols: Set<String>): List<Int> {
    // ADS-B and P25 are excluded here because they use dedicated binaries.
    val frequencies = mutableListOf<Int>()
    if ("tpms" in enabledProtocols) {
      frequencies.add(SdrPreferences.frequencyHz(context))
    }
    if ("pocsag" in enabledProtocols) {
      frequencies.add(929_612_500) // Default POCSAG frequency
    }
    return frequencies.distinct()
  }

  internal fun handleSdrReading(reading: SdrReading) {
    val input = ObservationBuilderRegistry.build(reading)
    ScanDiagnosticsStore.update {
      it.copy(
        sdrCallbackCount = it.sdrCallbackCount + 1,
        rawCallbackCount = it.rawCallbackCount + 1,
        lastReadingAt = System.currentTimeMillis(),
        lastError = null
      )
    }
    val logMessage = when (reading) {
      is SdrReading.Tpms -> "SDR tpms model=${reading.model} sensor=${reading.sensorId} " +
        "pressure=${reading.pressureKpa} temp=${reading.temperatureC}"
      is SdrReading.Pocsag -> "SDR pocsag address=${reading.address} func=${reading.functionCode} " +
        "message=${reading.message?.take(50)}"
      is SdrReading.Adsb -> "SDR adsb icao=${reading.icao} callsign=${reading.callsign} " +
        "alt=${reading.altitude} speed=${reading.speed}"
      is SdrReading.P25 -> "SDR p25 unit=${reading.unitId} nac=${reading.nac} " +
        "talkgroup=${reading.talkGroupId}"
    }
    DebugLog.log(logMessage)
    observationRecorder.record(input)
  }
}
