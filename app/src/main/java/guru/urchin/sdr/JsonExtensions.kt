package guru.urchin.sdr

import org.json.JSONObject

internal fun JSONObject.optStringOrNull(key: String): String? {
  if (!has(key) || isNull(key)) return null
  return optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
}

internal fun JSONObject.optDoubleOrNull(key: String): Double? {
  if (!has(key) || isNull(key)) return null
  return optDouble(key).takeIf { !it.isNaN() && !it.isInfinite() }
}

internal fun JSONObject.optIntOrNull(key: String): Int? {
  if (!has(key) || isNull(key)) return null
  return optInt(key)
}

internal fun JSONObject.optBooleanOrNull(key: String): Boolean? {
  if (!has(key) || isNull(key)) return null
  return when {
    optInt(key, -1) == 1 -> true
    optInt(key, -1) == 0 -> false
    else -> optBoolean(key)
  }
}
