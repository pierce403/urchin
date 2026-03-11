package guru.urchin.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Formatters {
  private var cachedLocale: Locale? = null
  private var cachedFormatter: DateTimeFormatter? = null

  fun formatTimestamp(timestamp: Long): String {
    val locale = Locale.getDefault()
    val formatter = if (locale == cachedLocale && cachedFormatter != null) {
      cachedFormatter!!
    } else {
      DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(locale).also { cachedFormatter = it; cachedLocale = locale }
    }
    return formatter.format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
  }

  fun formatRssi(rssi: Int): String {
    return if (rssi <= -100) {
      "Signal unavailable"
    } else {
      "RSSI: $rssi dBm"
    }
  }

  fun formatPressure(kpa: Double): String {
    return String.format(Locale.US, "%.1f kPa / %.1f PSI", kpa, kpa * 0.145038)
  }

  fun formatTemperature(celsius: Double): String {
    return String.format(Locale.US, "%.1f C / %.1f F", celsius, celsius * 9.0 / 5.0 + 32.0)
  }

  fun formatGain(gain: Int?): String {
    return gain?.let { "$it dB" } ?: "Auto"
  }

  fun formatSightingsCount(count: Int): String {
    return if (count == 1) {
      "1 sighting"
    } else {
      "$count sightings"
    }
  }
}
