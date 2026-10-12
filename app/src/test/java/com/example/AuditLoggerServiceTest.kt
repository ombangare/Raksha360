package com.example

import android.content.Context
import com.example.data.local.dao.AuditLogDao
import com.example.data.local.entity.AuditLogEntity
import com.example.service.audit.AuditLoggerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuditLoggerServiceTest {

    private lateinit var context: Context
    private val insertedLogs = mutableListOf<AuditLogEntity>()

    private val fakeDao = object : AuditLogDao {
        override fun getRecentLogs(): Flow<List<AuditLogEntity>> = flowOf(insertedLogs)
        override fun getLogsByCategory(category: String): Flow<List<AuditLogEntity>> =
            flowOf(insertedLogs.filter { it.category == category })
        override fun getLogsBySeverity(severity: String): Flow<List<AuditLogEntity>> =
            flowOf(insertedLogs.filter { it.severity == severity })
        override suspend fun getLogCount(): Int = insertedLogs.size
        override suspend fun insertLog(log: AuditLogEntity): Long {
            insertedLogs.add(log)
            return insertedLogs.size.toLong()
        }
        override suspend fun clearLogs() {
            insertedLogs.clear()
        }
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        insertedLogs.clear()
    }

    @Test
    fun testEnrollmentLoggingContainsNoRawAudioData() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val service = AuditLoggerService(context, fakeDao, CoroutineScope(testDispatcher), testDispatcher)

        service.logEnrollmentCompleted(sampleCount = 5, threshold = 0.78f)
        testScheduler.advanceUntilIdle()

        assertEquals(1, insertedLogs.size)
        val log = insertedLogs.first()
        assertEquals("VOICE_ENROLLMENT_COMPLETED", log.action)
        assertEquals("VOICE_SECURITY", log.category)
        assertEquals("INFO", log.severity)

        // Sensitive data checks
        assertFalse("Log must not contain raw audio byte dumps", log.detail.contains("ByteArray"))
        assertFalse("Log must not contain raw float embeddings", log.detail.contains("FloatArray"))
        assertTrue("Log should mention Keystore sealing", log.detail.contains("Android Keystore"))
    }

    @Test
    fun testVoiceSosActivationLogsCriticalEvent() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val service = AuditLoggerService(context, fakeDao, CoroutineScope(testDispatcher), testDispatcher)

        service.logVoiceSosActivation(
            verifiedPhrase = "Raksha360, help me",
            speakerScore = 0.92f,
            threshold = 0.78f,
            locationSector = "Sadar, Nagpur"
        )
        testScheduler.advanceUntilIdle()

        assertEquals(1, insertedLogs.size)
        val log = insertedLogs.first()
        assertEquals("VOICE_SOS_ACTIVATED", log.action)
        assertEquals("EMERGENCY_DISPATCH", log.category)
        assertEquals("CRITICAL", log.severity)
        assertTrue(log.detail.contains("Raksha360, help me"))
        assertTrue(log.detail.contains("Sadar, Nagpur"))
    }

    @Test
    fun testGuardModeStatusChangeLogging() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val service = AuditLoggerService(context, fakeDao, CoroutineScope(testDispatcher), testDispatcher)

        service.logGuardModeChanged(enabled = true, triggerPhrase = "Raksha360, help me")
        testScheduler.advanceUntilIdle()

        assertEquals(1, insertedLogs.size)
        val log = insertedLogs.first()
        assertEquals("GUARD_MODE_ENABLED", log.action)
        assertEquals("SYSTEM_STATE", log.category)
        assertTrue(log.detail.contains("Foreground mic listener started"))
    }

    @Test
    fun testSanitizationMasksPhoneNumbersAndBearerTokens() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val service = AuditLoggerService(context, fakeDao, CoroutineScope(testDispatcher), testDispatcher)

        service.logSecurityEvent(
            action = "EXTERNAL_DISPATCH",
            category = "EMERGENCY_DISPATCH",
            details = "Dispatching to contact +91 9876543210 using Bearer secret_jwt_token_9918231",
            severity = "ALERT"
        )
        testScheduler.advanceUntilIdle()

        assertEquals(1, insertedLogs.size)
        val log = insertedLogs.first()
        assertFalse("Raw token must be redacted", log.detail.contains("secret_jwt_token_9918231"))
        assertTrue("Redacted token marker must appear", log.detail.contains("Bearer [REDACTED_TOKEN]"))
        assertFalse("Full phone number must not appear in clear", log.detail.contains("9876543210"))
    }
}
