package guru.urchin.scan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import guru.urchin.alerts.AlertObservation
import guru.urchin.alerts.DeviceAlertMatcher
import guru.urchin.alerts.DeviceAlertNotifier
import guru.urchin.data.AlertRuleEntity
import guru.urchin.data.AlertRuleRepository
import guru.urchin.data.DeviceObservation
import guru.urchin.data.DeviceRepository
import guru.urchin.util.DebugLog
import org.json.JSONArray
import org.json.JSONObject

class ObservationRecorder(
  private val repository: DeviceRepository,
  private val scope: CoroutineScope,
  private val alertRuleRepository: AlertRuleRepository? = null,
  private val alertNotifier: DeviceAlertNotifier? = null,
  var locationProvider: LocationProvider? = null
) {
  private var alertRules: List<AlertRuleEntity> = emptyList()
  private val firedAlertKeys = mutableSetOf<String>()

  init {
    alertRuleRepository?.observeEnabledRules()
      ?.onEach { alertRules = it }
      ?.launchIn(scope)
  }

  fun resetAlertDedup() {
    firedAlertKeys.clear()
  }

  fun record(input: ObservationInput) {
    val key = DeviceKey.from(input)
    ScanDiagnosticsStore.update {
      it.copy(deviceKeys = it.deviceKeys + key)
    }
    val fix = locationProvider?.lastFix
    val stamped = if (fix != null && input.receiverLat == null) {
      input.copy(
        receiverLat = fix.latitude,
        receiverLon = fix.longitude,
        receiverAltitude = fix.altitude,
        receiverAccuracy = fix.accuracyMeters
      )
    } else {
      input
    }
    val metadata = buildMetadataJson(stamped)
    DebugLog.log(
      "Observation ${stamped.source} protocol=${stamped.protocolType ?: "unknown"} rssi=${stamped.rssi}"
    )
    val observation = DeviceObservation(
      deviceKey = key,
      name = stamped.name,
      address = stamped.tpmsSensorId ?: stamped.pocsagCapCode ?: stamped.adsbIcao ?: stamped.p25UnitId ?: stamped.loraDevAddr ?: stamped.meshNodeId ?: stamped.wmbusSerialNumber ?: stamped.zwaveHomeId ?: stamped.sidewalkSmsn ?: stamped.dmrRadioId ?: stamped.nxdnUnitId ?: stamped.address,
      rssi = stamped.rssi,
      timestamp = stamped.timestamp,
      metadataJson = metadata,
      protocolType = stamped.protocolType,
      receiverLat = stamped.receiverLat,
      receiverLon = stamped.receiverLon
    )

    evaluateAlerts(stamped, key)

    scope.launch {
      repository.recordObservation(observation)
    }
  }

  private val knownDeviceKeys = mutableSetOf<String>()

  private fun evaluateAlerts(input: ObservationInput, deviceKey: String) {
    if (alertNotifier == null || alertRules.isEmpty()) return

    val isNew = knownDeviceKeys.add(deviceKey)
    val sensorId = input.tpmsSensorId ?: input.adsbIcao ?: input.pocsagCapCode ?: input.p25UnitId ?: input.loraDevAddr ?: input.meshNodeId ?: input.wmbusSerialNumber ?: input.zwaveHomeId ?: input.sidewalkSmsn ?: input.dmrRadioId ?: input.nxdnUnitId
    val alertObs = AlertObservation(
      deviceKey = deviceKey,
      displayName = input.name ?: input.tpmsModel ?: input.adsbCallsign,
      sensorId = sensorId,
      protocolType = input.protocolType,
      source = input.source,
      rssi = input.rssi,
      isNewDevice = isNew
    )

    val matches = DeviceAlertMatcher.findMatches(alertRules, alertObs)
    for (match in matches) {
      val alertKey = "${match.rule.id}:$deviceKey"
      if (firedAlertKeys.add(alertKey)) {
        alertNotifier.notifyMatch(match, alertObs)
      }
    }
  }

  private fun buildMetadataJson(input: ObservationInput): String {
    val json = JSONObject()
    json.put("source", input.source)
    json.putIfNotNull("transport", input.transport)
    json.put("name", input.name)
    json.put("address", input.address)
    json.putIfNotNull("nameSource", input.nameSource)
    json.putIfNotNull("vendorName", input.vendorName)
    json.putIfNotNull("vendorSource", input.vendorSource)
    json.putIfNotNull("vendorConfidence", input.vendorConfidence)
    json.putIfNotNull("classificationCategory", input.classificationCategory)
    json.putIfNotNull("classificationLabel", input.classificationLabel)
    json.putIfNotNull("classificationConfidence", input.classificationConfidence)
    json.put("rssi", input.rssi)
    json.put("timestamp", input.timestamp)

    val classificationEvidence = JSONArray()
    input.classificationEvidence.forEach { classificationEvidence.put(it) }
    json.put("classificationEvidence", classificationEvidence)

    json.putIfNotNull("protocolType", input.protocolType)

    // TPMS fields
    json.putIfNotNull("tpmsModel", input.tpmsModel)
    json.putIfNotNull("tpmsSensorId", input.tpmsSensorId)
    json.putIfNotNull("tpmsPressureKpa", input.tpmsPressureKpa)
    json.putIfNotNull("tpmsTemperatureC", input.tpmsTemperatureC)
    json.putIfNotNull("tpmsBatteryOk", input.tpmsBatteryOk)
    json.putIfNotNull("tpmsFrequencyMhz", input.tpmsFrequencyMhz)
    json.putIfNotNull("tpmsSnr", input.tpmsSnr)

    // POCSAG fields
    json.putIfNotNull("pocsagCapCode", input.pocsagCapCode)
    json.putIfNotNull("pocsagFunctionCode", input.pocsagFunctionCode)
    json.putIfNotNull("pocsagMessage", input.pocsagMessage)

    // ADS-B fields
    json.putIfNotNull("adsbIcao", input.adsbIcao)
    json.putIfNotNull("adsbCallsign", input.adsbCallsign)
    json.putIfNotNull("adsbAltitude", input.adsbAltitude)
    json.putIfNotNull("adsbSpeed", input.adsbSpeed)
    json.putIfNotNull("adsbHeading", input.adsbHeading)
    json.putIfNotNull("adsbLat", input.adsbLat)
    json.putIfNotNull("adsbLon", input.adsbLon)
    json.putIfNotNull("adsbSquawk", input.adsbSquawk)

    // P25 fields
    json.putIfNotNull("p25UnitId", input.p25UnitId)
    json.putIfNotNull("p25Nac", input.p25Nac)
    json.putIfNotNull("p25Wacn", input.p25Wacn)
    json.putIfNotNull("p25SystemId", input.p25SystemId)
    json.putIfNotNull("p25TalkGroupId", input.p25TalkGroupId)
    json.putIfNotNull("p25EncryptionAlgorithm", input.p25EncryptionAlgorithm)
    json.putIfNotNull("p25EncryptionKeyId", input.p25EncryptionKeyId)
    json.putIfNotNull("p25Emergency", input.p25Emergency)
    json.putIfNotNull("p25VoiceOrData", input.p25VoiceOrData)

    // LoRaWAN fields
    json.putIfNotNull("loraDevAddr", input.loraDevAddr)
    json.putIfNotNull("loraSpreadingFactor", input.loraSpreadingFactor)
    json.putIfNotNull("loraCodingRate", input.loraCodingRate)
    json.putIfNotNull("loraPayloadSize", input.loraPayloadSize)
    json.putIfNotNull("loraCrcOk", input.loraCrcOk)
    json.putIfNotNull("loraFPort", input.loraFPort)
    json.putIfNotNull("loraFrameCounter", input.loraFrameCounter)
    json.putIfNotNull("loraMType", input.loraMType)

    // Meshtastic fields
    json.putIfNotNull("meshNodeId", input.meshNodeId)
    json.putIfNotNull("meshDestId", input.meshDestId)
    json.putIfNotNull("meshPacketId", input.meshPacketId)
    json.putIfNotNull("meshHopLimit", input.meshHopLimit)
    json.putIfNotNull("meshHopStart", input.meshHopStart)
    json.putIfNotNull("meshChannelHash", input.meshChannelHash)
    json.putIfNotNull("meshPortNum", input.meshPortNum)
    json.putIfNotNull("meshPayloadText", input.meshPayloadText)

    // Wireless M-Bus fields
    json.putIfNotNull("wmbusManufacturer", input.wmbusManufacturer)
    json.putIfNotNull("wmbusSerialNumber", input.wmbusSerialNumber)
    json.putIfNotNull("wmbusMeterVersion", input.wmbusMeterVersion)
    json.putIfNotNull("wmbusMeterType", input.wmbusMeterType)

    // Z-Wave fields
    json.putIfNotNull("zwaveHomeId", input.zwaveHomeId)
    json.putIfNotNull("zwaveNodeId", input.zwaveNodeId)
    json.putIfNotNull("zwaveFrameType", input.zwaveFrameType)
    json.putIfNotNull("zwaveCommandClass", input.zwaveCommandClass)
    json.putIfNotNull("zwaveNodeRole", input.zwaveNodeRole)
    json.putIfNotNull("zwaveSecurityLevel", input.zwaveSecurityLevel)

    // Amazon Sidewalk fields
    json.putIfNotNull("sidewalkSmsn", input.sidewalkSmsn)
    json.putIfNotNull("sidewalkFrameType", input.sidewalkFrameType)

    // DMR fields
    json.putIfNotNull("dmrRadioId", input.dmrRadioId)
    json.putIfNotNull("dmrColorCode", input.dmrColorCode)
    json.putIfNotNull("dmrSlot", input.dmrSlot)
    json.putIfNotNull("dmrTalkGroup", input.dmrTalkGroup)
    json.putIfNotNull("dmrDataType", input.dmrDataType)
    json.putIfNotNull("dmrEncrypted", input.dmrEncrypted)

    // NXDN fields
    json.putIfNotNull("nxdnUnitId", input.nxdnUnitId)
    json.putIfNotNull("nxdnRan", input.nxdnRan)
    json.putIfNotNull("nxdnTalkGroup", input.nxdnTalkGroup)
    json.putIfNotNull("nxdnMessageType", input.nxdnMessageType)

    json.putIfNotNull("rawJson", input.rawJson)

    // Receiver geolocation
    json.putIfNotNull("receiverLat", input.receiverLat)
    json.putIfNotNull("receiverLon", input.receiverLon)
    json.putIfNotNull("receiverAltitude", input.receiverAltitude)
    json.putIfNotNull("receiverAccuracy", input.receiverAccuracy)

    // Compute range/bearing from receiver to ADS-B target if both positions available
    if (input.receiverLat != null && input.receiverLon != null &&
        input.adsbLat != null && input.adsbLon != null) {
      val results = FloatArray(2)
      android.location.Location.distanceBetween(
        input.receiverLat, input.receiverLon,
        input.adsbLat, input.adsbLon,
        results
      )
      json.put("adsbRangeKm", results[0] / 1000.0)
      json.put("adsbBearingDeg", results[1].toDouble())
    }

    return json.toString(2)
  }

  private fun JSONObject.putIfNotNull(key: String, value: Any?) {
    if (value != null) {
      put(key, value)
    }
  }
}
