package guru.urchin.sdr

import guru.urchin.scan.ObservationInput

object TpmsObservationBuilder {
  fun build(reading: SdrReading.Tpms): ObservationInput {
    val displayName = "TPMS ${reading.model} ${reading.sensorId}"
    val vendor = TpmsSensorVendorLookup.lookup(reading.model)
    val resolvedVendorName = vendor?.manufacturer ?: reading.model
    val resolvedVendorSource = if (vendor != null) "rtl_433 protocol (mapped)" else "rtl_433 protocol"
    val resolvedVendorConfidence = if (vendor != null) "high" else "medium"
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
      vendorName = resolvedVendorName,
      vendorSource = resolvedVendorSource,
      vendorConfidence = resolvedVendorConfidence,
      classificationCategory = "tpms_sensor",
      classificationLabel = "TPMS sensor",
      classificationConfidence = "high",
      protocolType = "tpms",
      classificationEvidence = listOf("source:sdr", "protocol:${reading.model}"),
      tpmsModel = reading.model,
      tpmsSensorId = reading.sensorId,
      tpmsPressureKpa = reading.pressureKpa,
      tpmsTemperatureC = reading.temperatureC,
      tpmsBatteryOk = reading.batteryOk,
      tpmsFrequencyMhz = reading.frequencyMhz,
      tpmsSnr = reading.snr,
      rawJson = reading.rawJson
    )
  }
}
