package guru.urchin.sdr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import guru.urchin.util.DebugLog
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket

/**
 * Generic TCP line-stream client. Connects to a host:port, reads newline-delimited
 * text, and passes each line through [parseLine] to produce typed readings.
 * Used for rtl_433 (TPMS/POCSAG), dump1090 (ADS-B), and OP25 (P25) bridges.
 */
class TcpStreamBridge<T>(
  private val label: String,
  private val parseLine: (String) -> T?
) {
  private var connectionJob: Job? = null
  private var socket: Socket? = null

  fun connect(
    scope: CoroutineScope,
    host: String,
    port: Int,
    onReading: (T) -> Unit,
    onError: (String) -> Unit
  ) {
    disconnect()
    connectionJob = scope.launch(Dispatchers.IO) {
      try {
        DebugLog.log("$label connecting to $host:$port")
        val s = Socket(host, port)
        s.soTimeout = 30_000
        socket = s
        DebugLog.log("$label connected")
        BufferedReader(InputStreamReader(s.getInputStream())).use { reader ->
          while (isActive && !s.isClosed) {
            val line = reader.readLine() ?: break
            val reading = parseLine(line)
            if (reading != null) {
              onReading(reading)
            }
          }
        }
        DebugLog.log("$label stream ended")
      } catch (e: Exception) {
        if (isActive) {
          DebugLog.log("$label error: ${e.message}", level = android.util.Log.ERROR)
          withContext(Dispatchers.Main) { onError(e.message ?: "$label connection failed") }
        }
      } finally {
        socket?.close()
        socket = null
      }
    }
  }

  fun disconnect() {
    connectionJob?.cancel()
    connectionJob = null
    socket?.close()
    socket = null
  }

  val isConnected: Boolean
    get() = socket?.isConnected == true && socket?.isClosed == false
}
