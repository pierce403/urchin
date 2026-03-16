package guru.urchin.analysis

import guru.urchin.data.DeviceDao
import guru.urchin.data.SightingDao
import guru.urchin.util.DebugLog

/**
 * Detects statistically unusual RF activity against rolling baselines.
 * Flags new emitter surges, RSSI anomalies, and temporal pattern deviations.
 * Results are reported via [AnomalyListener] for integration with the alert system.
 */
class AnomalyDetector(
  private val deviceDao: DeviceDao,
  private val sightingDao: SightingDao
) {

  data class Anomaly(
    val type: AnomalyType,
    val protocolType: String?,
    val description: String,
    val severity: Double,
    val timestamp: Long
  )

  enum class AnomalyType {
    NEW_EMITTER_SURGE,
    RSSI_ANOMALY,
    TRANSMISSION_RATE_ANOMALY,
    TEMPORAL_ANOMALY
  }

  fun interface AnomalyListener {
    fun onAnomalyDetected(anomaly: Anomaly)
  }

  private var listener: AnomalyListener? = null
  private val baselineDeviceCountByProtocol = mutableMapOf<String, RollingAverage>()
  private var lastCheckTimestamp = 0L

  fun setListener(listener: AnomalyListener) {
    this.listener = listener
  }

  suspend fun runDetection() {
    val now = System.currentTimeMillis()
    val windowMs = ANALYSIS_WINDOW_MS

    val recentSightings = sightingDao.getSightingsAfter(now - windowMs)
    if (recentSightings.isEmpty()) return

    // Check for new emitter surges per protocol
    val devices = deviceDao.getDevices()
    val devicesByProtocol = devices.groupBy { it.protocolType ?: "unknown" }

    for ((protocol, protocolDevices) in devicesByProtocol) {
      val recentCount = protocolDevices.count {
        it.firstSeen > now - SURGE_WINDOW_MS
      }

      val baseline = baselineDeviceCountByProtocol.getOrPut(protocol) {
        RollingAverage()
      }

      if (baseline.count >= MIN_BASELINE_SAMPLES) {
        val avg = baseline.average
        val threshold = avg * SURGE_MULTIPLIER + SURGE_MIN_NEW
        if (recentCount > threshold && recentCount >= SURGE_MIN_NEW) {
          val severity = (recentCount - avg) / avg.coerceAtLeast(1.0)
          report(Anomaly(
            type = AnomalyType.NEW_EMITTER_SURGE,
            protocolType = protocol,
            description = "$recentCount new ${protocol.uppercase()} emitters in last ${SURGE_WINDOW_MS / 60000} min (baseline: ${avg.toInt()})",
            severity = severity.coerceAtMost(1.0),
            timestamp = now
          ))
        }
      }

      baseline.add(recentCount.toDouble())
    }

    // Check for RSSI anomalies: devices with RSSI significantly different from their average
    for (device in devices) {
      if (device.observationCount < MIN_OBSERVATIONS_FOR_RSSI_ANOMALY) continue
      val deviation = device.lastRssi - device.rssiAvg
      if (deviation > RSSI_ANOMALY_THRESHOLD_DB) {
        report(Anomaly(
          type = AnomalyType.RSSI_ANOMALY,
          protocolType = device.protocolType,
          description = "${device.displayName ?: device.deviceKey.take(8)}: RSSI ${device.lastRssi} dBm vs avg ${device.rssiAvg.toInt()} dBm (+${deviation.toInt()} dB)",
          severity = (deviation / 30.0).coerceAtMost(1.0),
          timestamp = now
        ))
      }
    }

    lastCheckTimestamp = now
    DebugLog.log("Anomaly detection: checked ${devices.size} devices across ${devicesByProtocol.size} protocols")
  }

  private fun report(anomaly: Anomaly) {
    DebugLog.log("ANOMALY: ${anomaly.type} - ${anomaly.description}")
    listener?.onAnomalyDetected(anomaly)
  }

  private class RollingAverage {
    var count = 0
      private set
    var average = 0.0
      private set

    fun add(value: Double) {
      count++
      average += (value - average) / count
    }
  }

  companion object {
    private const val ANALYSIS_WINDOW_MS = 60 * 60 * 1000L // 1 hour
    private const val SURGE_WINDOW_MS = 30 * 60 * 1000L // 30 minutes
    private const val SURGE_MULTIPLIER = 3.0
    private const val SURGE_MIN_NEW = 5
    private const val MIN_BASELINE_SAMPLES = 3
    private const val RSSI_ANOMALY_THRESHOLD_DB = 20.0
    private const val MIN_OBSERVATIONS_FOR_RSSI_ANOMALY = 10
  }
}
