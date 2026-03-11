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

class P25Process(private val context: Context) {
  private var process: Process? = null
  private var readerJob: Job? = null
  private var errorReaderJob: Job? = null
  private var monitorJob: Job? = null
  private var usbRelay: RtlSdrUsbRelay? = null
  @Volatile private var lastStderrLine: String? = null
  @Volatile private var stopRequested: Boolean = false

  fun start(
    scope: CoroutineScope,
    hardwareProfile: SdrHardwareProfile,
    usbDeviceId: Int?,
    frequencyHz: Int,
    gain: Int?,
    onReading: (SdrReading.P25) -> Unit,
    onError: (String) -> Unit
  ) {
    stop()
    stopRequested = false
    lastStderrLine = null
    val status = SdrRuntimeInspector.p25ScannerStatus(context)
    if (!status.exists) {
      DebugLog.log(status.missingMessage(), level = android.util.Log.ERROR)
      onError(status.missingMessage())
      return
    }
    val binary = File(requireNotNull(status.resolvedLocation))
    val relay = try {
      if (hardwareProfile.usesAndroidUsbFdRelay && usbDeviceId != null) {
        RtlSdrUsbRelay.open(context, usbDeviceId)
      } else {
        null
      }
    } catch (e: Exception) {
      val message = e.message ?: "Failed to prepare Android USB relay for p25_scanner."
      DebugLog.log(message, level = android.util.Log.ERROR, throwable = e)
      onError(message)
      return
    }

    val args = buildList {
      add(binary.absolutePath)
      add("-f"); add(frequencyHz.toString())
      gain?.let { add("-g"); add(it.toString()) }
    }

    try {
      DebugLog.log("Starting p25_scanner: ${args.joinToString(" ")}")
      val proc = relay?.startProcess(args) ?: ProcessBuilder(args)
        .redirectErrorStream(false)
        .start()
      usbRelay = relay
      process = proc

      readerJob = scope.launch(Dispatchers.IO) {
        try {
          BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
            while (isActive) {
              val line = reader.readLine() ?: break
              val reading = P25NetworkBridge.parseP25Json(line)
              if (reading != null) {
                withContext(Dispatchers.Main) { onReading(reading) }
              }
            }
          }
        } catch (e: Exception) {
          if (isActive) {
            DebugLog.log("p25_scanner stdout read error: ${e.message}", level = android.util.Log.ERROR)
          }
        }
      }

      errorReaderJob = scope.launch(Dispatchers.IO) {
        try {
          BufferedReader(InputStreamReader(proc.errorStream)).use { reader ->
            while (isActive) {
              val line = reader.readLine() ?: break
              lastStderrLine = line
              DebugLog.log("p25_scanner stderr: $line")
            }
          }
        } catch (_: Exception) {
          // Ignore stderr read errors
        }
      }

      monitorJob = scope.launch(Dispatchers.IO) {
        val exitCode = proc.waitFor()
        if (stopRequested || process !== proc) return@launch
        val detail = lastStderrLine?.let { ": $it" } ?: ""
        val message = "p25_scanner exited with code $exitCode$detail"
        DebugLog.log(message, level = android.util.Log.ERROR)
        withContext(Dispatchers.Main) {
          if (!stopRequested && process === proc) {
            onError(message)
          }
        }
      }
    } catch (e: Exception) {
      relay?.close()
      DebugLog.log("Failed to start p25_scanner: ${e.message}", level = android.util.Log.ERROR)
      onError("Failed to start P25 scanner: ${e.message}")
    }
  }

  fun stop() {
    stopRequested = true
    monitorJob?.cancel()
    monitorJob = null
    readerJob?.cancel()
    readerJob = null
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
}
