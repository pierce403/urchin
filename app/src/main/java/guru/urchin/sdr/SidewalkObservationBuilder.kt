package guru.urchin.sdr

import guru.urchin.scan.ObservationInput

object SidewalkObservationBuilder {
  fun build(reading: SdrReading.Sidewalk): ObservationInput {
    val displayName = "Sidewalk ${reading.smsn}"
    return ObservationInput(
      name = displayName,
      address = null,
      rssi = reading.rssi?.toInt() ?: -100,
      timestamp = System.currentTimeMillis(),
      serviceUuids = emptyList(),
      manufacturerData = emptyMap(),
      source = "SDR",
      transport = "sdr",
      nameSource = "sidewalk_phy_header",
      protocolType = "sidewalk",
      classificationCategory = "sidewalk_device",
      classificationLabel = "Amazon Sidewalk device",
      classificationConfidence = "high",
      classificationEvidence = listOf("source:sdr", "protocol:sidewalk"),
      sidewalkSmsn = reading.smsn,
      sidewalkFrameType = reading.frameType,
      rawJson = reading.rawJson
    )
  }
}
