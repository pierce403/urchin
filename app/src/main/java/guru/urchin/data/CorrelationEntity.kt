package guru.urchin.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
  tableName = "correlations",
  primaryKeys = ["deviceKeyA", "deviceKeyB"],
  indices = [Index(value = ["deviceKeyA"]), Index(value = ["deviceKeyB"])]
)
data class CorrelationEntity(
  val deviceKeyA: String,
  val deviceKeyB: String,
  val correlationType: String,
  val confidence: Double,
  val coOccurrences: Int,
  val firstCorrelated: Long,
  val lastCorrelated: Long
)
