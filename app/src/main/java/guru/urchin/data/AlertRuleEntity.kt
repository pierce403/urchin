package guru.urchin.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alert_rules")
data class AlertRuleEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val matchType: String,
  val matchPattern: String,
  val displayValue: String,
  val emoji: String,
  val soundPreset: String,
  val enabled: Boolean,
  val createdAt: Long,
  val rssiThreshold: Int? = null,
  val absenceMinutes: Int? = null
)
