package com.example.voice.model

import android.content.Context
import com.example.voice.audio.AudioFeatureExtractor
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.SpeakerModel
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.sqrt

data class AudioSampleQuality(
    val isAcceptable: Boolean,
    val snrDb: Float,
    val rmsLevel: Float,
    val guidanceMessage: String
)

data class PhraseDetectionResult(
    val phraseMatched: Boolean,
    val phraseDetected: String,
    val confidence: Float,
    val isTfliteInference: Boolean,
    val modelLabel: String
)

data class SpeakerVerificationResult(
    val isSpeakerVerified: Boolean,
    val similarityScore: Float,
    val thresholdUsed: Float,
    val isTfliteInference: Boolean,
    val modelVersion: String
)

data class VoicePipelineResult(
    val triggerConfirmed: Boolean,
    val phraseResult: PhraseDetectionResult,
    val speakerResult: SpeakerVerificationResult,
    val timestamp: Long = System.currentTimeMillis()
)

data class VoiceSampleAnalysis(
    val phraseResult: PhraseDetectionResult,
    val speakerResult: SpeakerVerificationResult
)

interface IVoiceModelEngine {
    val modelStatusLabel: String
    val isProductionModelLoaded: Boolean
    val wakePhraseAssetPath: String
    val speakerVerifierAssetPath: String

    /** Default keeps test doubles source-compatible; the real engine overrides this. */
    val isPhraseEngineReady: Boolean
        get() = isProductionModelLoaded

    fun preparePhraseEngine(): Boolean = isPhraseEngineReady
    fun resetPhraseRecognizer() {}
    fun checkAudioQuality(buffer: ShortArray): AudioSampleQuality
    fun extractSpeakerEmbedding(samples: List<ShortArray>): FloatArray
    fun detectEmergencyPhrase(buffer: ShortArray, targetPhrase: String): PhraseDetectionResult
    fun verifySpeaker(buffer: ShortArray, enrolledEmbedding: FloatArray, threshold: Float): SpeakerVerificationResult
}

/**
 * Offline voice engine backed by Vosk ASR and Vosk speaker embeddings.
 * Model files must be prepared using scripts/prepare-voice-model.ps1; there is deliberately
 * no sound-energy fallback because loud noise must never be treated as recognized speech.
 *
 * Vosk provides the speech transcript and speaker vector locally. The similarity threshold is
 * a configurable application threshold, not a calibrated biometric operating point; validate
 * it with real positive/impostor samples before any safety-critical release.
 */
class VoiceModelEngine(private val context: Context) : IVoiceModelEngine {

    companion object {
        const val ASR_MODEL_DIRECTORY = "vosk-model-small-en-us-0.15"
        const val SPEAKER_MODEL_DIRECTORY = "vosk-model-spk-0.4"
        const val DEFAULT_EMERGENCY_PHRASE = "Raksha 360, help me"
        private const val SAMPLE_RATE = 16000f
        private const val MIN_SPEECH_SAMPLE_COUNT = 8000
    }

    override val wakePhraseAssetPath: String = ASR_MODEL_DIRECTORY
    override val speakerVerifierAssetPath: String = SPEAKER_MODEL_DIRECTORY

    private val lock = Any()
    private var speechModel: Model? = null
    private var speakerModel: SpeakerModel? = null
    private var streamRecognizer: Recognizer? = null
    private var lastLoadError: String? = null

    override val isPhraseEngineReady: Boolean
        get() = synchronized(lock) { speechModel != null && speakerModel != null }

    // True means both pretrained runtime models are loaded, not that field accuracy is certified.
    override val isProductionModelLoaded: Boolean
        get() = isPhraseEngineReady

    override val modelStatusLabel: String
        get() = when {
            isPhraseEngineReady -> "Offline Vosk speech + speaker models loaded; threshold calibration and phone testing still required."
            !assetsContainModels() -> "Voice SOS models are missing. Run scripts/prepare-voice-model.ps1, then rebuild the app."
            lastLoadError != null -> "Voice SOS model load failed: ${lastLoadError}. Restart after checking the model assets."
            else -> "Offline voice models are bundled and will initialize when Voice SOS is enabled."
        }

    /** Load/copy large models on an IO thread before Guard Mode starts reading the microphone. */
    override fun preparePhraseEngine(): Boolean = synchronized(lock) {
        if (speechModel != null && speakerModel != null) return@synchronized true
        try {
            val speechPath = ensureModelOnDisk(ASR_MODEL_DIRECTORY, listOf("am/final.mdl", "conf/model.conf", "graph/HCLr.fst"))
            val speakerPath = ensureModelOnDisk(SPEAKER_MODEL_DIRECTORY, listOf("final.ext.raw", "mfcc.conf", "mean.vec", "transform.mat"))
            val loadedSpeech = Model(speechPath.absolutePath)
            val loadedSpeaker = try {
                SpeakerModel(speakerPath.absolutePath)
            } catch (error: Exception) {
                loadedSpeech.close()
                throw error
            }
            speechModel = loadedSpeech
            speakerModel = loadedSpeaker
            lastLoadError = null
            true
        } catch (error: Exception) {
            lastLoadError = error.message?.take(180) ?: error.javaClass.simpleName
            streamRecognizer?.close()
            streamRecognizer = null
            speechModel?.close()
            speakerModel?.close()
            speechModel = null
            speakerModel = null
            false
        }
    }

    private fun assetsContainModels(): Boolean {
        return try {
            val speech = context.assets.list(ASR_MODEL_DIRECTORY)?.isNotEmpty() == true
            val speaker = context.assets.list(SPEAKER_MODEL_DIRECTORY)?.isNotEmpty() == true
            speech && speaker
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureModelOnDisk(directoryName: String, requiredRelativeFiles: List<String>): File {
        val destination = File(context.filesDir, directoryName)
        if (requiredRelativeFiles.all { File(destination, it).isFile }) return destination

        val assetChildren = context.assets.list(directoryName)
        if (assetChildren.isNullOrEmpty()) {
            throw IOException("Asset folder '$directoryName' missing. Run scripts/prepare-voice-model.ps1 and rebuild.")
        }
        if (destination.exists()) destination.deleteRecursively()
        destination.mkdirs()
        copyAssetTree(directoryName, destination)
        val missing = requiredRelativeFiles.firstOrNull { !File(destination, it).isFile }
        if (missing != null) {
            destination.deleteRecursively()
            throw IOException("Model '$directoryName' is incomplete (missing $missing). Re-run the model setup script.")
        }
        return destination
    }

    private fun copyAssetTree(assetPath: String, destination: File) {
        val children = context.assets.list(assetPath)
        if (children.isNullOrEmpty()) {
            destination.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            return
        }
        destination.mkdirs()
        children.forEach { child -> copyAssetTree("$assetPath/$child", File(destination, child)) }
    }

    override fun resetPhraseRecognizer() = synchronized(lock) {
        streamRecognizer?.close()
        streamRecognizer = null
    }

    override fun checkAudioQuality(buffer: ShortArray): AudioSampleQuality {
        val metrics = AudioFeatureExtractor.evaluateQuality(buffer)
        return AudioSampleQuality(metrics.isAcceptable, metrics.snrDb, metrics.rms, metrics.feedback)
    }

    /**
     * Creates an averaged, L2-normalized Vosk speaker embedding from actual captured samples.
     * Returns an empty vector if a model is absent or the sample is too short/low quality.
     */
    override fun extractSpeakerEmbedding(samples: List<ShortArray>): FloatArray {
        if (samples.isEmpty() || !preparePhraseEngine()) return FloatArray(0)
        val vectors = samples.mapNotNull { sample -> extractSpeakerVector(sample) }
        if (vectors.isEmpty()) return FloatArray(0)
        val dimension = vectors.first().size
        if (dimension == 0 || vectors.any { it.size != dimension }) return FloatArray(0)
        val mean = FloatArray(dimension)
        vectors.forEach { vector -> vector.forEachIndexed { index, value -> mean[index] += value } }
        mean.indices.forEach { mean[it] /= vectors.size.toFloat() }
        return normalize(mean)
    }

    override fun detectEmergencyPhrase(buffer: ShortArray, targetPhrase: String): PhraseDetectionResult {
        if (buffer.isEmpty() || !preparePhraseEngine()) {
            return PhraseDetectionResult(false, "", 0f, false, modelStatusLabel)
        }
        return synchronized(lock) {
            try {
                val recognizer = streamRecognizer ?: Recognizer(requireNotNull(speechModel), SAMPLE_RATE).also {
                    it.setWords(true)
                    streamRecognizer = it
                }
                // Feed every chunk, including quiet chunks; Vosk needs the silence to finalize an utterance.
                val completeUtterance = recognizer.acceptWaveForm(buffer, buffer.size)
                if (!completeUtterance) {
                    return@synchronized PhraseDetectionResult(false, "", 0f, false, "Vosk offline ASR · listening")
                }
                val resultJson = recognizer.result
                val parsed = JSONObject(resultJson)
                val transcript = parsed.optString("text", "")
                val matched = phraseMatches(transcript, targetPhrase)
                if (!matched) {
                    PhraseDetectionResult(false, transcript, 0f, false, "Vosk offline ASR · phrase not matched")
                } else {
                    PhraseDetectionResult(true, transcript, wordConfidence(parsed), false, "Vosk offline ASR · exact phrase variant matched")
                }
            } catch (error: Exception) {
                streamRecognizer?.close()
                streamRecognizer = null
                PhraseDetectionResult(false, "", 0f, false, "Vosk ASR error: ${error.message?.take(100) ?: error.javaClass.simpleName}")
            }
        }
    }

    /** Single-clip analysis for the in-app test button; never dispatches an emergency. */
    fun analyzeSingleSample(buffer: ShortArray, targetPhrase: String, enrolledEmbedding: FloatArray, threshold: Float): VoiceSampleAnalysis {
        if (buffer.size < MIN_SPEECH_SAMPLE_COUNT || !preparePhraseEngine()) {
            return VoiceSampleAnalysis(
                PhraseDetectionResult(false, "", 0f, false, modelStatusLabel),
                SpeakerVerificationResult(false, 0f, threshold, false, modelStatusLabel)
            )
        }
        val finalTranscript = recognizeFinalTranscript(buffer)
        val parsed = try { JSONObject(finalTranscript.second) } catch (_: Exception) { JSONObject() }
        val phraseMatched = phraseMatches(finalTranscript.first, targetPhrase)
        val phrase = PhraseDetectionResult(
            phraseMatched,
            if (phraseMatched) finalTranscript.first else "",
            if (phraseMatched) wordConfidence(parsed) else 0f,
            false,
            "Vosk offline single-clip analysis"
        )
        val speaker = verifySpeaker(buffer, enrolledEmbedding, threshold)
        return VoiceSampleAnalysis(phrase, speaker)
    }

    override fun verifySpeaker(buffer: ShortArray, enrolledEmbedding: FloatArray, threshold: Float): SpeakerVerificationResult {
        if (enrolledEmbedding.isEmpty() || buffer.size < MIN_SPEECH_SAMPLE_COUNT || !preparePhraseEngine()) {
            return SpeakerVerificationResult(false, 0f, threshold, false, "Vosk speaker model unavailable or sample too short")
        }
        val currentEmbedding = extractSpeakerVector(buffer)
        if (currentEmbedding.isEmpty() || currentEmbedding.size != enrolledEmbedding.size) {
            return SpeakerVerificationResult(false, 0f, threshold, false, "Speaker embedding unavailable or incompatible; re-enrol the voice")
        }
        val similarity = AudioFeatureExtractor.cosineSimilarity(normalize(currentEmbedding), normalize(enrolledEmbedding))
        return SpeakerVerificationResult(similarity >= threshold, similarity, threshold, false, "Vosk speaker embedding · threshold not statistically calibrated")
    }

    private fun extractSpeakerVector(buffer: ShortArray): FloatArray {
        if (buffer.size < MIN_SPEECH_SAMPLE_COUNT || !preparePhraseEngine()) return FloatArray(0)
        val recognizer = try { Recognizer(requireNotNull(speechModel), SAMPLE_RATE, requireNotNull(speakerModel)) } catch (_: Exception) { return FloatArray(0) }
        return try {
            recognizer.setWords(true)
            val endpointReached = recognizer.acceptWaveForm(buffer, buffer.size)
            val resultJson = if (endpointReached) recognizer.result else recognizer.finalResult
            val result = JSONObject(resultJson)
            val values = result.optJSONArray("spk") ?: return FloatArray(0)
            if (values.length() == 0) return FloatArray(0)
            FloatArray(values.length()) { index -> values.optDouble(index, 0.0).toFloat() }
        } catch (_: Exception) {
            FloatArray(0)
        } finally {
            recognizer.close()
        }
    }

    private fun recognizeFinalTranscript(buffer: ShortArray): Pair<String, String> {
        val recognizer = Recognizer(requireNotNull(speechModel), SAMPLE_RATE)
        return try {
            recognizer.setWords(true)
            val endpointReached = recognizer.acceptWaveForm(buffer, buffer.size)
            val finalJson = if (endpointReached) recognizer.result else recognizer.finalResult
            Pair(JSONObject(finalJson).optString("text", ""), finalJson)
        } finally {
            recognizer.close()
        }
    }

    private fun wordConfidence(result: JSONObject): Float {
        val words = result.optJSONArray("result") ?: return 0f
        if (words.length() == 0) return 0f
        var sum = 0f
        var count = 0
        for (i in 0 until words.length()) {
            val word = words.optJSONObject(i) ?: continue
            if (word.has("conf")) {
                sum += word.optDouble("conf", 0.0).toFloat()
                count++
            }
        }
        return if (count == 0) 0f else (sum / count).coerceIn(0f, 1f)
    }

    private fun phraseMatches(transcript: String, targetPhrase: String): Boolean {
        val heard = normalizeText(transcript)
        if (heard.isBlank()) return false
        return phraseVariants(targetPhrase).any { variant ->
            val expected = normalizeText(variant)
            expected.isNotBlank() && heard == expected
        }
    }

    private fun phraseVariants(phrase: String): List<String> {
        val variants = linkedSetOf(phrase)
        val normalized = normalizeText(phrase)
        variants += normalized.replace("360", "three sixty")
        variants += normalized.replace("360", "three six zero")
        variants += normalized.replace("three sixty", "360")
        if (normalized.contains("raksha")) {
            // Narrow ASR spelling variants for the same intended proper name. The entire
            // normalized utterance must still match and enrolled-speaker verification is mandatory.
            variants += normalized.replace("raksha", "rak sha")
            val commonAsrConfusion = normalized.replace("raksha", "russia")
            variants += commonAsrConfusion
            variants += commonAsrConfusion.replace("360", "three sixty")
            variants += commonAsrConfusion.replace("360", "three six zero")
        }
        if (normalized.contains("naari")) {
            variants += normalized.replace("naari", "nari")
            variants += normalized.replace("naari", "nary")
        }
        return variants.toList()
    }

    private fun normalizeText(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalize(vector: FloatArray): FloatArray {
        val norm = sqrt(vector.sumOf { it.toDouble() * it.toDouble() }).toFloat()
        if (norm <= 1e-8f) return FloatArray(0)
        return FloatArray(vector.size) { index -> vector[index] / norm }
    }
}
