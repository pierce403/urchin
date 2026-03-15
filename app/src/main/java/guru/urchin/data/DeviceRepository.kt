package guru.urchin.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Central data access for devices and sightings. [recordObservation] upserts the
 * device, inserts a sighting row on new sighting windows, and prunes old data
 * using per-protocol retention periods (ADS-B 7 days, P25 14 days, others 30 days).
 */
class DeviceRepository(
  private val database: AppDatabase,
  private val deviceDao: DeviceDao,
  private val sightingDao: SightingDao
) {
  private var lastPrunedAt = 0L

  companion object {
    private const val RETENTION_DAYS_DEFAULT = 30L
    private const val RETENTION_DAYS_ADSB = 7L
    private const val RETENTION_DAYS_P25 = 14L
    private const val RETENTION_DAYS_LORAWAN = 14L

    private const val RETENTION_DAYS_MESHTASTIC = 14L
    private const val RETENTION_DAYS_SIDEWALK = 14L
    // WmBus meters and Z-Wave nodes are fixed infrastructure — retain longer
    private const val RETENTION_DAYS_WMBUS = 30L
    private const val RETENTION_DAYS_ZWAVE = 30L

    fun retentionDaysForProtocol(protocolType: String?): Long = when (protocolType) {
      "adsb" -> RETENTION_DAYS_ADSB
      "p25" -> RETENTION_DAYS_P25
      "lorawan" -> RETENTION_DAYS_LORAWAN
      "meshtastic" -> RETENTION_DAYS_MESHTASTIC
      "sidewalk" -> RETENTION_DAYS_SIDEWALK
      "wmbus" -> RETENTION_DAYS_WMBUS
      "zwave" -> RETENTION_DAYS_ZWAVE
      else -> RETENTION_DAYS_DEFAULT
    }
  }

  fun observeDevices(): Flow<List<DeviceEntity>> = deviceDao.observeDevices()

  fun observeDevice(deviceKey: String): Flow<DeviceEntity?> = deviceDao.observeDevice(deviceKey)

  fun observeSightings(deviceKey: String): Flow<List<SightingEntity>> =
    sightingDao.observeSightings(deviceKey)

  suspend fun setStarred(deviceKey: String, starred: Boolean) {
    deviceDao.setStarred(deviceKey, starred)
  }

  suspend fun setUserCustomName(deviceKey: String, name: String?) {
    deviceDao.setUserCustomName(deviceKey, name?.takeIf(String::isNotBlank))
  }

  suspend fun recordObservation(observation: DeviceObservation) {
    database.withTransaction {
      guru.urchin.util.DebugLog.log(
        "Record observation key=${observation.deviceKey.take(8)} name=${observation.name ?: "unknown"} " +
          "rssi=${observation.rssi}"
      )
      val existing = deviceDao.getDevice(observation.deviceKey)
      val updated = if (existing == null) {
        DeviceEntity(
          deviceKey = observation.deviceKey,
          displayName = observation.name,
          lastAddress = observation.address,
          firstSeen = observation.timestamp,
          lastSeen = observation.timestamp,
          lastSightingAt = observation.timestamp,
          sightingsCount = 1,
          observationCount = 1,
          lastRssi = observation.rssi,
          rssiMin = observation.rssi,
          rssiMax = observation.rssi,
          rssiAvg = observation.rssi.toDouble(),
          lastMetadataJson = observation.metadataJson,
          starred = false,
          protocolType = observation.protocolType
        )
      } else {
        val isNewSighting = ContinuousSightingPolicy.isNewSighting(
          existing.lastSightingAt,
          observation.timestamp
        )
        val observationCount = existing.observationCount + 1
        val sightingsCount = if (isNewSighting) {
          existing.sightingsCount + 1
        } else {
          existing.sightingsCount
        }
        val avg = ((existing.rssiAvg * existing.observationCount) + observation.rssi) / observationCount
        DeviceEntity(
          deviceKey = existing.deviceKey,
          displayName = observation.name ?: existing.displayName,
          lastAddress = observation.address ?: existing.lastAddress,
          firstSeen = minOf(existing.firstSeen, observation.timestamp),
          lastSeen = maxOf(existing.lastSeen, observation.timestamp),
          lastSightingAt = if (isNewSighting) observation.timestamp else existing.lastSightingAt,
          sightingsCount = sightingsCount,
          observationCount = observationCount,
          lastRssi = observation.rssi,
          rssiMin = minOf(existing.rssiMin, observation.rssi),
          rssiMax = maxOf(existing.rssiMax, observation.rssi),
          rssiAvg = avg,
          lastMetadataJson = observation.metadataJson ?: existing.lastMetadataJson,
          starred = existing.starred,
          userCustomName = existing.userCustomName,
          protocolType = observation.protocolType ?: existing.protocolType
        )
      }

      deviceDao.upsertDevice(updated)
      if (existing == null || updated.lastSightingAt == observation.timestamp) {
        sightingDao.insertSighting(
          SightingEntity(
            deviceKey = observation.deviceKey,
            timestamp = observation.timestamp,
            rssi = observation.rssi,
            name = observation.name,
            address = observation.address,
            metadataJson = observation.metadataJson,
            protocolType = observation.protocolType,
            receiverLat = observation.receiverLat,
            receiverLon = observation.receiverLon
          )
        )
      }

      pruneIfNeeded(observation.timestamp)
    }
  }

  private suspend fun pruneIfNeeded(now: Long) {
    if (!DeviceMaintenancePolicy.shouldPrune(lastPrunedAt, now)) {
      return
    }
    val msPerDay = 24 * 60 * 60 * 1000L
    for ((protocol, days) in mapOf("adsb" to RETENTION_DAYS_ADSB, "p25" to RETENTION_DAYS_P25, "lorawan" to RETENTION_DAYS_LORAWAN, "meshtastic" to RETENTION_DAYS_MESHTASTIC, "sidewalk" to RETENTION_DAYS_SIDEWALK)) {
      val threshold = now - days * msPerDay
      sightingDao.pruneOlderThanForProtocol(threshold, protocol)
      deviceDao.deleteOlderThanForProtocol(threshold, protocol)
    }
    val defaultThreshold = now - RETENTION_DAYS_DEFAULT * msPerDay
    for (protocol in listOf("tpms", "pocsag", "wmbus", "zwave")) {
      sightingDao.pruneOlderThanForProtocol(defaultThreshold, protocol)
      deviceDao.deleteOlderThanForProtocol(defaultThreshold, protocol)
    }
    sightingDao.pruneOlderThanForNullProtocol(defaultThreshold)
    deviceDao.deleteOlderThanForNullProtocol(defaultThreshold)
    lastPrunedAt = now
  }
}
