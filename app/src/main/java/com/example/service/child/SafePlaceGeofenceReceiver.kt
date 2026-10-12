package com.example.service.child

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.NaariKavachApp
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SafePlaceGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return
        val transition = event.geofenceTransition
        if (transition != Geofence.GEOFENCE_TRANSITION_ENTER && transition != Geofence.GEOFENCE_TRANSITION_EXIT) return
        val ids = event.triggeringGeofences?.map { it.requestId }.orEmpty()
        if (ids.isEmpty()) return
        val pending = goAsync()
        val app = context.applicationContext as? NaariKavachApp
        if (app == null) {
            pending.finish()
            return
        }
        app.applicationScope.launch(Dispatchers.IO) {
            try {
                ids.forEach { id ->
                    app.childSafetyRepo.recordGeofenceTransition(id, transition)
                    postNotification(context, id, transition)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun postNotification(context: Context, childAliasId: String, transition: Int) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33) return
        val entered = transition == Geofence.GEOFENCE_TRANSITION_ENTER
        val notification = NotificationCompat.Builder(context, NaariKavachApp.CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(if (entered) "Raksha360 safe-place arrival" else "Raksha360 safe-place departure")
            .setContentText(if (entered) "A configured child profile entered its saved boundary." else "A configured child profile exited its saved boundary.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Profile reference: ${childAliasId.take(8)}… . Confirm the child’s status in Raksha360. Device location boundaries may be delayed or inaccurate."))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify((childAliasId.hashCode() and 0x7fffffff), notification)
        } catch (_: SecurityException) { }
    }
}
