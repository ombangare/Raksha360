package com.example.data.repository

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.NaariKavachApp
import com.example.data.local.dao.ChildSafetyDao
import com.example.data.local.entity.ChildProfileEntity
import com.example.data.local.entity.ChildSafetyAlertEntity
import com.example.data.local.entity.SafetyJourneyEntity
import com.example.data.local.entity.SchoolHandoverEntity
import com.example.service.child.JourneyOverdueWorker
import com.example.service.child.SafePlaceGeofenceManager
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow

/** Local-first child-safety records. Server/guardian-device synchronization is a separate API integration. */
class ChildSafetyRepository(
    private val context: Context,
    private val dao: ChildSafetyDao
) {
    val children: Flow<List<ChildProfileEntity>> = dao.observeChildren()
    val journeys: Flow<List<SafetyJourneyEntity>> = dao.observeJourneys()
    val handovers: Flow<List<SchoolHandoverEntity>> = dao.observeHandovers()
    val alerts: Flow<List<ChildSafetyAlertEntity>> = dao.observeAlerts()

    suspend fun createChildProfile(
        alias: String,
        ageBand: String,
        schoolName: String,
        guardianLabel: String,
        safePlaceName: String,
        latitude: Double?,
        longitude: Double?,
        radiusMeters: Int
    ): Result<ChildProfileEntity> {
        val cleanAlias = alias.trim()
        if (cleanAlias.length !in 2..60) return Result.failure(IllegalArgumentException("Enter a child alias between 2 and 60 characters."))
        if (guardianLabel.isBlank()) return Result.failure(IllegalArgumentException("Enter a guardian label before creating a child safety profile."))
        if ((latitude == null) != (longitude == null)) return Result.failure(IllegalArgumentException("Enter both safe-place coordinates or leave both blank."))
        if (latitude != null && safePlaceName.isBlank()) return Result.failure(IllegalArgumentException("Enter a safe-place name when you provide coordinates."))
        if (latitude != null && longitude != null && (latitude !in -90.0..90.0 || longitude !in -180.0..180.0)) {
            return Result.failure(IllegalArgumentException("Safe-place coordinates are outside the valid range."))
        }
        if (radiusMeters !in 100..1000) return Result.failure(IllegalArgumentException("Geofence radius must be between 100 and 1000 metres."))
        val profile = ChildProfileEntity(
            id = UUID.randomUUID().toString(),
            displayName = cleanAlias,
            ageBand = ageBand.trim().ifBlank { "Not specified" },
            schoolName = schoolName.trim(),
            guardianLabel = guardianLabel.trim(),
            safePlaceName = safePlaceName.trim(),
            safePlaceLatitude = latitude,
            safePlaceLongitude = longitude,
            geofenceRadiusMeters = radiusMeters,
            locationSharingEnabled = false
        )
        dao.saveChild(profile)
        addAlert(profile.id, null, "PROFILE_CREATED", "INFO", "Child safety profile created", "A local alias profile was added. Verify guardian consent before location sharing.")
        return Result.success(profile)
    }

    suspend fun enableSafePlaceGeofence(childId: String, guardianConsentConfirmed: Boolean): Result<Unit> {
        if (!guardianConsentConfirmed) return Result.failure(SecurityException("Confirm guardian consent before enabling child location monitoring."))
        val profile = dao.getChild(childId) ?: return Result.failure(IllegalArgumentException("Child profile not found."))
        val latitude = profile.safePlaceLatitude ?: return Result.failure(IllegalArgumentException("Add a safe-place latitude and longitude before enabling a geofence."))
        val longitude = profile.safePlaceLongitude ?: return Result.failure(IllegalArgumentException("Add a safe-place latitude and longitude before enabling a geofence."))
        val consentTimestamp = System.currentTimeMillis()
        dao.updateChild(profile.copy(locationConsentAt = consentTimestamp))
        val result = SafePlaceGeofenceManager(context).addSafePlace(profile, latitude, longitude)
        if (result.isSuccess) {
            dao.updateChild(profile.copy(locationSharingEnabled = true, locationConsentAt = consentTimestamp))
            addAlert(profile.id, null, "GEOFENCE_ENABLED", "INFO", "Safe-place monitoring enabled", "Android confirmed the geofence registration on this device.")
        }
        return result
    }

    suspend fun createJourney(
        child: ChildProfileEntity?,
        childAlias: String,
        origin: String,
        destination: String,
        expectedMinutes: Int
    ): Result<SafetyJourneyEntity> {
        if (origin.isBlank() || destination.isBlank()) return Result.failure(IllegalArgumentException("Origin and destination are required."))
        if (expectedMinutes !in 5..1440) return Result.failure(IllegalArgumentException("Expected arrival must be 5 minutes to 24 hours from now."))
        if (child == null && childAlias.isBlank()) return Result.failure(IllegalArgumentException("Select a child profile or enter a journey alias."))
        val now = System.currentTimeMillis()
        val journey = SafetyJourneyEntity(
            id = UUID.randomUUID().toString(),
            childProfileId = child?.id,
            childLabel = child?.displayName ?: childAlias.trim(),
            originLabel = origin.trim(),
            destinationLabel = destination.trim(),
            startedAt = now,
            expectedArrivalAt = now + TimeUnit.MINUTES.toMillis(expectedMinutes.toLong()),
            lastCheckInAt = now,
            lastLatitude = null,
            lastLongitude = null,
            status = "ACTIVE",
            createdAt = now
        )
        dao.saveJourney(journey)
        scheduleJourneyDeadline(journey)
        addAlert(child?.id, journey.id, "JOURNEY_STARTED", "INFO", "Safe journey started", "A journey check-in schedule was created locally.")
        return Result.success(journey)
    }

    suspend fun markJourneySafe(journeyId: String): Result<Unit> {
        val journey = dao.getJourney(journeyId) ?: return Result.failure(IllegalArgumentException("Journey not found."))
        if (journey.status != "ACTIVE" && journey.status != "OVERDUE") {
            return Result.failure(IllegalStateException("This journey is already ${journey.status.lowercase()}."))
        }
        dao.updateJourney(journey.copy(status = "SAFE_ARRIVAL", lastCheckInAt = System.currentTimeMillis()))
        WorkManager.getInstance(context).cancelUniqueWork(journeyWorkName(journey.id))
        addAlert(journey.childProfileId, journey.id, "SAFE_ARRIVAL", "INFO", "Journey check-in completed", "A user marked the journey safe. This does not independently verify physical arrival.")
        return Result.success(Unit)
    }

    /** Returns false only when the worker ran early and should retry after backoff. */
    suspend fun markJourneyOverdue(journeyId: String): Boolean {
        val journey = dao.getJourney(journeyId) ?: return true
        if (journey.status != "ACTIVE") return true
        if (journey.expectedArrivalAt > System.currentTimeMillis()) return false
        dao.updateJourney(journey.copy(status = "OVERDUE"))
        addAlert(journey.childProfileId, journey.id, "JOURNEY_OVERDUE", "HIGH", "Journey check-in overdue", "The expected check-in time has passed. Contact the guardian and verify the situation; this does not prove an emergency.")
        return true
    }

    suspend fun createSchoolHandover(
        child: ChildProfileEntity?,
        childAlias: String,
        schoolName: String,
        guardianName: String,
        scheduledAt: Long,
        method: String = "ONE_TIME_PIN"
    ): Result<Pair<SchoolHandoverEntity, String>> {
        if (schoolName.isBlank() || guardianName.isBlank()) return Result.failure(IllegalArgumentException("School and authorized guardian are required."))
        if (scheduledAt < System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5)) return Result.failure(IllegalArgumentException("Choose a future pickup time."))
        if (child == null && childAlias.isBlank()) return Result.failure(IllegalArgumentException("Select a child profile or enter a child alias."))
        val random = SecureRandom()
        val pin = (100000 + random.nextInt(900000)).toString()
        val now = System.currentTimeMillis()
        val handover = SchoolHandoverEntity(
            id = UUID.randomUUID().toString(),
            childProfileId = child?.id,
            childLabel = child?.displayName ?: childAlias.trim(),
            schoolName = schoolName.trim(),
            authorizedGuardian = guardianName.trim(),
            scheduledAt = scheduledAt,
            status = "SCHEDULED",
            verificationMethod = method,
            oneTimePinHash = sha256(pin),
            pinExpiresAt = maxOf(scheduledAt, now) + TimeUnit.MINUTES.toMillis(10),
            verificationAttempts = 0,
            verifiedAt = null,
            createdAt = now
        )
        dao.saveHandover(handover)
        addAlert(child?.id, handover.id, "HANDOVER_CREATED", "INFO", "School handover scheduled", "A one-time pickup PIN was generated. Share it only with the authorized guardian using a trusted channel.")
        return Result.success(handover to pin)
    }

    suspend fun verifySchoolHandover(handoverId: String, pin: String): Result<Unit> {
        val record = dao.getHandover(handoverId) ?: return Result.failure(IllegalArgumentException("Handover not found."))
        if (record.status == "VERIFIED") return Result.failure(IllegalStateException("This pickup PIN has already been used."))
        if (record.status == "EXPIRED") return Result.failure(IllegalStateException("This one-time PIN has expired. Create a new handover record."))
        if (record.status == "CANCELLED" || record.status == "REVIEW_REQUIRED") return Result.failure(IllegalStateException("This handover is not eligible for PIN verification."))
        if (System.currentTimeMillis() < record.scheduledAt - TimeUnit.MINUTES.toMillis(5)) {
            return Result.failure(IllegalStateException("Pickup verification opens five minutes before the scheduled time."))
        }
        if (record.verificationAttempts >= 5) {
            dao.updateHandover(record.copy(status = "REVIEW_REQUIRED"))
            addAlert(record.childProfileId, record.id, "HANDOVER_LOCKED", "HIGH", "Pickup verification locked", "Too many PIN attempts were recorded.")
            return Result.failure(IllegalStateException("Verification is locked after five attempts."))
        }
        if ((record.pinExpiresAt ?: 0L) < System.currentTimeMillis()) {
            dao.updateHandover(record.copy(status = "EXPIRED", verificationAttempts = record.verificationAttempts + 1))
            addAlert(record.childProfileId, record.id, "HANDOVER_PIN_EXPIRED", "MEDIUM", "Pickup PIN expired", "Generate a new handover record before attempting another pickup.")
            return Result.failure(IllegalStateException("This one-time PIN has expired."))
        }
        val matched = MessageDigest.isEqual(sha256(pin.trim()).toByteArray(), (record.oneTimePinHash ?: "").toByteArray())
        if (!matched || !pin.trim().matches(Regex("[0-9]{6}"))) {
            val nextAttempts = record.verificationAttempts + 1
            val nextStatus = if (nextAttempts >= 5) "REVIEW_REQUIRED" else "AWAITING_PICKUP"
            dao.updateHandover(record.copy(status = nextStatus, verificationAttempts = nextAttempts))
            addAlert(record.childProfileId, record.id, "HANDOVER_PIN_REJECTED", if (nextAttempts >= 5) "HIGH" else "MEDIUM", "Pickup verification failed", "An invalid one-time PIN was entered. Verify the authorized pickup contact.")
            return Result.failure(IllegalArgumentException("PIN does not match. Attempt $nextAttempts of 5."))
        }
        dao.updateHandover(record.copy(status = "VERIFIED", oneTimePinHash = null, verifiedAt = System.currentTimeMillis()))
        addAlert(record.childProfileId, record.id, "HANDOVER_VERIFIED", "INFO", "Pickup PIN verified", "The PIN was used once and invalidated. School staff confirmation and guardian identity still need to follow partner policy.")
        return Result.success(Unit)
    }

    suspend fun recordGeofenceTransition(childId: String, transition: Int) {
        val profile = dao.getChild(childId) ?: return
        if (transition == com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_ENTER) {
            addAlert(childId, null, "SAFE_PLACE_ENTER", "INFO", "Safe-place boundary entered", "Android reported entry into ${profile.safePlaceName.ifBlank { "the configured safe place" }}. Confirm the child's status in the app; a geofence event alone does not mark a journey safe.")
        } else if (transition == com.google.android.gms.location.Geofence.GEOFENCE_TRANSITION_EXIT) {
            addAlert(childId, null, "SAFE_PLACE_EXIT", "MEDIUM", "Safe-place boundary exited", "Android reported exit from ${profile.safePlaceName.ifBlank { "the configured safe place" }}. Verify context before escalating.")
        }
    }

    suspend fun markAlertRead(alertId: String) = dao.markAlertRead(alertId)

    private suspend fun addAlert(childId: String?, relatedId: String?, type: String, severity: String, title: String, message: String) {
        dao.saveAlert(ChildSafetyAlertEntity(UUID.randomUUID().toString(), childId, relatedId, type, severity, title, message))
    }

    private fun scheduleJourneyDeadline(journey: SafetyJourneyEntity) {
        val delayMs = (journey.expectedArrivalAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<JourneyOverdueWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(JourneyOverdueWorker.KEY_JOURNEY_ID to journey.id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(journeyWorkName(journey.id), ExistingWorkPolicy.REPLACE, request)
    }

    private fun journeyWorkName(id: String) = "raksha360-journey-$id"
    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
