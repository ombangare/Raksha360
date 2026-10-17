package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.NaariKavachApp
import com.example.voice.audio.AudioFeatureExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class GuardModeService : Service() {

    companion object {
        const val ACTION_START_GUARD = "com.example.action.START_GUARD"
        const val ACTION_STOP_GUARD = "com.example.action.STOP_GUARD"
        const val ACTION_PAUSE_LISTENING = "com.example.action.PAUSE_LISTENING"
        const val ACTION_RESUME_LISTENING = "com.example.action.RESUME_LISTENING"
        private const val NOTIFICATION_ID = 2001

        @Volatile
        var isServiceRunning = false
            private set

        @Volatile
        var isListeningPaused = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, GuardModeService::class.java).apply {
                action = ACTION_START_GUARD
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, GuardModeService::class.java).apply {
                action = ACTION_STOP_GUARD
            }
            context.startService(intent)
        }

        fun pauseListening(context: Context) {
            val intent = Intent(context, GuardModeService::class.java).apply {
                action = ACTION_PAUSE_LISTENING
            }
            context.startService(intent)
        }

        fun resumeListening(context: Context) {
            val intent = Intent(context, GuardModeService::class.java).apply {
                action = ACTION_RESUME_LISTENING
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private var listeningJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var shakeDetector: com.example.voice.sensor.ShakeDetector? = null
    private var shakeSettingsJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_GUARD -> startGuardMode()
            ACTION_STOP_GUARD -> stopGuardMode()
            ACTION_PAUSE_LISTENING -> handlePauseListening()
            ACTION_RESUME_LISTENING -> handleResumeListening()
            else -> startGuardMode()
        }
        return START_NOT_STICKY
    }

    private fun handlePauseListening() {
        if (!isServiceRunning) return
        isListeningPaused = true
        listeningJob?.cancel()
        cleanupAudioRecord()
    }

    private fun handleResumeListening() {
        if (!isServiceRunning || !isListeningPaused) return
        isListeningPaused = false
        startAudioListeningLoop()
    }

    private fun startGuardMode() {
        if (isServiceRunning) return
        isServiceRunning = true

        val app = NaariKavachApp.instance
        app.settingsRepo.setGuardModeEnabled(true)
        app.applicationScope.launch {
            app.auditLogRepo.log("GUARD_MODE_STARTED", "Microphone foreground service listening for Voice SOS", "INFO")
        }

        val notification = buildGuardNotification()
        val hasLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val foregroundType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                if (hasLocationPermission) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            startForeground(NOTIFICATION_ID, notification, foregroundType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (hasLocationPermission) {
            app.locationManager.startLocationUpdates(minTimeMs = 30_000L, minDistanceM = 15f)
        }

        // Start reactive Shake SOS sensor monitoring
        shakeSettingsJob?.cancel()
        shakeSettingsJob = serviceScope.launch {
            app.settingsRepo.isShakeSosEnabled.collect { enabled ->
                if (enabled) {
                    if (shakeDetector == null) {
                        val shake = com.example.voice.sensor.ShakeDetector(applicationContext) {
                            val appInstance = NaariKavachApp.instance
                            EmergencyOrchestrator.triggerEmergency(
                                context = applicationContext,
                                emergencyRepo = appInstance.emergencyRepo,
                                locationManager = appInstance.locationManager,
                                audioRecorder = appInstance.incidentAudioRecorder,
                                evidenceRepo = appInstance.evidenceRepo,
                                auditRepo = appInstance.auditLogRepo,
                                settingsRepo = appInstance.settingsRepo,
                                scope = appInstance.applicationScope,
                                triggerType = "SHAKE_SOS"
                            )
                        }
                        shake.sensitivity = app.settingsRepo.shakeSensitivity.value
                        shake.start()
                        shakeDetector = shake
                    }
                } else {
                    shakeDetector?.stop()
                    shakeDetector = null
                }
            }
        }
        serviceScope.launch {
            app.settingsRepo.shakeSensitivity.collect { sens ->
                shakeDetector?.sensitivity = sens
            }
        }

        startAudioListeningLoop()
    }

    private fun stopGuardMode() {
        isServiceRunning = false
        val app = NaariKavachApp.instance
        app.settingsRepo.setGuardModeEnabled(false)
        app.applicationScope.launch {
            app.auditLogRepo.log("GUARD_MODE_STOPPED", "Guard Mode stopped by user", "INFO")
        }

        listeningJob?.cancel()
        cleanupAudioRecord()
        shakeSettingsJob?.cancel()
        shakeSettingsJob = null
        shakeDetector?.stop()
        shakeDetector = null
        serviceScope.launch {
            val locationSessionStillActive = app.settingsRepo.isJourneyActive.value ||
                com.example.service.location.ForegroundLocationService.isLiveLocationActive.value ||
                app.emergencyRepo.activeEmergency.first() != null
            publishDeviceHeartbeat(app, monitoringEnabled = locationSessionStillActive)
            if (!locationSessionStillActive) app.locationManager.stopLocationUpdates()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun publishDeviceHeartbeat(app: NaariKavachApp, monitoringEnabled: Boolean) {
        val result = app.backendClient.postDeviceHeartbeat(
            monitoringEnabled = monitoringEnabled,
            offlineLocationEmailConsent = app.settingsRepo.offlineLocationEmailConsent.value,
            contacts = app.trustedContactRepo.getActiveContactsList(),
            location = app.locationManager.currentLocation.value
        )
        if (result.isFailure && app.settingsRepo.policeEndpointConfigured.value && !app.settingsRepo.isDemoMode.value) {
            app.auditLogRepo.log("DEVICE_HEARTBEAT_SYNC_FAILED", result.exceptionOrNull()?.message ?: "Heartbeat failed", "WARNING")
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioListeningLoop() {
        listeningJob?.cancel()
        listeningJob = serviceScope.launch {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val windowSize = maxOf(minBuf, 4096)

            try {
                val app = NaariKavachApp.instance
                val profile = app.voiceProfileRepo.currentProfile.value
                if (profile == null || !profile.isEnrolled || profile.sampleCount < 5 || profile.embedding.size <= 32) {
                    isServiceRunning = false
                    app.settingsRepo.setGuardModeEnabled(false)
                    publishDeviceHeartbeat(app, monitoringEnabled = false)
                    app.applicationScope.launch {
                        app.auditLogRepo.log("VOICE_PROFILE_REENROLL_REQUIRED", "Guard Mode stopped because the stored voice profile is missing or uses a legacy embedding format", "WARNING")
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                val modelsReady = app.voiceModelEngine.preparePhraseEngine()
                if (!modelsReady) {
                    isServiceRunning = false
                    app.settingsRepo.setGuardModeEnabled(false)
                    publishDeviceHeartbeat(app, monitoringEnabled = false)
                    app.applicationScope.launch {
                        app.auditLogRepo.log("VOICE_ENGINE_UNAVAILABLE", "Guard Mode stopped because offline speech and speaker models were unavailable", "WARNING")
                    }
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@launch
                }

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    windowSize * 2
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    stopGuardMode()
                    return@launch
                }

                audioRecord?.startRecording()
                publishDeviceHeartbeat(app, monitoringEnabled = true)
                var lastHeartbeatAt = System.currentTimeMillis()
                val buffer = ShortArray(windowSize)

                while (isActive && isServiceRunning) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    val now = System.currentTimeMillis()
                    if (now - lastHeartbeatAt >= 60_000L) {
                        publishDeviceHeartbeat(app, monitoringEnabled = true)
                        lastHeartbeatAt = now
                    }
                    if (read > 0) {
                        val activeSlice = buffer.copyOf(read)
                        // Do not skip silence. Offline ASR needs trailing silence to finalize the
                        // utterance; the pipeline buffers the preceding speech for speaker verification.
                        val app = NaariKavachApp.instance
                        val result = app.voicePipeline.processAudioWindow(activeSlice)

                        if (result.triggerConfirmed) {
                            // Deterministic Trigger Path: Valid phrase + Verified Speaker
                            EmergencyOrchestrator.triggerEmergency(
                                context = applicationContext,
                                emergencyRepo = app.emergencyRepo,
                                locationManager = app.locationManager,
                                audioRecorder = app.incidentAudioRecorder,
                                evidenceRepo = app.evidenceRepo,
                                auditRepo = app.auditLogRepo,
                                settingsRepo = app.settingsRepo,
                                scope = app.applicationScope,
                                triggerType = "VOICE_SOS",
                                phrase = result.phraseResult.phraseDetected,
                                speakerScore = result.speakerResult.similarityScore
                            )
                            delay(5000) // Pause detector after emergency trigger
                        }
                    }
                }
            } catch (error: Exception) {
                isServiceRunning = false
                val app = NaariKavachApp.instance
                app.settingsRepo.setGuardModeEnabled(false)
                publishDeviceHeartbeat(app, monitoringEnabled = false)
                app.applicationScope.launch {
                    app.auditLogRepo.log("VOICE_GUARD_RUNTIME_ERROR", "Guard Mode stopped safely after a microphone/runtime error: ${error.javaClass.simpleName}", "WARNING")
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } finally {
                cleanupAudioRecord()
            }
        }
    }

    private fun cleanupAudioRecord() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null
        try { NaariKavachApp.instance.voicePipeline.reset() } catch (_: Exception) { }
    }

    private fun buildGuardNotification(): Notification {
        val app = NaariKavachApp.instance
        val phrase = app.voiceProfileRepo.currentProfile.value?.phrase ?: com.example.data.repository.VoiceProfileRepository.DEFAULT_PHRASE

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, GuardModeService::class.java).apply {
            action = ACTION_STOP_GUARD
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NaariKavachApp.CHANNEL_GUARD_MODE)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("Guard Mode Active - Voice SOS Protected")
            .setContentText("Listening for trigger: \"$phrase\"")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Guard Mode", pendingStop)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        listeningJob?.cancel()
        cleanupAudioRecord()
        shakeSettingsJob?.cancel()
        shakeSettingsJob = null
        shakeDetector?.stop()
        shakeDetector = null
        val app = NaariKavachApp.instance
        if (!app.settingsRepo.isJourneyActive.value && !com.example.service.location.ForegroundLocationService.isLiveLocationActive.value) {
            serviceScope.launch { if (app.emergencyRepo.activeEmergency.first() == null) app.locationManager.stopLocationUpdates() }
        }
    }
}
