package guru.urchin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CorrelationDao {
  @Query("SELECT * FROM correlations WHERE deviceKeyA = :deviceKey OR deviceKeyB = :deviceKey ORDER BY confidence DESC")
  fun observeCorrelations(deviceKey: String): Flow<List<CorrelationEntity>>

  @Query("SELECT * FROM correlations WHERE deviceKeyA = :deviceKey OR deviceKeyB = :deviceKey ORDER BY confidence DESC")
  suspend fun getCorrelations(deviceKey: String): List<CorrelationEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertCorrelation(correlation: CorrelationEntity)

  @Query("DELETE FROM correlations WHERE lastCorrelated < :threshold")
  suspend fun pruneOlderThan(threshold: Long)

  @Query("SELECT * FROM correlations ORDER BY confidence DESC")
  suspend fun getAllCorrelations(): List<CorrelationEntity>
}
