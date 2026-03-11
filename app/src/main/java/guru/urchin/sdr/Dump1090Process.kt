package guru.urchin.sdr

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import guru.urchin.util.DebugLog
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class Dump1090Process(private val context: Context) {
  private var process: Process? = null
  private var readerJob: Job? = null
  private var errorReaderJob: Job? = null

  fun start(
    scope: CoroutineScope,
    hardwareProfile: SdrHardwareProfile,
    gain: Int?,
    onReading: (SdrReading.Adsb) -> Unit,
    onError: (String) -> Unit
  ) {
    stop()
    val status = SdrRuntimeInspector.dump1090Status(context)
    if (!status.exists) {
      DebugLog.log(status.missingMessage(), level = android.util.Log.ERROR)
      onError(status.missingMessage())
      return
    }
    val binary = File(requireNotNull(status.resolvedLocation))

    val args = buildList {
      add(binary.absolutePath)
      add("--net")
      add("--net-only")
      add("--write-json")
      add("-")
      gain?.let { add("--gain"); add(it.toString()) }
    }

    try {
      DebugLog.log("Starting dump1090: ${args.joinToString(" ")}")
      val proc = ProcessBuilder(args)
        .redirectErrorStream(false)
        .start()
      process = proc

      readerJob = scope.launch(Dispatchers.IO) {
        try {
          BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
            while (isActive) {
              val line = reader.readLine() ?: break
              val reading = AdsbJsonParser.parse(line)
              if (reading != null) {
                onReading(reading)
              }
            }
          }
        } catch (e: Exception) {
          if (isActive) {
            DebugLog.log("dump1090 stdout read error: ${e.message}", level = android.util.Log.ERROR)
          }
        }
      }

      errorReaderJob = scope.launch(Dispatchers.IO) {
        try {
          BufferedReader(InputStreamReader(proc.errorStream)).use { reader ->
            while (isActive) {
              val line = reader.readLine() ?: break
              DebugLog.log("dump1090 stderr: $line")
            }
          }
        } catch (_: Exception) {}
      }
    } catch (e: Exception) {
      DebugLog.log("Failed to start dump1090: ${e.message}", level = android.util.Log.ERROR)
      onError("Failed to start dump1090: ${e.message}")
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
}

class Dump1090JsonPoller {
  private var pollingJob: Job? = null

  fun start(
    scope: CoroutineScope,
    url: String,
    intervalMs: Long = 1000L,
    onReading: (SdrReading.Adsb) -> Unit,
    onError: (String) -> Unit
  ) {
    stop()
    pollingJob = scope.launch(Dispatchers.IO) {
      while (isActive) {
        try {
          val connection = URL(url).openConnection() as HttpURLConnection
          try {
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val json = connection.inputStream.bufferedReader().readText()
            val aircraft = AdsbJsonParser.parseAircraftArray(json)
            for (reading in aircraft) {
              onReading(reading)
            }
          } finally {
            connection.disconnect()
          }
        } catch (e: Exception) {
          if (isActive) {
            DebugLog.log("ADS-B JSON poll error: ${e.message}")
          }
        }
        kotlinx.coroutines.delay(intervalMs)
      }
    }
  }

  fun stop() {
    pollingJob?.cancel()
    pollingJob = null
  }
}
