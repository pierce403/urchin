package guru.urchin.group

import guru.urchin.data.AlertRuleEntity
import guru.urchin.data.DeviceEntity
import guru.urchin.data.SightingEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes and deserializes Room entities to/from JSON for affinity group bundles.
 */
object BundleSerializer {

  fun serializePayload(
    devices: List<DeviceEntity>,
    sightings: List<SightingEntity>,
    alertRules: List<AlertRuleEntity>,
    starredDeviceKeys: List<String>
  ): String {
    val root = JSONObject()
    root.put("devices", JSONArray().apply { devices.forEach { put(deviceToJson(it)) } })
    root.put("sightings", JSONArray().apply { sightings.forEach { put(sightingToJson(it)) } })
    root.put("alertRules", JSONArray().apply { alertRules.forEach { put(alertRuleToJson(it)) } })
    root.put("starredDeviceKeys", JSONArray().apply { starredDeviceKeys.forEach { put(it) } })
    return root.toString()
  }

  fun deserializePayload(json: String): BundlePayload {
    val root = JSONObject(json)
    return BundlePayload(
      devices = parseArray(root.optJSONArray("devices")) { deviceFromJson(it) },
      sightings = parseArray(root.optJSONArray("sightings")) { sightingFromJson(it) },
      alertRules = parseArray(root.optJSONArray("alertRules")) { alertRuleFromJson(it) },
      starredDeviceKeys = parseStringArray(root.optJSONArray("starredDeviceKeys"))
    )
  }

  private fun deviceToJson(d: DeviceEntity) = JSONObject().apply {
    put("deviceKey", d.deviceKey)
    put("displayName", d.displayName ?: JSONObject.NULL)
    put("lastAddress", d.lastAddress ?: JSONObject.NULL)
    put("firstSeen", d.firstSeen)
    put("lastSeen", d.lastSeen)
    put("lastSightingAt", d.lastSightingAt)
    put("sightingsCount", d.sightingsCount)
    put("observationCount", d.observationCount)
    put("lastRssi", d.lastRssi)
    put("rssiMin", d.rssiMin)
    put("rssiMax", d.rssiMax)
    put("rssiAvg", d.rssiAvg)
    put("lastMetadataJson", d.lastMetadataJson ?: JSONObject.NULL)
    put("starred", d.starred)
    put("userCustomName", d.userCustomName ?: JSONObject.NULL)
    put("protocolType", d.protocolType ?: JSONObject.NULL)
  }

  private fun sightingToJson(s: SightingEntity) = JSONObject().apply {
    put("deviceKey", s.deviceKey)
    put("timestamp", s.timestamp)
    put("rssi", s.rssi)
    put("name", s.name ?: JSONObject.NULL)
    put("address", s.address ?: JSONObject.NULL)
    put("metadataJson", s.metadataJson ?: JSONObject.NULL)
    put("protocolType", s.protocolType ?: JSONObject.NULL)
  }

  private fun alertRuleToJson(r: AlertRuleEntity) = JSONObject().apply {
    put("matchType", r.matchType)
    put("matchPattern", r.matchPattern)
    put("displayValue", r.displayValue)
    put("emoji", r.emoji)
    put("soundPreset", r.soundPreset)
    put("enabled", r.enabled)
    put("createdAt", r.createdAt)
  }

  private fun optNullableString(o: JSONObject, key: String): String? =
    if (o.isNull(key)) null else o.optString(key).ifEmpty { null }

  private fun deviceFromJson(o: JSONObject) = DeviceEntity(
    deviceKey = o.getString("deviceKey"),
    displayName = optNullableString(o, "displayName"),
    lastAddress = optNullableString(o, "lastAddress"),
    firstSeen = o.getLong("firstSeen"),
    lastSeen = o.getLong("lastSeen"),
    lastSightingAt = o.optLong("lastSightingAt", 0),
    sightingsCount = o.optInt("sightingsCount", 0),
    observationCount = o.optInt("observationCount", 0),
    lastRssi = o.optInt("lastRssi", 0),
    rssiMin = o.optInt("rssiMin", 0),
    rssiMax = o.optInt("rssiMax", 0),
    rssiAvg = o.optDouble("rssiAvg", 0.0),
    lastMetadataJson = optNullableString(o, "lastMetadataJson"),
    starred = o.optBoolean("starred", false),
    userCustomName = optNullableString(o, "userCustomName"),
    protocolType = optNullableString(o, "protocolType")
  )

  private fun sightingFromJson(o: JSONObject) = SightingEntity(
    deviceKey = o.getString("deviceKey"),
    timestamp = o.getLong("timestamp"),
    rssi = o.optInt("rssi", 0),
    name = optNullableString(o, "name"),
    address = optNullableString(o, "address"),
    metadataJson = optNullableString(o, "metadataJson"),
    protocolType = optNullableString(o, "protocolType")
  )

  private fun alertRuleFromJson(o: JSONObject) = AlertRuleEntity(
    matchType = o.getString("matchType"),
    matchPattern = o.getString("matchPattern"),
    displayValue = o.optString("displayValue", ""),
    emoji = o.optString("emoji", ""),
    soundPreset = o.optString("soundPreset", "DEFAULT"),
    enabled = o.optBoolean("enabled", true),
    createdAt = o.optLong("createdAt", System.currentTimeMillis())
  )

  private fun <T> parseArray(arr: JSONArray?, mapper: (JSONObject) -> T): List<T> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).map { mapper(arr.getJSONObject(it)) }
  }

  private fun parseStringArray(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).map { arr.getString(it) }
  }
}

data class BundlePayload(
  val devices: List<DeviceEntity>,
  val sightings: List<SightingEntity>,
  val alertRules: List<AlertRuleEntity>,
  val starredDeviceKeys: List<String>
)
