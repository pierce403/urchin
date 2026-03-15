package guru.urchin.sdr

import guru.urchin.scan.ObservationInput

object ZwaveObservationBuilder {
  fun build(reading: SdrReading.Zwave): ObservationInput {
    val displayName = "Z-Wave ${reading.homeId}:${reading.nodeId}"
    return ObservationInput(
      name = displayName,
      address = null,
      rssi = reading.rssi?.toInt() ?: -100,
      timestamp = System.currentTimeMillis(),
      serviceUuids = emptyList(),
      manufacturerData = emptyMap(),
      source = "SDR",
      transport = "sdr",
      nameSource = "zwave_mac_header",
      protocolType = "zwave",
      classificationCategory = "smart_home_device",
      classificationLabel = "Z-Wave device",
      classificationConfidence = "high",
      classificationEvidence = listOf("source:sdr", "protocol:zwave"),
      zwaveHomeId = reading.homeId,
      zwaveNodeId = reading.nodeId,
      zwaveFrameType = reading.frameType,
      zwaveCommandClass = reading.commandClass,
      zwaveNodeRole = reading.nodeRole,
      zwaveSecurityLevel = reading.securityLevel,
      rawJson = reading.rawJson
    )
  }
}
