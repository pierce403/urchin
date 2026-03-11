package guru.urchin.data

data class DeviceObservation(
  val deviceKey: String,
  val name: String?,
  val address: String?,
  val rssi: Int,
  val timestamp: Long,
  val metadataJson: String?,
  val protocolType: String? = null
)
