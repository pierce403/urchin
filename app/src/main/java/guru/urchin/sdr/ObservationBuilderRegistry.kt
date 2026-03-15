package guru.urchin.sdr

import guru.urchin.scan.ObservationInput

/** Dispatches [SdrReading] sealed variants to the appropriate protocol-specific observation builder. */
object ObservationBuilderRegistry {
  fun build(reading: SdrReading): ObservationInput = when (reading) {
    is SdrReading.Tpms -> TpmsObservationBuilder.build(reading)
    is SdrReading.Pocsag -> PocsagObservationBuilder.build(reading)
    is SdrReading.Adsb -> AdsbObservationBuilder.build(reading)
    is SdrReading.P25 -> P25ObservationBuilder.build(reading)
    is SdrReading.LoRaWan -> LoRaWanObservationBuilder.build(reading)
    is SdrReading.Meshtastic -> MeshtasticObservationBuilder.build(reading)
    is SdrReading.WmBus -> WmBusObservationBuilder.build(reading)
    is SdrReading.Zwave -> ZwaveObservationBuilder.build(reading)
    is SdrReading.Sidewalk -> SidewalkObservationBuilder.build(reading)
    is SdrReading.Dmr -> DmrObservationBuilder.build(reading)
    is SdrReading.Nxdn -> NxdnObservationBuilder.build(reading)
  }
}
