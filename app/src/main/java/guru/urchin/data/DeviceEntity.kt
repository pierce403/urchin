package guru.urchin.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "devices",
  indices = [Index(value = ["lastSeen"]), Index(value = ["protocolType", "lastSeen"])]
)
data class DeviceEntity(
  @PrimaryKey val deviceKey: String,
  val displayName: String?,
  val lastAddress: String?,
  val firstSeen: Long,
  val lastSeen: Long,
  val lastSightingAt: Long,
  val sightingsCount: Int,
  val observationCount: Int,
  val lastRssi: Int,
  val rssiMin: Int,
  val rssiMax: Int,
  val rssiAvg: Double,
  val lastMetadataJson: String?,
  val starred: Boolean,
  val userCustomName: String? = null,
  val protocolType: String? = null
)
