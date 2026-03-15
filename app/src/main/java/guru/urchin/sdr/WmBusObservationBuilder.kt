package guru.urchin.sdr

import guru.urchin.scan.ObservationInput

object WmBusObservationBuilder {
  fun build(reading: SdrReading.WmBus): ObservationInput {
    val typePart = reading.meterType?.let { " ($it)" } ?: ""
    val displayName = "${reading.manufacturer} Meter ${reading.serialNumber}$typePart"
    return ObservationInput(
      name = displayName,
      address = null,
      rssi = reading.rssi?.toInt() ?: -100,
      timestamp = System.currentTimeMillis(),
      serviceUuids = emptyList(),
      manufacturerData = emptyMap(),
      source = "SDR",
      transport = "sdr",
      nameSource = "wmbus_link_layer",
      protocolType = "wmbus",
      classificationCategory = "utility_meter",
      classificationLabel = "Wireless M-Bus meter",
      classificationConfidence = "high",
      classificationEvidence = listOf("source:sdr", "protocol:wmbus"),
      wmbusManufacturer = reading.manufacturer,
      wmbusSerialNumber = reading.serialNumber,
      wmbusMeterVersion = reading.meterVersion,
      wmbusMeterType = reading.meterType,
      rawJson = reading.rawJson
    )
  }
}
