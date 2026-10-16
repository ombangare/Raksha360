package com.example.data.remote

/**
 * FastAPI Backend Contract Data Transfer Objects
 */
data class EmergencyEventRequest(
    val eventId: String,
    val timestamp: Long,
    val triggerType: String, // "VOICE_SOS", "PANIC_BUTTON", "JOURNEY_TIMEOUT"
    val phraseDetected: String,
    val speakerConfidence: Float,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val locationDescription: String,
    val deviceSessionId: String,
    val isDemoMode: Boolean = false
)

data class EmergencyEventResponse(
    val eventId: String,
    val status: String, // "RECEIVED", "DISPATCHED"
    val trackingToken: String,
    val serverTimestamp: Long
)

data class EmergencyLocationStreamRequest(
    val eventId: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedKmph: Float,
    val batteryPct: Int
)

data class EmergencyLocationResponse(
    val recorded: Boolean,
    val liveSessionUrl: String
)

data class TrustedContactAlertRequest(
    val eventId: String,
    val timestamp: Long,
    val contacts: List<ContactAlertItem>,
    val triggerType: String,
    val lastKnownLocation: LocationSnippet,
    val liveTrackingUrl: String?
)

data class ContactAlertItem(
    val name: String,
    val phoneNumber: String,
    val email: String?,
    val deliveryChannels: List<String> // "SMS", "EMAIL", "PUSH"
)

data class LocationSnippet(
    val latitude: Double,
    val longitude: Double,
    val address: String
)

data class TrustedContactAlertResponse(
    val eventId: String,
    val sentCount: Int,
    val failedCount: Int,
    val channelsDispatched: List<String>
)

data class PoliceAlertRequest(
    val eventId: String,
    val jurisdictionZone: String = "Nagpur Commissionerate - Sadar PS",
    val timestamp: Long,
    val triggerType: String,
    val verifiedVoiceTrigger: Boolean,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val audioIncidentRecorded: Boolean,
    val sha256IntegrityHash: String?,
    val callerNotes: String = "SOS Signal from Raksha360 Protection App",
    val isDemoMode: Boolean = false
)

data class PoliceAlertResponse(
    val acknowledged: Boolean,
    val controlRoomTicketId: String,
    val assignedUnit: String?,
    val estimatedResponseMinutes: Int?,
    val message: String
)

/**
 * Clean Interface definition matching FastAPI backend specification
 */
interface BackendApiContract {
    suspend fun postEmergencyEvent(request: EmergencyEventRequest): Result<EmergencyEventResponse>
    suspend fun postEmergencyLocation(request: EmergencyLocationStreamRequest): Result<EmergencyLocationResponse>
    suspend fun postTrustedContactAlert(request: TrustedContactAlertRequest): Result<TrustedContactAlertResponse>
    suspend fun postPoliceAlert(request: PoliceAlertRequest): Result<PoliceAlertResponse>
    suspend fun verifyGoogleIdToken(idToken: String, nonce: String? = null): Result<GoogleAuthResponse>
    suspend fun verifySession(sessionToken: String): Result<GoogleAuthResponse>
    suspend fun revokeSession(sessionToken: String): Result<Boolean>
    suspend fun getAssistantSession(): Result<AssistantSessionResponse>
    suspend fun revokeLiveLocationShare(eventId: String): Result<Boolean>
}

data class AssistantSessionResponse(
    val configured: Boolean,
    val agentId: String,
    val agentName: String,
    val signedUrl: String?,
    val message: String? = null,
    val error: String? = null
)

data class GoogleAuthRequest(
    val idToken: String,
    val nonce: String? = null
)

data class GoogleAuthResponse(
    val authenticated: Boolean,
    val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null,
    val role: String = "CITIZEN_USER",
    val sessionToken: String,
    val expiresAt: Long
)

data class IncidentReportRequest(
    val title: String,
    val category: String,
    val description: String,
    val locationDescription: String,
    val latitude: Double?,
    val longitude: Double?,
    val reportedAt: Long = System.currentTimeMillis(),
    val sourceEventId: String? = null,
    val evidenceSha256: String? = null,
    val isDemoMode: Boolean = false
)

data class IncidentReportResponse(
    val reportId: String,
    val status: String,
    val receivedAt: Long,
    val isDemo: Boolean,
    val message: String
)
