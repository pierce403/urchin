package guru.urchin.ui

import guru.urchin.scan.ScanDiagnosticsSnapshot
import guru.urchin.sdr.SdrState
import guru.urchin.util.Formatters

object DiagnosticsReportBuilder {
  fun build(
    sdrState: SdrState,
    diagnostics: ScanDiagnosticsSnapshot,
    deviceCount: Int,
    logEntries: List<String>
  ): String {
    return buildString {
      val guidance = setupGuidance(sdrState, diagnostics)
      if (guidance.isNotEmpty()) {
        appendLine("Setup guide")
        guidance.forEach { appendLine(it) }
        appendLine()
      }
      appendLine("Urchin diagnostics")
      appendLine("Generated: ${Formatters.formatTimestamp(System.currentTimeMillis())}")
      appendLine("State: ${describeState(sdrState)}")
      appendLine("Source: ${diagnostics.sourceLabel ?: "unknown"}")
      appendLine("Hardware: ${diagnostics.hardwareLabel ?: "none detected"}")
      appendLine("Host: ${diagnostics.networkHost ?: "-"}")
      appendLine("Port: ${diagnostics.networkPort ?: 0}")
      appendLine("Frequency: ${diagnostics.frequencyHz ?: 0} Hz")
      appendLine("Gain: ${diagnostics.gain?.toString() ?: "auto"}")
      appendLine("Observed sensors: $deviceCount")
      appendLine("Unique keys: ${diagnostics.uniqueDeviceCount}")
      appendLine("rtl_433 callbacks: ${diagnostics.sdrCallbackCount}")
      appendLine("Raw observations: ${diagnostics.rawCallbackCount}")
      appendLine("Last reading: ${diagnostics.lastReadingAt?.let(Formatters::formatTimestamp) ?: "none"}")
      appendLine("Last error: ${diagnostics.lastError ?: "none"}")
      appendLine()
      appendLine("Recent log")
      logEntries.takeLast(40).forEach(::appendLine)
    }.trimEnd()
  }

  private fun setupGuidance(state: SdrState, diagnostics: ScanDiagnosticsSnapshot): List<String> {
    val isNetwork = diagnostics.sourceLabel == "network"
    return when (state) {
      SdrState.Idle -> if (isNetwork) {
        listOf(
          "Network mode is selected.",
          "→ Ensure rtl_433 is running on the configured host and tap Start."
        )
      } else {
        listOf(
          "USB mode is selected.",
          "→ Connect an RTL-SDR (0BDA:2838 / 0BDA:2832) or HackRF One (1D50:6089) via a USB OTG adapter.",
          "→ Then tap Start to begin scanning."
        )
      }
      SdrState.UsbNotConnected -> listOf(
        "No supported USB SDR was detected.",
        "→ Plug in an RTL-SDR or HackRF One via a USB OTG adapter.",
        "→ If already connected, try unplugging and replugging.",
        "→ Alternatively, switch to Network mode and run rtl_433 on a connected host."
      )
      SdrState.UsbPermissionDenied -> listOf(
        "USB access was denied.",
        "→ Tap 'Retry capture' and approve the USB permission prompt.",
        "→ If the prompt does not reappear, unplug and replug the device, then retry."
      )
      is SdrState.Error -> {
        val msg = state.message
        val hint = when {
          "not found" in msg || "binary" in msg.lowercase() ->
            "→ The rtl_433 or dump1090 binary was not found. See the rtl_433 Android setup guide."
          "permission" in msg.lowercase() ->
            "→ Check that USB permission was granted and retry."
          "connect" in msg.lowercase() || "refused" in msg.lowercase() ->
            "→ Verify the network host and port in the filter panel."
          else ->
            "→ Check the recent log below for details."
        }
        listOf("Capture error: $msg", hint)
      }
      SdrState.Scanning -> {
        val hw = diagnostics.hardwareLabel
        if (hw != null) {
          val tipLines = mutableListOf("Active: $hw")
          if ("hackrf" in hw.lowercase()) {
            tipLines.add("→ HackRF One: wide frequency range, frequency hopping enabled.")
          } else if ("rtl" in hw.lowercase()) {
            tipLines.add("→ RTL-SDR: set gain manually if auto-gain gives poor results.")
          }
          tipLines
        } else {
          emptyList()
        }
      }
    }
  }

  private fun describeState(state: SdrState): String {
    return when (state) {
      SdrState.Idle -> "idle"
      SdrState.Scanning -> "scanning"
      SdrState.UsbNotConnected -> "usb device not connected"
      SdrState.UsbPermissionDenied -> "usb permission denied"
      is SdrState.Error -> "error: ${state.message}"
    }
  }
}
