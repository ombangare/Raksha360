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
import com.example.data.repository.HelplineContact
import com.example.service.location.UserLocation
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.ConnectException
import java.util.UUID
import java.util.concurrent.TimeUnit
import android.util.Log
import com.example.data.repository.AuthStage
import com.example.data.repository.AuthErrorCategory
import com.example.data.repository.AuthenticationException
import com.example.domain.route.*

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

    val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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
            return@withContext Result.failure(IOException("Device heartbeat skipped: Demo Mode is active or control room is not configured."))
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

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    data class HealthCheckResponse(
        val status: String,
        val database: String,
        val service: String,
        val incidentRecords: Int,
        val version: String,
        val time: String,
        val latencyMs: Long
    )

    /** Unauthenticated health probe to verify control room reachability and database readiness. */
    suspend fun checkHealth(customUrl: String? = null): Result<HealthCheckResponse> = withContext(Dispatchers.IO) {
        val config = getApiConfig()
        val targetBase = (customUrl ?: config.baseUrl).trim().trimEnd('/')
        val normalized = if (targetBase.endsWith("/api/v1")) targetBase else "$targetBase/api/v1"

        if (!isOnline()) {
            return@withContext Result.failure(IOException("No network connection available."))
        }

        val start = System.currentTimeMillis()
        try {
            val request = Request.Builder().url("$normalized/health").get().build()
            val response = okHttpClient.newCall(request).execute()
            val latency = System.currentTimeMillis() - start
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Backend returned HTTP ${response.code}: ${body.take(120)}"))
            }

            val json = JSONObject(body)
            Result.success(
                HealthCheckResponse(
                    status = json.optString("status", "ONLINE"),
                    database = json.optString("database", "unknown"),
                    service = json.optString("service", "Raksha360 Control Room API"),
                    incidentRecords = json.optInt("incidentRecords", 0),
                    version = json.optString("version", "1.0"),
                    time = json.optString("time", ""),
                    latencyMs = latency
                )
            )
        } catch (e: Exception) {
            Result.failure(IOException("Cannot reach control room service at $normalized: ${e.localizedMessage ?: "Connection failed"}"))
        }
    }

    /** Tests the backend health route and optionally verifies the supplied token (user session or device token). */
    suspend fun testConnection(baseUrl: String, token: String = ""): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (!(trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
            return@withContext Result.failure(IOException("URL must start with http:// or https://."))
        }
        val normalized = if (trimmed.endsWith("/api/v1")) trimmed else "$trimmed/api/v1"
        if (com.example.BuildConfig.IS_PRODUCTION && normalized.startsWith("http://")
            && !normalized.contains("10.0.2.2") && !normalized.contains("localhost") && !normalized.contains("127.0.0.1")) {
            return@withContext Result.failure(IOException("Production API requires secure HTTPS. Cleartext HTTP is not permitted for remote connections."))
        }
        try {
            val healthRequest = Request.Builder().url("$normalized/health").get().build()
            val healthResponse = okHttpClient.newCall(healthRequest).execute()
            val healthBody = healthResponse.body?.string().orEmpty()
            if (!healthResponse.isSuccessful) return@withContext Result.failure(IOException("Health check failed (HTTP ${healthResponse.code}): ${healthBody.take(120)}"))
            val healthJson = JSONObject(healthBody)
            val serviceName = healthJson.optString("service", "Raksha360 Control Room")
            val dbName = healthJson.optString("database", "online")

            val cleanToken = token.trim()
            if (cleanToken.isBlank()) {
                return@withContext Result.success("Reachable: $serviceName is ONLINE (Database: $dbName). Sign in with Google to enable live dispatch.")
            }

            val sessionRequest = if (cleanToken.startsWith("rk_")) {
                Request.Builder()
                    .url("$normalized/auth/session")
                    .header("Authorization", "Bearer $cleanToken")
                    .get()
                    .build()
            } else {
                Request.Builder()
                    .url("$normalized/device/session")
                    .header("Authorization", "Bearer $cleanToken")
                    .post("{}".toRequestBody(jsonMedia))
                    .build()
            }

            val sessionResponse = okHttpClient.newCall(sessionRequest).execute()
            val sessionBody = sessionResponse.body?.string().orEmpty()
            if (!sessionResponse.isSuccessful) {
                return@withContext Result.failure(IOException("Health check reachable but authorization rejected (HTTP ${sessionResponse.code}). Verify credentials."))
            }
            Result.success("Connected to $serviceName · Database: $dbName · Authorization verified.")
        } catch (e: Exception) {
            Result.failure(IOException("Cannot reach backend at $normalized: ${e.localizedMessage ?: "Connection failed"}"))
        }
    }

    suspend fun fetchPublicHelplines(): Result<List<HelplineContact>> = withContext(Dispatchers.IO) {
        val config = getApiConfig()
        if (!isOnline() || !config.isConfigured) {
            return@withContext Result.failure(IOException("Phone is offline or backend unconfigured"))
        }
        try {
            val request = Request.Builder()
                .url("${config.baseUrl.removeSuffix("/")}/public-helplines")
                .header("Authorization", "Bearer ${config.authToken}")
                .get()
                .build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}: $body"))
            }
            val root = JSONObject(body)
            val array = root.optJSONArray("contacts") ?: JSONArray()
            val list = mutableListOf<HelplineContact>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    HelplineContact(
                        id = obj.optString("id", "helpline_$i"),
                        name = obj.optString("name", "Emergency Helpline"),
                        category = obj.optString("category", "emergency"),
                        phone = obj.optString("phone", "112"),
                        scope = obj.optString("scope", "")
                    )
                )
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun postIncidentReport(request: IncidentReportRequest): Result<IncidentReportResponse> = withContext(Dispatchers.IO) {
        val config = getApiConfig()
        if (config.isDemoMode || !config.isConfigured) {
            // Truthful Demo Mode simulation for offline testing
            return@withContext Result.success(
                IncidentReportResponse(
                    reportId = "DEMO-QUEUE-${UUID.randomUUID().toString().take(8).uppercase()}",
                    status = "Queued (Demo Mode)",
                    receivedAt = System.currentTimeMillis(),
                    isDemo = true,
                    message = "Demo Mode: Report saved in local evaluation queue. No live police dispatch occurred."
                )
            )
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
                request.evidenceSha256?.let { put("evidenceSha256", it) }
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

            if (!isOnline()) {
                return@withContext Result.failure(IOException("No network connection (Offline)"))
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

    override suspend fun verifyGoogleIdToken(idToken: String, nonce: String?): Result<GoogleAuthResponse> =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val config = getApiConfig()

            Log.i("BackendApiClient", "[STAGE: BACKEND_AUTHENTICATION] Starting token verification with backend")

            if (!isOnline()) {
                val elapsed = System.currentTimeMillis() - startTime
                Log.w("BackendApiClient", "[STAGE: BACKEND_AUTHENTICATION] Phone is offline after ${elapsed}ms (Category: BACKEND_OFFLINE)")
                return@withContext Result.failure(
                    AuthenticationException(
                        stage = AuthStage.BackendAuthentication,
                        category = AuthErrorCategory.BACKEND_OFFLINE,
                        elapsedMs = elapsed,
                        message = "No network connection. Please connect to the internet to sign in."
                    )
                )
            }

            try {
                val json = JSONObject().apply {
                    put("idToken", idToken)
                    if (nonce != null) put("nonce", nonce)
                }

                val httpRequest = Request.Builder()
                    .url("${config.baseUrl.removeSuffix("/")}/auth/google")
                    .header("Content-Type", "application/json")
                    .post(json.toString().toRequestBody(jsonMedia))
                    .build()

                val response = okHttpClient.newCall(httpRequest).execute()
                val elapsed = System.currentTimeMillis() - startTime
                val body = response.body?.string() ?: "{}"

                if (!response.isSuccessful) {
                    val detail = try {
                        JSONObject(body).optString("detail", "HTTP ${response.code}")
                    } catch (_: Exception) {
                        "HTTP ${response.code}"
                    }
                    Log.e(
                        "BackendApiClient",
                        "[STAGE: BACKEND_AUTHENTICATION] Server rejected authentication (HTTP ${response.code}) after ${elapsed}ms (Category: BACKEND_HTTP_ERROR)"
                    )
                    return@withContext Result.failure(
                        AuthenticationException(
                            stage = AuthStage.BackendAuthentication,
                            category = AuthErrorCategory.BACKEND_HTTP_ERROR,
                            elapsedMs = elapsed,
                            httpStatusCode = response.code,
                            message = "Google authentication rejected by server ($detail)",
                            cause = IOException("HTTP ${response.code}: $detail")
                        )
                    )
                }

                Log.i(
                    "BackendApiClient",
                    "[STAGE: BACKEND_AUTHENTICATION] Server authentication successful (HTTP 200) after ${elapsed}ms"
                )

                val resObj = JSONObject(body)
                Result.success(
                    GoogleAuthResponse(
                        authenticated = resObj.optBoolean("authenticated", true),
                        uid = resObj.getString("uid"),
                        email = resObj.getString("email"),
                        displayName = resObj.getString("displayName"),
                        photoUrl = resObj.optString("photoUrl").takeIf { it.isNotBlank() && it != "null" },
                        role = resObj.optString("role", "CITIZEN_USER"),
                        sessionToken = resObj.getString("sessionToken"),
                        expiresAt = resObj.getLong("expiresAt")
                    )
                )
            } catch (e: SocketTimeoutException) {
                val elapsed = System.currentTimeMillis() - startTime
                Log.w(
                    "BackendApiClient",
                    "[STAGE: BACKEND_AUTHENTICATION] Connection timed out after ${elapsed}ms (Category: BACKEND_NETWORK_TIMEOUT)"
                )
                Result.failure(
                    AuthenticationException(
                        stage = AuthStage.BackendAuthentication,
                        category = AuthErrorCategory.BACKEND_NETWORK_TIMEOUT,
                        elapsedMs = elapsed,
                        message = "Connection to Raksha360 control room timed out after ${elapsed / 1000}s. The server may be waking up; please try again.",
                        cause = e
                    )
                )
            } catch (e: java.io.InterruptedIOException) {
                val elapsed = System.currentTimeMillis() - startTime
                Log.w(
                    "BackendApiClient",
                    "[STAGE: BACKEND_AUTHENTICATION] Request interrupted/timeout after ${elapsed}ms (Category: BACKEND_NETWORK_TIMEOUT)"
                )
                Result.failure(
                    AuthenticationException(
                        stage = AuthStage.BackendAuthentication,
                        category = AuthErrorCategory.BACKEND_NETWORK_TIMEOUT,
                        elapsedMs = elapsed,
                        message = "Connection to Raksha360 control room timed out. Please try again.",
                        cause = e
                    )
                )
            } catch (e: ConnectException) {
                val elapsed = System.currentTimeMillis() - startTime
                Log.e(
                    "BackendApiClient",
                    "[STAGE: BACKEND_AUTHENTICATION] Failed to connect to server after ${elapsed}ms (Category: BACKEND_NETWORK_ERROR)"
                )
                Result.failure(
                    AuthenticationException(
                        stage = AuthStage.BackendAuthentication,
                        category = AuthErrorCategory.BACKEND_NETWORK_ERROR,
                        elapsedMs = elapsed,
                        message = "Unable to connect to Raksha360 control room server. Please verify your internet connection.",
                        cause = e
                    )
                )
            } catch (e: IOException) {
                val elapsed = System.currentTimeMillis() - startTime
                Log.e(
                    "BackendApiClient",
                    "[STAGE: BACKEND_AUTHENTICATION] Network I/O failure after ${elapsed}ms (Category: BACKEND_NETWORK_ERROR)"
                )
                Result.failure(
                    AuthenticationException(
                        stage = AuthStage.BackendAuthentication,
                        category = AuthErrorCategory.BACKEND_NETWORK_ERROR,
                        elapsedMs = elapsed,
                        message = "Network error communicating with control room: ${e.message}",
                        cause = e
                    )
                )
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                Log.e(
                    "BackendApiClient",
                    "[STAGE: BACKEND_AUTHENTICATION] Unexpected error after ${elapsed}ms (Category: UNKNOWN_ERROR)",
                    e
                )
                Result.failure(
                    AuthenticationException(
                        stage = AuthStage.BackendAuthentication,
                        category = AuthErrorCategory.UNKNOWN_ERROR,
                        elapsedMs = elapsed,
                        message = e.localizedMessage ?: "Unexpected error during authentication",
                        cause = e
                    )
                )
            }
        }

    override suspend fun verifySession(sessionToken: String): Result<GoogleAuthResponse> =
        withContext(Dispatchers.IO) {
            val config = getApiConfig()
            if (!isOnline()) {
                return@withContext Result.failure(IOException("No network connection."))
            }

            try {
                val httpRequest = Request.Builder()
                    .url("${config.baseUrl.removeSuffix("/")}/auth/session")
                    .header("Authorization", "Bearer $sessionToken")
                    .get()
                    .build()

                val response = okHttpClient.newCall(httpRequest).execute()
                val body = response.body?.string() ?: "{}"

                if (!response.isSuccessful) {
                    val detail = try {
                        JSONObject(body).optString("detail", "HTTP ${response.code}")
                    } catch (_: Exception) {
                        "HTTP ${response.code}"
                    }
                    return@withContext Result.failure(IOException("Session invalid: $detail"))
                }

                val resObj = JSONObject(body)
                Result.success(
                    GoogleAuthResponse(
                        authenticated = resObj.optBoolean("authenticated", true),
                        uid = resObj.getString("uid"),
                        email = resObj.getString("email"),
                        displayName = resObj.getString("displayName"),
                        photoUrl = resObj.optString("photoUrl").takeIf { it.isNotBlank() && it != "null" },
                        role = resObj.optString("role", "CITIZEN_USER"),
                        sessionToken = resObj.optString("sessionToken", sessionToken),
                        expiresAt = resObj.getLong("expiresAt")
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun revokeSession(sessionToken: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val config = getApiConfig()
            if (!isOnline()) {
                return@withContext Result.success(true)
            }

            try {
                val emptyBody = "".toRequestBody(jsonMedia)
                val httpRequest = Request.Builder()
                    .url("${config.baseUrl.removeSuffix("/")}/auth/signout")
                    .header("Authorization", "Bearer $sessionToken")
                    .post(emptyBody)
                    .build()

                val response = okHttpClient.newCall(httpRequest).execute()
                Result.success(response.isSuccessful)
            } catch (e: Exception) {
                Result.success(true) // Best-effort sign-out
            }
        }

    override suspend fun getAssistantSession(): Result<AssistantSessionResponse> =
        withContext(Dispatchers.IO) {
            val config = getApiConfig()
            try {
                val httpRequest = Request.Builder()
                    .url("${config.baseUrl.removeSuffix("/")}/assistant/session")
                    .header("Authorization", "Bearer ${config.authToken}")
                    .get()
                    .build()

                val response = okHttpClient.newCall(httpRequest).execute()
                val body = response.body?.string().orEmpty()
                val resObj = try { JSONObject(body) } catch (_: Exception) { JSONObject() }

                if (!response.isSuccessful) {
                    val detail = resObj.optString("detail").takeIf { it.isNotBlank() }
                        ?: resObj.optString("error").takeIf { it.isNotBlank() }
                        ?: "Backend returned HTTP ${response.code}"
                    return@withContext Result.success(
                        AssistantSessionResponse(
                            configured = false,
                            agentId = resObj.optString("agentId", "agent_3701m51qchkaenxsjnzr8jvrm66"),
                            agentName = resObj.optString("agentName", "Raksha360 AI Assistant"),
                            signedUrl = null,
                            conversationToken = null,
                            message = detail,
                            error = detail
                        )
                    )
                }

                val errorMsg = resObj.optString("error").takeIf { it.isNotBlank() && it != "null" }
                val messageText = resObj.optString("message").takeIf { it.isNotBlank() && it != "null" }
                val token = resObj.optString("conversationToken").takeIf { it.isNotBlank() && it != "null" }

                Result.success(
                    AssistantSessionResponse(
                        configured = resObj.optBoolean("configured", false),
                        agentId = resObj.optString("agentId", "agent_3701m51qchkaenxsjnzr8jvrm66"),
                        agentName = resObj.optString("agentName", "Raksha360 AI Assistant"),
                        signedUrl = resObj.optString("signedUrl").takeIf { it.isNotBlank() && it != "null" },
                        conversationToken = token,
                        message = messageText ?: errorMsg,
                        error = errorMsg ?: messageText
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun revokeLiveLocationShare(eventId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            val config = getApiConfig()
            try {
                val emptyBody = "".toRequestBody(jsonMedia)
                val httpRequest = Request.Builder()
                    .url("${config.baseUrl.removeSuffix("/")}/emergency-events/$eventId/revoke-location-share")
                    .header("Authorization", "Bearer ${config.authToken}")
                    .post(emptyBody)
                    .build()

                val response = okHttpClient.newCall(httpRequest).execute()
                Result.success(response.isSuccessful)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun analyzeRoute(
        coordinates: List<Pair<Double, Double>>,
        label: String = "Candidate Route",
        mode: String = "walking"
    ): Result<RouteAnalysisResult> = withContext(Dispatchers.IO) {
        val config = getApiConfig()
        try {
            val coordsArray = JSONArray()
            coordinates.forEach { (lat, lon) ->
                coordsArray.put(JSONObject().apply {
                    put("latitude", lat)
                    put("longitude", lon)
                })
            }
            val body = JSONObject().apply {
                put("coordinates", coordsArray)
                put("label", label)
                put("mode", mode)
            }.toString().toRequestBody(jsonMedia)

            val request = Request.Builder()
                .url("${config.baseUrl.removeSuffix("/")}/routes/analyze")
                .header("Authorization", "Bearer ${config.authToken}")
                .post(body)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Route analyze failed: HTTP ${response.code}"))
            }
            val resObj = JSONObject(response.body?.string() ?: "{}")

            val lightingObj = resObj.optJSONObject("lightingCoverage") ?: JSONObject()
            val woodedObj = resObj.optJSONObject("woodedExposure") ?: JSONObject()
            val isolationObj = resObj.optJSONObject("relativeIsolation") ?: JSONObject()
            val accidentObj = resObj.optJSONObject("historicalAccidentExposure") ?: JSONObject()
            val freshnessObj = resObj.optJSONObject("dataFreshness") ?: JSONObject()

            val reasons = mutableListOf<String>()
            val reasonsArray = resObj.optJSONArray("evidenceExplanations")
            if (reasonsArray != null) {
                for (i in 0 until reasonsArray.length()) {
                    reasons.add(reasonsArray.getString(i))
                }
            }

            val categories = mutableListOf<String>()
            val catArray = woodedObj.optJSONArray("categories")
            if (catArray != null) {
                for (i in 0 until catArray.length()) {
                    categories.add(catArray.getString(i))
                }
            }

            val nearBlackspots = mutableListOf<RouteBlackspotItem>()
            val bsArray = accidentObj.optJSONArray("nearBlackspots")
            if (bsArray != null) {
                for (i in 0 until bsArray.length()) {
                    val bs = bsArray.getJSONObject(i)
                    nearBlackspots.add(
                        RouteBlackspotItem(
                            id = bs.optInt("id", 0),
                            name = bs.optString("name", ""),
                            distanceMeters = bs.optDouble("distanceMeters", 0.0),
                            highway = bs.optString("highway", "")
                        )
                    )
                }
            }

            val result = RouteAnalysisResult(
                label = resObj.optString("label", label),
                mode = resObj.optString("mode", mode),
                routeLengthKm = resObj.optDouble("routeLengthKm", 0.0),
                etaMinutes = resObj.optInt("etaMinutes", 0),
                primaryLabel = resObj.optString("primaryLabel", "Route Context"),
                compositeSupportScore = resObj.optDouble("compositeSupportScore", 50.0),
                supportCoveragePercent = resObj.optDouble("supportCoveragePercent", 0.0),
                nearestEmergencyFacility = resObj.optString("nearestEmergencyFacility").takeIf { it.isNotBlank() && it != "null" },
                lightingCoverage = RouteLightingCoverage(
                    explicitLitKm = lightingObj.optDouble("explicitLitKm", 0.0),
                    explicitUnlitKm = lightingObj.optDouble("explicitUnlitKm", 0.0),
                    unknownLightingKm = lightingObj.optDouble("unknownLightingKm", 0.0),
                    note = lightingObj.optString("note", "")
                ),
                woodedExposure = RouteWoodedExposure(
                    intersectingKm = woodedObj.optDouble("intersectingKm", 0.0),
                    coveragePercent = woodedObj.optDouble("coveragePercent", 0.0),
                    categories = categories,
                    note = woodedObj.optString("note", "")
                ),
                relativeIsolation = RouteRelativeIsolation(
                    lowSupportSegmentsKm = isolationObj.optDouble("lowSupportSegmentsKm", 0.0),
                    coincidingSignals = isolationObj.optString("coincidingSignals", "")
                ),
                historicalAccidentExposure = RouteHistoricalAccidentExposure(
                    blackspotCount = accidentObj.optInt("blackspotCount", 0),
                    exposureScore = accidentObj.optDouble("exposureScore", 0.0),
                    nearBlackspots = nearBlackspots,
                    disclaimer = accidentObj.optString("disclaimer", "")
                ),
                evidenceExplanations = reasons,
                dataFreshness = RouteDataFreshness(
                    extractedAtUtc = freshnessObj.optString("extractedAtUtc", ""),
                    source = freshnessObj.optString("source", ""),
                    coverageNote = freshnessObj.optString("coverageNote", "")
                )
            )
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun compareRoutes(
        routes: List<RouteCandidateInput>
    ): Result<RouteComparisonSummary> = withContext(Dispatchers.IO) {
        val config = getApiConfig()
        try {
            val routesArray = JSONArray()
            routes.forEach { r ->
                val coordsArray = JSONArray()
                r.coordinates.forEach { (lat, lon) ->
                    coordsArray.put(JSONObject().apply {
                        put("latitude", lat)
                        put("longitude", lon)
                    })
                }
                routesArray.put(JSONObject().apply {
                    put("id", r.id)
                    put("label", r.label)
                    put("mode", r.mode)
                    put("coordinates", coordsArray)
                })
            }
            val body = JSONObject().apply {
                put("routes", routesArray)
            }.toString().toRequestBody(jsonMedia)

            val request = Request.Builder()
                .url("${config.baseUrl.removeSuffix("/")}/routes/compare")
                .header("Authorization", "Bearer ${config.authToken}")
                .post(body)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Route compare failed: HTTP ${response.code}"))
            }
            val resObj = JSONObject(response.body?.string() ?: "{}")
            val rankings = mutableListOf<RouteRankItem>()
            val rankArray = resObj.optJSONArray("candidateRankings")
            if (rankArray != null) {
                for (i in 0 until rankArray.length()) {
                    val item = rankArray.getJSONObject(i)
                    rankings.add(
                        RouteRankItem(
                            routeId = item.optString("routeId", ""),
                            label = item.optString("label", ""),
                            compositeSupportScore = item.optDouble("compositeSupportScore", 50.0),
                            primaryLabel = item.optString("primaryLabel", ""),
                            routeLengthKm = item.optDouble("routeLengthKm", 0.0),
                            etaMinutes = item.optInt("etaMinutes", 0),
                            nearBlackspots = item.optInt("nearBlackspots", 0),
                            woodedKm = item.optDouble("woodedKm", 0.0),
                            supportCoveragePercent = item.optDouble("supportCoveragePercent", 0.0)
                        )
                    )
                }
            }

            Result.success(
                RouteComparisonSummary(
                    candidateCount = resObj.optInt("candidateCount", rankings.size),
                    mostSupportedRouteId = resObj.optString("mostSupportedRouteId").takeIf { it.isNotBlank() && it != "null" },
                    recommendationNote = resObj.optString("recommendationNote", ""),
                    candidateRankings = rankings
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRouteLayersSummary(): Result<RouteLayersSummary> = withContext(Dispatchers.IO) {
        val config = getApiConfig()
        try {
            val request = Request.Builder()
                .url("${config.baseUrl.removeSuffix("/")}/routes/layers/summary")
                .header("Authorization", "Bearer ${config.authToken}")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Route layers summary failed: HTTP ${response.code}"))
            }
            val resObj = JSONObject(response.body?.string() ?: "{}")
            val countsObj = resObj.optJSONObject("counts") ?: JSONObject()

            Result.success(
                RouteLayersSummary(
                    status = resObj.optString("status", "LOADED"),
                    blackspotCount = countsObj.optInt("blackspots", 0),
                    facilitiesCount = countsObj.optInt("facilities", 0),
                    greenPolygonCount = countsObj.optInt("greenPolygons", 0),
                    roadFeaturesCount = countsObj.optInt("roadFeatures", 0),
                    bboxNote = resObj.optString("bboxNote", "")
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
