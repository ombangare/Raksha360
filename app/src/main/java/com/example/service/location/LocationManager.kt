package com.example.service.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager as AndroidLocationManager
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LocationStatus {
    ACTIVE_GPS,
    ACQUIRING,
    PERMISSION_DENIED,
    GPS_DISABLED,
    STALE,
    DEMO_MODE,
    UNAVAILABLE
}

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,
    val timestamp: Long,
    val isFallback: Boolean = false,
    val description: String = "Location unavailable",
    val status: LocationStatus = LocationStatus.ACQUIRING,
    val provenance: String = "UNAVAILABLE"
)

class AppLocationManager(private val context: Context) {

    companion object {
        // Nagpur center reference coordinates for isolated demo simulation only
        const val NAGPUR_CENTER_LAT = 21.1458
        const val NAGPUR_CENTER_LNG = 79.0882
        const val SADAR_NAGPUR_LAT = 21.1612
        const val SADAR_NAGPUR_LNG = 79.0825
    }

    private val androidLocManager = context.getSystemService(Context.LOCATION_SERVICE) as? AndroidLocationManager

    private val _currentLocation = MutableStateFlow(
        UserLocation(
            latitude = 0.0,
            longitude = 0.0,
            accuracy = 0.0f,
            speed = 0f,
            timestamp = System.currentTimeMillis(),
            isFallback = true,
            description = "Acquiring GPS fix…",
            status = LocationStatus.ACQUIRING,
            provenance = "UNAVAILABLE"
        )
    )
    val currentLocation: StateFlow<UserLocation> = _currentLocation.asStateFlow()

    private val _isLocationEnabled = MutableStateFlow(isGpsEnabled())
    val isLocationEnabled: StateFlow<Boolean> = _isLocationEnabled.asStateFlow()

    fun isGpsEnabled(): Boolean {
        return try {
            val gps = androidLocManager?.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER) ?: false
            val network = androidLocManager?.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER) ?: false
            gps || network
        } catch (_: Exception) {
            false
        }
    }

    fun applyDemoLocation() {
        _currentLocation.value = UserLocation(
            latitude = SADAR_NAGPUR_LAT,
            longitude = SADAR_NAGPUR_LNG,
            accuracy = 15.0f,
            speed = 0f,
            timestamp = System.currentTimeMillis(),
            isFallback = true,
            description = "Demo Mode • Nagpur (Simulation)",
            status = LocationStatus.DEMO_MODE,
            provenance = "DEMO_SIMULATION"
        )
    }

    fun onPermissionDenied() {
        if (_currentLocation.value.status != LocationStatus.ACTIVE_GPS) {
            _currentLocation.value = _currentLocation.value.copy(
                status = LocationStatus.PERMISSION_DENIED,
                description = "Location Permission Required"
            )
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val isGps = location.provider?.equals(AndroidLocationManager.GPS_PROVIDER, ignoreCase = true) == true
            _currentLocation.value = UserLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                speed = location.speed,
                timestamp = location.time,
                isFallback = false,
                description = "Nagpur • Lat ${String.format("%.4f", location.latitude)}, Lng ${String.format("%.4f", location.longitude)} (±${location.accuracy.toInt()}m)",
                status = LocationStatus.ACTIVE_GPS,
                provenance = if (isGps) "GPS_HARDWARE" else "NETWORK_PROVIDER"
            )
        }

        override fun onProviderEnabled(provider: String) {
            _isLocationEnabled.value = isGpsEnabled()
        }

        override fun onProviderDisabled(provider: String) {
            _isLocationEnabled.value = isGpsEnabled()
            if (!isGpsEnabled() && _currentLocation.value.status != LocationStatus.DEMO_MODE) {
                _currentLocation.value = _currentLocation.value.copy(
                    status = LocationStatus.GPS_DISABLED,
                    description = "Device GPS Disabled"
                )
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(minTimeMs: Long = 4000, minDistanceM: Float = 5f) {
        _isLocationEnabled.value = isGpsEnabled()
        val lm = androidLocManager ?: return

        try {
            // Check last known location first
            val lastGps = lm.getLastKnownLocation(AndroidLocationManager.GPS_PROVIDER)
            val lastNet = lm.getLastKnownLocation(AndroidLocationManager.NETWORK_PROVIDER)
            val bestLast = lastGps ?: lastNet
            if (bestLast != null) {
                val ageMs = System.currentTimeMillis() - bestLast.time
                val isStale = ageMs > 120_000L
                val isGps = bestLast.provider?.equals(AndroidLocationManager.GPS_PROVIDER, ignoreCase = true) == true
                _currentLocation.value = UserLocation(
                    latitude = bestLast.latitude,
                    longitude = bestLast.longitude,
                    accuracy = bestLast.accuracy,
                    speed = bestLast.speed,
                    timestamp = bestLast.time,
                    isFallback = isStale,
                    description = if (isStale) "Nagpur • Last known fix (${ageMs / 60_000}m ago)"
                                  else "Nagpur • Lat ${String.format("%.4f", bestLast.latitude)}, Lng ${String.format("%.4f", bestLast.longitude)}",
                    status = if (isStale) LocationStatus.STALE else LocationStatus.ACTIVE_GPS,
                    provenance = if (isGps) "GPS_HARDWARE" else "NETWORK_PROVIDER"
                )
            }

            if (lm.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    AndroidLocationManager.GPS_PROVIDER,
                    minTimeMs,
                    minDistanceM,
                    locationListener
                )
            }
            if (lm.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    AndroidLocationManager.NETWORK_PROVIDER,
                    minTimeMs,
                    minDistanceM,
                    locationListener
                )
            }
        } catch (_: SecurityException) {
            // Handled safely
        }
    }

    fun stopLocationUpdates() {
        try {
            androidLocManager?.removeUpdates(locationListener)
        } catch (_: Exception) {}
    }
}
