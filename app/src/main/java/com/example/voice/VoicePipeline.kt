package com.example.voice

import com.example.voice.audio.AudioFeatureExtractor
import com.example.voice.model.IVoiceModelEngine
import com.example.voice.model.PhraseDetectionResult
import com.example.voice.model.SpeakerVerificationResult
import com.example.voice.model.VoicePipelineResult

/**
 * Continuous offline pipeline. Each PCM chunk, including silence, is delivered to the ASR engine
 * so it can close an utterance. The recent audio ring is used only after a phrase is recognized;
 * this gives speaker verification the complete phrase instead of a single ~250 ms microphone chunk.
 */
class VoicePipeline(
    private val modelEngine: IVoiceModelEngine,
    private val getEnrolledProfile: () -> EnrolledProfileData?
) {
    data class EnrolledProfileData(
        val targetPhrase: String,
        val speakerEmbedding: FloatArray,
        val verificationThreshold: Float = 0.72f,
        val isEnrolled: Boolean = true
    )

    private var lastTriggerTimestamp = 0L
    private val cooldownPeriodMs = 15000L
    private val maxRecentSamples = 16000 * 5
    private val recentAudio = ArrayDeque<Short>()

    @Synchronized
    fun reset() {
        recentAudio.clear()
        modelEngine.resetPhraseRecognizer()
        lastTriggerTimestamp = 0L
    }

    @Synchronized
    fun processAudioWindow(audioWindow: ShortArray): VoicePipelineResult {
        val now = System.currentTimeMillis()
        val profile = getEnrolledProfile()
        if (profile == null || !profile.isEnrolled || profile.speakerEmbedding.isEmpty() || audioWindow.isEmpty()) {
            return rejected(now, modelEngine.modelStatusLabel, profile?.verificationThreshold ?: 0.72f)
        }
        if (now - lastTriggerTimestamp < cooldownPeriodMs) {
            return rejected(now, "Voice SOS cooldown active", profile.verificationThreshold)
        }

        // Always feed quiet chunks to ASR: the recognizer relies on trailing silence to finalize text.
        audioWindow.forEach(recentAudio::addLast)
        while (recentAudio.size > maxRecentSamples) recentAudio.removeFirst()
        val phraseResult = modelEngine.detectEmergencyPhrase(audioWindow, profile.targetPhrase)
        if (!phraseResult.phraseMatched) {
            return VoicePipelineResult(
                false,
                phraseResult,
                SpeakerVerificationResult(false, 0f, profile.verificationThreshold, false, "Waiting for exact phrase"),
                now
            )
        }

        val utterance = recentAudio.toList().toShortArray()
        val speakerResult = modelEngine.verifySpeaker(utterance, profile.speakerEmbedding, profile.verificationThreshold)
        val confirmed = phraseResult.confidence >= 0.70f && speakerResult.isSpeakerVerified &&
            speakerResult.similarityScore >= profile.verificationThreshold

        android.util.Log.d(
            "VoicePipeline",
            "Voice SOS evaluation: phraseMatched=${phraseResult.phraseMatched}, phraseConf=${phraseResult.confidence}, spkScore=${speakerResult.similarityScore}, threshold=${profile.verificationThreshold}, spkVerified=${speakerResult.isSpeakerVerified}, confirmed=$confirmed"
        )

        recentAudio.clear()
        modelEngine.resetPhraseRecognizer()
        if (confirmed) lastTriggerTimestamp = now

        return VoicePipelineResult(confirmed, phraseResult, speakerResult, now)
    }

    private fun rejected(now: Long, label: String, threshold: Float): VoicePipelineResult = VoicePipelineResult(
        false,
        PhraseDetectionResult(false, "", 0f, false, label),
        SpeakerVerificationResult(false, 0f, threshold, false, label),
        now
    )
}
