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

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float,
    val timestamp: Long,
    val isFallback: Boolean = false,
    val description: String = "Sadar, Nagpur"
)

class AppLocationManager(private val context: Context) {

    companion object {
        // Nagpur Sadar coordinates
        const val SADAR_NAGPUR_LAT = 21.1612
        const val SADAR_NAGPUR_LNG = 79.0825
    }

    private val androidLocManager = context.getSystemService(Context.LOCATION_SERVICE) as? AndroidLocationManager

    private val _currentLocation = MutableStateFlow(
        UserLocation(
            latitude = SADAR_NAGPUR_LAT,
            longitude = SADAR_NAGPUR_LNG,
            accuracy = 12.0f,
            speed = 0f,
            timestamp = System.currentTimeMillis(),
            isFallback = true,
            description = "Sadar, Nagpur (Default)"
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

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _currentLocation.value = UserLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                speed = location.speed,
                timestamp = location.time,
                isFallback = false,
                description = "Lat: ${String.format("%.4f", location.latitude)}, Lng: ${String.format("%.4f", location.longitude)}"
            )
        }

        override fun onProviderEnabled(provider: String) {
            _isLocationEnabled.value = isGpsEnabled()
        }

        override fun onProviderDisabled(provider: String) {
            _isLocationEnabled.value = isGpsEnabled()
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
                _currentLocation.value = UserLocation(
                    latitude = bestLast.latitude,
                    longitude = bestLast.longitude,
                    accuracy = bestLast.accuracy,
                    speed = bestLast.speed,
                    timestamp = bestLast.time,
                    isFallback = false,
                    description = "Nagpur (Lat: ${String.format("%.4f", bestLast.latitude)}, Lng: ${String.format("%.4f", bestLast.longitude)})"
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
