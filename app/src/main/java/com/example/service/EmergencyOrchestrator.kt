package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.repository.AuditLogRepository
import com.example.data.repository.EmergencyRepository
import com.example.data.repository.EvidenceRepository
import com.example.data.repository.SettingsRepository
import com.example.service.location.AppLocationManager
import com.example.voice.audio.IncidentAudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

object EmergencyOrchestrator {

    private val _emergencyTriggeredEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val emergencyTriggeredEvents = _emergencyTriggeredEvents.asSharedFlow()

    private var activeEventId: String? = null

    fun getActiveEventId(): String? = activeEventId

    fun triggerEmergency(
        context: Context,
        emergencyRepo: EmergencyRepository,
        locationManager: AppLocationManager,
        audioRecorder: IncidentAudioRecorder,
        evidenceRepo: EvidenceRepository,
        auditRepo: AuditLogRepository,
        settingsRepo: SettingsRepository,
        scope: CoroutineScope,
        triggerType: String, // "VOICE_SOS", "PANIC_BUTTON", "TEST_SIMULATION"
        phrase: String = "",
        speakerScore: Float = 0f
    ) {
        scope.launch(Dispatchers.IO) {
            val loc = locationManager.currentLocation.value
            when (triggerType) {
                "VOICE_SOS" -> {
                    auditRepo.logVoiceSosActivation(
                        verifiedPhrase = phrase,
                        speakerScore = speakerScore,
                        threshold = 0.72f,
                        locationSector = loc.description
                    )
                }
                "CHILD_SOS" -> {
                    auditRepo.log("CHILD_SOS_EVENT_CREATED", "Child safety emergency event created", "CRITICAL")
                }
                "SHAKE_SOS" -> {
                    auditRepo.log("SHAKE_SOS_ACTIVATION", "Emergency triggered via physical device shake detection", "CRITICAL", "EMERGENCY")
                }
                else -> {
                    auditRepo.logManualPanicActivation(
                        locationSector = loc.description
                    )
                }
            }

            // 1. Create and dispatch emergency in repository
            val event = emergencyRepo.createAndDispatchEmergency(
                triggerType = triggerType,
                phraseDetected = phrase,
                speakerScore = speakerScore,
                lat = loc.latitude,
                lng = loc.longitude,
                accuracyMeters = loc.accuracy,
                locationDescription = loc.description,
                isDemoMode = settingsRepo.isDemoMode.value
            )

            activeEventId = event.id
            _emergencyTriggeredEvents.tryEmit(event.id)

            // 2. Start Automatic Incident Audio Recording if configured
            if (settingsRepo.autoRecordIncidentAudio.value) {
                audioRecorder.startIncidentRecording(event.id) {
                    // Recording started
                }
            }

            // 3. Start Foreground Emergency Live Location Service & Dedicated Location Service
            com.example.service.location.ForegroundLocationService.start(context, event.id, "EMERGENCY")

            val intent = Intent(context, EmergencyOrchestrationService::class.java).apply {
                action = EmergencyOrchestrationService.ACTION_START_EMERGENCY
                putExtra(EmergencyOrchestrationService.EXTRA_EVENT_ID, event.id)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun terminateEmergency(
        context: Context,
        emergencyRepo: EmergencyRepository,
        audioRecorder: IncidentAudioRecorder,
        evidenceRepo: EvidenceRepository,
        auditRepo: AuditLogRepository,
        scope: CoroutineScope,
        eventId: String,
        reason: String
    ) {
        scope.launch(Dispatchers.IO) {
            auditRepo.logEmergencyStateChanged(
                eventId = eventId,
                fromState = "ACTIVE",
                toState = "RESOLVED",
                reason = reason
            )

            // Stop incident audio recording and save evidence
            val recordingResult = audioRecorder.stopIncidentRecording()
            if (recordingResult != null) {
                evidenceRepo.addEvidence(
                    id = recordingResult.evidenceId,
                    eventId = eventId,
                    title = "Incident Audio Evidence ($eventId)",
                    filePath = recordingResult.filePath,
                    sha256Checksum = recordingResult.sha256Checksum,
                    durationSecs = recordingResult.durationSeconds,
                    fileSizeBytes = recordingResult.fileSizeBytes
                )
                emergencyRepo.updateEventEvidence(eventId, recordingResult.filePath, recordingResult.sha256Checksum)
            }

            emergencyRepo.resolveActiveEmergency(eventId, reason)
            activeEventId = null

            // Stop Foreground Emergency Service & Dedicated Location Service
            com.example.service.location.ForegroundLocationService.stop(context)
            val intent = Intent(context, EmergencyOrchestrationService::class.java).apply {
                action = EmergencyOrchestrationService.ACTION_STOP_EMERGENCY
            }
            context.startService(intent)
        }
    }
}
