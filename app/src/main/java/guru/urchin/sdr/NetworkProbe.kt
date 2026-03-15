package guru.urchin.sdr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

data class ProbeTarget(val label: String, val host: String, val port: Int)
data class ProbeResult(val target: ProbeTarget, val reachable: Boolean, val errorMessage: String? = null)

object NetworkProbe {
    private const val TIMEOUT_MS = 3000

    suspend fun probeAll(targets: List<ProbeTarget>): List<ProbeResult> =
        withContext(Dispatchers.IO) {
            targets.map { target ->
                async {
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(target.host, target.port), TIMEOUT_MS)
                        }
                        ProbeResult(target, reachable = true)
                    } catch (e: IOException) {
                        ProbeResult(target, reachable = false, errorMessage = e.message)
                    } catch (e: SecurityException) {
                        ProbeResult(target, reachable = false, errorMessage = e.message)
                    } catch (e: IllegalArgumentException) {
                        ProbeResult(target, reachable = false, errorMessage = "Invalid address: ${e.message}")
                    }
                }
            }.awaitAll()
        }
}
