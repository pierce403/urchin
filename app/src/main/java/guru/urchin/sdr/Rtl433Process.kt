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
import java.io.InputStreamReader

class Rtl433Process(private val context: Context) {
  private var process: Process? = null
  private var readerJob: Job? = null
  private var errorReaderJob: Job? = null
  private var monitorJob: Job? = null
  private var usbRelay: RtlSdrUsbRelay? = null
  @Volatile private var lastStderrLine: String? = null
  @Volatile private var readingCount: Int = 0
  @Volatile private var stopRequested: Boolean = false

  fun start(
    scope: CoroutineScope,
    hardwareProfile: SdrHardwareProfile,
    usbDeviceId: Int?,
    frequencyHz: Int,
    gain: Int?,
    onReading: (SdrReading) -> Unit,
    onError: (String) -> Unit
  ) {
    stop()
    stopRequested = false
    lastStderrLine = null
    readingCount = 0
    if (hardwareProfile.rtl433DeviceArg == "driver=hackrf") {
      val message = "HackRF USB mode is not bundled in this APK. Use Network bridge for HackRF capture."
      DebugLog.log(message, level = android.util.Log.ERROR)
      onError(message)
      return
    }
    val binary = try {
      Rtl433BinaryInstaller.ensureInstalled(context)
    } catch (e: Exception) {
      val message = e.message ?: "Failed to install bundled rtl_433."
      DebugLog.log(message, level = android.util.Log.ERROR, throwable = e)
      onError(message)
      return
    }
    val relay = try {
      if (hardwareProfile.usesAndroidUsbFdRelay && usbDeviceId != null) {
        RtlSdrUsbRelay.open(context, usbDeviceId)
      } else {
        null
      }
    } catch (e: Exception) {
      val message = e.message ?: "Failed to prepare Android USB relay for rtl_433."
      DebugLog.log(message, level = android.util.Log.ERROR, throwable = e)
      onError(message)
      return
    }

    val args = buildArgs(
      binaryPath = binary.absolutePath,
      hardwareProfile = hardwareProfile,
      frequencyHz = frequencyHz,
      gain = gain
    )

    try {
      DebugLog.log("Starting rtl_433: ${args.joinToString(" ")}")
      val proc = ProcessBuilder(args).apply {
        relay?.let {
          environment()["URCHIN_RTLSDR_FD"] = it.fd.toString()
        }
      }
        .redirectErrorStream(false)
        .start()
      usbRelay = relay
      process = proc

      readerJob = scope.launch(Dispatchers.IO) {
        try {
          BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
            while (isActive) {
              val line = reader.readLine() ?: break
              val reading = Rtl433JsonParser.parse(line)
              if (reading != null) {
                readingCount += 1
                withContext(Dispatchers.Main) { onReading(reading) }
              }
            }
          }
        } catch (e: Exception) {
          if (isActive) {
            DebugLog.log("rtl_433 stdout read error: ${e.message}", level = android.util.Log.ERROR)
          }
        }
      }

      errorReaderJob = scope.launch(Dispatchers.IO) {
        try {
          BufferedReader(InputStreamReader(proc.errorStream)).use { reader ->
            while (isActive) {
              val line = reader.readLine() ?: break
              lastStderrLine = line
              DebugLog.log("rtl_433 stderr: $line")
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
        val message = if (readingCount == 0) {
          "rtl_433 exited with code $exitCode before producing readings$detail"
        } else {
          "rtl_433 exited with code $exitCode$detail"
        }
        DebugLog.log(message, level = android.util.Log.ERROR)
        withContext(Dispatchers.Main) {
          if (!stopRequested && process === proc) {
            onError(message)
          }
        }
      }
    } catch (e: Exception) {
      relay?.close()
      DebugLog.log("Failed to start rtl_433: ${e.message}", level = android.util.Log.ERROR)
      onError("Failed to start rtl_433: ${e.message}")
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
        false // Process has exited
      } catch (_: IllegalThreadStateException) {
        true // Process is still running
      }
    }

  companion object {
    internal fun buildArgs(
      binaryPath: String,
      hardwareProfile: SdrHardwareProfile,
      frequencyHz: Int,
      gain: Int?
    ): List<String> = buildList {
      add(binaryPath)
      hardwareProfile.rtl433DeviceArg?.let {
        add("-d")
        add(it)
      }
      add("-f"); add(frequencyHz.toString())
      add("-F"); add("json")
      add("-M"); add("level")
      gain?.let { add("-g"); add(it.toString()) }
    }
  }
}
