package com.example.service.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager as AndroidLocationManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.NaariKavachApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Dedicated Android Foreground Location Service that handles periodic location updates
 * and fulfills the requirements for live location sharing during active emergencies and journeys.
 */
class ForegroundLocationService : Service() {

    companion object {
        const val ACTION_START_LOCATION_STREAM = "com.example.action.START_LOCATION_STREAM"
        const val ACTION_STOP_LOCATION_STREAM = "com.example.action.STOP_LOCATION_STREAM"
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_SESSION_TYPE = "extra_session_type" // "EMERGENCY" or "JOURNEY"

        private const val NOTIFICATION_ID = 5050
        const val CHANNEL_LOCATION_SERVICE = "location_stream_channel"

        private val _isLiveLocationActive = MutableStateFlow(false)
        val isLiveLocationActive: StateFlow<Boolean> = _isLiveLocationActive.asStateFlow()

        private val _lastDispatchedLocation = MutableStateFlow<UserLocation?>(null)
        val lastDispatchedLocation: StateFlow<UserLocation?> = _lastDispatchedLocation.asStateFlow()

        fun start(context: Context, eventId: String, sessionType: String = "EMERGENCY") {
            val intent = Intent(context, ForegroundLocationService::class.java).apply {
                action = ACTION_START_LOCATION_STREAM
                putExtra(EXTRA_EVENT_ID, eventId)
                putExtra(EXTRA_SESSION_TYPE, sessionType)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ForegroundLocationService::class.java).apply {
                action = ACTION_STOP_LOCATION_STREAM
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var streamJob: Job? = null
    private var currentEventId: String? = null
    private var sessionType: String = "EMERGENCY"
    private var androidLocationManager: AndroidLocationManager? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val loc = UserLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                speed = location.speed,
                timestamp = location.time,
                isFallback = false,
                description = "Nagpur • Lat ${String.format("%.4f", location.latitude)}, Lng ${String.format("%.4f", location.longitude)}"
            )
            _lastDispatchedLocation.value = loc
            updateNotification(loc)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        androidLocationManager = getSystemService(Context.LOCATION_SERVICE) as? AndroidLocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_LOCATION_STREAM -> {
                val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: "SESSION-LOC"
                sessionType = intent.getStringExtra(EXTRA_SESSION_TYPE) ?: "EMERGENCY"
                startLocationTracking(eventId)
            }
            ACTION_STOP_LOCATION_STREAM -> stopLocationTracking()
            else -> {}
        }
        return START_NOT_STICKY
    }

    private fun startLocationTracking(eventId: String) {
        currentEventId = eventId
        _isLiveLocationActive.value = true

        val initialNotification = buildLocationNotification(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        registerPeriodicLocationUpdates()
        startDispatchLoop(eventId)
    }

    private fun stopLocationTracking() {
        _isLiveLocationActive.value = false
        streamJob?.cancel()
        try {
            androidLocationManager?.removeUpdates(locationListener)
        } catch (_: Exception) {}
        serviceScope.launch {
            val app = NaariKavachApp.instance
            val stillMonitoring = app.settingsRepo.isGuardModeEnabled.value
            val result = app.backendClient.postDeviceHeartbeat(
                monitoringEnabled = stillMonitoring,
                offlineLocationEmailConsent = app.settingsRepo.offlineLocationEmailConsent.value,
                contacts = app.trustedContactRepo.getActiveContactsList(),
                location = _lastDispatchedLocation.value ?: app.locationManager.currentLocation.value
            )
            if (result.isFailure && app.settingsRepo.policeEndpointConfigured.value && !app.settingsRepo.isDemoMode.value) {
                app.auditLogRepo.log("DEVICE_HEARTBEAT_SYNC_FAILED", result.exceptionOrNull()?.message ?: "Heartbeat failed", "WARNING")
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerPeriodicLocationUpdates() {
        val lm = androidLocationManager ?: return
        try {
            if (lm.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    AndroidLocationManager.GPS_PROVIDER,
                    3000L,
                    3.0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }
            if (lm.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    AndroidLocationManager.NETWORK_PROVIDER,
                    3000L,
                    3.0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }
        } catch (_: SecurityException) {}
    }

    private fun startDispatchLoop(eventId: String) {
        streamJob?.cancel()
        streamJob = serviceScope.launch {
            val app = NaariKavachApp.instance
            var lastHeartbeatAt = 0L

            while (isActive && _isLiveLocationActive.value) {
                delay(5000)
                val loc = _lastDispatchedLocation.value ?: app.locationManager.currentLocation.value
                app.emergencyRepo.streamLocationUpdate(
                    eventId = eventId,
                    lat = loc.latitude,
                    lng = loc.longitude,
                    acc = loc.accuracy,
                    speed = loc.speed
                )
                val now = System.currentTimeMillis()
                if (now - lastHeartbeatAt >= 60_000L) {
                    val heartbeat = app.backendClient.postDeviceHeartbeat(
                        monitoringEnabled = true,
                        offlineLocationEmailConsent = app.settingsRepo.offlineLocationEmailConsent.value,
                        contacts = app.trustedContactRepo.getActiveContactsList(),
                        location = loc
                    )
                    if (heartbeat.isFailure && app.settingsRepo.policeEndpointConfigured.value && !app.settingsRepo.isDemoMode.value) {
                        app.auditLogRepo.log("DEVICE_HEARTBEAT_SYNC_FAILED", heartbeat.exceptionOrNull()?.message ?: "Heartbeat failed", "WARNING")
                    }
                    lastHeartbeatAt = now
                }
            }
        }
    }

    private fun updateNotification(loc: UserLocation) {
        val notification = buildLocationNotification(loc)
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildLocationNotification(loc: UserLocation?): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dial112Intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
        val pending112 = PendingIntent.getActivity(
            this,
            1,
            dial112Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (sessionType == "EMERGENCY") "Live Emergency Location Sharing Active" else "Journey Safety Location Sharing"
        val coordText = if (loc != null) {
            "Nagpur • Lat ${String.format("%.4f", loc.latitude)}, Lng ${String.format("%.4f", loc.longitude)}"
        } else {
            "Sharing live GPS breadcrumbs with trusted contacts & control room"
        }

        return NotificationCompat.Builder(this, CHANNEL_LOCATION_SERVICE)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(coordText)
            .setPriority(if (sessionType == "EMERGENCY") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_call, "Call 112", pending112)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_LOCATION_SERVICE,
                "Live Location Sharing Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing live location updates during safety journeys and emergencies"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isLiveLocationActive.value = false
        streamJob?.cancel()
        try {
            androidLocationManager?.removeUpdates(locationListener)
        } catch (_: Exception) {}
    }
}
