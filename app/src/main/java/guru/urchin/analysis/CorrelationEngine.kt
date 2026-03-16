package guru.urchin.analysis

import guru.urchin.data.CorrelationDao
import guru.urchin.data.CorrelationEntity
import guru.urchin.data.SightingDao
import guru.urchin.data.SightingEntity
import guru.urchin.util.DebugLog

/**
 * Cross-protocol correlation engine. Identifies emitters across different protocols
 * that consistently co-occur within a configurable time window, suggesting they
 * are co-located or co-moving (e.g., a vehicle's TPMS sensors alongside a
 * Meshtastic node, or a building's Z-Wave network and Sidewalk gateway).
 *
 * Runs periodically and updates [CorrelationEntity] records with confidence scores
 * based on temporal co-occurrence frequency.
 */
class CorrelationEngine(
  private val sightingDao: SightingDao,
  private val correlationDao: CorrelationDao
) {
  companion object {
    private const val CO_OCCURRENCE_WINDOW_MS = 30_000L
    private const val MIN_CO_OCCURRENCES = 3
    private const val ANALYSIS_WINDOW_MS = 7 * 24 * 60 * 60 * 1000L
    private const val PRUNE_AGE_MS = 30 * 24 * 60 * 60 * 1000L
  }

  suspend fun runCorrelation() {
    val now = System.currentTimeMillis()
    val since = now - ANALYSIS_WINDOW_MS
    val sightings = sightingDao.getSightingsAfter(since)

    if (sightings.size < 2) return

    DebugLog.log("Correlation: analyzing ${sightings.size} sightings")

    // Group sightings by device key
    val byDevice = sightings.groupBy { it.deviceKey }
    if (byDevice.size < 2) return

    // Build temporal index: for each sighting, find other devices seen within the window
    val deviceKeys = byDevice.keys.toList()
    val coOccurrenceCounts = mutableMapOf<Pair<String, String>, MutableList<Long>>()

    for (i in deviceKeys.indices) {
      val keyA = deviceKeys[i]
      val sightingsA = byDevice[keyA] ?: continue
      val protocolA = sightingsA.firstOrNull()?.protocolType

      for (j in (i + 1) until deviceKeys.size) {
        val keyB = deviceKeys[j]
        val sightingsB = byDevice[keyB] ?: continue
        val protocolB = sightingsB.firstOrNull()?.protocolType

        // Only correlate across different protocols
        if (protocolA == protocolB) continue

        val timestamps = findCoOccurrences(sightingsA, sightingsB)
        if (timestamps.isNotEmpty()) {
          val pair = if (keyA < keyB) keyA to keyB else keyB to keyA
          coOccurrenceCounts.getOrPut(pair) { mutableListOf() }.addAll(timestamps)
        }
      }
    }

    // Store correlations that meet the minimum threshold
    var stored = 0
    for ((pair, timestamps) in coOccurrenceCounts) {
      if (timestamps.size < MIN_CO_OCCURRENCES) continue

      val confidence = calculateConfidence(
        pair, byDevice, timestamps.size
      )

      correlationDao.upsertCorrelation(
        CorrelationEntity(
          deviceKeyA = pair.first,
          deviceKeyB = pair.second,
          correlationType = "temporal",
          confidence = confidence,
          coOccurrences = timestamps.size,
          firstCorrelated = timestamps.min(),
          lastCorrelated = timestamps.max()
        )
      )
      stored++
    }

    // Prune stale correlations
    correlationDao.pruneOlderThan(now - PRUNE_AGE_MS)

    DebugLog.log("Correlation: stored $stored cross-protocol correlations")
  }

  private fun findCoOccurrences(
    sightingsA: List<SightingEntity>,
    sightingsB: List<SightingEntity>
  ): List<Long> {
    val sortedB = sightingsB.sortedBy { it.timestamp }
    val timestamps = mutableListOf<Long>()

    for (a in sightingsA) {
      val matchIdx = sortedB.binarySearchBy(a.timestamp) { it.timestamp }
      val searchStart = (if (matchIdx >= 0) matchIdx else -(matchIdx + 1) - 1).coerceAtLeast(0)

      for (offset in -1..1) {
        val idx = searchStart + offset
        if (idx < 0 || idx >= sortedB.size) continue
        val b = sortedB[idx]
        if (kotlin.math.abs(a.timestamp - b.timestamp) <= CO_OCCURRENCE_WINDOW_MS) {
          timestamps.add(a.timestamp)
          break
        }
      }
    }
    return timestamps
  }

  private fun calculateConfidence(
    pair: Pair<String, String>,
    byDevice: Map<String, List<SightingEntity>>,
    coCount: Int
  ): Double {
    val countA = byDevice[pair.first]?.size ?: 1
    val countB = byDevice[pair.second]?.size ?: 1
    val minCount = minOf(countA, countB)
    // Jaccard-like: co-occurrences / opportunities
    return (coCount.toDouble() / minCount).coerceAtMost(1.0)
  }
}
