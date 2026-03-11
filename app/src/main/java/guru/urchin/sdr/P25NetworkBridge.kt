package guru.urchin.sdr

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import guru.urchin.util.DebugLog
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Connects to a remote OP25 instance via HTTP polling and parses P25 control
 * channel metadata into SdrReading.P25 events.
 *
 * For TCP stream mode, use TcpStreamBridge with [parseP25Json] as the parser.
 */
class P25NetworkBridge {
  private var connectionJob: Job? = null

  fun connectHttp(
    scope: CoroutineScope,
    url: String,
    intervalMs: Long = 2000L,
    onReading: (SdrReading.P25) -> Unit,
    onError: (String) -> Unit
  ) {
    disconnect()
    connectionJob = scope.launch(Dispatchers.IO) {
      while (isActive) {
        try {
          val connection = URL(url).openConnection() as HttpURLConnection
          try {
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val jsonText = connection.inputStream.bufferedReader().readText()
            val readings = parseP25StatusJson(jsonText)
            for (reading in readings) {
              onReading(reading)
            }
          } finally {
            connection.disconnect()
          }
        } catch (e: Exception) {
          if (isActive) {
            DebugLog.log("P25 HTTP poll error: ${e.message}")
          }
        }
        delay(intervalMs)
      }
    }
  }

  fun disconnect() {
    connectionJob?.cancel()
    connectionJob = null
  }

  val isConnected: Boolean
    get() = connectionJob?.isActive == true

  companion object {
    fun parseP25Json(jsonLine: String): SdrReading.P25? {
      return try {
        val json = JSONObject(jsonLine)
        val unitId = json.optStringOrNull("unit_id")
          ?: json.optStringOrNull("src")
          ?: json.optStringOrNull("srcaddr")
          ?: return null

        SdrReading.P25(
          unitId = unitId,
          nac = json.optStringOrNull("nac"),
          wacn = json.optStringOrNull("wacn"),
          systemId = json.optStringOrNull("system_id") ?: json.optStringOrNull("sysid"),
          talkGroupId = json.optStringOrNull("talkgroup") ?: json.optStringOrNull("tgid"),
          rssi = json.optDoubleOrNull("rssi"),
          snr = json.optDoubleOrNull("snr"),
          frequencyMhz = json.optDoubleOrNull("freq"),
          rawJson = jsonLine
        )
      } catch (_: Exception) {
        null
      }
    }

    fun parseP25StatusJson(jsonText: String): List<SdrReading.P25> {
      return try {
        val json = JSONObject(jsonText)
        val units = json.optJSONArray("units") ?: return emptyList()
        (0 until units.length()).mapNotNull { i ->
          val obj = units.optJSONObject(i) ?: return@mapNotNull null
          val unitId = obj.optStringOrNull("unit_id")
            ?: obj.optStringOrNull("src")
            ?: return@mapNotNull null
          SdrReading.P25(
            unitId = unitId,
            nac = obj.optStringOrNull("nac"),
            wacn = obj.optStringOrNull("wacn"),
            systemId = obj.optStringOrNull("system_id") ?: obj.optStringOrNull("sysid"),
            talkGroupId = obj.optStringOrNull("talkgroup") ?: obj.optStringOrNull("tgid"),
            rssi = obj.optDoubleOrNull("rssi"),
            snr = null,
            frequencyMhz = obj.optDoubleOrNull("freq"),
            rawJson = obj.toString()
          )
        }
      } catch (_: Exception) {
        emptyList()
      }
    }
  }
}
