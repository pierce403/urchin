package guru.urchin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SightingDao {
  @Query("SELECT * FROM sightings WHERE deviceKey = :deviceKey ORDER BY timestamp DESC")
  fun observeSightings(deviceKey: String): Flow<List<SightingEntity>>

  @Insert
  suspend fun insertSighting(sighting: SightingEntity)

  @Query("DELETE FROM sightings WHERE timestamp < :threshold AND protocolType = :protocol")
  suspend fun pruneOlderThanForProtocol(threshold: Long, protocol: String)

  @Query("DELETE FROM sightings WHERE timestamp < :threshold AND protocolType IS NULL")
  suspend fun pruneOlderThanForNullProtocol(threshold: Long)
}
