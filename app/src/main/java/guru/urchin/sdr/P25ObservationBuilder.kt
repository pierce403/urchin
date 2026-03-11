package guru.urchin.sdr

import guru.urchin.scan.ObservationInput

object P25ObservationBuilder {
  fun build(reading: SdrReading.P25): ObservationInput {
    val talkGroupPart = reading.talkGroupId?.let { " on TG $it" } ?: ""
    val displayName = "Unit ${reading.unitId}$talkGroupPart"
    return ObservationInput(
      name = displayName,
      address = null,
      rssi = reading.rssi?.toInt() ?: -100,
      timestamp = System.currentTimeMillis(),
      serviceUuids = emptyList(),
      manufacturerData = emptyMap(),
      source = "SDR",
      transport = "sdr",
      nameSource = "p25_control_channel",
      protocolType = "p25",
      classificationCategory = "radio_unit",
      classificationLabel = "P25 radio unit",
      classificationConfidence = "high",
      classificationEvidence = listOf("source:sdr", "protocol:p25"),
      p25UnitId = reading.unitId,
      p25Nac = reading.nac,
      p25Wacn = reading.wacn,
      p25SystemId = reading.systemId,
      p25TalkGroupId = reading.talkGroupId,
      rawJson = reading.rawJson
    )
  }
}
