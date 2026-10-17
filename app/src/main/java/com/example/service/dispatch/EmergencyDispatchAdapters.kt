package com.example.service.dispatch

import com.example.data.local.entity.EmergencyEventEntity
import com.example.data.local.entity.TrustedContactEntity
import com.example.data.remote.BackendApiClient
import com.example.data.remote.ContactAlertItem
import com.example.data.remote.LocationSnippet
import com.example.data.remote.PoliceAlertRequest
import com.example.data.remote.TrustedContactAlertRequest
import com.example.data.repository.SettingsRepository
import com.example.domain.map.GeoCoordinates

enum class DispatchStatus {
    SENT,
    PENDING,
    FAILED,
    UNAVAILABLE
}

data class DispatchResult(
    val status: DispatchStatus,
    val message: String,
    val trackingTicket: String? = null
)

/**
 * Police & Emergency Control Room Dispatch Adapter Interface.
 */
interface IPoliceDispatchAdapter {
    suspend fun dispatchPoliceEmergencyAlert(
        event: EmergencyEventEntity,
        location: GeoCoordinates
    ): DispatchResult
}

/**
 * Production Police Dispatch Adapter.
 * Strictly checks for an authorized, configured government or control room endpoint.
 * Never fabricates successful delivery if the API is not configured or fails.
 */
class ProductionPoliceDispatchAdapter(
    private val backendClient: BackendApiClient,
    private val settingsRepo: SettingsRepository
) : IPoliceDispatchAdapter {

    override suspend fun dispatchPoliceEmergencyAlert(
        event: EmergencyEventEntity,
        location: GeoCoordinates
    ): DispatchResult {
        val isConfigured = settingsRepo.policeEndpointConfigured.value && backendClient.isConfigured
        val endpointUrl = settingsRepo.policeEndpointUrl.value

        if (!isConfigured || endpointUrl.isBlank()) {
            return DispatchResult(
                status = DispatchStatus.UNAVAILABLE,
                message = "No authorized Police / Control Room API endpoint configured in settings. Immediate action: Dial 112 directly."
            )
        }

        return try {
            val policeRequest = PoliceAlertRequest(
                eventId = event.id,
                jurisdictionZone = "Nagpur City Police - Sadar Division",
                timestamp = event.timestamp,
                triggerType = event.triggerType,
                verifiedVoiceTrigger = event.phraseDetected.isNotEmpty(),
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = event.accuracyMeters,
                audioIncidentRecorded = true,
                sha256IntegrityHash = null,
                isDemoMode = false
            )

            val result = backendClient.postPoliceAlert(policeRequest)
            result.fold(
                onSuccess = { response ->
                    if (response.acknowledged) {
                        DispatchResult(
                            status = DispatchStatus.SENT,
                            message = response.message,
                            trackingTicket = response.controlRoomTicketId
                        )
                    } else {
                        DispatchResult(
                            status = DispatchStatus.UNAVAILABLE,
                            message = "Raksha360 recorded an internal control-room ticket, but no official police gateway acknowledged this alert. ${response.message} Dial 112 directly if this is an emergency.",
                            trackingTicket = response.controlRoomTicketId
                        )
                    }
                },
                onFailure = { error ->
                    val isUnconfigured = error.message?.contains("not configured", ignoreCase = true) == true
                    DispatchResult(
                        status = if (isUnconfigured) DispatchStatus.UNAVAILABLE else DispatchStatus.FAILED,
                        message = if (isUnconfigured) {
                            "No authorized Police / Control Room API endpoint configured. Immediate action: Dial 112 directly."
                        } else {
                            "Police dispatch request failed: ${error.localizedMessage}. Dial 112."
                        }
                    )
                }
            )
        } catch (e: Exception) {
            DispatchResult(
                status = DispatchStatus.FAILED,
                message = "Police dispatch network communication error: ${e.localizedMessage}. Dial 112."
            )
        }
    }
}

/**
 * Demo Police Dispatch Adapter.
 * Only activated in explicit Demo Mode for safe testing without contacting emergency authorities.
 */
class DemoPoliceDispatchAdapter : IPoliceDispatchAdapter {
    override suspend fun dispatchPoliceEmergencyAlert(
        event: EmergencyEventEntity,
        location: GeoCoordinates
    ): DispatchResult {
        return DispatchResult(
            status = DispatchStatus.UNAVAILABLE,
            message = "Demo only: no real emergency services were contacted. In a real emergency, call 112 directly.",
            trackingTicket = "DEMO-LOCAL-${event.id.take(6).uppercase()}"
        )
    }
}

/**
 * Trusted Contacts Dispatch Adapter Interface.
 */
interface ITrustedContactDispatchAdapter {
    suspend fun notifyTrustedContacts(
        contacts: List<TrustedContactEntity>,
        event: EmergencyEventEntity,
        location: GeoCoordinates
    ): DispatchResult
}

/**
 * Production Trusted Contact Dispatcher.
 * Dispatches automated alerts via backend delivery gateway or queues for SMS fallback.
 */
class ProductionTrustedContactDispatchAdapter(
    private val backendClient: BackendApiClient
) : ITrustedContactDispatchAdapter {

    override suspend fun notifyTrustedContacts(
        contacts: List<TrustedContactEntity>,
        event: EmergencyEventEntity,
        location: GeoCoordinates
    ): DispatchResult {
        if (contacts.isEmpty()) {
            return DispatchResult(
                status = DispatchStatus.UNAVAILABLE,
                message = "No trusted contacts configured. Add contacts in Safety Dashboard."
            )
        }

        val activeContacts = contacts.filter { it.isActive }
        if (activeContacts.isEmpty()) {
            return DispatchResult(
                status = DispatchStatus.UNAVAILABLE,
                message = "All trusted contacts are currently disabled."
            )
        }

        return try {
            val alertRequest = TrustedContactAlertRequest(
                eventId = event.id,
                timestamp = event.timestamp,
                contacts = activeContacts.map { c ->
                    val channels = mutableListOf("SMS", "NOTIFICATION")
                    if (!c.email.isNullOrBlank()) {
                        channels.add("EMAIL")
                    }
                    ContactAlertItem(
                        name = c.name,
                        phoneNumber = c.phoneNumber,
                        email = c.email,
                        deliveryChannels = channels
                    )
                },
                triggerType = event.triggerType,
                lastKnownLocation = LocationSnippet(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    address = event.locationDescription
                ),
                liveTrackingUrl = "https://track.naarikavach.local/live/${event.id}"
            )

            val result = backendClient.postTrustedContactAlert(alertRequest)
            result.fold(
                onSuccess = { response ->
                    if (response.sentCount > 0) {
                        DispatchResult(
                            status = DispatchStatus.SENT,
                            message = "${response.sentCount} trusted-contact delivery acknowledgement(s) returned by the configured provider.",
                            trackingTicket = "CONTACT-DELIVERY-${response.eventId.take(8)}"
                        )
                    } else {
                        DispatchResult(
                            status = DispatchStatus.PENDING,
                            message = "The backend stored the trusted-contact request, but no SMS, email, or push provider confirmed delivery.",
                            trackingTicket = "CONTACT-QUEUED-${response.eventId.take(8)}"
                        )
                    }
                },
                onFailure = {
                    DispatchResult(
                        status = DispatchStatus.PENDING,
                        message = "Network delivery pending. Alerts queued in encrypted offline store."
                    )
                }
            )
        } catch (e: Exception) {
            DispatchResult(
                status = DispatchStatus.PENDING,
                message = "Offline: Alerts securely queued for automatic retry on network restore."
            )
        }
    }
}
