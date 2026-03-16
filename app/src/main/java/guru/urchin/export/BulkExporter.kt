package guru.urchin.export

import guru.urchin.data.DeviceEntity
import guru.urchin.data.SightingEntity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Exports the device and sighting database in CSV, KML, and GeoJSON formats
 * for external analysis in Analyst Notebook, Google Earth, GIS toolchains,
 * or custom Python pipelines.
 */
object BulkExporter {

  data class ExportResult(val fileName: String, val content: String, val mimeType: String)

  private val utcFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
  }

  fun exportCsv(
    devices: List<DeviceEntity>,
    sightings: List<SightingEntity>,
    protocolFilter: String? = null
  ): ExportResult {
    val filtered = filterByProtocol(devices, sightings, protocolFilter)
    val sb = StringBuilder()

    sb.appendLine(
      "deviceKey,displayName,protocolType,lastAddress,firstSeen,lastSeen," +
        "sightingsCount,observationCount,lastRssi,rssiMin,rssiMax,rssiAvg," +
        "starred,receiverLat,receiverLon," +
        "tpmsModel,tpmsSensorId,tpmsPressureKpa,tpmsTemperatureC,tpmsBatteryOk," +
        "pocsagCapCode,pocsagFunctionCode,pocsagMessage," +
        "adsbIcao,adsbCallsign,adsbAltitude,adsbSpeed,adsbHeading,adsbLat,adsbLon,adsbSquawk," +
        "p25UnitId,p25Nac,p25Wacn,p25SystemId,p25TalkGroupId," +
        "loraDevAddr,loraSpreadingFactor,loraCodingRate," +
        "meshNodeId,meshDestId,meshHopLimit,meshHopStart,meshChannelHash," +
        "wmbusManufacturer,wmbusSerialNumber,wmbusMeterType," +
        "zwaveHomeId,zwaveNodeId,zwaveFrameType," +
        "sidewalkSmsn,sidewalkFrameType"
    )

    val sightingsByDevice = filtered.second.groupBy { it.deviceKey }

    for (device in filtered.first) {
      val meta = parseMetadata(device.lastMetadataJson)
      val latestSighting = sightingsByDevice[device.deviceKey]?.maxByOrNull { it.timestamp }

      sb.appendLine(
        listOf(
          csvEscape(device.deviceKey),
          csvEscape(device.userCustomName ?: device.displayName),
          csvEscape(device.protocolType),
          csvEscape(device.lastAddress),
          utcFormat.format(Date(device.firstSeen)),
          utcFormat.format(Date(device.lastSeen)),
          device.sightingsCount,
          device.observationCount,
          device.lastRssi,
          device.rssiMin,
          device.rssiMax,
          String.format(Locale.US, "%.1f", device.rssiAvg),
          device.starred,
          latestSighting?.receiverLat ?: "",
          latestSighting?.receiverLon ?: "",
          csvEscape(meta.optString("tpmsModel", "")),
          csvEscape(meta.optString("tpmsSensorId", "")),
          meta.optStringOrEmpty("tpmsPressureKpa"),
          meta.optStringOrEmpty("tpmsTemperatureC"),
          meta.optStringOrEmpty("tpmsBatteryOk"),
          csvEscape(meta.optString("pocsagCapCode", "")),
          meta.optStringOrEmpty("pocsagFunctionCode"),
          csvEscape(meta.optString("pocsagMessage", "")),
          csvEscape(meta.optString("adsbIcao", "")),
          csvEscape(meta.optString("adsbCallsign", "")),
          meta.optStringOrEmpty("adsbAltitude"),
          meta.optStringOrEmpty("adsbSpeed"),
          meta.optStringOrEmpty("adsbHeading"),
          meta.optStringOrEmpty("adsbLat"),
          meta.optStringOrEmpty("adsbLon"),
          csvEscape(meta.optString("adsbSquawk", "")),
          csvEscape(meta.optString("p25UnitId", "")),
          csvEscape(meta.optString("p25Nac", "")),
          csvEscape(meta.optString("p25Wacn", "")),
          csvEscape(meta.optString("p25SystemId", "")),
          csvEscape(meta.optString("p25TalkGroupId", "")),
          csvEscape(meta.optString("loraDevAddr", "")),
          csvEscape(meta.optString("loraSpreadingFactor", "")),
          csvEscape(meta.optString("loraCodingRate", "")),
          csvEscape(meta.optString("meshNodeId", "")),
          csvEscape(meta.optString("meshDestId", "")),
          meta.optStringOrEmpty("meshHopLimit"),
          meta.optStringOrEmpty("meshHopStart"),
          csvEscape(meta.optString("meshChannelHash", "")),
          csvEscape(meta.optString("wmbusManufacturer", "")),
          csvEscape(meta.optString("wmbusSerialNumber", "")),
          csvEscape(meta.optString("wmbusMeterType", "")),
          csvEscape(meta.optString("zwaveHomeId", "")),
          meta.optStringOrEmpty("zwaveNodeId"),
          csvEscape(meta.optString("zwaveFrameType", "")),
          csvEscape(meta.optString("sidewalkSmsn", "")),
          csvEscape(meta.optString("sidewalkFrameType", ""))
        ).joinToString(",")
      )
    }

    val suffix = protocolFilter?.let { "-$it" } ?: ""
    return ExportResult("urchin-export$suffix.csv", sb.toString(), "text/csv")
  }

  fun exportKml(
    devices: List<DeviceEntity>,
    sightings: List<SightingEntity>,
    protocolFilter: String? = null
  ): ExportResult {
    val filtered = filterByProtocol(devices, sightings, protocolFilter)
    val sightingsByDevice = filtered.second.groupBy { it.deviceKey }
    val deviceMap = filtered.first.associateBy { it.deviceKey }

    val sb = StringBuilder()
    sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
    sb.appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
    sb.appendLine("<Document>")
    sb.appendLine("<name>Urchin SDR Export</name>")
    sb.appendLine("<description>RF emitter observations</description>")

    // Protocol-based styles
    for (protocol in listOf("tpms", "pocsag", "adsb", "uat", "p25", "lorawan", "meshtastic", "wmbus", "zwave", "sidewalk")) {
      val color = protocolColor(protocol)
      sb.appendLine("""<Style id="style-$protocol"><IconStyle><color>$color</color><scale>0.8</scale></IconStyle></Style>""")
    }

    // Placemarks from geolocated sightings
    for ((deviceKey, deviceSightings) in sightingsByDevice) {
      val device = deviceMap[deviceKey] ?: continue
      for (sighting in deviceSightings) {
        val lat = sighting.receiverLat ?: continue
        val lon = sighting.receiverLon ?: continue
        val name = xmlEscape(device.userCustomName ?: device.displayName ?: device.lastAddress ?: deviceKey.take(8))
        val protocol = device.protocolType ?: "unknown"
        val time = utcFormat.format(Date(sighting.timestamp))

        sb.appendLine("<Placemark>")
        sb.appendLine("<name>$name</name>")
        sb.appendLine("<description>${xmlEscape(protocol.uppercase())} RSSI=${sighting.rssi} dBm at $time</description>")
        sb.appendLine("""<styleUrl>#style-$protocol</styleUrl>""")
        sb.appendLine("<TimeStamp><when>$time</when></TimeStamp>")
        sb.appendLine("<Point><coordinates>$lon,$lat,0</coordinates></Point>")
        sb.appendLine("</Placemark>")
      }
    }

    // ADS-B targets with their own positions
    for (device in filtered.first) {
      if (device.protocolType != "adsb" && device.protocolType != "uat") continue
      val meta = parseMetadata(device.lastMetadataJson)
      val lat = meta.optDouble("adsbLat", Double.NaN)
      val lon = meta.optDouble("adsbLon", Double.NaN)
      if (lat.isNaN() || lon.isNaN()) continue
      val name = xmlEscape(meta.optString("adsbCallsign", device.lastAddress ?: ""))
      val alt = meta.optInt("adsbAltitude", 0)
      val time = utcFormat.format(Date(device.lastSeen))

      sb.appendLine("<Placemark>")
      sb.appendLine("<name>$name (aircraft)</name>")
      sb.appendLine("<description>ICAO=${meta.optString("adsbIcao", "")} ALT=${alt}ft RSSI=${device.lastRssi} dBm</description>")
      sb.appendLine("""<styleUrl>#style-${device.protocolType}</styleUrl>""")
      sb.appendLine("<TimeStamp><when>$time</when></TimeStamp>")
      sb.appendLine("<Point><altitudeMode>absolute</altitudeMode><coordinates>$lon,$lat,${alt * 0.3048}</coordinates></Point>")
      sb.appendLine("</Placemark>")
    }

    sb.appendLine("</Document>")
    sb.appendLine("</kml>")

    val suffix = protocolFilter?.let { "-$it" } ?: ""
    return ExportResult("urchin-export$suffix.kml", sb.toString(), "application/vnd.google-earth.kml+xml")
  }

  fun exportGeoJson(
    devices: List<DeviceEntity>,
    sightings: List<SightingEntity>,
    protocolFilter: String? = null
  ): ExportResult {
    val filtered = filterByProtocol(devices, sightings, protocolFilter)
    val sightingsByDevice = filtered.second.groupBy { it.deviceKey }
    val deviceMap = filtered.first.associateBy { it.deviceKey }

    val features = JSONArray()

    // Receiver-position sightings
    for ((deviceKey, deviceSightings) in sightingsByDevice) {
      val device = deviceMap[deviceKey] ?: continue
      for (sighting in deviceSightings) {
        val lat = sighting.receiverLat ?: continue
        val lon = sighting.receiverLon ?: continue
        features.put(buildFeature(
          lon, lat,
          mapOf(
            "deviceKey" to deviceKey,
            "name" to (device.userCustomName ?: device.displayName),
            "protocol" to device.protocolType,
            "rssi" to sighting.rssi,
            "timestamp" to utcFormat.format(Date(sighting.timestamp)),
            "type" to "receiver_position"
          )
        ))
      }
    }

    // ADS-B target positions
    for (device in filtered.first) {
      if (device.protocolType != "adsb" && device.protocolType != "uat") continue
      val meta = parseMetadata(device.lastMetadataJson)
      val lat = meta.optDouble("adsbLat", Double.NaN)
      val lon = meta.optDouble("adsbLon", Double.NaN)
      if (lat.isNaN() || lon.isNaN()) continue
      features.put(buildFeature(
        lon, lat,
        mapOf(
          "deviceKey" to device.deviceKey,
          "name" to (meta.optString("adsbCallsign", "") + " (aircraft)"),
          "protocol" to device.protocolType,
          "icao" to meta.optString("adsbIcao", ""),
          "altitude_ft" to meta.optInt("adsbAltitude", 0),
          "rssi" to device.lastRssi,
          "timestamp" to utcFormat.format(Date(device.lastSeen)),
          "type" to "target_position"
        )
      ))
    }

    val collection = JSONObject()
    collection.put("type", "FeatureCollection")
    collection.put("features", features)

    val suffix = protocolFilter?.let { "-$it" } ?: ""
    return ExportResult("urchin-export$suffix.geojson", collection.toString(2), "application/geo+json")
  }

  private fun buildFeature(lon: Double, lat: Double, properties: Map<String, Any?>): JSONObject {
    val feature = JSONObject()
    feature.put("type", "Feature")
    val geometry = JSONObject()
    geometry.put("type", "Point")
    geometry.put("coordinates", JSONArray().put(lon).put(lat))
    feature.put("geometry", geometry)
    val props = JSONObject()
    for ((k, v) in properties) {
      if (v != null) props.put(k, v)
    }
    feature.put("properties", props)
    return feature
  }

  private fun filterByProtocol(
    devices: List<DeviceEntity>,
    sightings: List<SightingEntity>,
    protocol: String?
  ): Pair<List<DeviceEntity>, List<SightingEntity>> {
    if (protocol == null) return devices to sightings
    return devices.filter { it.protocolType == protocol } to
      sightings.filter { it.protocolType == protocol }
  }

  private fun parseMetadata(json: String?): JSONObject {
    return try {
      if (json != null) JSONObject(json) else JSONObject()
    } catch (_: Exception) {
      JSONObject()
    }
  }

  private fun csvEscape(value: String?): String {
    if (value.isNullOrEmpty()) return ""
    return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
      "\"${value.replace("\"", "\"\"")}\""
    } else {
      value
    }
  }

  private fun xmlEscape(value: String): String {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
      .replace("\"", "&quot;").replace("'", "&apos;")
  }

  private fun JSONObject.optStringOrEmpty(key: String): String {
    return if (has(key)) optString(key, "") else ""
  }

  private fun protocolColor(protocol: String): String = when (protocol) {
    "tpms" -> "ff00ff00"      // green
    "pocsag" -> "ff00ffff"    // cyan
    "adsb" -> "ff0000ff"      // blue
    "uat" -> "ff4444ff"       // light blue
    "p25" -> "ffff0000"       // red
    "lorawan" -> "ffff8800"   // orange
    "meshtastic" -> "ffffff00" // yellow
    "wmbus" -> "ff8800ff"     // purple
    "zwave" -> "ffff0088"     // pink
    "sidewalk" -> "ff888888"  // gray
    else -> "ffffffff"        // white
  }
}
