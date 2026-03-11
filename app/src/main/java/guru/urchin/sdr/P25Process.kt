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

  fun start(
    scope: CoroutineScope,
    hardwareProfile: SdrHardwareProfile,
    frequencyHz: Int,
    gain: Int?,
    onReading: (SdrReading.P25) -> Unit,
    onError: (String) -> Unit
  ) {
    stop()
    val binary = resolveBinary()
    if (!binary.exists()) {
      DebugLog.log("p25_scanner binary not found at ${binary.absolutePath}", level = android.util.Log.ERROR)
      onError("P25 scanner binary not found. NDK build required.")
      return
    }

    val args = buildList {
      add(binary.absolutePath)
      add("-f"); add(frequencyHz.toString())
      gain?.let { add("-g"); add(it.toString()) }
    }

    try {
      DebugLog.log("Starting p25_scanner: ${args.joinToString(" ")}")
      val proc = ProcessBuilder(args)
        .redirectErrorStream(false)
        .start()
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
              DebugLog.log("p25_scanner stderr: $line")
            }
          }
        } catch (_: Exception) {
          // Ignore stderr read errors
        }
      }
    } catch (e: Exception) {
      DebugLog.log("Failed to start p25_scanner: ${e.message}", level = android.util.Log.ERROR)
      onError("Failed to start P25 scanner: ${e.message}")
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
        false
      } catch (_: IllegalThreadStateException) {
        true
      }
    }

  private fun resolveBinary(): File {
    val nativeDir = File(context.applicationInfo.nativeLibraryDir)
    val candidates = listOf(
      File(nativeDir, "libp25_scanner.so"),
      File(nativeDir, "p25_scanner")
    )
    return candidates.firstOrNull(File::exists) ?: candidates.first()
  }
}
