package guru.urchin.analysis

import guru.urchin.data.CorrelationEntity
import guru.urchin.data.DeviceEntity
import guru.urchin.data.SightingEntity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Electronic Order of Battle (EOB) report builder. Produces a structured
 * inventory of all observed RF emitters organized by protocol, with operating
 * parameters, activity patterns, and correlated device clusters.
 */
object EobBuilder {

  private val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
  }

  fun buildReport(
    devices: List<DeviceEntity>,
    sightings: List<SightingEntity>,
    correlations: List<CorrelationEntity>
  ): String {
    val json = JSONObject()
    json.put("reportType", "Electronic Order of Battle")
    json.put("generatedAt", utcFormat.format(Date()))
    json.put("totalEmitters", devices.size)
    json.put("totalSightings", sightings.size)

    // Summary by protocol
    val byProtocol = devices.groupBy { it.protocolType ?: "unknown" }
    val protocolSummaries = JSONArray()

    for ((protocol, protocolDevices) in byProtocol.entries.sortedByDescending { it.value.size }) {
      val summary = JSONObject()
      summary.put("protocol", protocol)
      summary.put("uniqueEmitters", protocolDevices.size)
      summary.put("totalObservations", protocolDevices.sumOf { it.observationCount })

      val earliest = protocolDevices.minOf { it.firstSeen }
      val latest = protocolDevices.maxOf { it.lastSeen }
      summary.put("firstSeen", utcFormat.format(Date(earliest)))
      summary.put("lastSeen", utcFormat.format(Date(latest)))

      val rssiValues = protocolDevices.map { it.rssiAvg }
      summary.put("avgRssi", rssiValues.average().let { String.format(Locale.US, "%.1f", it) })
      summary.put("minRssi", protocolDevices.minOf { it.rssiMin })
      summary.put("maxRssi", protocolDevices.maxOf { it.rssiMax })

      // Top emitters by observation count
      val topEmitters = JSONArray()
      for (device in protocolDevices.sortedByDescending { it.observationCount }.take(10)) {
        val emitter = JSONObject()
        emitter.put("id", device.lastAddress ?: device.deviceKey.take(12))
        emitter.put("name", device.userCustomName ?: device.displayName)
        emitter.put("observations", device.observationCount)
        emitter.put("sightings", device.sightingsCount)
        emitter.put("avgRssi", String.format(Locale.US, "%.1f", device.rssiAvg))
        emitter.put("firstSeen", utcFormat.format(Date(device.firstSeen)))
        emitter.put("lastSeen", utcFormat.format(Date(device.lastSeen)))
        topEmitters.put(emitter)
      }
      summary.put("topEmitters", topEmitters)

      protocolSummaries.put(summary)
    }
    json.put("protocols", protocolSummaries)

    // Correlated clusters
    if (correlations.isNotEmpty()) {
      val deviceMap = devices.associateBy { it.deviceKey }
      val clusters = JSONArray()
      for (c in correlations.sortedByDescending { it.confidence }) {
        val cluster = JSONObject()
        val deviceA = deviceMap[c.deviceKeyA]
        val deviceB = deviceMap[c.deviceKeyB]
        cluster.put("emitterA", JSONObject().apply {
          put("protocol", deviceA?.protocolType)
          put("name", deviceA?.userCustomName ?: deviceA?.displayName)
          put("id", deviceA?.lastAddress ?: c.deviceKeyA.take(12))
        })
        cluster.put("emitterB", JSONObject().apply {
          put("protocol", deviceB?.protocolType)
          put("name", deviceB?.userCustomName ?: deviceB?.displayName)
          put("id", deviceB?.lastAddress ?: c.deviceKeyB.take(12))
        })
        cluster.put("confidence", String.format(Locale.US, "%.0f%%", c.confidence * 100))
        cluster.put("coOccurrences", c.coOccurrences)
        clusters.put(cluster)
      }
      json.put("correlatedClusters", clusters)
    }

    return json.toString(2)
  }

  fun buildTextReport(
    devices: List<DeviceEntity>,
    sightings: List<SightingEntity>,
    correlations: List<CorrelationEntity>
  ): String {
    val sb = StringBuilder()
    sb.appendLine("=== ELECTRONIC ORDER OF BATTLE ===")
    sb.appendLine("Generated: ${utcFormat.format(Date())}")
    sb.appendLine("Total emitters: ${devices.size}")
    sb.appendLine("Total sightings: ${sightings.size}")
    sb.appendLine()

    val byProtocol = devices.groupBy { it.protocolType ?: "unknown" }
    for ((protocol, protocolDevices) in byProtocol.entries.sortedByDescending { it.value.size }) {
      sb.appendLine("--- ${protocol.uppercase()} (${protocolDevices.size} emitters) ---")
      for (device in protocolDevices.sortedByDescending { it.observationCount }) {
        val id = device.lastAddress ?: device.deviceKey.take(12)
        val name = device.userCustomName ?: device.displayName ?: ""
        val rssi = String.format(Locale.US, "%.0f", device.rssiAvg)
        sb.appendLine("  $id  $name  RSSI avg=$rssi  obs=${device.observationCount}  ${utcFormat.format(Date(device.lastSeen))}")
      }
      sb.appendLine()
    }

    if (correlations.isNotEmpty()) {
      val deviceMap = devices.associateBy { it.deviceKey }
      sb.appendLine("--- CORRELATED CLUSTERS ---")
      for (c in correlations.sortedByDescending { it.confidence }) {
        val a = deviceMap[c.deviceKeyA]
        val b = deviceMap[c.deviceKeyB]
        val nameA = "${a?.protocolType?.uppercase() ?: "?"} ${a?.displayName ?: c.deviceKeyA.take(8)}"
        val nameB = "${b?.protocolType?.uppercase() ?: "?"} ${b?.displayName ?: c.deviceKeyB.take(8)}"
        sb.appendLine("  $nameA <-> $nameB  ${(c.confidence * 100).toInt()}% (${c.coOccurrences} co-occurrences)")
      }
    }

    return sb.toString()
  }
}
