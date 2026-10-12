package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.security.KeystoreCryptoManager
import com.example.voice.VoicePipeline
import com.example.voice.audio.AudioFeatureExtractor
import com.example.voice.model.AudioSampleQuality
import com.example.voice.model.IVoiceModelEngine
import com.example.voice.model.PhraseDetectionResult
import com.example.voice.model.SpeakerVerificationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun testAppNameStringResource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Raksha360", appName)
    }

    @Test
    fun testAudioFeatureExtractorAndCosineSimilarity() {
        // Enrolled user embedding
        val embUser = FloatArray(32) { (it + 1).toFloat() }
        // Normalizing
        val norm = kotlin.math.sqrt(embUser.map { it * it }.sum().toDouble()).toFloat()
        for (i in embUser.indices) embUser[i] /= norm

        // Same user slightly perturbed
        val embSame = embUser.clone()
        embSame[0] += 0.01f

        val simSame = AudioFeatureExtractor.cosineSimilarity(embUser, embSame)
        assertTrue("Same speaker embedding similarity should exceed 95%", simSame > 0.95f)

        // Impostor / Wrong speaker embedding
        val embImpostor = FloatArray(32) { (32 - it).toFloat() }
        val impNorm = kotlin.math.sqrt(embImpostor.map { it * it }.sum().toDouble()).toFloat()
        for (i in embImpostor.indices) embImpostor[i] /= impNorm

        val simImpostor = AudioFeatureExtractor.cosineSimilarity(embUser, embImpostor)
        assertTrue("Impostor speaker similarity should be lower than threshold", simImpostor < 0.78f)
    }

    @Test
    fun testVoicePipelineDeterministicTriggers() {
        val enrolledPhrase = "Raksha 360, help me"
        val enrolledEmbedding = FloatArray(32) { 0.5f }

        // Test Mock Engine
        val mockEngine = object : IVoiceModelEngine {
            override val modelStatusLabel = "Mock Test Engine"
            override val isProductionModelLoaded = false
            override val wakePhraseAssetPath = "mock/wake"
            override val speakerVerifierAssetPath = "mock/verifier"

            override fun checkAudioQuality(buffer: ShortArray) = AudioSampleQuality(true, 24f, 1500f, "Good")
            override fun extractSpeakerEmbedding(samples: List<ShortArray>) = enrolledEmbedding

            override fun detectEmergencyPhrase(buffer: ShortArray, targetPhrase: String): PhraseDetectionResult {
                val isTarget = targetPhrase == enrolledPhrase && buffer[0] > 100
                return PhraseDetectionResult(isTarget, if (isTarget) targetPhrase else "", if (isTarget) 0.92f else 0.1f, false, "Mock")
            }

            override fun verifySpeaker(buffer: ShortArray, enrolledEmbedding: FloatArray, threshold: Float): SpeakerVerificationResult {
                val score = if (buffer[1] > 100) 0.88f else 0.45f
                return SpeakerVerificationResult(score >= threshold, score, threshold, false, "Mock")
            }
        }

        val pipeline = VoicePipeline(mockEngine) {
            VoicePipeline.EnrolledProfileData(
                targetPhrase = enrolledPhrase,
                speakerEmbedding = enrolledEmbedding,
                verificationThreshold = 0.78f,
                isEnrolled = true
            )
        }

        // Scenario 1: Correct Phrase + Enrolled Speaker
        val validAudio = ShortArray(1024) { 500.toShort() }
        val res1 = pipeline.processAudioWindow(validAudio)
        assertTrue("Valid phrase + enrolled speaker must trigger Voice SOS", res1.triggerConfirmed)

        // Scenario 2: Repeated accidental trigger within cooldown (15s)
        val resCooldown = pipeline.processAudioWindow(validAudio)
        assertFalse("Voice SOS must reject rapid fire triggers within cooldown period", resCooldown.triggerConfirmed)

        // Scenario 3: Wrong Speaker (buffer[1] low)
        val pipeline2 = VoicePipeline(mockEngine) {
            VoicePipeline.EnrolledProfileData(
                targetPhrase = enrolledPhrase,
                speakerEmbedding = enrolledEmbedding,
                verificationThreshold = 0.78f,
                isEnrolled = true
            )
        }
        val wrongSpeakerAudio = ShortArray(1024) { 0.toShort() }.apply {
            this[0] = 500 // phrase detected
            this[1] = 10  // speaker failed
        }
        val res2 = pipeline2.processAudioWindow(wrongSpeakerAudio)
        assertFalse("Wrong speaker must be rejected", res2.triggerConfirmed)

        // Scenario 4: Wrong Phrase
        val wrongPhraseAudio = ShortArray(1024) { 0.toShort() }.apply {
            this[0] = 10  // phrase failed
            this[1] = 500 // speaker high
        }
        val res3 = pipeline2.processAudioWindow(wrongPhraseAudio)
        assertFalse("Wrong phrase must be rejected", res3.triggerConfirmed)

        // Scenario 5: Silent / Low energy audio (below VAD threshold)
        val silentAudio = ShortArray(1024) { 5.toShort() }
        val resSilent = pipeline2.processAudioWindow(silentAudio)
        assertFalse("Audio below VAD energy threshold must be safely ignored to save battery", resSilent.triggerConfirmed)
    }

    @Test
    fun testKeystoreCryptoManagerEncryption() {
        val crypto = KeystoreCryptoManager()
        val plain = "Raksha360_SecureEmergencyPayload_7x"
        val encrypted = crypto.encrypt(plain)
        assertNotNull(encrypted)
        val decrypted = crypto.decrypt(encrypted)
        assertEquals(plain, decrypted)
    }

    @Test
    fun testSha256IntegrityHashCalculation() {
        val crypto = KeystoreCryptoManager()
        val sampleData = "Incident_Evidence_Waveform_TamperProof"
        val hash1 = crypto.computeSha256(sampleData)
        val hash2 = crypto.computeSha256(sampleData)
        assertEquals("SHA-256 must be deterministic", hash1, hash2)
        assertEquals(64, hash1.length) // 256 bits = 64 hex characters
    }
}
