package guru.urchin.group

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import guru.urchin.data.AlertRuleDao
import guru.urchin.data.DeviceDao
import guru.urchin.data.DeviceEntity
import guru.urchin.data.SightingDao

/**
 * Merges imported bundle data into the local database using additive-only semantics.
 * Never deletes or downgrades local data.
 *
 * Uses max() for counts instead of sum to prevent inflation on re-exchange.
 * Batch-loads existing sighting keys for O(1) dedup lookups.
 * Entire merge runs inside a Room transaction for atomicity.
 */
class DataMerger(
  private val database: RoomDatabase,
  private val deviceDao: DeviceDao,
  private val sightingDao: SightingDao,
  private val alertRuleDao: AlertRuleDao
) {
  suspend fun merge(payload: BundlePayload, groupId: String? = null): MergeResult {
    return database.withTransaction {
      mergeInTransaction(payload, groupId)
    }
  }

  private suspend fun mergeInTransaction(payload: BundlePayload, groupId: String?): MergeResult {
    var devicesAdded = 0
    var devicesUpdated = 0
    var sightingsAdded = 0
    var alertRulesAdded = 0

    for (imported in payload.devices) {
      val local = deviceDao.getDevice(imported.deviceKey)
      if (local == null) {
        val withOrigin = if (groupId != null) {
          imported.copy(sharedFromGroupIds = groupId)
        } else imported
        deviceDao.upsertDevice(withOrigin)
        devicesAdded++
      } else {
        val merged = mergeDevice(local, imported, groupId)
        if (merged != local) {
          deviceDao.upsertDevice(merged)
          devicesUpdated++
        }
      }
    }

    if (payload.sightings.isNotEmpty()) {
      val importedDeviceKeys = payload.sightings.map { it.deviceKey }.distinct()
      val existingKeys = importedDeviceKeys.chunked(500).flatMap { chunk ->
        sightingDao.getExistingSightingKeys(chunk)
      }.toHashSet()

      for (imported in payload.sightings) {
        val key = "${imported.deviceKey}|${imported.timestamp}"
        if (key !in existingKeys) {
          sightingDao.insertSighting(imported.copy(id = 0))
          existingKeys.add(key)
          sightingsAdded++
        }
      }
    }

    if (payload.alertRules.isNotEmpty()) {
      val existingRules = alertRuleDao.getRules()
      val existingRuleKeys = existingRules.map { "${it.matchType}|${it.matchPattern}" }.toSet()
      for (imported in payload.alertRules) {
        val key = "${imported.matchType}|${imported.matchPattern}"
        if (key !in existingRuleKeys) {
          alertRuleDao.insert(imported.copy(id = 0))
          alertRulesAdded++
        }
      }
    }

    for (deviceKey in payload.starredDeviceKeys) {
      val device = deviceDao.getDevice(deviceKey)
      if (device != null && !device.starred) {
        deviceDao.setStarred(deviceKey, true)
      }
    }

    return MergeResult(
      devicesAdded = devicesAdded,
      devicesUpdated = devicesUpdated,
      sightingsAdded = sightingsAdded,
      alertRulesAdded = alertRulesAdded
    )
  }

  private fun mergeDevice(local: DeviceEntity, imported: DeviceEntity, groupId: String?): DeviceEntity {
    val mergedGroupIds = unionGroupIds(local.sharedFromGroupIds, groupId)
    return local.copy(
      firstSeen = minOf(local.firstSeen, imported.firstSeen),
      lastSeen = maxOf(local.lastSeen, imported.lastSeen),
      lastSightingAt = maxOf(local.lastSightingAt, imported.lastSightingAt),
      sightingsCount = maxOf(local.sightingsCount, imported.sightingsCount),
      observationCount = maxOf(local.observationCount, imported.observationCount),
      lastRssi = if (imported.lastSeen > local.lastSeen) imported.lastRssi else local.lastRssi,
      rssiMin = minOf(local.rssiMin, imported.rssiMin),
      rssiMax = maxOf(local.rssiMax, imported.rssiMax),
      rssiAvg = (local.rssiAvg + imported.rssiAvg) / 2.0,
      lastMetadataJson = if (imported.lastSeen > local.lastSeen) imported.lastMetadataJson else local.lastMetadataJson,
      starred = local.starred || imported.starred,
      displayName = local.displayName ?: imported.displayName,
      lastAddress = if (imported.lastSeen > local.lastSeen) imported.lastAddress else local.lastAddress,
      userCustomName = local.userCustomName ?: imported.userCustomName,
      sharedFromGroupIds = mergedGroupIds
    )
  }

  private fun unionGroupIds(existing: String?, newGroupId: String?): String? {
    if (newGroupId == null) return existing
    if (existing == null) return newGroupId
    val ids = existing.split(",").toMutableSet()
    ids.add(newGroupId)
    return ids.joinToString(",")
  }
}

data class MergeResult(
  val devicesAdded: Int,
  val devicesUpdated: Int,
  val sightingsAdded: Int,
  val alertRulesAdded: Int
) {
  val totalItems get() = devicesAdded + devicesUpdated + sightingsAdded + alertRulesAdded
}
