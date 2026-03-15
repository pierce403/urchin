package guru.urchin.util

import guru.urchin.data.DeviceEntity
import guru.urchin.sdr.optBooleanOrNull
import guru.urchin.sdr.optDoubleOrNull
import guru.urchin.sdr.optIntOrNull
import guru.urchin.sdr.optStringOrNull
import org.json.JSONObject

/**
 * Deserialized form of `DeviceEntity.lastMetadataJson`. Holds fields for all
 * protocols in a flat structure. Parsed by [SensorMetadataParser].
 */
data class SensorMetadata(
  val source: String? = null,
  val vendorName: String? = null,
  val vendorSource: String? = null,
  val vendorConfidence: String? = null,
  val classificationLabel: String? = null,
  val protocolType: String? = null,
  val tpmsModel: String? = null,
  val tpmsSensorId: String? = null,
  val tpmsPressureKpa: Double? = null,
  val tpmsTemperatureC: Double? = null,
  val tpmsBatteryOk: Boolean? = null,
  val tpmsFrequencyMhz: Double? = null,
  val tpmsSnr: Double? = null,
  val pocsagCapCode: String? = null,
  val pocsagFunctionCode: Int? = null,
  val pocsagMessage: String? = null,
  val adsbIcao: String? = null,
  val adsbCallsign: String? = null,
  val adsbAltitude: Int? = null,
  val adsbSpeed: Double? = null,
  val adsbHeading: Double? = null,
  val adsbLat: Double? = null,
  val adsbLon: Double? = null,
  val adsbSquawk: String? = null,
  val p25UnitId: String? = null,
  val p25Nac: String? = null,
  val p25Wacn: String? = null,
  val p25SystemId: String? = null,
  val p25TalkGroupId: String? = null,
  val loraDevAddr: String? = null,
  val loraSpreadingFactor: String? = null,
  val loraCodingRate: String? = null,
  val loraPayloadSize: Int? = null,
  val loraCrcOk: Boolean? = null,
  val meshNodeId: String? = null,
  val meshDestId: String? = null,
  val meshPacketId: Int? = null,
  val meshHopLimit: Int? = null,
  val meshHopStart: Int? = null,
  val meshChannelHash: String? = null,
  val wmbusManufacturer: String? = null,
  val wmbusSerialNumber: String? = null,
  val wmbusMeterVersion: Int? = null,
  val wmbusMeterType: String? = null,
  val zwaveHomeId: String? = null,
  val zwaveNodeId: Int? = null,
  val zwaveFrameType: String? = null,
  val sidewalkSmsn: String? = null,
  val sidewalkFrameType: String? = null,
  val rssi: Int? = null,
  val rawJson: String? = null
)

data class SensorPresentation(
  val title: String,
  val listSummary: String,
  val detailLines: List<String>,
  val searchText: String,
  val protocolType: String?
)

object SensorMetadataParser {
  fun parse(metadataJson: String?): SensorMetadata {
    if (metadataJson.isNullOrBlank()) {
      return SensorMetadata()
    }

    return runCatching {
      val json = JSONObject(metadataJson)
      SensorMetadata(
        source = json.optStringOrNull("source"),
        vendorName = json.optStringOrNull("vendorName"),
        vendorSource = json.optStringOrNull("vendorSource"),
        vendorConfidence = json.optStringOrNull("vendorConfidence"),
        classificationLabel = json.optStringOrNull("classificationLabel"),
        protocolType = json.optStringOrNull("protocolType"),
        tpmsModel = json.optStringOrNull("tpmsModel"),
        tpmsSensorId = json.optStringOrNull("tpmsSensorId"),
        tpmsPressureKpa = json.optDoubleOrNull("tpmsPressureKpa"),
        tpmsTemperatureC = json.optDoubleOrNull("tpmsTemperatureC"),
        tpmsBatteryOk = json.optBooleanOrNull("tpmsBatteryOk"),
        tpmsFrequencyMhz = json.optDoubleOrNull("tpmsFrequencyMhz"),
        tpmsSnr = json.optDoubleOrNull("tpmsSnr"),
        pocsagCapCode = json.optStringOrNull("pocsagCapCode"),
        pocsagFunctionCode = json.optIntOrNull("pocsagFunctionCode"),
        pocsagMessage = json.optStringOrNull("pocsagMessage"),
        adsbIcao = json.optStringOrNull("adsbIcao"),
        adsbCallsign = json.optStringOrNull("adsbCallsign"),
        adsbAltitude = json.optIntOrNull("adsbAltitude"),
        adsbSpeed = json.optDoubleOrNull("adsbSpeed"),
        adsbHeading = json.optDoubleOrNull("adsbHeading"),
        adsbLat = json.optDoubleOrNull("adsbLat"),
        adsbLon = json.optDoubleOrNull("adsbLon"),
        adsbSquawk = json.optStringOrNull("adsbSquawk"),
        p25UnitId = json.optStringOrNull("p25UnitId"),
        p25Nac = json.optStringOrNull("p25Nac"),
        p25Wacn = json.optStringOrNull("p25Wacn"),
        p25SystemId = json.optStringOrNull("p25SystemId"),
        p25TalkGroupId = json.optStringOrNull("p25TalkGroupId"),
        loraDevAddr = json.optStringOrNull("loraDevAddr"),
        loraSpreadingFactor = json.optStringOrNull("loraSpreadingFactor"),
        loraCodingRate = json.optStringOrNull("loraCodingRate"),
        loraPayloadSize = json.optIntOrNull("loraPayloadSize"),
        loraCrcOk = json.optBooleanOrNull("loraCrcOk"),
        meshNodeId = json.optStringOrNull("meshNodeId"),
        meshDestId = json.optStringOrNull("meshDestId"),
        meshPacketId = json.optIntOrNull("meshPacketId"),
        meshHopLimit = json.optIntOrNull("meshHopLimit"),
        meshHopStart = json.optIntOrNull("meshHopStart"),
        meshChannelHash = json.optStringOrNull("meshChannelHash"),
        wmbusManufacturer = json.optStringOrNull("wmbusManufacturer"),
        wmbusSerialNumber = json.optStringOrNull("wmbusSerialNumber"),
        wmbusMeterVersion = json.optIntOrNull("wmbusMeterVersion"),
        wmbusMeterType = json.optStringOrNull("wmbusMeterType"),
        zwaveHomeId = json.optStringOrNull("zwaveHomeId"),
        zwaveNodeId = json.optIntOrNull("zwaveNodeId"),
        zwaveFrameType = json.optStringOrNull("zwaveFrameType"),
        sidewalkSmsn = json.optStringOrNull("sidewalkSmsn"),
        sidewalkFrameType = json.optStringOrNull("sidewalkFrameType"),
        rssi = json.optIntOrNull("rssi"),
        rawJson = json.optStringOrNull("rawJson")
      )
    }.getOrDefault(SensorMetadata())
  }
}

/**
 * Builds protocol-aware display data ([SensorPresentation]) from a [DeviceEntity].
 * Dispatches on `protocolType` to produce per-protocol titles, summaries, detail lines,
 * and search text for the device list and detail screens.
 */
object SensorPresentationBuilder {
  fun build(device: DeviceEntity): SensorPresentation {
    val metadata = SensorMetadataParser.parse(device.lastMetadataJson)
    val protocol = metadata.protocolType ?: "tpms"

    return when (protocol) {
      "pocsag" -> buildPocsag(device, metadata)
      "adsb", "uat" -> buildAdsb(device, metadata, protocol)
      "p25" -> buildP25(device, metadata)
      "lorawan" -> buildLoRaWan(device, metadata)
      "meshtastic" -> buildMeshtastic(device, metadata)
      "wmbus" -> buildWmBus(device, metadata)
      "zwave" -> buildZwave(device, metadata)
      "sidewalk" -> buildSidewalk(device, metadata)
      else -> buildTpms(device, metadata)
    }
  }

  private fun buildTpms(device: DeviceEntity, metadata: SensorMetadata): SensorPresentation {
    val preferredTitle = device.userCustomName?.takeIf(String::isNotBlank)
      ?: device.displayName?.takeIf(String::isNotBlank)
      ?: metadata.tpmsSensorId?.let { "TPMS $it" }
      ?: metadata.tpmsModel?.let { "TPMS $it" }
      ?: "Unknown TPMS sensor"

    val listSummaryParts = buildList {
      metadata.tpmsPressureKpa?.let { add(Formatters.formatPressure(it)) }
      metadata.tpmsTemperatureC?.let { add(Formatters.formatTemperature(it)) }
      metadata.tpmsBatteryOk?.let { add(if (it) "Battery OK" else "Battery low") }
      metadata.tpmsFrequencyMhz?.let { add(String.format("%.2f MHz", it)) }
    }

    val detailLines = buildList {
      metadata.tpmsSensorId?.let { add("Sensor ID: $it") }
      metadata.tpmsModel?.let { add("Protocol: $it") }
      metadata.vendorName?.let { vendor ->
        val source = metadata.vendorSource?.let { " ($it)" }.orEmpty()
        add("Vendor: $vendor$source")
      }
      metadata.classificationLabel?.let { add("Classification: $it") }
      metadata.tpmsPressureKpa?.let { add(Formatters.formatPressure(it)) }
      metadata.tpmsTemperatureC?.let { add(Formatters.formatTemperature(it)) }
      metadata.tpmsBatteryOk?.let { add("Battery: ${if (it) "OK" else "Low"}") }
      metadata.tpmsFrequencyMhz?.let { add(String.format("Frequency: %.2f MHz", it)) }
      metadata.tpmsSnr?.let { add(String.format("SNR: %.1f dB", it)) }
      metadata.rssi?.let { add(Formatters.formatRssi(it)) }
      metadata.source?.let { add("Source: $it") }
    }

    return SensorPresentation(
      title = preferredTitle,
      listSummary = listSummaryParts.joinToString(" • "),
      detailLines = detailLines,
      searchText = buildSearchText(preferredTitle, metadata),
      protocolType = "tpms"
    )
  }

  private fun buildPocsag(device: DeviceEntity, metadata: SensorMetadata): SensorPresentation {
    val preferredTitle = device.userCustomName?.takeIf(String::isNotBlank)
      ?: metadata.pocsagCapCode?.let { "Pager $it" }
      ?: device.displayName?.takeIf(String::isNotBlank)
      ?: "Unknown pager"

    val listSummaryParts = buildList {
      metadata.pocsagCapCode?.let { add("CAP: $it") }
      metadata.pocsagFunctionCode?.let { add("Func: $it") }
      metadata.pocsagMessage?.let { msg ->
        add(msg.take(60) + if (msg.length > 60) "..." else "")
      }
    }

    val detailLines = buildList {
      metadata.pocsagCapCode?.let { add("CAP Code: $it") }
      metadata.pocsagFunctionCode?.let { add("Function: $it") }
      metadata.classificationLabel?.let { add("Classification: $it") }
      metadata.pocsagMessage?.let { add("Message: $it") }
      metadata.rssi?.let { add(Formatters.formatRssi(it)) }
      metadata.source?.let { add("Source: $it") }
    }

    return SensorPresentation(
      title = preferredTitle,
      listSummary = listSummaryParts.joinToString(" • "),
      detailLines = detailLines,
      searchText = buildSearchText(preferredTitle, metadata),
      protocolType = "pocsag"
    )
  }

  private fun buildAdsb(device: DeviceEntity, metadata: SensorMetadata, protocol: String = "adsb"): SensorPresentation {
    val callsignPart = metadata.adsbCallsign?.takeIf(String::isNotBlank)
    val preferredTitle = device.userCustomName?.takeIf(String::isNotBlank)
      ?: if (callsignPart != null) "Aircraft $callsignPart (${metadata.adsbIcao})"
      else metadata.adsbIcao?.let { "Aircraft $it" }
      ?: device.displayName?.takeIf(String::isNotBlank)
      ?: "Unknown aircraft"

    val listSummaryParts = buildList {
      metadata.adsbIcao?.let { add("ICAO: $it") }
      metadata.adsbAltitude?.let { add("Alt: ${it}ft") }
      metadata.adsbSpeed?.let { add(String.format("%.0f kts", it)) }
      metadata.adsbSquawk?.let { add("Squawk: $it") }
    }

    val detailLines = buildList {
      metadata.adsbIcao?.let { add("ICAO Address: $it") }
      metadata.adsbCallsign?.let { add("Callsign: $it") }
      metadata.adsbSquawk?.let { add("Squawk: $it") }
      metadata.classificationLabel?.let { add("Classification: $it") }
      metadata.adsbAltitude?.let { add("Altitude: ${it} ft") }
      metadata.adsbSpeed?.let { add(String.format("Speed: %.0f kts", it)) }
      metadata.adsbHeading?.let { add(String.format("Heading: %.0f°", it)) }
      if (metadata.adsbLat != null && metadata.adsbLon != null) {
        add(String.format("Position: %.4f, %.4f", metadata.adsbLat, metadata.adsbLon))
      }
      metadata.rssi?.let { add(Formatters.formatRssi(it)) }
      metadata.source?.let { add("Source: $it") }
    }

    return SensorPresentation(
      title = preferredTitle,
      listSummary = listSummaryParts.joinToString(" • "),
      detailLines = detailLines,
      searchText = buildSearchText(preferredTitle, metadata),
      protocolType = protocol
    )
  }

  private fun buildP25(device: DeviceEntity, metadata: SensorMetadata): SensorPresentation {
    val tgPart = metadata.p25TalkGroupId?.let { " on TG $it" } ?: ""
    val preferredTitle = device.userCustomName?.takeIf(String::isNotBlank)
      ?: metadata.p25UnitId?.let { "Unit $it$tgPart" }
      ?: device.displayName?.takeIf(String::isNotBlank)
      ?: "Unknown P25 unit"

    val listSummaryParts = buildList {
      metadata.p25UnitId?.let { add("Unit: $it") }
      metadata.p25TalkGroupId?.let { add("TG: $it") }
      metadata.p25Nac?.let { add("NAC: $it") }
      metadata.p25Wacn?.let { add("WACN: $it") }
    }

    val detailLines = buildList {
      metadata.p25UnitId?.let { add("Unit ID: $it") }
      metadata.p25TalkGroupId?.let { add("Talk Group: $it") }
      metadata.p25Nac?.let { add("NAC: $it") }
      metadata.p25Wacn?.let { add("WACN: $it") }
      metadata.p25SystemId?.let { add("System ID: $it") }
      metadata.classificationLabel?.let { add("Classification: $it") }
      metadata.rssi?.let { add(Formatters.formatRssi(it)) }
      metadata.source?.let { add("Source: $it") }
    }

    return SensorPresentation(
      title = preferredTitle,
      listSummary = listSummaryParts.joinToString(" • "),
      detailLines = detailLines,
      searchText = buildSearchText(preferredTitle, metadata),
      protocolType = "p25"
    )
  }

  private fun buildLoRaWan(device: DeviceEntity, metadata: SensorMetadata): SensorPresentation {
    val sfPart = metadata.loraSpreadingFactor?.let { " $it" } ?: ""
    val preferredTitle = device.userCustomName?.takeIf(String::isNotBlank)
      ?: metadata.loraDevAddr?.let { "LoRa $it$sfPart" }
      ?: device.displayName?.takeIf(String::isNotBlank)
      ?: "Unknown LoRaWAN device"

    val listSummaryParts = buildList {
      metadata.loraDevAddr?.let { add("DevAddr: $it") }
      metadata.loraSpreadingFactor?.let { add(it) }
      metadata.loraPayloadSize?.let { add("${it}B") }
    }

    val detailLines = buildList {
      metadata.loraDevAddr?.let { add("DevAddr: $it") }
      metadata.loraSpreadingFactor?.let { add("Spreading Factor: $it") }
      metadata.loraCodingRate?.let { add("Coding Rate: $it") }
      metadata.loraPayloadSize?.let { add("Payload: $it bytes") }
      metadata.loraCrcOk?.let { add("CRC: ${if (it) "OK" else "Failed"}") }
      metadata.classificationLabel?.let { add("Classification: $it") }
      metadata.rssi?.let { add(Formatters.formatRssi(it)) }
      metadata.source?.let { add("Source: $it") }
    }

    return SensorPresentation(
      title = preferredTitle,
      listSummary = listSummaryParts.joinToString(" • "),
      detailLines = detailLines,
      searchText = buildSearchText(preferredTitle, metadata),
      protocolType = "lorawan"
    )
  }

  private fun buildMeshtastic(device: DeviceEntity, metadata: SensorMetadata): SensorPresentation {
    val preferredTitle = device.userCustomName?.takeIf(String::isNotBlank)
      ?: metadata.meshNodeId?.let { "Mesh $it" }
      ?: device.displayName?.takeIf(String::isNotBlank)
      ?: "Unknown Meshtastic node"

    val listSummaryParts = buildList {
      metadata.meshNodeId?.let { add("Node: $it") }
      metadata.meshHopLimit?.let { remaining ->
        metadata.meshHopStart?.let { start -> add("Hops: ${start - remaining} of $start") }
      }
      metadata.meshChannelHash?.let { add("Ch: $it") }
    }

    val detailLines = buildList {
      metadata.meshNodeId?.let { add("Node ID: $it") }
      metadata.meshDestId?.let { add("Destination: $it") }
      metadata.meshPacketId?.let { add("Packet ID: $it") }
      metadata.meshHopLimit?.let { add("Hop Limit: $it") }
      metadata.meshHopStart?.let { add("Hop Start: $it") }
      metadata.meshChannelHash?.let { add("Channel Hash: $it") }
      metadata.classificationLabel?.let { add("Classification: $it") }
      metadata.rssi?.let { add(Formatters.formatRssi(it)) }
      metadata.source?.let { add("Source: $it") }
    }

    return SensorPresentation(
      title = preferredTitle,
      listSummary = listSummaryParts.joinToString(" • "),
      detailLines = detailLines,
      searchText = buildSearchText(preferredTitle, metadata),
      protocolType = "meshtastic"
    )
  }

  private fun buildWmBus(device: DeviceEntity, metadata: SensorMetadata): SensorPresentation {
    val preferredTitle = device.userCustomName?.takeIf(String::isNotBlank)
      ?: metadata.wmbusSerialNumber?.let { serial ->
        val mfr = metadata.wmbusManufacturer?.let { "$it " } ?: ""
        val type = metadata.wmbusMeterType?.let { " ($it)" } ?: ""
        "${mfr}Meter $serial$type"
      }
      ?: device.displayName?.takeIf(String::isNotBlank)
      ?: "Unknown meter"

    val listSummaryParts = buildList {
      metadata.wmbusManufacturer?.let { add(it) }
      metadata.wmbusSerialNumber?.let { add("S/N: $it") }
      metadata.wmbusMeterType?.let { add(it) }
    }

    val detailLines = buildList {
      metadata.wmbusManufacturer?.let { add("Manufacturer: $it") }
      metadata.wmbusSerialNumber?.let { add("Serial Number: $it") }
      metadata.wmbusMeterVersion?.let { add("Version: $it") }
      metadata.wmbusMeterType?.let { add("Meter Type: $it") }
      metadata.classificationLabel?.let { add("Classification: $it") }
      metadata.rssi?.let { add(Formatters.formatRssi(it)) }
      metadata.source?.let { add("Source: $it") }
    }

    return SensorPresentation(
      title = preferredTitle,
      listSummary = listSummaryParts.joinToString(" • "),
      detailLines = detailLines,
      searchText = buildSearchText(preferredTitle, metadata),
      protocolType = "wmbus"
    )
  }

  private fun buildZwave(device: DeviceEntity, metadata: SensorMetadata): SensorPresentation {
    val preferredTitle = device.userCustomName?.takeIf(String::isNotBlank)
      ?: if (metadata.zwaveHomeId != null && metadata.zwaveNodeId != null)
        "Z-Wave ${metadata.zwaveHomeId}:${metadata.zwaveNodeId}"
      else device.displayName?.takeIf(String::isNotBlank)
      ?: "Unknown Z-Wave device"

    val listSummaryParts = buildList {
      metadata.zwaveHomeId?.let { add("Home: $it") }
      metadata.zwaveNodeId?.let { add("Node: $it") }
      metadata.zwaveFrameType?.let { add(it) }
    }

    val detailLines = buildList {
      metadata.zwaveHomeId?.let { add("Home ID: $it") }
      metadata.zwaveNodeId?.let { add("Node ID: $it") }
      metadata.zwaveFrameType?.let { add("Frame Type: $it") }
      metadata.classificationLabel?.let { add("Classification: $it") }
      metadata.rssi?.let { add(Formatters.formatRssi(it)) }
      metadata.source?.let { add("Source: $it") }
    }

    return SensorPresentation(
      title = preferredTitle,
      listSummary = listSummaryParts.joinToString(" • "),
      detailLines = detailLines,
      searchText = buildSearchText(preferredTitle, metadata),
      protocolType = "zwave"
    )
  }

  private fun buildSidewalk(device: DeviceEntity, metadata: SensorMetadata): SensorPresentation {
    val preferredTitle = device.userCustomName?.takeIf(String::isNotBlank)
      ?: metadata.sidewalkSmsn?.let { "Sidewalk $it" }
      ?: device.displayName?.takeIf(String::isNotBlank)
      ?: "Unknown Sidewalk device"

    val listSummaryParts = buildList {
      metadata.sidewalkSmsn?.let { add("SMSN: $it") }
      metadata.sidewalkFrameType?.let { add(it) }
    }

    val detailLines = buildList {
      metadata.sidewalkSmsn?.let { add("SMSN: $it") }
      metadata.sidewalkFrameType?.let { add("Frame Type: $it") }
      metadata.classificationLabel?.let { add("Classification: $it") }
      metadata.rssi?.let { add(Formatters.formatRssi(it)) }
      metadata.source?.let { add("Source: $it") }
    }

    return SensorPresentation(
      title = preferredTitle,
      listSummary = listSummaryParts.joinToString(" • "),
      detailLines = detailLines,
      searchText = buildSearchText(preferredTitle, metadata),
      protocolType = "sidewalk"
    )
  }

  private fun buildSearchText(title: String, metadata: SensorMetadata): String {
    return buildString {
      append(title)
      append('\n')
      listOfNotNull(
        metadata.vendorName,
        metadata.classificationLabel,
        metadata.tpmsSensorId,
        metadata.tpmsModel,
        metadata.pocsagCapCode,
        metadata.pocsagMessage,
        metadata.adsbIcao,
        metadata.adsbCallsign,
        metadata.adsbSquawk,
        metadata.p25UnitId,
        metadata.p25TalkGroupId,
        metadata.p25Nac,
        metadata.loraDevAddr,
        metadata.meshNodeId,
        metadata.meshChannelHash,
        metadata.wmbusSerialNumber,
        metadata.wmbusManufacturer,
        metadata.zwaveHomeId,
        metadata.sidewalkSmsn,
        metadata.rawJson
      ).forEach {
        append(it)
        append('\n')
      }
    }
  }
}
