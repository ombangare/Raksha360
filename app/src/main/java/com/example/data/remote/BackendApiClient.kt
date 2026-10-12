package com.example.data.remote

import android.content.Context
import android.os.BatteryManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.data.local.entity.TrustedContactEntity
import com.example.service.location.UserLocation
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class BackendApiClient(
    private val context: Context,
    private val getApiConfig: () -> ApiConfiguration
) : BackendApiContract {

    data class ApiConfiguration(
        val isConfigured: Boolean = false,
        val baseUrl: String = "https://api.naarikavach.local",
        val authToken: String = "",
        val isDemoMode: Boolean = true
    )

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val stableDeviceId: String by lazy {
        val prefs = context.getSharedPreferences("raksha360_device_identity", Context.MODE_PRIVATE)
        val existing = prefs.getString("device_id", null)
        existing ?: UUID.randomUUID().toString().also { prefs.edit().putString("device_id", it).apply() }
    }

    /** Reports that the app's safety monitoring is armed and sends only a real cached device location. */
    suspend fun postDeviceHeartbeat(
        monitoringEnabled: Boolean,
        offlineLocationEmailConsent: Boolean,
        contacts: List<TrustedContactEntity>,
        location: UserLocation? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val config = getApiConfig()
        if (config.isDemoMode || !config.isConfigured) {
            return@withContext Result.failure(IOException("Device heartbeat not sent: connect the phone to the laptop backend and disable Demo Mode."))
        }
        if (!isOnline()) return@withContext Result.failure(IOException("Device heartbeat not sent because the phone is offline."))
        try {
            val now = System.currentTimeMillis()
            val realLocation = location?.takeIf {
                !it.isFallback && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 && it.timestamp <= now + 300_000
            }
            val battery = try {
                val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
            } catch (_: Exception) { null }
            val contactArray = JSONArray()
            val emailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
            contacts.filter { it.isActive }.forEach { contact ->
                val normalizedEmail = contact.email?.trim()?.takeIf { emailPattern.matches(it) }
                contactArray.put(JSONObject().apply {
                    put("name", contact.name)
                    put("relationship", contact.relationship)
                    put("email", normalizedEmail ?: JSONObject.NULL)
                    put("notifyByEmail", contact.notifyByEmail && normalizedEmail != null)
                    put("isActive", contact.isActive)
                })
            }
            val json = JSONObject().apply {
                put("deviceId", stableDeviceId)
                put("timestamp", now)
                put("monitoringEnabled", monitoringEnabled)
                put("offlineLocationEmailConsent", offlineLocationEmailConsent)
                put("latitude", realLocation?.latitude ?: JSONObject.NULL)
                put("longitude", realLocation?.longitude ?: JSONObject.NULL)
                put("accuracyMeters", realLocation?.accuracy?.toDouble() ?: JSONObject.NULL)
                put("locationTimestamp", realLocation?.timestamp ?: JSONObject.NULL)
                put("batteryPct", battery ?: JSONObject.NULL)
                put("contacts", contactArray)
            }
            val request = Request.Builder()
                .url("${config.baseUrl.removeSuffix("/")}/device/heartbeat")
                .header("Authorization", "Bearer ${config.authToken}")
                .header("Content-Type", "application/json")
                .post(json.toString().toRequestBody(jsonMedia))
                .build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(IOException("Heartbeat rejected (HTTP ${response.code}): $body"))
            val result = JSONObject(body)
            if (!result.optBoolean("accepted", false)) return@withContext Result.failure(IOException("Backend did not accept the heartbeat."))
            Result.success(result.optString("status", "ONLINE"))
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Tests both the backend health route and the authenticated Android-device route. */
    suspend fun testConnection(baseUrl: String, token: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (!(trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
            return@withContext Result.failure(IOException("URL must start with http:// or https://."))
        }
        val normalized = if (trimmed.endsWith("/api/v1")) trimmed else "$trimmed/api/v1"
        if (com.example.BuildConfig.IS_PRODUCTION && normalized.startsWith("http://")
            && !normalized.contains("10.0.2.2") && !normalized.contains("localhost") && !normalized.contains("127.0.0.1")) {
            return@withContext Result.failure(IOException("Production API requires secure HTTPS. Cleartext HTTP is not permitted for remote connections."))
        }
        if (token.isBlank()) return@withContext Result.failure(IOException("Enter the authorized device token."))
        try {
            val healthRequest = Request.Builder().url("$normalized/health").get().build()
            val healthResponse = okHttpClient.newCall(healthRequest).execute()
            val healthBody = healthResponse.body?.string().orEmpty()
            if (!healthResponse.isSuccessful) return@withContext Result.failure(IOException("Health check failed (HTTP ${healthResponse.code}): ${healthBody.take(120)}"))
            val healthJson = JSONObject(healthBody)
            val sessionRequest = Request.Builder()
                .url("$normalized/device/session")
                .header("Authorization", "Bearer ${token.trim()}")
                .post("{}".toRequestBody(jsonMedia))
                .build()
            val sessionResponse = okHttpClient.newCall(sessionRequest).execute()
            val sessionBody = sessionResponse.body?.string().orEmpty()
            if (!sessionResponse.isSuccessful) {
                return@withContext Result.failure(IOException("Health check reachable but device authentication rejected (HTTP ${sessionResponse.code}). Verify device token."))
            }
            val sessionJson = JSONObject(sessionBody)
            if (!sessionJson.optBoolean("authenticated", false)) return@withContext Result.failure(IOException("Backend did not confirm device authentication."))
            Result.success("Connected to ${healthJson.optString("service", "Raksha360 backend")} · Database: ${healthJson.optString("database", "online")}")
        } catch (e: Exception) {
            Result.failure(IOException("Cannot reach backend at $normalized: ${e.localizedMessage ?: "Connection failed"}"))
        }
    }

    suspend fun postIncidentReport(request: IncidentReportRequest): Result<IncidentReportResponse> = withContext(Dispatchers.IO) {
        val config = getApiConfig()
        if (config.isDemoMode || !config.isConfigured) {
            return@withContext Result.failure(IOException("Report not sent. Connect the phone to the local backend and turn off Demo Mode in Privacy Center → Backend Connection."))
        }
        try {
            val json = JSONObject().apply {
                put("title", request.title)
                put("category", request.category)
                put("description", request.description)
                put("locationDescription", request.locationDescription)
                put("latitude", request.latitude)
                put("longitude", request.longitude)
                put("reportedAt", request.reportedAt)
                put("sourceEventId", request.sourceEventId)
                put("isDemoMode", false)
            }
            val httpRequest = Request.Builder()
                .url("${config.baseUrl.removeSuffix("/")}/reports")
                .header("Authorization", "Bearer ${config.authToken}")
                .header("Content-Type", "application/json")
                .post(json.toString().toRequestBody(jsonMedia))
                .build()
            val response = okHttpClient.newCall(httpRequest).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext Result.failure(IOException("Report rejected (HTTP ${response.code}): $body"))
            val result = JSONObject(body)
            Result.success(IncidentReportResponse(
                reportId = result.optString("reportId", ""),
                status = result.optString("status", "New"),
                receivedAt = result.optLong("receivedAt", System.currentTimeMillis()),
                isDemo = result.optBoolean("isDemo", false),
                message = result.optString("message", "Report saved to Raksha360's local control-room queue. This is not an official police filing.")
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun postEmergencyEvent(request: EmergencyEventRequest): Result<EmergencyEventResponse> =
        withContext(Dispatchers.IO) {
            val config = getApiConfig()

            if (config.isDemoMode || !config.isConfigured) {
                // Local simulation for demo mode / testing
                val ticket = "DEMO-TKT-" + UUID.randomUUID().toString().take(8).uppercase()
                return@withContext Result.success(
                    EmergencyEventResponse(
                        eventId = request.eventId,
                        status = "RECEIVED",
                        trackingToken = ticket,
                        serverTimestamp = System.currentTimeMillis()
                    )
                )
            }

            if (!isOnline()) {
                return@withContext Result.failure(IOException("No network connection available (Offline)"))
            }

            try {
                val json = JSONObject().apply {
                    put("eventId", request.eventId)
                    put("timestamp", request.timestamp)
                    put("triggerType", request.triggerType)
                    put("phraseDetected", request.phraseDetected)
                    put("speakerConfidence", request.speakerConfidence)
                    put("latitude", request.latitude)
                    put("longitude", request.longitude)
                    put("accuracyMeters", request.accuracyMeters)
                    put("locationDescription", request.locationDescription)
                    put("deviceSessionId", request.deviceSessionId)
                    put("isDemoMode", request.isDemoMode)
                }

                val httpRequest = Request.Builder()
                    .url("${config.baseUrl.removeSuffix("/")}/emergency-events")
                    .header("Authorization", "Bearer ${config.authToken}")
                    .header("Content-Type", "application/json")
                    .post(json.toString().toRequestBody(jsonMedia))
                    .build()

                val response = okHttpClient.newCall(httpRequest).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP Error ${response.code}: ${response.message}"))
                }

                val bodyStr = response.body?.string() ?: ""
                val respJson = JSONObject(bodyStr)
                Result.success(
                    EmergencyEventResponse(
                        eventId = respJson.optString("eventId", request.eventId),
                        status = respJson.optString("status", "RECEIVED"),
                        trackingToken = respJson.optString("trackingToken", "SYNC-" + UUID.randomUUID().toString().take(6)),
                        serverTimestamp = respJson.optLong("serverTimestamp", System.currentTimeMillis())
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun postEmergencyLocation(request: EmergencyLocationStreamRequest): Result<EmergencyLocationResponse> =
        withContext(Dispatchers.IO) {
            val config = getApiConfig()

            if (!isOnline()) {
                return@withContext Result.failure(IOException("No network connection (Offline)"))
            }

            if (config.isDemoMode || !config.isConfigured) {
                // The location may exist in local Room state, but no server accepted it in demo mode.
                return@withContext Result.success(
                    EmergencyLocationResponse(
                        recorded = false,
                        liveSessionUrl = ""
                    )
                )
            }

            try {
                val json = JSONObject().apply {
                    put("eventId", request.eventId)
                    put("timestamp", request.timestamp)
                    put("latitude", request.latitude)
                    put("longitude", request.longitude)
                    put("accuracyMeters", request.accuracyMeters)
                    put("speedKmph", request.speedKmph)
                    put("batteryPct", request.batteryPct)
                }

                val httpRequest = Request.Builder()
                    .url("${config.baseUrl.removeSuffix("/")}/emergency-locations")
                    .header("Authorization", "Bearer ${config.authToken}")
                    .post(json.toString().toRequestBody(jsonMedia))
                    .build()

                val response = okHttpClient.newCall(httpRequest).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }
                Result.success(
                    EmergencyLocationResponse(
                        recorded = true,
                        liveSessionUrl = "${config.baseUrl.removeSuffix("/")}/live/${request.eventId}"
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun postTrustedContactAlert(request: TrustedContactAlertRequest): Result<TrustedContactAlertResponse> =
        withContext(Dispatchers.IO) {
            val config = getApiConfig()

            if (!isOnline()) {
                return@withContext Result.failure(IOException("No network connection (Offline)"))
            }

            if (config.isDemoMode || !config.isConfigured) {
                // No SMS, email or push provider is configured. Do not display fake delivery success.
                return@withContext Result.success(
                    TrustedContactAlertResponse(
                        eventId = request.eventId,
                        sentCount = 0,
                        failedCount = request.contacts.size,
                        channelsDispatched = emptyList()
                    )
                )
            }

            try {
                val contactsArray = JSONArray()
                request.contacts.forEach { c ->
                    contactsArray.put(JSONObject().apply {
                        put("name", c.name)
                        put("phoneNumber", c.phoneNumber)
                        put("email", c.email)
                        put("deliveryChannels", JSONArray(c.deliveryChannels))
                    })
                }

                val json = JSONObject().apply {
                    put("eventId", request.eventId)
                    put("timestamp", request.timestamp)
                    put("contacts", contactsArray)
                    put("triggerType", request.triggerType)
                    put("latitude", request.lastKnownLocation.latitude)
                    put("longitude", request.lastKnownLocation.longitude)
                    put("address", request.lastKnownLocation.address)
                    put("liveTrackingUrl", request.liveTrackingUrl)
                }

                val httpRequest = Request.Builder()
                    .url("${config.baseUrl.removeSuffix("/")}/trusted-contact-alerts")
                    .header("Authorization", "Bearer ${config.authToken}")
                    .post(json.toString().toRequestBody(jsonMedia))
                    .build()

                val response = okHttpClient.newCall(httpRequest).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP Error ${response.code}"))
                }

                val body = response.body?.string() ?: "{}"
                val resObj = JSONObject(body)
                Result.success(
                    TrustedContactAlertResponse(
                        eventId = resObj.optString("eventId", request.eventId),
                        sentCount = resObj.optInt("sentCount", request.contacts.size),
                        failedCount = resObj.optInt("failedCount", 0),
                        channelsDispatched = resObj.optJSONArray("channelsDispatched")?.let { array -> List(array.length()) { index -> array.optString(index) } } ?: emptyList()
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun postPoliceAlert(request: PoliceAlertRequest): Result<PoliceAlertResponse> =
        withContext(Dispatchers.IO) {
            val config = getApiConfig()

            if (!isOnline()) {
                return@withContext Result.failure(IOException("No network connection (Offline)"))
            }

            if (config.isDemoMode || !config.isConfigured) {
                // Keep the event locally; a demo-mode request is not a real police dispatch.
                return@withContext Result.success(
                    PoliceAlertResponse(
                        acknowledged = false,
                        controlRoomTicketId = "DEMO-LOCAL-${request.eventId.take(12)}",
                        assignedUnit = null,
                        estimatedResponseMinutes = null,
                        message = "Demo only: no police service was contacted and no unit was dispatched. In a real emergency, call 112 directly."
                    )
                )
            }

            try {
                val json = JSONObject().apply {
                    put("eventId", request.eventId)
                    put("jurisdictionZone", request.jurisdictionZone)
                    put("timestamp", request.timestamp)
                    put("triggerType", request.triggerType)
                    put("verifiedVoiceTrigger", request.verifiedVoiceTrigger)
                    put("latitude", request.latitude)
                    put("longitude", request.longitude)
                    put("accuracyMeters", request.accuracyMeters)
                    put("audioIncidentRecorded", request.audioIncidentRecorded)
                    put("sha256IntegrityHash", request.sha256IntegrityHash)
                    put("callerNotes", request.callerNotes)
                    put("isDemoMode", false)
                }

                val httpRequest = Request.Builder()
                    .url("${config.baseUrl.removeSuffix("/")}/police-alerts")
                    .header("Authorization", "Bearer ${config.authToken}")
                    .post(json.toString().toRequestBody(jsonMedia))
                    .build()

                val response = okHttpClient.newCall(httpRequest).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}: Police endpoint rejected request"))
                }

                val body = response.body?.string() ?: "{}"
                val resObj = JSONObject(body)
                Result.success(
                    PoliceAlertResponse(
                        acknowledged = resObj.optBoolean("acknowledged", false),
                        controlRoomTicketId = resObj.optString("controlRoomTicketId", ""),
                        assignedUnit = resObj.optString("assignedUnit").takeIf { it.isNotBlank() },
                        estimatedResponseMinutes = resObj.optInt("estimatedResponseMinutes", -1).takeIf { it >= 0 },
                        message = resObj.optString("message", "The local backend did not confirm official dispatch.")
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
