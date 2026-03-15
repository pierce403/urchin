package guru.urchin.sdr

import guru.urchin.scan.ObservationInput

object LoRaWanObservationBuilder {
  fun build(reading: SdrReading.LoRaWan): ObservationInput {
    val sfPart = reading.spreadingFactor?.let { " $it" } ?: ""
    val displayName = "LoRa ${reading.devAddr}$sfPart"
    return ObservationInput(
      name = displayName,
      address = null,
      rssi = reading.rssi?.toInt() ?: -100,
      timestamp = System.currentTimeMillis(),
      serviceUuids = emptyList(),
      manufacturerData = emptyMap(),
      source = "SDR",
      transport = "sdr",
      nameSource = "lorawan_gateway",
      protocolType = "lorawan",
      classificationCategory = "lorawan_device",
      classificationLabel = "LoRaWAN end device",
      classificationConfidence = "high",
      classificationEvidence = listOf("source:sdr", "protocol:lorawan"),
      loraDevAddr = reading.devAddr,
      loraSpreadingFactor = reading.spreadingFactor,
      loraCodingRate = reading.codingRate,
      loraPayloadSize = reading.payloadSize,
      loraCrcOk = reading.crcOk,
      loraFPort = reading.fPort,
      loraFrameCounter = reading.frameCounter,
      loraMType = reading.mType,
      rawJson = reading.rawJson
    )
  }
}
