package com.example.service.child

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.local.entity.ChildProfileEntity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Registers one Android OS geofence per configured child safe-place alias. */
class SafePlaceGeofenceManager(private val context: Context) {
    suspend fun addSafePlace(profile: ChildProfileEntity, latitude: Double, longitude: Double): Result<Unit> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return Result.failure(SecurityException("Precise location permission is required."))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return Result.failure(SecurityException("Allow background location in Android Settings before enabling arrival/exit alerts."))
        }
        val geofence = Geofence.Builder()
            .setRequestId(profile.id)
            .setCircularRegion(latitude, longitude, profile.geofenceRadiusMeters.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setNotificationResponsiveness(5000)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // Do not treat registration while already inside as a confirmed arrival.
            .addGeofence(geofence)
            .build()
        return suspendCancellableCoroutine { continuation ->
            try {
                LocationServices.getGeofencingClient(context).addGeofences(request, geofencePendingIntent())
                    .addOnSuccessListener {
                        if (continuation.isActive) continuation.resume(Result.success(Unit))
                    }
                    .addOnFailureListener { error ->
                        if (continuation.isActive) continuation.resume(Result.failure(error))
                    }
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resume(Result.failure(error))
            }
        }
    }

    private fun geofencePendingIntent(): PendingIntent {
        val intent = Intent(context, SafePlaceGeofenceReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, 7601, intent, flags)
    }
}
