package guru.urchin.sdr

import guru.urchin.scan.ObservationInput

object MeshtasticObservationBuilder {
  fun build(reading: SdrReading.Meshtastic): ObservationInput {
    val hopInfo = reading.hopStart?.let { start ->
      reading.hopLimit?.let { limit -> " hop ${start - limit}/$start" }
    } ?: ""
    val displayName = "Mesh ${reading.nodeId}$hopInfo"
    return ObservationInput(
      name = displayName,
      address = null,
      rssi = reading.rssi?.toInt() ?: -100,
      timestamp = System.currentTimeMillis(),
      serviceUuids = emptyList(),
      manufacturerData = emptyMap(),
      source = "SDR",
      transport = "sdr",
      nameSource = "meshtastic_header",
      protocolType = "meshtastic",
      classificationCategory = "mesh_node",
      classificationLabel = "Meshtastic node",
      classificationConfidence = "high",
      classificationEvidence = listOf("source:sdr", "protocol:meshtastic"),
      meshNodeId = reading.nodeId,
      meshDestId = reading.destId,
      meshPacketId = reading.packetId,
      meshHopLimit = reading.hopLimit,
      meshHopStart = reading.hopStart,
      meshChannelHash = reading.channelHash,
      meshPortNum = reading.portNum,
      meshPayloadText = reading.payloadText,
      rawJson = reading.rawJson
    )
  }
}
