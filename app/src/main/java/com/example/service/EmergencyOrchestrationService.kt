package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.NaariKavachApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class EmergencyOrchestrationService : Service() {

    companion object {
        const val ACTION_START_EMERGENCY = "com.example.action.START_EMERGENCY"
        const val ACTION_STOP_EMERGENCY = "com.example.action.STOP_EMERGENCY"
        const val EXTRA_EVENT_ID = "extra_event_id"
        private const val NOTIFICATION_ID = 9110

        @Volatile
        var isEmergencyServiceActive = false
            private set
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var locationStreamJob: Job? = null
    private var currentEventId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_EMERGENCY -> {
                val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: ""
                startEmergency(eventId)
            }
            ACTION_STOP_EMERGENCY -> stopEmergency()
            else -> {}
        }
        return START_NOT_STICKY
    }

    private fun startEmergency(eventId: String) {
        currentEventId = eventId
        isEmergencyServiceActive = true

        val notification = buildEmergencyNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startLiveLocationStreaming(eventId)
    }

    private fun stopEmergency() {
        isEmergencyServiceActive = false
        locationStreamJob?.cancel()
        currentEventId = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startLiveLocationStreaming(eventId: String) {
        locationStreamJob?.cancel()
        locationStreamJob = serviceScope.launch {
            val app = NaariKavachApp.instance
            app.locationManager.startLocationUpdates(minTimeMs = 4000, minDistanceM = 3f)

            while (isActive && isEmergencyServiceActive) {
                delay(6000)
                val loc = app.locationManager.currentLocation.value
                app.emergencyRepo.streamLocationUpdate(
                    eventId = eventId,
                    lat = loc.latitude,
                    lng = loc.longitude,
                    acc = loc.accuracy,
                    speed = loc.speed
                )
            }
        }
    }

    private fun buildEmergencyNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Direct 112 official dial intent
        val dial112Intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
        val pending112 = PendingIntent.getActivity(
            this,
            2,
            dial112Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NaariKavachApp.CHANNEL_EMERGENCY)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("EMERGENCY ACTIVE - Raksha360")
            .setContentText("Live GPS & incident telemetry streaming. Tap to view.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_call, "Call 112 Police", pending112)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isEmergencyServiceActive = false
        locationStreamJob?.cancel()
    }
}
