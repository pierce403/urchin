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

  fun start(
    scope: CoroutineScope,
    hardwareProfile: SdrHardwareProfile,
    frequencyHz: Int,
    gain: Int?,
    onReading: (SdrReading) -> Unit,
    onError: (String) -> Unit
  ) {
    stop()
    val binary = try {
      Rtl433BinaryInstaller.ensureInstalled(context)
    } catch (e: Exception) {
      val message = e.message ?: "Failed to install bundled rtl_433."
      DebugLog.log(message, level = android.util.Log.ERROR, throwable = e)
      onError(message)
      return
    }

    val args = buildList {
      add(binary.absolutePath)
      hardwareProfile.rtl433DeviceArg?.let {
        add("-d")
        add(it)
      }
      add("-f"); add(frequencyHz.toString())
      add("-F"); add("json")
      add("-M"); add("level")
      gain?.let { add("-g"); add(it.toString()) }
    }

    try {
      DebugLog.log("Starting rtl_433: ${args.joinToString(" ")}")
      val proc = ProcessBuilder(args)
        .redirectErrorStream(false)
        .start()
      process = proc

      readerJob = scope.launch(Dispatchers.IO) {
        try {
          BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
            while (isActive) {
              val line = reader.readLine() ?: break
              val reading = Rtl433JsonParser.parse(line)
              if (reading != null) {
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
              DebugLog.log("rtl_433 stderr: $line")
            }
          }
        } catch (_: Exception) {
          // Ignore stderr read errors
        }
      }
    } catch (e: Exception) {
      DebugLog.log("Failed to start rtl_433: ${e.message}", level = android.util.Log.ERROR)
      onError("Failed to start rtl_433: ${e.message}")
    }
  }

  fun stop() {
    readerJob?.cancel()
    readerJob = null
    errorReaderJob?.cancel()
    errorReaderJob = null
    process?.destroy()
    process = null
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
}
