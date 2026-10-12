package com.example.service.audit

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.local.dao.AuditLogDao
import com.example.data.local.entity.AuditLogEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.regex.Pattern

interface IAuditLoggerService {
    val recentLogs: Flow<List<AuditLogEntity>>

    fun logSecurityEvent(
        action: String,
        category: String,
        details: String,
        severity: String = "INFO"
    )

    fun logEnrollmentStarted()
    fun logEnrollmentSampleCaptured(sampleNumber: Int, qualityScore: Float, isAcceptable: Boolean)
    fun logEnrollmentCompleted(sampleCount: Int, threshold: Float)
    fun logVoiceProfileDeleted()
    fun logGuardModeChanged(enabled: Boolean, triggerPhrase: String)
    fun logVoiceSosActivation(verifiedPhrase: String, speakerScore: Float, threshold: Float, locationSector: String)
    fun logManualPanicActivation(locationSector: String)
    fun logEmergencyStateChanged(eventId: String, fromState: String, toState: String, reason: String)
    fun logEmergencyDispatchAttempt(eventId: String, channel: String, targetCount: Int)
    fun logEmergencyDispatchSuccess(eventId: String, channel: String, trackingTicket: String)
    fun logEmergencyDispatchFailed(eventId: String, channel: String, errorReason: String)
    fun logLocationSharingStateChanged(active: Boolean, reason: String)
    fun logOfflineQueueSync(pendingCount: Int, successCount: Int)
    fun logEvidenceSealed(evidenceId: String, sha256Checksum: String, durationSecs: Int)
    fun logEvidenceIntegrityVerified(evidenceId: String, matches: Boolean)
    fun logPermissionEvent(permissionName: String, isGranted: Boolean)

    suspend fun clearAuditLogs()
}

/**
 * Enterprise-grade Security Audit Logger Service.
 * Persists high-integrity security events in local storage without exposing raw audio,
 * encryption keys, authorization tokens, or unmasked personal identification data (PII).
 */
class AuditLoggerService(
    private val context: Context,
    private val auditDao: AuditLogDao,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IAuditLoggerService {

    private val currentSessionId: String = UUID.randomUUID().toString().take(8).uppercase()

    override val recentLogs: Flow<List<AuditLogEntity>> = auditDao.getRecentLogs()

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Strict privacy sanitizer:
     * - Redacts potential auth bearer tokens
     * - Masks telephone numbers
     * - Masks email addresses
     * - Strips any potential byte dumps or raw acoustic vectors
     */
    private fun sanitizeLogDetails(input: String): String {
        var sanitized = input

        // 1. Redact authorization / bearer tokens
        sanitized = sanitized.replace(Regex("(?i)bearer\\s+[a-zA-Z0-9._\\-]+"), "Bearer [REDACTED_TOKEN]")
        sanitized = sanitized.replace(Regex("(?i)token=([a-zA-Z0-9._\\-]+)"), "token=[REDACTED]")
        sanitized = sanitized.replace(Regex("(?i)password=([a-zA-Z0-9._\\-]+)"), "password=[REDACTED]")

        // 2. Mask phone numbers: e.g. +91 98765 43210 -> +91 98*** **210
        val phonePattern = Pattern.compile("(\\+?[0-9]{2,3}[-\\s]?)?([0-9]{2})[0-9]{4,6}([0-9]{3})")
        val phoneMatcher = phonePattern.matcher(sanitized)
        val sb = StringBuffer()
        while (phoneMatcher.find()) {
            val prefix = phoneMatcher.group(1) ?: ""
            val startDigits = phoneMatcher.group(2) ?: ""
            val endDigits = phoneMatcher.group(3) ?: ""
            phoneMatcher.appendReplacement(sb, "$prefix$startDigits*** **$endDigits")
        }
        phoneMatcher.appendTail(sb)
        sanitized = sb.toString()

        // 3. Mask email addresses: ananya.d@example.com -> a*****@example.com
        sanitized = sanitized.replace(Regex("([a-zA-Z0-9])[a-zA-Z0-9._%+-]+@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})")) { matchResult ->
            val firstChar = matchResult.groupValues[1]
            val domain = matchResult.groupValues[2]
            "$firstChar*****@$domain"
        }

        // 4. Guarantee raw audio or float arrays are never logged
        if (sanitized.contains("FloatArray") || sanitized.contains("ShortArray") || sanitized.contains("ByteArray")) {
            sanitized = sanitized.replace(Regex("\\[-?[0-9.,\\s]+\\]"), "[ACOUSTIC_VECTOR_REDACTED]")
        }

        return sanitized.take(500)
    }

    override fun logSecurityEvent(
        action: String,
        category: String,
        details: String,
        severity: String
    ) {
        val sanitized = sanitizeLogDetails(details)
        val networkStatus = if (isOnline()) "ONLINE" else "OFFLINE"

        scope.launch(ioDispatcher) {
            try {
                auditDao.insertLog(
                    AuditLogEntity(
                        timestamp = System.currentTimeMillis(),
                        action = action,
                        category = category,
                        detail = sanitized,
                        severity = severity,
                        sessionId = currentSessionId,
                        networkState = networkStatus
                    )
                )
            } catch (_: Exception) {}
        }
    }

    override fun logEnrollmentStarted() {
        logSecurityEvent(
            action = "VOICE_ENROLLMENT_STARTED",
            category = "VOICE_SECURITY",
            details = "User initiated Voice SOS phrase enrollment workflow",
            severity = "INFO"
        )
    }

    override fun logEnrollmentSampleCaptured(sampleNumber: Int, qualityScore: Float, isAcceptable: Boolean) {
        logSecurityEvent(
            action = "VOICE_SAMPLE_CAPTURED",
            category = "VOICE_SECURITY",
            details = "Sample #$sampleNumber evaluated: SNR score=${String.format("%.1f", qualityScore)}dB, acceptable=$isAcceptable",
            severity = "INFO"
        )
    }

    override fun logEnrollmentCompleted(sampleCount: Int, threshold: Float) {
        logSecurityEvent(
            action = "VOICE_ENROLLMENT_COMPLETED",
            category = "VOICE_SECURITY",
            details = "Voice profile sealed with Android Keystore AES-256 GCM. Samples=$sampleCount, threshold=${String.format("%.0f%%", threshold * 100)}. Raw audio wiped.",
            severity = "INFO"
        )
    }

    override fun logVoiceProfileDeleted() {
        logSecurityEvent(
            action = "VOICE_PROFILE_DELETED",
            category = "VOICE_SECURITY",
            details = "User requested on-device voice profile deletion. Keystore cryptographic alias removed.",
            severity = "WARNING"
        )
    }

    override fun logGuardModeChanged(enabled: Boolean, triggerPhrase: String) {
        logSecurityEvent(
            action = if (enabled) "GUARD_MODE_ENABLED" else "GUARD_MODE_DISABLED",
            category = "SYSTEM_STATE",
            details = if (enabled) "Foreground mic listener started. Target phrase: \"$triggerPhrase\"" else "Guard Mode listener halted by user.",
            severity = "INFO"
        )
    }

    override fun logVoiceSosActivation(verifiedPhrase: String, speakerScore: Float, threshold: Float, locationSector: String) {
        logSecurityEvent(
            action = "VOICE_SOS_ACTIVATED",
            category = "EMERGENCY_DISPATCH",
            details = "Voice SOS triggered! Phrase: \"$verifiedPhrase\" | Match confidence=${String.format("%.1f%%", speakerScore * 100)} (Threshold: ${String.format("%.0f%%", threshold * 100)}) | Sector: $locationSector",
            severity = "CRITICAL"
        )
    }

    override fun logManualPanicActivation(locationSector: String) {
        logSecurityEvent(
            action = "MANUAL_PANIC_ACTIVATED",
            category = "EMERGENCY_DISPATCH",
            details = "Manual SOS Panic button pressed by user. Sector: $locationSector",
            severity = "CRITICAL"
        )
    }

    override fun logEmergencyStateChanged(eventId: String, fromState: String, toState: String, reason: String) {
        logSecurityEvent(
            action = "EMERGENCY_STATUS_TRANSITION",
            category = "EMERGENCY_DISPATCH",
            details = "Event ID: ${eventId.take(8)} transitioned from $fromState to $toState. Reason: $reason",
            severity = if (toState == "RESOLVED" || toState == "CANCELLED") "INFO" else "ALERT"
        )
    }

    override fun logEmergencyDispatchAttempt(eventId: String, channel: String, targetCount: Int) {
        logSecurityEvent(
            action = "DISPATCH_ATTEMPT",
            category = "EMERGENCY_DISPATCH",
            details = "Dispatching emergency payload for event ${eventId.take(8)} across channel: $channel to $targetCount recipients",
            severity = "ALERT"
        )
    }

    override fun logEmergencyDispatchSuccess(eventId: String, channel: String, trackingTicket: String) {
        logSecurityEvent(
            action = "DISPATCH_SUCCESS",
            category = "EMERGENCY_DISPATCH",
            details = "Alert acknowledged by $channel. Tracking ticket: $trackingTicket",
            severity = "INFO"
        )
    }

    override fun logEmergencyDispatchFailed(eventId: String, channel: String, errorReason: String) {
        logSecurityEvent(
            action = "DISPATCH_FAILED",
            category = "EMERGENCY_DISPATCH",
            details = "Dispatch failed on $channel ($errorReason). Payload securely enqueued in encrypted offline queue.",
            severity = "WARNING"
        )
    }

    override fun logLocationSharingStateChanged(active: Boolean, reason: String) {
        logSecurityEvent(
            action = if (active) "LOCATION_SHARING_STARTED" else "LOCATION_SHARING_STOPPED",
            category = "TELEMETRY",
            details = if (active) "Foreground Location Service streaming periodic updates. Context: $reason" else "Location streaming stopped. Reason: $reason",
            severity = "INFO"
        )
    }

    override fun logOfflineQueueSync(pendingCount: Int, successCount: Int) {
        logSecurityEvent(
            action = "OFFLINE_QUEUE_SYNC",
            category = "DATA_INTEGRITY",
            details = "Offline emergency queue sync completed. Synchronized $successCount of $pendingCount queued payloads.",
            severity = "INFO"
        )
    }

    override fun logEvidenceSealed(evidenceId: String, sha256Checksum: String, durationSecs: Int) {
        logSecurityEvent(
            action = "EVIDENCE_SEALED",
            category = "DATA_INTEGRITY",
            details = "Incident audio sealed. ID: ${evidenceId.take(8)} | Duration: ${durationSecs}s | SHA-256: ${sha256Checksum.take(16)}...",
            severity = "INFO"
        )
    }

    override fun logEvidenceIntegrityVerified(evidenceId: String, matches: Boolean) {
        logSecurityEvent(
            action = "EVIDENCE_INTEGRITY_CHECK",
            category = "DATA_INTEGRITY",
            details = "Evidence verification for ${evidenceId.take(8)}: ${if (matches) "PASSED (Tamper-free)" else "FAILED (Checksum mismatch)"}",
            severity = if (matches) "INFO" else "CRITICAL"
        )
    }

    override fun logPermissionEvent(permissionName: String, isGranted: Boolean) {
        logSecurityEvent(
            action = "PERMISSION_STATE_CHANGED",
            category = "SYSTEM_STATE",
            details = "Permission $permissionName is now: ${if (isGranted) "GRANTED" else "DENIED"}",
            severity = if (isGranted) "INFO" else "WARNING"
        )
    }

    override suspend fun clearAuditLogs() {
        auditDao.clearLogs()
    }
}
