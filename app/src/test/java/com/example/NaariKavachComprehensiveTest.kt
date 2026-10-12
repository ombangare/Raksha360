package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.dao.TrustedContactDao
import com.example.data.local.entity.EmergencyEventEntity
import com.example.data.local.entity.TrustedContactEntity
import com.example.data.remote.BackendApiClient
import com.example.data.repository.AuthenticationRepository
import com.example.data.repository.AuthState
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
        assertEquals(DispatchStatus.SENT, result.status)
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

        // When isDemoMode = true, seeds clearly tagged demo contacts
        repo.seedDemoContactsIfEmpty(isDemoMode = true)
        assertEquals(3, contactList.size)
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
}
