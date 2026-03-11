package guru.urchin.sdr

import guru.urchin.scan.ObservationInput

object PocsagObservationBuilder {
  fun build(reading: SdrReading.Pocsag): ObservationInput {
    val displayName = "Pager ${reading.address}"
    return ObservationInput(
      name = displayName,
      address = null,
      rssi = reading.rssi?.toInt() ?: -100,
      timestamp = System.currentTimeMillis(),
      serviceUuids = emptyList(),
      manufacturerData = emptyMap(),
      source = "SDR",
      transport = "sdr",
      nameSource = "rtl_433_protocol",
      vendorName = reading.model,
      vendorSource = "rtl_433 protocol",
      vendorConfidence = "medium",
      protocolType = "pocsag",
      classificationCategory = "pager",
      classificationLabel = "POCSAG pager",
      classificationConfidence = "high",
      classificationEvidence = listOf("source:sdr", "protocol:${reading.model}"),
      pocsagCapCode = reading.address,
      pocsagFunctionCode = reading.functionCode,
      pocsagMessage = reading.message,
      rawJson = reading.rawJson
    )
  }
}
