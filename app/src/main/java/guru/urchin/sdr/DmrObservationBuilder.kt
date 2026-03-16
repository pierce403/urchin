package guru.urchin.sdr

import guru.urchin.scan.ObservationInput

object DmrObservationBuilder {
  fun build(reading: SdrReading.Dmr): ObservationInput {
    val tgPart = reading.talkGroup?.let { " TG $it" } ?: ""
    val displayName = "DMR ${reading.radioId}$tgPart"
    return ObservationInput(
      name = displayName,
      address = null,
      rssi = reading.rssi?.toInt() ?: -100,
      timestamp = System.currentTimeMillis(),
      serviceUuids = emptyList(),
      manufacturerData = emptyMap(),
      source = "SDR",
      transport = "sdr",
      nameSource = "dmr_control_channel",
      protocolType = "dmr",
      classificationCategory = "radio_unit",
      classificationLabel = "DMR radio unit",
      classificationConfidence = "high",
      classificationEvidence = listOf("source:sdr", "protocol:dmr"),
      dmrRadioId = reading.radioId,
      dmrColorCode = reading.colorCode,
      dmrSlot = reading.slot,
      dmrTalkGroup = reading.talkGroup,
      dmrDataType = reading.dataType,
      dmrEncrypted = reading.encrypted,
      rawJson = reading.rawJson
    )
  }
}
