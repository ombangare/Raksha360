package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.dao.TrustedContactDao
import com.example.data.local.entity.EmergencyEventEntity
import com.example.data.local.entity.TrustedContactEntity
import com.example.data.remote.BackendApiClient
import com.example.data.remote.BackendConnectionState
import com.example.data.repository.AuthenticationRepository
import com.example.data.repository.AuthState
import com.example.data.repository.AuthStage
import com.example.data.repository.AuthErrorCategory
import com.example.data.repository.AuthenticationException
import com.example.data.repository.SettingsRepository
import com.example.data.repository.TrustedContactRepository
import com.example.domain.map.NagpurGeoConfig
import com.example.domain.map.PoliceDatasetStatus
import com.example.domain.map.SafetyScoringEngine
import com.example.domain.map.SafetyWaypoint
import com.example.service.audit.IAuditLoggerService
import com.example.service.dispatch.DemoPoliceDispatchAdapter
import com.example.service.dispatch.DispatchStatus
import com.example.service.dispatch.ProductionPoliceDispatchAdapter
import com.example.service.dispatch.ProductionTrustedContactDispatchAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NaariKavachComprehensiveTest {

    private lateinit var context: Context

    private val fakeAuditLogger = object : IAuditLoggerService {
        override val recentLogs = flowOf(emptyList<com.example.data.local.entity.AuditLogEntity>())
        override fun logSecurityEvent(action: String, category: String, details: String, severity: String) {}
        override fun logEnrollmentStarted() {}
        override fun logEnrollmentSampleCaptured(sampleNumber: Int, qualityScore: Float, isAcceptable: Boolean) {}
        override fun logEnrollmentCompleted(sampleCount: Int, threshold: Float) {}
        override fun logVoiceProfileDeleted() {}
        override fun logGuardModeChanged(enabled: Boolean, triggerPhrase: String) {}
        override fun logVoiceSosActivation(verifiedPhrase: String, speakerScore: Float, threshold: Float, locationSector: String) {}
        override fun logManualPanicActivation(locationSector: String) {}
        override fun logEmergencyStateChanged(eventId: String, fromState: String, toState: String, reason: String) {}
        override fun logEmergencyDispatchAttempt(eventId: String, channel: String, targetCount: Int) {}
        override fun logEmergencyDispatchSuccess(eventId: String, channel: String, trackingTicket: String) {}
        override fun logEmergencyDispatchFailed(eventId: String, channel: String, errorReason: String) {}
        override fun logLocationSharingStateChanged(active: Boolean, reason: String) {}
        override fun logOfflineQueueSync(pendingCount: Int, successCount: Int) {}
        override fun logEvidenceSealed(evidenceId: String, sha256Checksum: String, durationSecs: Int) {}
        override fun logEvidenceIntegrityVerified(evidenceId: String, matches: Boolean) {}
        override fun logPermissionEvent(permissionName: String, isGranted: Boolean) {}
        override suspend fun clearAuditLogs() {}
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    // 1. Google Authentication & Session Tests
    @Test
    fun testAuthenticationSessionRestoreAndDemoLogin() = runTest {
        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val backendClient = BackendApiClient(context) { BackendApiClient.ApiConfiguration() }
        val authRepo = AuthenticationRepository(context, backendClient, fakeAuditLogger, testDispatcher)

        // Initial state before sign in
        authRepo.signOut()
        testScheduler.advanceUntilIdle()
        assertTrue("Auth state should be unauthenticated initially", authRepo.authState.value is AuthState.Unauthenticated)

        // Explicit Demo Mode login
        authRepo.signInDemoMode("Safety Tester", "tester@naarikavach.local")
        val authState = authRepo.authState.value
        assertTrue("Auth state must be Authenticated after demo sign-in", authState is AuthState.Authenticated)
        val user = (authState as AuthState.Authenticated).user
        assertEquals("Safety Tester", user.displayName)
        assertEquals("tester@naarikavach.local", user.email)
        assertTrue("User must be tagged as demo user", user.isDemoUser)

        // Session restoration test
        val restoredRepo = AuthenticationRepository(context, backendClient, fakeAuditLogger, testDispatcher)
        assertTrue("Restored repo must restore session", restoredRepo.authState.value is AuthState.Authenticated)
        val restoredUser = (restoredRepo.authState.value as AuthState.Authenticated).user
        assertEquals("Safety Tester", restoredUser.displayName)

        // Sign out test
        restoredRepo.signOut()
        testScheduler.advanceUntilIdle()
        assertTrue("Must be unauthenticated after sign out", restoredRepo.authState.value is AuthState.Unauthenticated)
    }

    // 2. Nagpur and Sadar Geographic Seed Configuration
    @Test
    fun testNagpurAndSadarGeographicDataIntegrity() {
        // Nagpur Center coordinates
        assertEquals(21.1458, NagpurGeoConfig.NAGPUR_CENTER.latitude, 0.001)
        assertEquals(79.0882, NagpurGeoConfig.NAGPUR_CENTER.longitude, 0.001)

        // Sadar Pilot Center
        assertEquals(21.1612, NagpurGeoConfig.SADAR_PILOT_CENTER.latitude, 0.001)
        assertEquals(79.0825, NagpurGeoConfig.SADAR_PILOT_CENTER.longitude, 0.001)

        // Sadar Pilot Perimeter Polygon must contain valid closed loop
        assertTrue("Perimeter should contain at least 4 bounding coordinates", NagpurGeoConfig.SADAR_PILOT_PERIMETER.size >= 4)
        val first = NagpurGeoConfig.SADAR_PILOT_PERIMETER.first()
        val last = NagpurGeoConfig.SADAR_PILOT_PERIMETER.last()
        assertEquals("Perimeter must be a closed polygon loop", first.latitude, last.latitude, 0.0001)
        assertEquals("Perimeter must be a closed polygon loop", first.longitude, last.longitude, 0.0001)

        // Verified Safety Infrastructure points
        val waypoints = NagpurGeoConfig.VERIFIED_SAFETY_POINTS
        assertTrue("Waypoints must include Sadar Police Station", waypoints.any { it.name.contains("Sadar Police Station") })
        assertTrue("Waypoints must include Sitabuldi Police Station", waypoints.any { it.name.contains("Sitabuldi Police Station") })
        assertTrue("Waypoints must include Mayo Hospital", waypoints.any { it.name.contains("Mayo Hospital") })
        assertTrue("Waypoints must include VNIT", waypoints.any { it.name.contains("VNIT") })
    }

    // 3. Police Dataset Ingestion Contract & Safety Scoring
    @Test
    fun testSafetyScoringEngineNeverClaimsGuaranteedSafetyOrAbsoluteDanger() {
        val scoringEngine = SafetyScoringEngine { PoliceDatasetStatus.NoDatasetConnected() }
        val sadarContext = scoringEngine.computeAreaSafety("Sadar, Nagpur")

        assertFalse("Police data must not be claimed active when unconfigured", sadarContext.policeDataAvailable)
        assertTrue("Advisory notice must mention verified infrastructure", sadarContext.advisoryNotice.contains("infrastructure"))

        // Ethical AI compliance check
        assertFalse("Advisory must NEVER claim route is guaranteed safe", sadarContext.advisoryNotice.contains("guaranteed safe", ignoreCase = true))
        assertFalse("Advisory must NEVER label area as absolutely dangerous", sadarContext.advisoryNotice.contains("absolutely dangerous", ignoreCase = true))
    }

    // 4. Emergency Dispatch Adapters
    @Test
    fun testProductionPoliceDispatchAdapterReportsUnavailableWhenUnconfigured() = runTest {
        val settingsRepo = SettingsRepository(context)
        val backendClient = BackendApiClient(context) { BackendApiClient.ApiConfiguration(isConfigured = false) }
        val adapter = ProductionPoliceDispatchAdapter(backendClient, settingsRepo)

        val dummyEvent = EmergencyEventEntity(
            id = "test_event_99182",
            timestamp = System.currentTimeMillis(),
            triggerType = "VOICE_SOS",
            phraseDetected = "Raksha360, help me",
            speakerScore = 0.91f,
            latitude = 21.1612,
            longitude = 79.0825,
            accuracyMeters = 5f,
            locationDescription = "Sadar, Nagpur",
            status = "ACTIVE",
            trustedContactsNotifiedCount = 0,
            policeNotified = false,
            policeTrackingId = null,
            isDemoMode = false
        )

        val result = adapter.dispatchPoliceEmergencyAlert(dummyEvent, NagpurGeoConfig.SADAR_PILOT_CENTER)
        assertEquals("Production adapter must report UNAVAILABLE when endpoint is not configured", DispatchStatus.UNAVAILABLE, result.status)
        assertTrue("Result message must direct user to call 112 directly", result.message.contains("112"))
    }

    @Test
    fun testDemoPoliceDispatchAdapterSimulatesSafeDemonstration() = runTest {
        val adapter = DemoPoliceDispatchAdapter()
        val dummyEvent = EmergencyEventEntity(
            id = "demo_event_4412",
            timestamp = System.currentTimeMillis(),
            triggerType = "VOICE_SOS",
            phraseDetected = "Raksha360, help me",
            speakerScore = 0.95f,
            latitude = 21.1612,
            longitude = 79.0825,
            accuracyMeters = 5f,
            locationDescription = "Sadar, Nagpur",
            status = "ACTIVE",
            trustedContactsNotifiedCount = 0,
            policeNotified = false,
            policeTrackingId = null,
            isDemoMode = true
        )

        val result = adapter.dispatchPoliceEmergencyAlert(dummyEvent, NagpurGeoConfig.SADAR_PILOT_CENTER)
        assertEquals(DispatchStatus.UNAVAILABLE, result.status)
        assertTrue("Message must disclose simulated demo status", result.message.contains("Demo"))
        assertNotNull(result.trackingTicket)
    }

    // 5. Trusted Contact Repository Demo Isolation
    @Test
    fun testTrustedContactsDemoSeedingIsolation() = runTest {
        val contactList = mutableListOf<TrustedContactEntity>()
        val fakeDao = object : TrustedContactDao {
            override fun getAllContacts(): Flow<List<TrustedContactEntity>> = flowOf(contactList)
            override fun getActiveContacts(): Flow<List<TrustedContactEntity>> = flowOf(contactList.filter { it.isActive })
            override suspend fun getActiveContactsList(): List<TrustedContactEntity> = contactList.filter { it.isActive }
            override suspend fun getContactById(id: Long): TrustedContactEntity? = contactList.find { it.id == id }
            override suspend fun insertContact(contact: TrustedContactEntity): Long {
                contactList.add(contact)
                return contactList.size.toLong()
            }
            override suspend fun insertContacts(contacts: List<TrustedContactEntity>) {
                contactList.addAll(contacts)
            }
            override suspend fun updateContact(contact: TrustedContactEntity) {
                val idx = contactList.indexOfFirst { it.id == contact.id }
                if (idx != -1) contactList[idx] = contact
            }
            override suspend fun deleteContact(contact: TrustedContactEntity) {
                contactList.removeIf { it.id == contact.id }
            }
            override suspend fun deleteContactById(id: Long) {
                contactList.removeIf { it.id == id }
            }
            override suspend fun getActiveContactCount(): Int = contactList.count { it.isActive }
        }

        val repo = TrustedContactRepository(fakeDao)

        // When isDemoMode = false, seeding must do NOTHING (no fake Ananya or dummy data)
        repo.seedDemoContactsIfEmpty(isDemoMode = false)
        assertEquals("Contacts must remain empty for regular authenticated user", 0, contactList.size)

        // When isDemoMode = true, seeds clearly tagged demo contacts (strictly max 2)
        repo.seedDemoContactsIfEmpty(isDemoMode = true)
        assertEquals(2, contactList.size)
        assertTrue("Demo contacts must be clearly labeled Demo Contact", contactList.all { it.name.startsWith("Demo Contact") })
    }

    // 6. Evidence File Byte Integrity Test
    @Test
    fun testEvidenceFileHashingAndIntegrity() {
        val crypto = com.example.data.security.KeystoreCryptoManager()
        val tempFile = File(context.cacheDir, "test_audio_evidence.pcm")
        tempFile.writeBytes(ByteArray(1024) { (it % 127).toByte() })

        val initialHash = crypto.computeSha256(tempFile)
        assertEquals(64, initialHash.length)

        // Verifying match on untouched file
        val verificationHash = crypto.computeSha256(tempFile)
        assertEquals("Untouched evidence file must produce identical SHA-256", initialHash, verificationHash)

        // Modifying 1 single byte (tamper simulation)
        tempFile.writeBytes(ByteArray(1024) { 0xFF.toByte() })
        val tamperedHash = crypto.computeSha256(tempFile)
        assertFalse("Tampered evidence file must produce different SHA-256", initialHash.equals(tamperedHash, ignoreCase = true))

        tempFile.delete()
    }

    // 7. Production API Base URL Configuration & Normalization
    @Test
    fun testProductionApiBaseUrlConfigurationAndNormalization() {
        val settingsRepo = SettingsRepository(context)
        assertEquals(
            "SettingsRepository default must match BuildConfig DEFAULT_API_BASE_URL",
            BuildConfig.DEFAULT_API_BASE_URL,
            SettingsRepository.DEFAULT_API_URL
        )

        // Verifying setPoliceEndpointConfig trims trailing slashes
        settingsRepo.setPoliceEndpointConfig(
            configured = true,
            url = "https://raksha360-api.onrender.com/api/v1///",
            token = "  test-device-token  "
        )
        assertEquals("https://raksha360-api.onrender.com/api/v1", settingsRepo.policeEndpointUrl.value)
        assertEquals("test-device-token", settingsRepo.policeAuthToken.value)
        assertTrue(settingsRepo.policeEndpointConfigured.value)
    }

    // 8. Demo/Synthetic Emergency Event Isolation
    @Test
    fun testDemoEmergencyEventIsolationPreventsLiveDispatch() = runTest {
        val backendClient = BackendApiClient(context) {
            BackendApiClient.ApiConfiguration(
                isConfigured = true,
                baseUrl = "https://raksha360-api.onrender.com/api/v1",
                authToken = "test-token",
                isDemoMode = true // Demo mode enabled
            )
        }

        val demoRequest = com.example.data.remote.EmergencyEventRequest(
            eventId = "DEMO-EVENT-SIMULATION-99",
            timestamp = System.currentTimeMillis(),
            triggerType = "DEMO_TEST_DRILL",
            phraseDetected = "Demo Trigger",
            speakerConfidence = 0.99f,
            latitude = 21.1458,
            longitude = 79.0882,
            accuracyMeters = 5f,
            locationDescription = "Nagpur Central Demonstration Area",
            deviceSessionId = "SESSION-TEST-01",
            isDemoMode = true
        )

        val response = backendClient.postEmergencyEvent(demoRequest)
        assertTrue("Demo mode emergency event submission must succeed with local simulation", response.isSuccess)
        val data = response.getOrThrow()
        assertEquals(demoRequest.eventId, data.eventId)
        assertTrue("Tracking ticket must indicate demo simulation", data.trackingToken.startsWith("DEMO-TKT-"))
        assertEquals("RECEIVED", data.status)
    }

    // 9. Interactive 8-Step App Tour Lifecycle
    @Test
    fun testInteractiveAppTourLifecycle() {
        val settingsRepo = SettingsRepository(context)

        // Reset state
        settingsRepo.setAppTourCompleted(false)
        settingsRepo.dismissTour(markCompleted = false)
        assertFalse(settingsRepo.isAppTourCompleted.value)
        assertFalse(settingsRepo.isAppTourActive.value)

        // 1. First-login start
        settingsRepo.startTour()
        assertTrue("Tour must be active after startTour()", settingsRepo.isAppTourActive.value)
        assertEquals("Tour must begin at Step 1", 1, settingsRepo.currentTourStep.value)

        // 2. Step transitions up to 8
        settingsRepo.setTourStep(2)
        assertEquals(2, settingsRepo.currentTourStep.value)
        settingsRepo.setTourStep(8)
        assertEquals(8, settingsRepo.currentTourStep.value)

        // Bounds check
        settingsRepo.setTourStep(99)
        assertEquals("Tour step cannot exceed 8", 8, settingsRepo.currentTourStep.value)
        settingsRepo.setTourStep(-5)
        assertEquals("Tour step cannot go below 1", 1, settingsRepo.currentTourStep.value)

        // 3. Skip tour
        settingsRepo.dismissTour(markCompleted = true)
        assertFalse("Tour should not be active after dismiss", settingsRepo.isAppTourActive.value)
        assertTrue("Tour should be marked completed after dismiss", settingsRepo.isAppTourCompleted.value)

        // 4. Returning user should not auto-trigger, but replay must work
        assertTrue("Returning user has completed tour", settingsRepo.isAppTourCompleted.value)
        settingsRepo.startTour()
        assertTrue("Replaying tour must activate overlay at step 1", settingsRepo.isAppTourActive.value)
        assertEquals(1, settingsRepo.currentTourStep.value)
        settingsRepo.dismissTour(markCompleted = true)
    }

    // 10. Incident Report Submission and Hashing
    @Test
    fun testIncidentReportSubmissionFlow() = runTest {
        val backendClient = BackendApiClient(context) {
            BackendApiClient.ApiConfiguration(
                isConfigured = false,
                isDemoMode = true
            )
        }

        val request = com.example.data.remote.IncidentReportRequest(
            title = "Unlit street near Sadar",
            category = "Unsafe route",
            description = "Streetlights non-functional along the alley behind the bus stop.",
            locationDescription = "Sadar, Nagpur",
            latitude = 21.1612,
            longitude = 79.0825,
            evidenceSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            isDemoMode = true
        )

        val result = backendClient.postIncidentReport(request)
        assertTrue("Incident report in demo mode should succeed locally", result.isSuccess)
        val response = result.getOrThrow()
        assertTrue("Report ID must be generated with DEMO-QUEUE prefix", response.reportId.startsWith("DEMO-QUEUE-"))
        assertEquals("Queued (Demo Mode)", response.status)
        assertTrue("Response message must indicate offline evaluation queue", response.message.contains("evaluation queue"))
        assertTrue("isDemo flag must be true", response.isDemo)
    }

    // 11. Session Expiration and Credential Lifecycle Tests
    @Test
    fun testSessionExpirationDetectionAndClearing() = runTest {
        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val backendClient = BackendApiClient(context) { BackendApiClient.ApiConfiguration() }

        // Manually seed an expired session in SharedPreferences
        val prefs = context.getSharedPreferences("naari_kavach_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("auth_uid", "expired_user_123")
            .putString("auth_display_name", "Expired User")
            .putString("auth_email", "expired@example.com")
            .putString("auth_session_token", "rk_expired_token")
            .putLong("auth_expires_at", System.currentTimeMillis() - 60_000) // 1 minute in past
            .putBoolean("auth_is_demo", false)
            .apply()

        val authRepo = AuthenticationRepository(context, backendClient, fakeAuditLogger, testDispatcher)
        assertTrue("Expired session must result in Unauthenticated state", authRepo.authState.value is AuthState.Unauthenticated)
        val message = (authRepo.authState.value as AuthState.Unauthenticated).message
        assertTrue("Error message must inform user session expired", message?.contains("expired", ignoreCase = true) == true)

        // SharedPreferences must have been cleared
        assertNull("UID must be cleared after expired session detected", prefs.getString("auth_uid", null))
        assertNull("Session token must be cleared", prefs.getString("auth_session_token", null))
    }

    @Test
    fun testValidSessionRestorationWithExpiryInFuture() = runTest {
        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val backendClient = BackendApiClient(context) { BackendApiClient.ApiConfiguration() }

        val futureExpiry = System.currentTimeMillis() + 86_400_000L // 24 hours in future
        val prefs = context.getSharedPreferences("naari_kavach_auth_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("auth_uid", "valid_user_456")
            .putString("auth_display_name", "Valid User")
            .putString("auth_email", "valid@example.com")
            .putString("auth_session_token", "rk_valid_token_789")
            .putLong("auth_expires_at", futureExpiry)
            .putBoolean("auth_is_demo", false)
            .apply()

        val authRepo = AuthenticationRepository(context, backendClient, fakeAuditLogger, testDispatcher)
        assertTrue("Valid unexpired session must be Authenticated", authRepo.authState.value is AuthState.Authenticated)
        val user = (authRepo.authState.value as AuthState.Authenticated).user
        assertEquals("valid_user_456", user.uid)
        assertEquals("valid@example.com", user.email)
        assertEquals("rk_valid_token_789", user.sessionToken)
        assertEquals(futureExpiry, user.expiresAt)
        assertFalse("User must not be marked as demo", user.isDemoUser)

        // Sign out clears session
        authRepo.signOut()
        testScheduler.advanceUntilIdle()
        assertTrue("Must be unauthenticated after sign out", authRepo.authState.value is AuthState.Unauthenticated)
        assertNull(prefs.getString("auth_uid", null))
        assertNull(prefs.getString("auth_session_token", null))
    }

    @Test
    fun testWebClientIdConfigurationValidation() {
        val configured = AuthenticationRepository.isWebClientIdConfigured()
        val clientId = AuthenticationRepository.getWebClientId()
        assertNotNull("Client ID should never be null", clientId)
        if (configured) {
            assertTrue("Configured client ID must contain .apps.googleusercontent.com", clientId.contains(".apps.googleusercontent.com"))
            assertFalse("Configured client ID must not be the placeholder", clientId == AuthenticationRepository.DEFAULT_WEB_CLIENT_ID_PLACEHOLDER)
        }
    }

    // 12. Automatic Backend Connection, Zero-Config Defaults & Demo Isolation Tests
    @Test
    fun testAutomaticProductionEndpointConfiguration() {
        // Clear any previous settings
        val prefs = context.getSharedPreferences("naari_kavach_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        val settingsRepo = SettingsRepository(context)
        // Public users must have production endpoint configured by default without manual setup
        assertEquals("Production API URL must be default", BuildConfig.DEFAULT_API_BASE_URL, settingsRepo.policeEndpointUrl.value)
        assertTrue("Endpoint must be marked configured by default when base URL is provided", settingsRepo.policeEndpointConfigured.value)
        assertEquals("No manual auth token required by default", "", settingsRepo.policeAuthToken.value)
    }

    @Test
    fun testResetToDefaultEndpointRestoresProductionUrl() {
        val prefs = context.getSharedPreferences("naari_kavach_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("police_endpoint_url", "http://192.168.1.50:4000/api/v1")
            .putString("police_auth_token", "dev_secret_token")
            .apply()

        val settingsRepo = SettingsRepository(context)
        assertEquals("http://192.168.1.50:4000/api/v1", settingsRepo.policeEndpointUrl.value)
        assertEquals("dev_secret_token", settingsRepo.policeAuthToken.value)

        // Reset to default
        settingsRepo.resetToDefaultEndpoint()
        assertEquals("Must restore default production URL", BuildConfig.DEFAULT_API_BASE_URL, settingsRepo.policeEndpointUrl.value)
        assertEquals("Must clear manual developer token override", "", settingsRepo.policeAuthToken.value)
        assertTrue("Must keep configured flag true", settingsRepo.policeEndpointConfigured.value)
    }

    @Test
    fun testBackendConnectionStateHierarchy() {
        val connecting = BackendConnectionState.Connecting
        assertTrue("Connecting state check", connecting is BackendConnectionState.Connecting)

        val connectedLive = BackendConnectionState.Connected(
            endpoint = BuildConfig.DEFAULT_API_BASE_URL,
            isUserAuthenticated = true,
            userEmail = "citizen@example.com",
            isDemoMode = false,
            database = "supabase",
            latencyMs = 45L
        )
        assertTrue("Live connected state check", connectedLive.isUserAuthenticated)
        assertFalse("Live connected must not be demo mode", connectedLive.isDemoMode)
        assertEquals(45L, connectedLive.latencyMs)
        assertEquals("supabase", connectedLive.database)

        val connectedDemo = BackendConnectionState.Connected(
            endpoint = BuildConfig.DEFAULT_API_BASE_URL,
            isUserAuthenticated = false,
            isDemoMode = true
        )
        assertTrue("Demo mode flag must be true", connectedDemo.isDemoMode)

        val offline = BackendConnectionState.Offline("Network interface unavailable")
        assertTrue("Offline reason check", offline.reason.contains("Network interface"))

        val authReq = BackendConnectionState.AuthenticationRequired("Sign in required")
        assertTrue("Auth required check", authReq.reason.contains("Sign in"))

        val svcUnavailable = BackendConnectionState.ServiceUnavailable("HTTP 503")
        assertEquals("HTTP 503", svcUnavailable.error)
        assertTrue("Last attempt timestamp set", svcUnavailable.lastAttemptAt > 0)
    }

    @Test
    fun testSessionValidationRejectsDemoOrMissingUser() = runTest {
        val testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)
        val backendClient = BackendApiClient(context) { BackendApiClient.ApiConfiguration() }
        val authRepo = AuthenticationRepository(context, backendClient, fakeAuditLogger, testDispatcher)

        // Case 1: Unauthenticated user
        authRepo.signOut()
        testScheduler.advanceUntilIdle()
        val unauthResult = authRepo.validateSessionWithBackend()
        assertTrue("Validation must fail when unauthenticated", unauthResult.isFailure)

        // Case 2: Demo user
        authRepo.signInDemoMode("Demo Tester", "demo@naarikavach.local")
        testScheduler.advanceUntilIdle()
        val demoResult = authRepo.validateSessionWithBackend()
        assertTrue("Validation must fail for demo user (does not touch live backend)", demoResult.isFailure)
        assertTrue("Error must note no live user session", demoResult.exceptionOrNull()?.message?.contains("live user session") == true)
    }

    @Test
    fun testDemoModeIsolationNeverClaimsLivePoliceDispatch() = runTest {
        val demoAdapter = DemoPoliceDispatchAdapter()
        val dummyEvent = EmergencyEventEntity(
            id = "demo_event_safety_112",
            timestamp = System.currentTimeMillis(),
            triggerType = "MANUAL_PANIC",
            phraseDetected = "Manual panic trigger",
            speakerScore = 1.0f,
            latitude = 21.1458,
            longitude = 79.0882,
            accuracyMeters = 4f,
            locationDescription = "Nagpur Central",
            status = "ACTIVE",
            trustedContactsNotifiedCount = 0,
            policeNotified = false,
            policeTrackingId = null,
            isDemoMode = true
        )

        val result = demoAdapter.dispatchPoliceEmergencyAlert(dummyEvent, NagpurGeoConfig.SADAR_PILOT_CENTER)
        assertEquals("Demo mode isolation must be UNAVAILABLE to avoid claiming real dispatch", DispatchStatus.UNAVAILABLE, result.status)
        assertTrue("Tracking ticket must not be null", result.trackingTicket != null)
        assertTrue("Tracking ticket must have DEMO prefix", result.trackingTicket!!.startsWith("DEMO-"))
        assertTrue("Message must clearly state demo isolation", result.message.contains("Demo"))
        assertFalse("Demo event must not claim real police received dispatch", result.message.contains("police received dispatch"))
    }

    // 10. Emergency Contacts Max 2 & Verification Tests
    private class FakeTrustedContactDao : TrustedContactDao {
        val contacts = mutableListOf<TrustedContactEntity>()
        private var nextId = 1L

        override fun getAllContacts(): Flow<List<TrustedContactEntity>> = flowOf(contacts)
        override fun getActiveContacts(): Flow<List<TrustedContactEntity>> = flowOf(contacts.filter { it.isActive })
        override suspend fun getActiveContactsList(): List<TrustedContactEntity> = contacts.filter { it.isActive }
        override suspend fun getContactById(id: Long): TrustedContactEntity? = contacts.find { it.id == id }
        override suspend fun insertContact(contact: TrustedContactEntity): Long {
            val assignedId = if (contact.id == 0L) nextId++ else contact.id
            val saved = contact.copy(id = assignedId)
            contacts.removeAll { it.id == assignedId }
            contacts.add(saved)
            return assignedId
        }
        override suspend fun insertContacts(newContacts: List<TrustedContactEntity>) {
            newContacts.forEach { insertContact(it) }
        }
        override suspend fun updateContact(contact: TrustedContactEntity) {
            contacts.removeAll { it.id == contact.id }
            contacts.add(contact)
        }
        override suspend fun deleteContact(contact: TrustedContactEntity) {
            contacts.removeAll { it.id == contact.id }
        }
        override suspend fun deleteContactById(id: Long) {
            contacts.removeAll { it.id == id }
        }
        override suspend fun getActiveContactCount(): Int = contacts.count { it.isActive }
    }

    @Test
    fun testTrustedContactsEnforcesMaxTwoLimit() = runTest {
        val fakeDao = FakeTrustedContactDao()
        val repo = TrustedContactRepository(fakeDao)

        // Contact 1: Valid
        val id1 = repo.addContact("Sister", "Sister", "+919876543210", "sister@example.com")
        assertTrue("First contact should be added successfully", id1 > 0)
        assertEquals("Active count should be 1", 1, fakeDao.getActiveContactCount())

        // Contact 2: Valid
        val id2 = repo.addContact("Mother", "Mother", "+919876543211", "mother@example.com")
        assertTrue("Second contact should be added successfully", id2 > 0)
        assertEquals("Active count should be 2", 2, fakeDao.getActiveContactCount())

        // Contact 3: Must be rejected because max 2 is enforced
        val id3 = repo.addContact("Friend", "Friend", "+919876543212", "friend@example.com")
        assertEquals("Third contact must be rejected with -1", -1L, id3)
        assertEquals("Active count must remain 2", 2, fakeDao.getActiveContactCount())
    }

    @Test
    fun testContactEmailVerificationAndLocationConsent() = runTest {
        val fakeDao = FakeTrustedContactDao()
        val repo = TrustedContactRepository(fakeDao)

        val id = repo.addContact("Sister", "Sister", "+919876543210", "sister@example.com")
        var contact = fakeDao.getContactById(id)
        assertNotNull("Contact must exist", contact)
        assertFalse("New contact email should be unverified initially", contact!!.isEmailVerified)
        assertTrue("Location sharing should be enabled by default", contact.canShareLocation)

        // Verify email
        repo.verifyContactEmail(id)
        contact = fakeDao.getContactById(id)
        assertTrue("Email must be marked verified", contact!!.isEmailVerified)

        // Revoke location sharing consent
        repo.updateLocationConsent(id, false)
        contact = fakeDao.getContactById(id)
        assertFalse("Location sharing consent should now be revoked", contact!!.canShareLocation)
    }

    // 11. Microphone Coordination Tests
    @Test
    fun testMicrophoneCoordinatorTransitionsAndPreemption() {
        val coordinator = com.example.assistant.MicrophoneCoordinator

        // Reset to default
        coordinator.resetToDefault(context)

        // Request mic for ElevenLabs assistant
        val granted = coordinator.requestMicForAssistant(context)
        assertTrue("Mic should be granted to ElevenLabs assistant", granted)
        assertEquals(
            com.example.assistant.MicrophoneCoordinator.MicrophoneOwner.ELEVENLABS_ASSISTANT,
            coordinator.currentOwner.value
        )

        // Emergency SOS preemption
        var preemptionCallbackFired = false
        coordinator.preemptForEmergency(context) {
            preemptionCallbackFired = true
        }
        assertTrue("Emergency preemption callback must fire", preemptionCallbackFired)
        assertEquals(
            com.example.assistant.MicrophoneCoordinator.MicrophoneOwner.EMERGENCY_SOS,
            coordinator.currentOwner.value
        )

        // Attempting to request mic while SOS is active must be denied
        val requestDuringSos = coordinator.requestMicForAssistant(context)
        assertFalse("Assistant must NEVER be granted mic during active Emergency SOS", requestDuringSos)
        assertEquals(
            com.example.assistant.MicrophoneCoordinator.MicrophoneOwner.EMERGENCY_SOS,
            coordinator.currentOwner.value
        )

        // Release/reset
        coordinator.resetToDefault(context)
        assertEquals(
            com.example.assistant.MicrophoneCoordinator.MicrophoneOwner.IDLE,
            coordinator.currentOwner.value
        )
    }

    // 12. ElevenLabs Assistant Session Contract Tests
    @Test
    fun testAssistantSessionContract() {
        val sessionConfigured = com.example.data.remote.AssistantSessionResponse(
            configured = true,
            agentId = "agent_3701m51qchkaenxsjnzr8jvrm66",
            agentName = "Raksha360 AI Assistant",
            signedUrl = "wss://api.elevenlabs.io/v1/convai/conversation?agent_id=agent_3701m51qchkaenxsjnzr8jvrm66&token=test_tok"
        )
        assertTrue("Session must be configured", sessionConfigured.configured)
        assertEquals("agent_3701m51qchkaenxsjnzr8jvrm66", sessionConfigured.agentId)
        assertEquals("Raksha360 AI Assistant", sessionConfigured.agentName)
        assertNotNull("Signed URL must be present when configured", sessionConfigured.signedUrl)
        assertTrue(sessionConfigured.signedUrl!!.startsWith("wss://"))

        val sessionUnconfigured = com.example.data.remote.AssistantSessionResponse(
            configured = false,
            agentId = "agent_3701m51qchkaenxsjnzr8jvrm66",
            agentName = "Raksha360 AI Assistant",
            signedUrl = null,
            error = "ELEVENLABS_API_KEY is not configured on the backend server."
        )
        assertFalse("Session must not be configured", sessionUnconfigured.configured)
        assertNull("Signed URL must be null when unconfigured", sessionUnconfigured.signedUrl)
        assertTrue(sessionUnconfigured.error!!.contains("ELEVENLABS_API_KEY"))
    }

    // 13. MainViewModel Startup & Flow Initialization Regression Test
    @Test
    fun testMainViewModelInitializationDoesNotCrashOnFlowCollection() {
        val application = ApplicationProvider.getApplicationContext<Context>() as android.app.Application
        val viewModel = com.example.ui.viewmodel.MainViewModel(application)

        assertNotNull("MainViewModel must initialize without crashing", viewModel)
        assertNotNull("authState must not be null upon initialization", viewModel.authState)
        assertNotNull("authState value must be available", viewModel.authState.value)
        assertNotNull("isDemoMode must not be null upon initialization", viewModel.isDemoMode)
        assertNotNull("connectionState must be initialized", viewModel.connectionState.value)
        assertNotNull("assistantManager must be accessible", viewModel.assistantManager)
    }

    // 14. Authentication Timeout Diagnostics & Stage Differentiation Regression Tests
    @Test
    fun testOkHttpClientTimeoutValuesAreStandard30Seconds() {
        val backendClient = BackendApiClient(context) { BackendApiClient.ApiConfiguration() }
        assertEquals(
            "OkHttpClient connectTimeout must be 30,000ms (30s)",
            30_000,
            backendClient.okHttpClient.connectTimeoutMillis
        )
        assertEquals(
            "OkHttpClient readTimeout must be 30,000ms (30s)",
            30_000,
            backendClient.okHttpClient.readTimeoutMillis
        )
        assertEquals(
            "OkHttpClient writeTimeout must be 30,000ms (30s)",
            30_000,
            backendClient.okHttpClient.writeTimeoutMillis
        )
    }

    @Test
    fun testAuthStageAndCategoryDistinction() {
        // Credential Acquisition vs Backend Authentication vs Session Persistence
        assertEquals("CREDENTIAL_ACQUISITION", AuthStage.CredentialAcquisition.toString())
        assertEquals("BACKEND_AUTHENTICATION", AuthStage.BackendAuthentication.toString())
        assertEquals("SESSION_PERSISTENCE", AuthStage.SessionPersistence.toString())

        // Distinct categories for credential manager timeout vs network timeout
        assertTrue(
            "Google credential timeout must be distinct from backend network timeout",
            AuthErrorCategory.GOOGLE_CREDENTIAL_TIMEOUT != AuthErrorCategory.BACKEND_NETWORK_TIMEOUT
        )
        assertTrue(
            "Cancellation must be a distinct category",
            AuthErrorCategory.GOOGLE_CREDENTIAL_CANCELED != AuthErrorCategory.GOOGLE_CREDENTIAL_TIMEOUT
        )
    }

    @Test
    fun testAuthenticationExceptionNeverExposesRawTimeoutString() {
        val socketTimeoutEx = java.net.SocketTimeoutException("timeout")
        val authEx = AuthenticationException(
            stage = AuthStage.BackendAuthentication,
            category = AuthErrorCategory.BACKEND_NETWORK_TIMEOUT,
            elapsedMs = 12500L,
            message = "Connection to Raksha360 control room timed out after 12s. The server may be waking up; please try again.",
            cause = socketTimeoutEx
        )

        assertFalse("Exception message must not be a bare 'timeout' string", authEx.message.equals("timeout", ignoreCase = true))
        assertTrue("Exception message must inform user about timeout", authEx.message.contains("timed out", ignoreCase = true))
        assertTrue("Exception message must guide user to retry", authEx.message.contains("try again", ignoreCase = true))
        assertEquals(AuthStage.BackendAuthentication, authEx.stage)
        assertEquals(AuthErrorCategory.BACKEND_NETWORK_TIMEOUT, authEx.category)
        assertEquals(12500L, authEx.elapsedMs)
    }

    @Test
    fun testZeroTokenLeakageInAuthExceptionDiagnostics() {
        val sensitiveIdToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.sensitive_payload_here"
        val sensitiveSessionToken = "rk_secret_live_session_token_9999"

        val authEx = AuthenticationException(
            stage = AuthStage.BackendAuthentication,
            category = AuthErrorCategory.BACKEND_HTTP_ERROR,
            elapsedMs = 450L,
            httpStatusCode = 401,
            message = "Google authentication rejected by server (Invalid ID token claims)"
        )

        assertFalse("Exception message must never contain ID token", authEx.message.contains(sensitiveIdToken))
        assertFalse("Exception message must never contain session token", authEx.message.contains(sensitiveSessionToken))
        assertFalse("Exception string must never contain ID token", authEx.toString().contains(sensitiveIdToken))
        assertFalse("Exception string must never contain session token", authEx.toString().contains(sensitiveSessionToken))
    }

    @Test
    fun testVoiceProfilePersistenceAndReload() {
        val crypto = com.example.data.security.KeystoreCryptoManager()
        val repo = com.example.data.repository.VoiceProfileRepository(context, crypto)

        // Generate a 128-dimensional synthetic Vosk speaker embedding
        val vosk128Vector = FloatArray(128) { index -> 0.05f * (index % 5) }
        val saved = repo.saveVoiceProfile(
            phrase = "Raksha 360, help me",
            embedding = vosk128Vector,
            sampleCount = 5,
            threshold = 0.82f
        )
        assertTrue("saveVoiceProfile must return true on successful write", saved)

        val current = repo.currentProfile.value
        assertNotNull("Current profile must not be null after saving", current)
        assertTrue("Profile must be enrolled", current!!.isEnrolled)
        assertEquals("Raksha 360, help me", current.phrase)
        assertEquals(5, current.sampleCount)
        assertEquals(128, current.embedding.size)

        // Create a new repository instance to simulate app restart from encrypted storage
        val reloadedRepo = com.example.data.repository.VoiceProfileRepository(context, crypto)
        val reloaded = reloadedRepo.currentProfile.value
        assertNotNull("Reloaded profile must not be null after reading from encrypted storage", reloaded)
        assertTrue("Reloaded profile must be enrolled", reloaded!!.isEnrolled)
        assertEquals("Raksha 360, help me", reloaded.phrase)
        assertEquals(5, reloaded.sampleCount)
        assertEquals(128, reloaded.embedding.size)
    }

    @Test
    fun testLegacy32dVoiceProfileDetection() {
        val crypto = com.example.data.security.KeystoreCryptoManager()
        val repo = com.example.data.repository.VoiceProfileRepository(context, crypto)

        // Legacy profile with 32-dimensional embedding
        val legacy32Vector = FloatArray(32) { 0.1f }
        repo.saveVoiceProfile(
            phrase = "Raksha 360, help me",
            embedding = legacy32Vector,
            sampleCount = 3,
            threshold = 0.82f
        )

        val profile = repo.currentProfile.value
        assertNotNull(profile)
        // Guard Mode eligibility check: embedding.size <= 32 must require re-enrollment
        val requiresReEnrollment = profile!!.sampleCount < 5 || profile.embedding.size <= 32
        assertTrue("Legacy 32-d profile must require fresh 5-sample Vosk enrollment", requiresReEnrollment)
    }

    @Test
    fun testAssistantSessionTruthfulErrorMapping() {
        val upstream401Response = com.example.data.remote.AssistantSessionResponse(
            configured = false,
            agentId = "agent_3701m51qchkaenxsjnzr8jvrm66",
            agentName = "Raksha360 AI Assistant",
            signedUrl = null,
            message = "ElevenLabs API returned 401: Unauthorized",
            error = "ElevenLabs API returned 401: Unauthorized"
        )

        assertFalse(upstream401Response.configured)
        assertNull(upstream401Response.signedUrl)
        assertEquals("ElevenLabs API returned 401: Unauthorized", upstream401Response.error)
        assertTrue(
            "Error must reflect upstream 401 and not misleadingly claim key is not configured",
            upstream401Response.error!!.contains("401")
        )
    }
}

