package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.security.KeystoreCryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class VoiceProfileRepository(
    private val context: Context,
    private val cryptoManager: KeystoreCryptoManager
) {
    companion object {
        private const val PREFS_NAME = "naari_kavach_voice_secure"
        private const val KEY_ENCRYPTED_PROFILE = "encrypted_voice_profile"
        const val DEFAULT_PHRASE = "Raksha 360, help me"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class VoiceProfile(
        val isEnrolled: Boolean,
        val phrase: String,
        val embedding: FloatArray,
        val threshold: Float,
        val enrolledAt: Long,
        val sampleCount: Int
    )

    private val _currentProfile = MutableStateFlow<VoiceProfile?>(null)
    val currentProfile: StateFlow<VoiceProfile?> = _currentProfile.asStateFlow()

    init {
        loadProfileFromEncryptedStorage()
    }

    fun loadProfileFromEncryptedStorage() {
        val encryptedData = prefs.getString(KEY_ENCRYPTED_PROFILE, null)
        if (encryptedData.isNullOrBlank()) {
            _currentProfile.value = null
            return
        }

        val decrypted = cryptoManager.decrypt(encryptedData)
        if (decrypted.isNullOrBlank()) {
            _currentProfile.value = null
            return
        }

        try {
            val json = JSONObject(decrypted)
            val phrase = json.optString("phrase", DEFAULT_PHRASE)
            val threshold = json.optDouble("threshold", 0.82).toFloat()
            val enrolledAt = json.optLong("enrolledAt", 0L)
            val sampleCount = json.optInt("sampleCount", 3)
            val embArray = json.optJSONArray("embedding") ?: JSONArray()
            val embedding = FloatArray(embArray.length())
            for (i in 0 until embArray.length()) {
                embedding[i] = embArray.getDouble(i).toFloat()
            }

            _currentProfile.value = VoiceProfile(
                isEnrolled = true,
                phrase = phrase,
                embedding = embedding,
                threshold = threshold,
                enrolledAt = enrolledAt,
                sampleCount = sampleCount
            )
        } catch (_: Exception) {
            _currentProfile.value = null
        }
    }

    fun saveVoiceProfile(
        phrase: String,
        embedding: FloatArray,
        sampleCount: Int,
        threshold: Float = 0.82f
    ): Boolean {
        return try {
            val json = JSONObject().apply {
                put("phrase", phrase)
                put("threshold", threshold.toDouble())
                put("enrolledAt", System.currentTimeMillis())
                put("sampleCount", sampleCount)
                val embArray = JSONArray()
                for (v in embedding) {
                    embArray.put(v.toDouble())
                }
                put("embedding", embArray)
            }

            val encrypted = cryptoManager.encrypt(json.toString())
            val saved = prefs.edit().putString(KEY_ENCRYPTED_PROFILE, encrypted).commit()
            if (saved) {
                _currentProfile.value = VoiceProfile(
                    isEnrolled = true,
                    phrase = phrase,
                    embedding = embedding,
                    threshold = threshold,
                    enrolledAt = System.currentTimeMillis(),
                    sampleCount = sampleCount
                )
            }
            saved
        } catch (_: Exception) {
            false
        }
    }

    fun updateThreshold(newThreshold: Float) {
        val current = _currentProfile.value ?: return
        saveVoiceProfile(current.phrase, current.embedding, current.sampleCount, newThreshold)
    }

    fun updatePhrase(newPhrase: String) {
        val current = _currentProfile.value ?: return
        saveVoiceProfile(newPhrase, current.embedding, current.sampleCount, current.threshold)
    }

    /**
     * Completely deletes voice profile and wipes any cached audio samples for strict user privacy.
     */
    fun deleteVoiceProfile() {
        prefs.edit().remove(KEY_ENCRYPTED_PROFILE).apply()
        _currentProfile.value = null

        // Delete any local sample files
        try {
            val voiceDir = File(context.filesDir, "voice_samples")
            if (voiceDir.exists()) {
                voiceDir.deleteRecursively()
            }
        } catch (_: Exception) {}
    }
}
