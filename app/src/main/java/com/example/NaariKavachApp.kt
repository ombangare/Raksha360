package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.local.AppDatabase
import com.example.data.remote.BackendApiClient
import com.example.data.repository.AuditLogRepository
import com.example.data.repository.EmergencyRepository
import com.example.data.repository.EvidenceRepository
import com.example.data.repository.SettingsRepository
import com.example.data.repository.TrustedContactRepository
import com.example.data.repository.VoiceProfileRepository
import com.example.data.security.KeystoreCryptoManager
import com.example.service.location.AppLocationManager
import com.example.voice.VoicePipeline
import com.example.voice.audio.AudioRecordManager
import com.example.voice.audio.IncidentAudioRecorder
import com.example.voice.model.VoiceModelEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NaariKavachApp : Application() {

    companion object {
        lateinit var instance: NaariKavachApp
            private set

        const val CHANNEL_GUARD_MODE = "guard_mode_channel"
        const val CHANNEL_EMERGENCY = "emergency_channel"
        const val CHANNEL_ALERTS = "alerts_channel"
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Security & Storage
    lateinit var cryptoManager: KeystoreCryptoManager
    lateinit var database: AppDatabase

    // Remote
    lateinit var backendClient: BackendApiClient

    // Repositories
    lateinit var authenticationRepo: com.example.data.repository.AuthenticationRepository
    lateinit var voiceProfileRepo: VoiceProfileRepository
    lateinit var settingsRepo: SettingsRepository
    lateinit var trustedContactRepo: TrustedContactRepository
    lateinit var emergencyRepo: EmergencyRepository
    lateinit var evidenceRepo: EvidenceRepository
    lateinit var auditLogRepo: AuditLogRepository
    lateinit var auditLoggerService: com.example.service.audit.AuditLoggerService
    lateinit var childSafetyRepo: com.example.data.repository.ChildSafetyRepository

    // Map & Safety Engines
    lateinit var mapProvider: com.example.domain.map.IMapViewProvider
    lateinit var safetyScoringEngine: com.example.domain.map.SafetyScoringEngine

    // Dispatch Adapters
    lateinit var policeDispatchAdapter: com.example.service.dispatch.IPoliceDispatchAdapter
    lateinit var trustedContactDispatchAdapter: com.example.service.dispatch.ITrustedContactDispatchAdapter

    // Audio & Voice Engine
    lateinit var voiceModelEngine: VoiceModelEngine
    lateinit var voicePipeline: VoicePipeline
    lateinit var audioRecordManager: AudioRecordManager
    lateinit var incidentAudioRecorder: IncidentAudioRecorder

    // Location
    lateinit var locationManager: AppLocationManager

    override fun onCreate() {
        super.onCreate()
        instance = this

        cryptoManager = KeystoreCryptoManager()
        database = AppDatabase.getInstance(this)
        settingsRepo = SettingsRepository(this)

        backendClient = BackendApiClient(this) {
            BackendApiClient.ApiConfiguration(
                isConfigured = settingsRepo.policeEndpointConfigured.value,
                baseUrl = settingsRepo.policeEndpointUrl.value,
                authToken = settingsRepo.policeAuthToken.value,
                isDemoMode = settingsRepo.isDemoMode.value
            )
        }

        voiceProfileRepo = VoiceProfileRepository(this, cryptoManager)
        trustedContactRepo = TrustedContactRepository(database.trustedContactDao())
        emergencyRepo = EmergencyRepository(
            context = this,
            eventDao = database.emergencyEventDao(),
            queueDao = database.offlineQueueDao(),
            cryptoManager = cryptoManager,
            backendClient = backendClient,
            trustedContactRepo = trustedContactRepo,
            settingsRepo = settingsRepo
        )
        evidenceRepo = EvidenceRepository(database.evidenceDao(), cryptoManager)
        auditLoggerService = com.example.service.audit.AuditLoggerService(this, database.auditLogDao(), applicationScope)
        auditLogRepo = AuditLogRepository(database.auditLogDao(), auditLoggerService)
        childSafetyRepo = com.example.data.repository.ChildSafetyRepository(this, database.childSafetyDao())
        authenticationRepo = com.example.data.repository.AuthenticationRepository(this, backendClient, auditLoggerService)

        mapProvider = com.example.domain.map.OsmNativeMapProvider()
        safetyScoringEngine = com.example.domain.map.SafetyScoringEngine()

        policeDispatchAdapter = if (settingsRepo.isDemoMode.value) {
            com.example.service.dispatch.DemoPoliceDispatchAdapter()
        } else {
            com.example.service.dispatch.ProductionPoliceDispatchAdapter(backendClient, settingsRepo)
        }
        trustedContactDispatchAdapter = com.example.service.dispatch.ProductionTrustedContactDispatchAdapter(backendClient)

        voiceModelEngine = VoiceModelEngine(this)
        voicePipeline = VoicePipeline(voiceModelEngine) {
            val profile = voiceProfileRepo.currentProfile.value
            if (profile != null && profile.isEnrolled) {
                VoicePipeline.EnrolledProfileData(
                    targetPhrase = profile.phrase,
                    speakerEmbedding = profile.embedding,
                    verificationThreshold = profile.threshold,
                    isEnrolled = true
                )
            } else {
                null
            }
        }

        audioRecordManager = AudioRecordManager(this)
        incidentAudioRecorder = IncidentAudioRecorder(this, cryptoManager)
        locationManager = AppLocationManager(this)

        createNotificationChannels()

        // Seed default trusted contacts only if Demo Mode is explicitly enabled
        applicationScope.launch(Dispatchers.IO) {
            trustedContactRepo.seedDemoContactsIfEmpty(settingsRepo.isDemoMode.value)
            auditLogRepo.log("APP_INITIALIZED", "Raksha360 initialized with Keystore security", "INFO")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            val guardChannel = NotificationChannel(
                CHANNEL_GUARD_MODE,
                "Voice SOS Guard Mode",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status while Raksha360 is monitoring for the configured emergency voice phrase"
                setShowBadge(false)
            }

            val emergencyChannel = NotificationChannel(
                CHANNEL_EMERGENCY,
                "Emergency Critical Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent status notifications during active emergency events"
                enableVibration(true)
                setShowBadge(true)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Safety, child check-in and offline-sync alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General alerts, check-in reminders and sync updates"
            }

            nm.createNotificationChannel(guardChannel)
            nm.createNotificationChannel(emergencyChannel)
            nm.createNotificationChannel(alertsChannel)
        }
    }
}
