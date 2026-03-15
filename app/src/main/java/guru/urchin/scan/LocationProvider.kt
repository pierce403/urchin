package guru.urchin.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * Provides the receiver's GPS position for geostamping observations.
 * Uses the platform LocationManager (no Play Services dependency) so it
 * works on de-Googled / hardened devices common in field deployments.
 */
class LocationProvider(private val context: Context) {

  data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val accuracyMeters: Float?,
    val timestampMs: Long
  )

  @Volatile
  var lastFix: LocationFix? = null
    private set

  private var listening = false

  private val locationListener = object : LocationListener {
    override fun onLocationChanged(location: Location) {
      lastFix = LocationFix(
        latitude = location.latitude,
        longitude = location.longitude,
        altitude = if (location.hasAltitude()) location.altitude else null,
        accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
        timestampMs = location.time
      )
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
  }

  fun start() {
    if (listening) return
    if (!hasLocationPermission()) return

    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return

    val provider = when {
      manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
      manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
      else -> return
    }

    try {
      manager.requestLocationUpdates(provider, UPDATE_INTERVAL_MS, MIN_DISTANCE_M, locationListener)
      listening = true

      // Seed with last known fix if available
      manager.getLastKnownLocation(provider)?.let { locationListener.onLocationChanged(it) }
    } catch (_: SecurityException) {
      // Permission revoked between check and request
    }
  }

  fun stop() {
    if (!listening) return
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
    manager.removeUpdates(locationListener)
    listening = false
  }

  private fun hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
      context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
  }

  companion object {
    private const val UPDATE_INTERVAL_MS = 5_000L
    private const val MIN_DISTANCE_M = 2f
  }
}
