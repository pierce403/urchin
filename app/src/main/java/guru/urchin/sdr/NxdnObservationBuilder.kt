package guru.urchin.sdr

import guru.urchin.scan.ObservationInput

object NxdnObservationBuilder {
  fun build(reading: SdrReading.Nxdn): ObservationInput {
    val tgPart = reading.talkGroup?.let { " TG $it" } ?: ""
    val displayName = "NXDN ${reading.unitId}$tgPart"
    return ObservationInput(
      name = displayName,
      address = null,
      rssi = reading.rssi?.toInt() ?: -100,
      timestamp = System.currentTimeMillis(),
      serviceUuids = emptyList(),
      manufacturerData = emptyMap(),
      source = "SDR",
      transport = "sdr",
      nameSource = "nxdn_control_channel",
      protocolType = "nxdn",
      classificationCategory = "radio_unit",
      classificationLabel = "NXDN radio unit",
      classificationConfidence = "high",
      classificationEvidence = listOf("source:sdr", "protocol:nxdn"),
      nxdnUnitId = reading.unitId,
      nxdnRan = reading.ran,
      nxdnTalkGroup = reading.talkGroup,
      nxdnMessageType = reading.messageType,
      rawJson = reading.rawJson
    )
  }
}
