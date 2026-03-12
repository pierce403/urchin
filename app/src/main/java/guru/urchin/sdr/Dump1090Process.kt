package guru.urchin.sdr

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import guru.urchin.util.DebugLog
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

class Dump1090Process(private val context: Context) {
  private var process: Process? = null
  private var errorReaderJob: Job? = null
  private var monitorJob: Job? = null
  private var readyJob: Job? = null
  private var usbRelay: RtlSdrUsbRelay? = null
  @Volatile private var lastStderrLine: String? = null
  @Volatile private var stopRequested: Boolean = false

  fun start(
    scope: CoroutineScope,
    hardwareProfile: SdrHardwareProfile,
    usbDeviceId: Int?,
    frequencyHz: Int,
    gain: Int?,
    sbsPort: Int,
    onReady: () -> Unit,
    onError: (String) -> Unit
  ) {
    stop()
    stopRequested = false
    lastStderrLine = null
    val status = SdrRuntimeInspector.dump1090Status(context)
    if (!status.exists) {
      DebugLog.log(status.missingMessage(), level = android.util.Log.ERROR)
      onError(status.missingMessage())
      return
    }
    if (!hardwareProfile.usesAndroidUsbFdRelay) {
      val message = "${hardwareProfile.label} USB mode is not bundled for ADS-B in this APK. Use Network bridge mode."
      DebugLog.log(message, level = android.util.Log.ERROR)
      onError(message)
      return
    }
    val binary = File(requireNotNull(status.resolvedLocation))
    val relay = try {
      if (usbDeviceId != null) {
        RtlSdrUsbRelay.open(context, usbDeviceId)
      } else {
        null
      }
    } catch (e: Exception) {
      val message = e.message ?: "Failed to prepare Android USB relay for dump1090."
      DebugLog.log(message, level = android.util.Log.ERROR, throwable = e)
      onError(message)
      return
    }

    val args = buildArgs(
      binaryPath = binary.absolutePath,
      frequencyHz = frequencyHz,
      gain = gain,
      sbsPort = sbsPort
    )

    try {
      DebugLog.log("Starting dump1090: ${args.joinToString(" ")}")
      val proc = relay?.startProcess(args) ?: ProcessBuilder(args)
        .redirectErrorStream(false)
        .start()
      usbRelay = relay
      process = proc

      errorReaderJob = scope.launch(Dispatchers.IO) {
        try {
          BufferedReader(InputStreamReader(proc.errorStream)).use { reader ->
            while (isActive) {
              val line = reader.readLine() ?: break
              lastStderrLine = line
              DebugLog.log("dump1090 stderr: $line")
            }
          }
        } catch (_: Exception) {}
      }

      readyJob = scope.launch(Dispatchers.IO) {
        val ready = waitForSbsPort(port = sbsPort, process = proc)
        withContext(Dispatchers.Main) {
          if (stopRequested || process !== proc) return@withContext
          if (ready) {
            onReady()
          } else {
            val detail = lastStderrLine?.let { ": $it" } ?: ""
            onError("dump1090 did not open ADS-B stream port $sbsPort$detail")
          }
        }
      }

      monitorJob = scope.launch(Dispatchers.IO) {
        val exitCode = proc.waitFor()
        if (stopRequested || process !== proc) return@launch
        val detail = lastStderrLine?.let { ": $it" } ?: ""
        val message = "dump1090 exited with code $exitCode$detail"
        DebugLog.log(message, level = android.util.Log.ERROR)
        withContext(Dispatchers.Main) {
          if (!stopRequested && process === proc) {
            onError(message)
          }
        }
      }
    } catch (e: Exception) {
      relay?.close()
      DebugLog.log("Failed to start dump1090: ${e.message}", level = android.util.Log.ERROR)
      onError("Failed to start dump1090: ${e.message}")
    }
  }

  fun stop() {
    stopRequested = true
    readyJob?.cancel()
    readyJob = null
    monitorJob?.cancel()
    monitorJob = null
    errorReaderJob?.cancel()
    errorReaderJob = null
    process?.destroy()
    process = null
    usbRelay?.close()
    usbRelay = null
  }

  val isRunning: Boolean
    get() {
      val proc = process ?: return false
      return try {
        proc.exitValue()
        false
      } catch (_: IllegalThreadStateException) {
        true
      }
    }

  companion object {
    internal fun buildArgs(
      binaryPath: String,
      frequencyHz: Int,
      gain: Int?,
      sbsPort: Int
    ): List<String> = buildList {
      add(binaryPath)
      add("--device-type"); add("rtlsdr")
      add("--device"); add("0")
      add("--freq"); add(frequencyHz.toString())
      add("--net")
      add("--net-bind-address"); add("127.0.0.1")
      add("--net-sbs-port"); add(sbsPort.toString())
      add("--quiet")
      gain?.let { add("--gain"); add(it.toString()) }
    }

    private fun waitForSbsPort(port: Int, process: Process): Boolean {
      repeat(50) {
        if (!isProcessAlive(process)) {
          return false
        }
        try {
          Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 100)
            return true
          }
        } catch (_: Exception) {
          Thread.sleep(100)
        }
      }
      return false
    }

    private fun isProcessAlive(process: Process): Boolean {
      return try {
        process.exitValue()
        false
      } catch (_: IllegalThreadStateException) {
        true
      }
    }
  }
}
