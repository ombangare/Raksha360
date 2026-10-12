package com.example.voice.audio

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-device signal-quality utilities used by the voice workflow.
 * The legacy 32-dimensional spectral vector is kept for compatibility with existing tests only;
 * it is NOT used by the Vosk speaker-verification path and is not a biometric speaker embedding.
 */
object AudioFeatureExtractor {

    const val SAMPLE_RATE = 16000
    const val EMBEDDING_DIM = 32

    /**
     * Calculates Voice Activity Detection (VAD) status from RMS energy.
     */
    fun computeRms(buffer: ShortArray): Float {
        if (buffer.isEmpty()) return 0f
        var sumSquares = 0.0
        for (sample in buffer) {
            sumSquares += sample.toDouble() * sample.toDouble()
        }
        return sqrt(sumSquares / buffer.size).toFloat()
    }

    /**
     * Computes Signal-to-Noise Ratio (SNR) in dB and background noise guidance.
     */
    fun evaluateQuality(buffer: ShortArray): QualityMetrics {
        val rms = computeRms(buffer)
        var maxPeak = 0
        var zeroCrossings = 0
        for (i in 0 until buffer.size - 1) {
            val s1 = buffer[i].toInt()
            val s2 = buffer[i + 1].toInt()
            if (abs(s1) > maxPeak) maxPeak = abs(s1)
            if ((s1 >= 0 && s2 < 0) || (s1 < 0 && s2 >= 0)) {
                zeroCrossings++
            }
        }

        val zcr = zeroCrossings.toFloat() / max(1, buffer.size)
        val peakRatio = maxPeak.toFloat() / 32768f
        val snrDb = if (rms > 50f) 20f * log10(rms / 40f) else 0f

        val isTooQuiet = rms < 200f
        val isClipping = peakRatio > 0.96f
        val isNoisy = zcr > 0.35f && rms < 600f

        val acceptable = !isTooQuiet && !isClipping && rms >= 250f

        val feedback = when {
            isClipping -> "Audio too loud or distorted. Please speak at a moderate distance."
            isTooQuiet -> "Too quiet. Please speak closer to the microphone."
            isNoisy -> "High background noise detected. Move to a calmer area."
            else -> "Voice sample quality is excellent."
        }

        return QualityMetrics(
            rms = rms,
            snrDb = snrDb,
            zeroCrossingRate = zcr,
            isAcceptable = acceptable,
            feedback = feedback
        )
    }

    data class QualityMetrics(
        val rms: Float,
        val snrDb: Float,
        val zeroCrossingRate: Float,
        val isAcceptable: Boolean,
        val feedback: String
    )

    /** Legacy 32-dimensional spectral descriptor. Do not use this for identity verification. */
    fun extractEmbedding(audio: ShortArray): FloatArray {
        val embedding = FloatArray(EMBEDDING_DIM)
        if (audio.size < 256) return embedding

        val frameSize = min(1024, audio.size)
        val numFrames = audio.size / frameSize
        if (numFrames == 0) return embedding

        val filterBanks = 16
        val accumulator = FloatArray(filterBanks * 2)

        for (f in 0 until numFrames) {
            val offset = f * frameSize
            // Apply Hamming window and calculate short-time spectral energy
            for (bank in 0 until filterBanks) {
                var realSum = 0.0
                var imagSum = 0.0
                val freqIndex = bank + 1
                for (n in 0 until frameSize) {
                    val sample = audio[offset + n].toDouble() / 32768.0
                    val hamming = 0.54 - 0.46 * cos(2.0 * Math.PI * n / (frameSize - 1))
                    val windowed = sample * hamming
                    val angle = 2.0 * Math.PI * freqIndex * n / frameSize
                    realSum += windowed * cos(angle)
                    imagSum -= windowed * sin(angle)
                }
                val energy = sqrt(realSum * realSum + imagSum * imagSum)
                accumulator[bank] += energy.toFloat()
            }
        }

        // Normalize accumulator across frames
        for (i in 0 until filterBanks) {
            accumulator[i] /= numFrames
        }

        // Project into 32-dimensional embedding
        for (i in 0 until filterBanks) {
            embedding[i] = accumulator[i]
            embedding[i + filterBanks] = if (i > 0) accumulator[i] - accumulator[i - 1] else accumulator[i]
        }

        // L2 normalization
        var normSum = 0.0
        for (v in embedding) normSum += v * v
        val norm = sqrt(normSum)
        if (norm > 1e-6) {
            for (i in embedding.indices) embedding[i] = (embedding[i] / norm).toFloat()
        }

        return embedding
    }

    /**
     * Computes cosine similarity between two normalized acoustic vectors (0.0 to 1.0).
     */
    fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        if (vec1.size != vec2.size || vec1.isEmpty()) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in vec1.indices) {
            dot += vec1[i].toDouble() * vec2[i].toDouble()
            normA += vec1[i].toDouble() * vec1[i].toDouble()
            normB += vec2[i].toDouble() * vec2[i].toDouble()
        }
        val denom = sqrt(normA) * sqrt(normB)
        if (denom < 1e-7) return 0f
        val sim = (dot / denom).toFloat()
        return max(0f, min(1f, sim))
    }
}
