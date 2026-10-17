package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NaariKavachApp
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.EmergencyEventEntity
import com.example.data.local.entity.EvidenceEntity
import com.example.data.local.entity.TrustedContactEntity
import com.example.data.local.entity.ChildProfileEntity
import com.example.data.local.entity.SafetyJourneyEntity
import com.example.data.local.entity.SchoolHandoverEntity
import com.example.data.local.entity.ChildSafetyAlertEntity
import com.example.data.repository.VoiceProfileRepository
import com.example.localization.AppStrings
import com.example.localization.LanguageStrings
import com.example.service.EmergencyOrchestrator
import com.example.service.GuardModeService
import com.example.service.location.UserLocation
import com.example.data.remote.BackendConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NaariKavachApp

    val assistantManager: com.example.assistant.ElevenLabsAssistantManager
        get() = app.assistantManager

    val voiceProfile: StateFlow<VoiceProfileRepository.VoiceProfile?> = app.voiceProfileRepo.currentProfile

    val isGuardModeActive: StateFlow<Boolean> = app.settingsRepo.isGuardModeEnabled
    val isDemoMode: StateFlow<Boolean> = app.settingsRepo.isDemoMode
    val selectedLanguage: StateFlow<String> = app.settingsRepo.selectedLanguage
    val autoRecordIncidentAudio: StateFlow<Boolean> = app.settingsRepo.autoRecordIncidentAudio
    val offlineLocationEmailConsent: StateFlow<Boolean> = app.settingsRepo.offlineLocationEmailConsent
    val isShakeSosEnabled: StateFlow<Boolean> = app.settingsRepo.isShakeSosEnabled
    val shakeSensitivity: StateFlow<String> = app.settingsRepo.shakeSensitivity
    val backendEndpointConfigured: StateFlow<Boolean> = app.settingsRepo.policeEndpointConfigured
    val backendEndpointUrl: StateFlow<String> = app.settingsRepo.policeEndpointUrl
    val backendAuthToken: StateFlow<String> = app.settingsRepo.policeAuthToken

    val activeEmergency: StateFlow<EmergencyEventEntity?> = app.emergencyRepo.activeEmergency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val pendingQueueCount: StateFlow<Int> = app.emergencyRepo.pendingQueueCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isSyncing: StateFlow<Boolean> = app.emergencyRepo.isSyncing
    val lastSyncMessage: StateFlow<String?> = app.emergencyRepo.lastSyncMessage

    val trustedContacts: StateFlow<List<TrustedContactEntity>> = app.trustedContactRepo.allContacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allEvidence: StateFlow<List<EvidenceEntity>> = app.evidenceRepo.allEvidence
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentAuditLogs: StateFlow<List<AuditLogEntity>> = app.auditLogRepo.recentLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val childProfiles: StateFlow<List<ChildProfileEntity>> = app.childSafetyRepo.children
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val childSafetyJourneys: StateFlow<List<SafetyJourneyEntity>> = app.childSafetyRepo.journeys
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val childSchoolHandovers: StateFlow<List<SchoolHandoverEntity>> = app.childSafetyRepo.handovers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val childSafetyAlerts: StateFlow<List<ChildSafetyAlertEntity>> = app.childSafetyRepo.alerts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val policeStations: StateFlow<List<com.example.data.repository.PoliceStationRecord>> = app.policeDirectoryRepo.stations
    val publicHelplines: StateFlow<List<com.example.data.repository.HelplineContact>> = app.policeDirectoryRepo.helplines

    val currentLocation: StateFlow<UserLocation> = app.locationManager.currentLocation

    val liveRms: StateFlow<Float> = app.audioRecordManager.liveRmsLevel
    val isMicrophoneRecording: StateFlow<Boolean> = app.audioRecordManager.isRecording

    // Authentication State
    val authState: StateFlow<com.example.data.repository.AuthState> = app.authenticationRepo.authState
    val currentUser: com.example.data.repository.UserProfile?
        get() = (authState.value as? com.example.data.repository.AuthState.Authenticated)?.user

    private val _connectionState = MutableStateFlow<BackendConnectionState>(BackendConnectionState.Connecting)
    val connectionState: StateFlow<BackendConnectionState> = _connectionState.asStateFlow()

    private var connectionCheckJob: Job? = null

    init {
        viewModelScope.launch {
            authState.collect {
                checkBackendConnection()
            }
        }
        viewModelScope.launch {
            isDemoMode.collect {
                checkBackendConnection()
            }
        }
    }

    fun searchPoliceStations(query: String) = app.policeDirectoryRepo.search(query)
    fun findNearestPoliceStation(lat: Double, lon: Double) = app.policeDirectoryRepo.findNearestStation(lat, lon)
    fun refreshHelplinesFromBackend() = viewModelScope.launch { app.policeDirectoryRepo.refreshHelplinesFromBackend() }

    fun startLocationUpdates(minTimeMs: Long = 4000, minDistanceM: Float = 5f) {
        app.locationManager.startLocationUpdates(minTimeMs, minDistanceM)
    }

    fun stopLocationUpdates() {
        app.locationManager.stopLocationUpdates()
    }

    fun retryLocationAcquisition() {
        app.locationManager.retryLocationAcquisition()
    }

    fun onLocationPermissionDenied() {
        app.locationManager.onPermissionDenied()
    }

    suspend fun signInWithGoogle(activity: android.app.Activity): Result<com.example.data.repository.UserProfile> {
        val result = app.authenticationRepo.signInWithGoogle(activity)
        if (result.isSuccess) {
            if (!app.settingsRepo.isAppTourCompleted.value) {
                app.settingsRepo.startTour()
            }
            checkBackendConnection(forceRetry = true)
        }
        return result
    }

    fun signInDemoMode(demoName: String = "Demo Safety Reviewer", demoEmail: String = "reviewer.safety@naarikavach.local") {
        app.authenticationRepo.signInDemoMode(demoName, demoEmail)
        viewModelScope.launch {
            app.trustedContactRepo.seedDemoContactsIfEmpty(true)
        }
        if (!app.settingsRepo.isAppTourCompleted.value) {
            app.settingsRepo.startTour()
        }
        checkBackendConnection(forceRetry = true)
    }

    fun signOut() {
        viewModelScope.launch {
            app.authenticationRepo.signOut()
            checkBackendConnection(forceRetry = true)
        }
    }

    fun checkBackendConnection(forceRetry: Boolean = false) {
        connectionCheckJob?.cancel()
        connectionCheckJob = viewModelScope.launch {
            _connectionState.value = BackendConnectionState.Connecting

            if (!app.backendClient.isOnline()) {
                _connectionState.value = BackendConnectionState.Offline(
                    reason = "Device is offline. Local safety features, voice triggers, siren, and offline queue remain active."
                )
                return@launch
            }

            val maxAttempts = if (forceRetry) 1 else 3
            var delayMs = 1500L
            var lastError: String? = null

            for (attempt in 1..maxAttempts) {
                val healthResult = app.backendClient.checkHealth()
                if (healthResult.isSuccess) {
                    val health = healthResult.getOrThrow()
                    val user = currentUser
                    val demo = app.settingsRepo.isDemoMode.value

                    if (demo) {
                        _connectionState.value = BackendConnectionState.Connected(
                            endpoint = app.settingsRepo.policeEndpointUrl.value,
                            isUserAuthenticated = (user != null && !user.isDemoUser),
                            userEmail = user?.email,
                            isDemoMode = true,
                            database = health.database,
                            latencyMs = health.latencyMs
                        )
                        return@launch
                    }

                    if (user == null || user.isDemoUser) {
                        _connectionState.value = BackendConnectionState.AuthenticationRequired(
                            reason = "Sign in with Google to enable live control room alerts. Offline safety remains active."
                        )
                        return@launch
                    }

                    // Authenticated citizen: validate session token with backend
                    val sessionValidation = app.authenticationRepo.validateSessionWithBackend()
                    if (sessionValidation.isSuccess) {
                        val verified = sessionValidation.getOrThrow()
                        _connectionState.value = BackendConnectionState.Connected(
                            endpoint = app.settingsRepo.policeEndpointUrl.value,
                            isUserAuthenticated = true,
                            userEmail = verified.email,
                            isDemoMode = false,
                            database = health.database,
                            latencyMs = health.latencyMs
                        )
                        // Trigger background heartbeat sync once verified
                        syncOfflineLocationEmailSettings()
                        return@launch
                    } else {
                        val error = sessionValidation.exceptionOrNull()
                        val msg = error?.message.orEmpty()
                        if (msg.contains("401") || msg.contains("invalid", ignoreCase = true) || msg.contains("expired", ignoreCase = true)) {
                            _connectionState.value = BackendConnectionState.AuthenticationRequired(
                                reason = "Session expired or rejected by server. Please sign in again."
                            )
                            return@launch
                        }
                        lastError = msg
                    }
                } else {
                    val error = healthResult.exceptionOrNull()
                    lastError = error?.message ?: "Control room unreachable."
                    if (!app.backendClient.isOnline()) {
                        _connectionState.value = BackendConnectionState.Offline(
                            reason = "Network connection lost during check."
                        )
                        return@launch
                    }
                }

                if (attempt < maxAttempts) {
                    delay(delayMs)
                    delayMs *= 2
                }
            }

            _connectionState.value = BackendConnectionState.ServiceUnavailable(
                error = lastError ?: "Control room service is currently unavailable.",
                lastAttemptAt = System.currentTimeMillis()
            )
        }
    }

    val isAppTourCompleted: StateFlow<Boolean> = app.settingsRepo.isAppTourCompleted
    val isAppTourActive: StateFlow<Boolean> = app.settingsRepo.isAppTourActive
    val currentTourStep: StateFlow<Int> = app.settingsRepo.currentTourStep

    fun startAppTour() {
        app.settingsRepo.startTour()
    }

    fun dismissAppTour(markCompleted: Boolean = true) {
        app.settingsRepo.dismissTour(markCompleted)
    }

    fun nextTourStep() {
        val current = currentTourStep.value
        if (current < 8) {
            app.settingsRepo.setTourStep(current + 1)
        } else {
            app.settingsRepo.dismissTour(markCompleted = true)
        }
    }

    fun previousTourStep() {
        val current = currentTourStep.value
        if (current > 1) {
            app.settingsRepo.setTourStep(current - 1)
        }
    }

    val isJourneyActive: StateFlow<Boolean> = app.settingsRepo.isJourneyActive
    val journeyDestination: StateFlow<String> = app.settingsRepo.journeyDestination
    val journeyEtaMinutes: StateFlow<Int> = app.settingsRepo.journeyEtaMinutes

    fun startJourney(origin: String = "Sadar, Nagpur", destination: String, etaMinutes: Int) {
        app.settingsRepo.setJourneyState(true, destination, etaMinutes)
        viewModelScope.launch {
            val child = childProfiles.value.firstOrNull()
            app.childSafetyRepo.createJourney(
                child = child,
                childAlias = child?.displayName ?: "User",
                origin = origin,
                destination = destination,
                expectedMinutes = etaMinutes
            )
            app.auditLogRepo.log("JOURNEY_STARTED", "Started safe journey: $origin → $destination ($etaMinutes min)", "INFO")
        }
    }

    fun endJourney() {
        app.settingsRepo.setJourneyState(false, "", 0)
        viewModelScope.launch {
            val active = childSafetyJourneys.value.firstOrNull { it.status == "ACTIVE" || it.status == "OVERDUE" }
            if (active != null) {
                app.childSafetyRepo.markJourneySafe(active.id)
            }
            app.auditLogRepo.log("JOURNEY_ENDED", "Safe journey completed and ended by user", "INFO")
        }
    }

    fun deleteAccountAndAllData(onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (isGuardModeActive.value) {
                    stopGuardMode()
                }
                if (activeEmergency.value != null) {
                    endEmergency("Account deleted")
                }
                app.settingsRepo.setJourneyState(false, "", 0)
                app.voiceProfileRepo.deleteVoiceProfile()
                app.database.clearAllTables()
                app.authenticationRepo.signOut()
                app.auditLoggerService.logSecurityEvent(
                    action = "ACCOUNT_DELETED",
                    category = "PRIVACY",
                    details = "All local data wiped, voice profile deleted, and session terminated.",
                    severity = "WARNING"
                )
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error deleting account data: ${e.message}", e)
            } finally {
                withContext(Dispatchers.Main) {
                    onComplete()
                }
            }
        }
    }

    suspend fun saveThreatEvidence(
        fileType: String,
        description: String,
        sha256Hash: String,
        threatLevel: String,
        details: String
    ): Result<EvidenceEntity> = withContext(Dispatchers.IO) {
        try {
            val rawBytes = details.toByteArray(Charsets.UTF_8)
            val evidence = EvidenceEntity(
                id = java.util.UUID.randomUUID().toString(),
                eventId = "threat_analysis_${System.currentTimeMillis()}",
                title = "$threatLevel: $description",
                filePath = "advisory://$fileType",
                sha256Checksum = sha256Hash,
                fileSizeBytes = rawBytes.size.toLong(),
                timestamp = System.currentTimeMillis(),
                isVerified = true
            )
            app.database.evidenceDao().insertEvidence(evidence)
            app.auditLogRepo.log("THREAT_EVIDENCE_STORED", "Advisory analysis stored ($threatLevel): $description", if (threatLevel == "HIGH") "WARNING" else "INFO")
            Result.success(evidence)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Map & Safety Engines
    val mapProvider: com.example.domain.map.IMapViewProvider get() = app.mapProvider
    val safetyScoringEngine: com.example.domain.map.SafetyScoringEngine get() = app.safetyScoringEngine

    // Model and Architecture telemetry
    val modelStatusLabel: String
        get() = app.voiceModelEngine.modelStatusLabel

    val isProductionModelLoaded: Boolean
        get() = app.voiceModelEngine.isProductionModelLoaded

    // Test mode verification result
    private val _testVerificationResult = MutableStateFlow<String?>(null)
    val testVerificationResult: StateFlow<String?> = _testVerificationResult.asStateFlow()

    fun getStrings(): LanguageStrings {
        return AppStrings.get(selectedLanguage.value)
    }

    fun startGuardMode() {
        val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
            app,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            _testVerificationResult.value = "Microphone permission is required to enable Guard Mode."
            app.settingsRepo.setGuardModeEnabled(false)
            return
        }

        val profile = voiceProfile.value
        if (profile == null || !profile.isEnrolled) {
            _testVerificationResult.value = "No voice profile enrolled. Open Voice SOS and complete enrollment with 5 voice samples before enabling Guard Mode."
            app.settingsRepo.setGuardModeEnabled(false)
            return
        }
        if (profile.embedding.size <= 32) {
            _testVerificationResult.value = "Legacy 32-dimensional voice profile detected. Please re-enroll 5 real samples with the updated Vosk speaker engine."
            app.settingsRepo.setGuardModeEnabled(false)
            return
        }
        if (profile.sampleCount < 5) {
            _testVerificationResult.value = "Incomplete voice profile (${profile.sampleCount}/5 samples). Please record all 5 samples before enabling Guard Mode."
            app.settingsRepo.setGuardModeEnabled(false)
            return
        }

        if (!app.voiceModelEngine.preparePhraseEngine()) {
            _testVerificationResult.value = "Voice SOS models could not be loaded: ${app.voiceModelEngine.modelStatusLabel}"
            app.settingsRepo.setGuardModeEnabled(false)
            return
        }

        _testVerificationResult.value = null
        GuardModeService.start(app)
        app.settingsRepo.setGuardModeEnabled(true)
        app.auditLoggerService.logGuardModeChanged(true, profile.phrase)
    }

    fun stopGuardMode() {
        GuardModeService.stop(app)
        app.settingsRepo.setGuardModeEnabled(false)
        app.auditLoggerService.logGuardModeChanged(false, "")
    }

    fun triggerManualPanic() {
        EmergencyOrchestrator.triggerEmergency(
            context = app,
            emergencyRepo = app.emergencyRepo,
            locationManager = app.locationManager,
            audioRecorder = app.incidentAudioRecorder,
            evidenceRepo = app.evidenceRepo,
            auditRepo = app.auditLogRepo,
            settingsRepo = app.settingsRepo,
            scope = viewModelScope,
            triggerType = "PANIC_BUTTON",
            phrase = "MANUAL SOS ACTIVATION",
            speakerScore = 1.0f
        )
    }

    /** Voice test analyzes a real microphone clip locally and never creates an emergency. */
    fun runVoiceSosTestCheck(sample: ShortArray) {
        val profile = voiceProfile.value
        if (profile == null || !profile.isEnrolled) {
            _testVerificationResult.value = "No voice profile enrolled. Open Voice SOS and record 5 samples to enroll."
            return
        }
        if (profile.embedding.size <= 32) {
            _testVerificationResult.value = "Legacy 32-dimensional profile detected. Re-enroll 5 real samples with the updated Vosk speaker engine."
            return
        }
        if (profile.sampleCount < 5) {
            _testVerificationResult.value = "Incomplete voice profile (${profile.sampleCount}/5 samples). Re-enroll all 5 samples."
            return
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val result = app.voiceModelEngine.analyzeSingleSample(sample, profile.phrase, profile.embedding, profile.threshold)
            val phrase = result.phraseResult
            val speaker = result.speakerResult
            val message = when {
                sample.size < 8000 -> "Recording was too short. Record the full phrase for about three seconds."
                !phrase.phraseMatched -> "Phrase not recognized. Say: \"${profile.phrase}\" clearly in English."
                !speaker.isSpeakerVerified -> "Speaker verification did not pass. Score ${String.format("%.1f%%", speaker.similarityScore * 100)}; no SOS was sent."
                phrase.confidence < 0.70f -> "Speech confidence was too low. No SOS was sent."
                else -> "Phrase and enrolled-speaker checks passed for this clip. This is a local test only; no emergency was sent."
            }
            _testVerificationResult.value = message
        }
    }

    fun triggerChildSos(childAlias: String) {
        viewModelScope.launch {
            app.auditLogRepo.log("CHILD_SOS_ACTIVATED", "Child SOS requested for profile alias: ${childAlias.trim().take(60)}", "CRITICAL")
            EmergencyOrchestrator.triggerEmergency(
                context = app,
                emergencyRepo = app.emergencyRepo,
                locationManager = app.locationManager,
                audioRecorder = app.incidentAudioRecorder,
                evidenceRepo = app.evidenceRepo,
                auditRepo = app.auditLogRepo,
                settingsRepo = app.settingsRepo,
                scope = viewModelScope,
                triggerType = "CHILD_SOS",
                phrase = "CHILD_SOS",
                speakerScore = 0f
            )
        }
    }

    fun endEmergency(reason: String = "User marked safe") {
        val active = activeEmergency.value ?: return
        EmergencyOrchestrator.terminateEmergency(
            context = app,
            emergencyRepo = app.emergencyRepo,
            audioRecorder = app.incidentAudioRecorder,
            evidenceRepo = app.evidenceRepo,
            auditRepo = app.auditLogRepo,
            scope = viewModelScope,
            eventId = active.id,
            reason = reason
        )
    }

    fun saveVoiceProfile(phrase: String, embedding: FloatArray, sampleCount: Int, threshold: Float = 0.72f): Boolean {
        val success = app.voiceProfileRepo.saveVoiceProfile(phrase, embedding, sampleCount, threshold)
        if (success) {
            app.auditLoggerService.logEnrollmentCompleted(sampleCount, threshold)
        }
        return success
    }

    fun deleteVoiceProfile() {
        app.voiceProfileRepo.deleteVoiceProfile()
        if (isGuardModeActive.value) {
            stopGuardMode()
        }
        app.auditLoggerService.logVoiceProfileDeleted()
    }

    fun updateThreshold(threshold: Float) {
        app.voiceProfileRepo.updateThreshold(threshold)
    }

    fun updatePhrase(newPhrase: String) {
        app.voiceProfileRepo.updatePhrase(newPhrase)
    }

    fun addTrustedContact(name: String, relation: String, phone: String, email: String?) {
        viewModelScope.launch {
            app.trustedContactRepo.addContact(name, relation, phone, email)
            app.auditLogRepo.log("CONTACT_ADDED", "Added trusted contact: $name ($relation)", "INFO")
            syncOfflineLocationEmailSettings()
        }
    }

    fun deleteTrustedContact(id: Long) {
        viewModelScope.launch {
            app.trustedContactRepo.deleteContact(id)
            app.auditLogRepo.log("CONTACT_DELETED", "Removed trusted contact ID: $id", "INFO")
            syncOfflineLocationEmailSettings()
        }
    }

    fun setOfflineLocationEmailConsent(enabled: Boolean) {
        app.settingsRepo.setOfflineLocationEmailConsent(enabled)
        app.applicationScope.launch {
            app.auditLogRepo.log(
                "OFFLINE_LOCATION_EMAIL_CONSENT_CHANGED",
                "Last-known-location email consent ${if (enabled) "enabled" else "revoked"}.",
                if (enabled) "WARNING" else "INFO"
            )
        }
        syncOfflineLocationEmailSettings()
    }

    fun setShakeSosEnabled(enabled: Boolean) {
        app.settingsRepo.setShakeSosEnabled(enabled)
        app.applicationScope.launch {
            app.auditLogRepo.log(
                "SHAKE_SOS_SETTING_CHANGED",
                "Physical gesture (Shake SOS) detection ${if (enabled) "enabled" else "disabled"}.",
                "INFO"
            )
        }
    }

    fun setShakeSensitivity(sensitivity: String) {
        app.settingsRepo.setShakeSensitivity(sensitivity)
    }

    fun updateTrustedContactActive(contact: TrustedContactEntity, enabled: Boolean) {
        viewModelScope.launch {
            app.trustedContactRepo.updateContact(contact.copy(isActive = enabled))
            app.auditLogRepo.log("CONTACT_STATUS_CHANGED", "Trusted contact ${contact.id} ${if (enabled) "enabled" else "disabled"}.", "INFO")
            syncOfflineLocationEmailSettings()
        }
    }

    fun verifyTrustedContact(id: Long) {
        viewModelScope.launch {
            app.trustedContactRepo.verifyContactEmail(id)
            app.auditLogRepo.log("CONTACT_VERIFIED", "Emergency contact $id email verified", "INFO")
            syncOfflineLocationEmailSettings()
        }
    }

    fun updateTrustedContactLocationConsent(contact: TrustedContactEntity, canShare: Boolean) {
        viewModelScope.launch {
            app.trustedContactRepo.updateLocationConsent(contact.id, canShare)
            app.auditLogRepo.log("CONTACT_LOCATION_CONSENT", "Contact ${contact.id} location consent: $canShare", "INFO")
            syncOfflineLocationEmailSettings()
        }
    }

    fun syncOfflineLocationEmailSettings() {
        viewModelScope.launch {
            val location = currentLocation.value
            val result = app.backendClient.postDeviceHeartbeat(
                monitoringEnabled = app.settingsRepo.isGuardModeEnabled.value,
                offlineLocationEmailConsent = app.settingsRepo.offlineLocationEmailConsent.value,
                contacts = app.trustedContactRepo.getActiveContactsList(),
                location = location
            )
            if (result.isFailure && app.settingsRepo.policeEndpointConfigured.value && !app.settingsRepo.isDemoMode.value) {
                app.auditLogRepo.log("DEVICE_HEARTBEAT_SYNC_FAILED", result.exceptionOrNull()?.message ?: "Heartbeat sync failed", "WARNING")
            }
        }
    }

    fun toggleDemoMode(enabled: Boolean) {
        app.settingsRepo.setDemoMode(enabled)
        viewModelScope.launch {
            app.auditLogRepo.log("DEMO_MODE_TOGGLED", "Demo mode set to: $enabled", "INFO")
            checkBackendConnection(forceRetry = true)
        }
    }

    fun testBackendConnection(url: String, token: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onResult(app.backendClient.testConnection(url, token))
        }
    }

    fun connectBackend(url: String, token: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val normalizedUrl = url.trim().trimEnd('/')
            val result = app.backendClient.testConnection(normalizedUrl, token.trim())
            if (result.isSuccess) {
                app.settingsRepo.setPoliceEndpointConfig(true, normalizedUrl, token.trim())
                app.settingsRepo.setDemoMode(false)
                app.auditLogRepo.log("LOCAL_BACKEND_CONNECTED", "Android app connected to Raksha360 backend at $normalizedUrl", "INFO")
                checkBackendConnection(forceRetry = true)
                onResult(result)
            } else {
                onResult(result)
            }
        }
    }

    fun resetBackendToDefault(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            app.settingsRepo.resetToDefaultEndpoint()
            app.auditLogRepo.log("BACKEND_RESET_TO_DEFAULT", "Backend URL reset to default production endpoint", "INFO")
            checkBackendConnection(forceRetry = true)
            onComplete?.invoke()
        }
    }

    fun submitIncidentReport(
        title: String,
        category: String,
        description: String,
        shareLocation: Boolean = true,
        evidenceSha256: String? = null,
        onResult: (Result<com.example.data.remote.IncidentReportResponse>) -> Unit
    ) {
        viewModelScope.launch {
            val location = currentLocation.value
            val useLocation = shareLocation && !location.isFallback
            val reportRequest = com.example.data.remote.IncidentReportRequest(
                title = title.trim(),
                category = category.trim(),
                description = description.trim(),
                locationDescription = if (useLocation) location.description else "Location not shared or GPS fix unavailable",
                latitude = if (useLocation) location.latitude else null,
                longitude = if (useLocation) location.longitude else null,
                reportedAt = System.currentTimeMillis(),
                evidenceSha256 = evidenceSha256?.takeIf { it.isNotBlank() },
                isDemoMode = isDemoMode.value
            )
            val result = app.backendClient.postIncidentReport(reportRequest)
            if (result.isSuccess) {
                app.auditLogRepo.log("INCIDENT_REPORT_SUBMITTED", "Report was added to Raksha360 local control-room queue", "INFO")
            }
            onResult(result)
        }
    }

    fun setLanguage(lang: String) {
        app.settingsRepo.setLanguage(lang)
    }

    fun syncOfflineQueueNow() {
        viewModelScope.launch {
            app.emergencyRepo.retryOfflineQueue()
        }
    }

    fun verifyEvidenceIntegrity(evidenceId: String) {
        viewModelScope.launch {
            val valid = app.evidenceRepo.verifyIntegrity(evidenceId)
            app.auditLogRepo.log(
                "EVIDENCE_SHA256_VERIFIED",
                "Evidence ID $evidenceId cryptographic verification: ${if (valid) "PASSED" else "FAILED"}",
                if (valid) "INFO" else "WARNING"
            )
        }
    }

    fun deleteEvidence(id: String) {
        viewModelScope.launch {
            app.evidenceRepo.deleteEvidence(id)
        }
    }

    fun createChildProfile(
        alias: String,
        ageBand: String,
        schoolName: String,
        guardianLabel: String,
        safePlaceName: String,
        latitudeText: String,
        longitudeText: String,
        radiusText: String,
        onResult: (Result<ChildProfileEntity>) -> Unit
    ) {
        viewModelScope.launch {
            val latitude = latitudeText.trim().takeIf { it.isNotBlank() }?.toDoubleOrNull()
            val longitude = longitudeText.trim().takeIf { it.isNotBlank() }?.toDoubleOrNull()
            val radius = radiusText.trim().toIntOrNull()
            if (radius == null) {
                onResult(Result.failure(IllegalArgumentException("Geofence radius must be a whole number between 100 and 1000.")))
                return@launch
            }
            if (latitudeText.isNotBlank() && latitude == null || longitudeText.isNotBlank() && longitude == null) {
                onResult(Result.failure(IllegalArgumentException("Latitude and longitude must be valid numbers.")))
                return@launch
            }
            onResult(app.childSafetyRepo.createChildProfile(alias, ageBand, schoolName, guardianLabel, safePlaceName, latitude, longitude, radius))
        }
    }

    fun enableSafePlaceGeofence(childId: String, guardianConsentConfirmed: Boolean, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onResult(app.childSafetyRepo.enableSafePlaceGeofence(childId, guardianConsentConfirmed)) }
    }

    fun createSafetyJourney(childAlias: String, origin: String, destination: String, expectedMinutesText: String, onResult: (Result<SafetyJourneyEntity>) -> Unit) {
        viewModelScope.launch {
            val child = childProfiles.value.firstOrNull { it.displayName.equals(childAlias.trim(), ignoreCase = true) }
            if (child == null) {
                onResult(Result.failure(IllegalArgumentException("Enter an existing child profile alias exactly as shown above.")))
                return@launch
            }
            val minutes = expectedMinutesText.toIntOrNull()
            if (minutes == null) {
                onResult(Result.failure(IllegalArgumentException("Expected arrival must be a whole number of minutes between 5 and 1440.")))
                return@launch
            }
            onResult(app.childSafetyRepo.createJourney(child, childAlias, origin, destination, minutes))
        }
    }

    fun markSafetyJourneySafe(journeyId: String, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch { onResult(app.childSafetyRepo.markJourneySafe(journeyId)) }
    }

    fun createSchoolHandover(childAlias: String, schoolName: String, guardianName: String, minutesFromNowText: String, onCreated: (Result<Pair<SchoolHandoverEntity, String>>) -> Unit) {
        viewModelScope.launch {
            val child = childProfiles.value.firstOrNull { it.displayName.equals(childAlias.trim(), ignoreCase = true) }
            if (child == null) {
                onCreated(Result.failure(IllegalArgumentException("Enter an existing child profile alias exactly as shown above.")))
                return@launch
            }
            val minutes = minutesFromNowText.toIntOrNull()
            if (minutes == null || minutes !in 5..1440) {
                onCreated(Result.failure(IllegalArgumentException("Pickup must be scheduled 5 minutes to 24 hours ahead.")))
                return@launch
            }
            val scheduledAt = System.currentTimeMillis() + minutes * 60_000L
            onCreated(app.childSafetyRepo.createSchoolHandover(child, childAlias, schoolName, guardianName, scheduledAt))
        }
    }

    fun verifySchoolHandover(handoverId: String, pin: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch { onResult(app.childSafetyRepo.verifySchoolHandover(handoverId, pin)) }
    }

    fun markChildSafetyAlertRead(alertId: String) {
        viewModelScope.launch { app.childSafetyRepo.markAlertRead(alertId) }
    }

    fun setVoiceTestMessage(message: String) {
        _testVerificationResult.value = message
    }

    fun clearTestResult() {
        _testVerificationResult.value = null
    }

    suspend fun analyzeRoute(
        coordinates: List<Pair<Double, Double>>,
        label: String = "Candidate Route",
        mode: String = "walking"
    ): Result<com.example.domain.route.RouteAnalysisResult> {
        return app.backendClient.analyzeRoute(coordinates, label, mode)
    }

    suspend fun compareRoutes(
        routes: List<com.example.domain.route.RouteCandidateInput>
    ): Result<com.example.domain.route.RouteComparisonSummary> {
        return app.backendClient.compareRoutes(routes)
    }

    suspend fun fetchRouteLayersSummary(): Result<com.example.domain.route.RouteLayersSummary> {
        return app.backendClient.fetchRouteLayersSummary()
    }
}
