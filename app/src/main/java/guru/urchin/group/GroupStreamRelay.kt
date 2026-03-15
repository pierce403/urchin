package guru.urchin.group

import guru.urchin.data.DeviceObservation
import guru.urchin.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Real-time encrypted observation streaming for affinity group members.
 * Connects to a relay server over TCP and exchanges observations encrypted
 * with the group's AES-256-GCM key.
 *
 * Outbound: serializes [DeviceObservation] → JSON → AES-256-GCM encrypt → base64 line → TCP
 * Inbound:  TCP → base64 line → AES-256-GCM decrypt → JSON → [DeviceObservation] → SharedFlow
 */
class GroupStreamRelay(
  private val scope: CoroutineScope
) {
  private val _inboundObservations = MutableSharedFlow<DeviceObservation>(extraBufferCapacity = 64)
  val inboundObservations: SharedFlow<DeviceObservation> = _inboundObservations

  private var outSocket: Socket? = null
  private var outWriter: PrintWriter? = null
  private var readJob: Job? = null
  private var groupKeyBytes: ByteArray? = null
  private var memberId: String? = null

  @Volatile
  var connected = false
    private set

  fun connect(host: String, port: Int, groupKey: ByteArray, memberId: String) {
    this.groupKeyBytes = groupKey
    this.memberId = memberId

    readJob = scope.launch(Dispatchers.IO) {
      try {
        val socket = Socket(host, port)
        outSocket = socket
        outWriter = PrintWriter(socket.getOutputStream(), true)
        connected = true
        DebugLog.log("GroupStream: connected to $host:$port")

        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        while (!socket.isClosed) {
          val line = reader.readLine() ?: break
          try {
            val obs = decryptObservation(line, groupKey)
            if (obs != null) {
              _inboundObservations.tryEmit(obs)
            }
          } catch (e: Exception) {
            DebugLog.log("GroupStream: decrypt error: ${e.message}")
          }
        }
      } catch (e: Exception) {
        DebugLog.log("GroupStream: connection error: ${e.message}")
      } finally {
        connected = false
        outSocket = null
        outWriter = null
      }
    }
  }

  fun disconnect() {
    readJob?.cancel()
    readJob = null
    try {
      outSocket?.close()
    } catch (_: Exception) {}
    outSocket = null
    outWriter = null
    connected = false
  }

  fun sendObservation(observation: DeviceObservation) {
    val key = groupKeyBytes ?: return
    val writer = outWriter ?: return
    scope.launch(Dispatchers.IO) {
      try {
        val encrypted = encryptObservation(observation, key, memberId ?: "unknown")
        writer.println(encrypted)
      } catch (e: Exception) {
        DebugLog.log("GroupStream: send error: ${e.message}")
      }
    }
  }

  private fun encryptObservation(obs: DeviceObservation, key: ByteArray, senderId: String): String {
    val json = JSONObject().apply {
      put("deviceKey", obs.deviceKey)
      put("name", obs.name)
      put("address", obs.address)
      put("rssi", obs.rssi)
      put("timestamp", obs.timestamp)
      put("metadataJson", obs.metadataJson)
      put("protocolType", obs.protocolType)
      put("senderId", senderId)
    }
    val plaintext = json.toString().toByteArray()
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
    val ciphertext = cipher.doFinal(plaintext)
    // Format: base64(iv + ciphertext)
    val combined = iv + ciphertext
    return android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP)
  }

  private fun decryptObservation(line: String, key: ByteArray): DeviceObservation? {
    val combined = android.util.Base64.decode(line, android.util.Base64.NO_WRAP)
    if (combined.size < 13) return null
    val iv = combined.copyOfRange(0, 12)
    val ciphertext = combined.copyOfRange(12, combined.size)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
    val plaintext = cipher.doFinal(ciphertext)
    val json = JSONObject(String(plaintext))
    return DeviceObservation(
      deviceKey = json.getString("deviceKey"),
      name = json.optString("name", null),
      address = json.optString("address", null),
      rssi = json.getInt("rssi"),
      timestamp = json.getLong("timestamp"),
      metadataJson = json.optString("metadataJson", null),
      protocolType = json.optString("protocolType", null)
    )
  }
}
