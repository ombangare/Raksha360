package com.example.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import androidx.core.content.ContextCompat
import com.example.data.remote.BackendApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID

/**
 * ElevenLabsAssistantManager
 * Manages full duplex conversational voice interaction with the pre-configured ElevenLabs agent
 * ("Raksha360 AI Assistant", agent_3701m51qchkaenxsjnzr8jvrm66).
 *
 * Ensures:
 * 1. Zero credential leakage on the device (uses backend signed-URL tokens only).
 * 2. Explicit microphone coordination with the offline Vosk speech recognition pipeline.
 * 3. Allowlisted client tools only.
 * 4. Sensitive action safety gates (cannot trigger SOS or FIR without explicit user confirmation).
 */
class ElevenLabsAssistantManager(
    private val context: Context,
    private val backendClient: BackendApiClient,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        const val AGENT_ID = "agent_3701m51qchkaenxsjnzr8jvrm66"
        const val AGENT_NAME = "Raksha360 AI Assistant"
        private const val SAMPLE_RATE = 16000
    }

    enum class State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED_LISTENING,
        CONNECTED_SPEAKING,
        UNCONFIGURED,
        ERROR
    }

    enum class MessageSender {
        USER,
        ASSISTANT,
        SYSTEM
    }

    data class ConversationMessage(
        val id: String = UUID.randomUUID().toString(),
        val sender: MessageSender,
        val text: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _messages = MutableStateFlow<List<ConversationMessage>>(emptyList())
    val messages: StateFlow<List<ConversationMessage>> = _messages.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private var webSocket: WebSocket? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null

    // Callback when AI triggers a screen navigation
    var onNavigateToScreen: ((String) -> Unit)? = null

    // Callback when AI proposes a draft report requiring user confirmation
    var onDraftReportProposed: ((title: String, category: String, desc: String) -> Unit)? = null

    fun startConversation() {
        if (_state.value == State.CONNECTING ||
            _state.value == State.CONNECTED_LISTENING ||
            _state.value == State.CONNECTED_SPEAKING
        ) {
            return
        }

        // Verify microphone permission
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            _state.value = State.ERROR
            _errorMessage.value = "Microphone permission is required to talk with the safety assistant."
            return
        }

        _state.value = State.CONNECTING
        _errorMessage.value = null

        scope.launch {
            val sessionResult = backendClient.getAssistantSession()
            sessionResult.fold(
                onSuccess = { session ->
                    if (!session.configured || session.signedUrl.isNullOrBlank()) {
                        _state.value = State.UNCONFIGURED
                        val err = session.error ?: session.message ?: "Safety assistant service is currently unavailable."
                        _errorMessage.value = err
                        appendMessage(MessageSender.SYSTEM, err)
                    } else {
                        connectWebSocket(session.signedUrl)
                    }
                },
                onFailure = { err ->
                    _state.value = State.ERROR
                    _errorMessage.value = "Failed to obtain temporary conversation token: ${err.message}"
                }
            )
        }
    }

    private fun connectWebSocket(signedUrl: String) {
        // Coordinate microphone with Vosk
        val granted = MicrophoneCoordinator.requestMicForAssistant(context)
        if (!granted) {
            _state.value = State.ERROR
            _errorMessage.value = "Microphone is currently locked for an active emergency SOS."
            return
        }

        val request = Request.Builder()
            .url(signedUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                _state.value = State.CONNECTED_LISTENING
                initAudioTrack()
                startRecordingLoop(ws)
                appendMessage(MessageSender.SYSTEM, "Connected to Raksha360 AI Assistant. Speak freely.")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(ws, text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                stopConversationInternal()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _state.value = State.ERROR
                _errorMessage.value = "Connection lost: ${t.message}"
                appendMessage(MessageSender.SYSTEM, "Assistant disconnected: ${t.message}")
                stopConversationInternal()
            }
        })
    }

    private fun handleIncomingMessage(ws: WebSocket, text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type")

            when (type) {
                "audio" -> {
                    val audioBase64 = json.optJSONObject("audio_event")?.optString("audio_base_64")
                    if (!audioBase64.isNullOrBlank()) {
                        playAudioChunk(audioBase64)
                    }
                }
                "agent_response" -> {
                    val responseText = json.optJSONObject("agent_response_event")?.optString("agent_response")
                    if (!responseText.isNullOrBlank()) {
                        appendMessage(MessageSender.ASSISTANT, responseText)
                    }
                }
                "user_transcript" -> {
                    val transcript = json.optJSONObject("user_transcription_event")?.optString("user_transcript")
                    if (!transcript.isNullOrBlank()) {
                        appendMessage(MessageSender.USER, transcript)
                    }
                }
                "interruption" -> {
                    // User interrupted; stop playing agent voice immediately
                    audioTrack?.pause()
                    audioTrack?.flush()
                    _state.value = State.CONNECTED_LISTENING
                }
                "client_tool_call" -> {
                    handleClientToolCall(ws, json.optJSONObject("client_tool_call"))
                }
            }
        } catch (_: Exception) {
            // Non-fatal parse errors
        }
    }

    private fun handleClientToolCall(ws: WebSocket, callObj: JSONObject?) {
        if (callObj == null) return
        val toolName = callObj.optString("tool_name")
        val callId = callObj.optString("tool_call_id")
        val params = callObj.optJSONObject("parameters") ?: JSONObject()

        val (resultStr, isError) = executeAllowlistedTool(toolName, params)

        val response = JSONObject().apply {
            put("type", "client_tool_result")
            put("tool_call_id", callId)
            put("result", resultStr)
            put("is_error", isError)
        }
        ws.send(response.toString())
    }

    private fun executeAllowlistedTool(toolName: String, params: JSONObject): Pair<String, Boolean> {
        return when (toolName) {
            "navigate_to_screen" -> {
                val screen = params.optString("screen", "HOME").uppercase()
                scope.launch(Dispatchers.Main) {
                    onNavigateToScreen?.invoke(screen)
                }
                Pair("{\"status\":\"NAVIGATED\",\"screen\":\"$screen\"}", false)
            }
            "check_location_permission" -> {
                val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                Pair("{\"locationPermissionGranted\":$hasFine}", false)
            }
            "search_police_stations" -> {
                Pair("{\"query\":\"Nagpur public police directory\",\"stations\":[{\"name\":\"Sadar Police Station\",\"phone\":\"0712-2560341\"},{\"name\":\"Sitabuldi Police Station\",\"phone\":\"0712-2561222\"}]}", false)
            }
            "explain_feature" -> {
                val feature = params.optString("feature").lowercase()
                val explanation = when {
                    feature.contains("voice") -> "Voice SOS uses offline on-device Vosk recognition to listen for the emergency trigger phrase without cloud audio upload."
                    feature.contains("contact") -> "Up to two emergency contacts can be configured. When verified, they receive automated SOS alert emails with your real location and a secure 2-hour live tracking link."
                    feature.contains("journey") -> "Safe Journey tracks your movement along a route and escalates if you miss check-ins or fail to arrive within the expected duration."
                    feature.contains("child") -> "Child & Family Safety monitors geofences and school handovers with one-time verification PINs."
                    else -> "Raksha360 provides proactive offline and connected personal safety for women and children in India."
                }
                Pair("{\"feature\":\"$feature\",\"explanation\":\"$explanation\"}", false)
            }
            "prepare_incident_draft" -> {
                val title = params.optString("title", "Safety Incident")
                val category = params.optString("category", "Harassment")
                val desc = params.optString("description", "")
                scope.launch(Dispatchers.Main) {
                    onDraftReportProposed?.invoke(title, category, desc)
                }
                Pair("{\"status\":\"DRAFT_PREPARED\",\"confirmationRequired\":true,\"message\":\"Report drafted. The user must review and confirm before submitting.\"}", false)
            }
            "check_sos_email_status" -> {
                Pair("{\"maxContacts\":2,\"note\":\"Automated email alerts send location to up to two verified emergency contacts upon confirmed SOS.\"}", false)
            }
            else -> {
                Pair("{\"error\":\"Tool $toolName is not registered in the Raksha360 allowlist.\"}", true)
            }
        }
    }

    private fun startRecordingLoop(ws: WebSocket) {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minBuf, 4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _state.value = State.ERROR
                _errorMessage.value = "Failed to initialize microphone for conversational assistant."
                return
            }
            audioRecord?.startRecording()
        } catch (e: Exception) {
            _state.value = State.ERROR
            _errorMessage.value = "Microphone error: ${e.message}"
            return
        }

        recordingJob = scope.launch {
            val buffer = ByteArray(2048) // 64ms chunk
            while (isActive && _state.value != State.DISCONNECTED) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0 && !_isMuted.value) {
                    var sum = 0.0
                    var i = 0
                    while (i < read - 1) {
                        val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                        sum += (sample.toDouble() * sample.toDouble())
                        i += 2
                    }
                    val rms = (kotlin.math.sqrt(sum / (read / 2.0)) / 16384.0).toFloat().coerceIn(0f, 1f)
                    _audioLevel.value = _audioLevel.value * 0.6f + rms * 0.4f

                    val base64Chunk = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                    val chunkJson = JSONObject().apply {
                        put("user_audio_chunk", base64Chunk)
                    }
                    ws.send(chunkJson.toString())
                }
            }
        }
    }

    private fun initAudioTrack() {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
    }

    private fun playAudioChunk(base64: String) {
        try {
            val pcmData = Base64.decode(base64, Base64.DEFAULT)
            var sum = 0.0
            var i = 0
            while (i < pcmData.size - 1) {
                val sample = (pcmData[i].toInt() and 0xFF) or (pcmData[i + 1].toInt() shl 8)
                sum += (sample.toDouble() * sample.toDouble())
                i += 2
            }
            val rms = (kotlin.math.sqrt(sum / (pcmData.size / 2.0)) / 16384.0).toFloat().coerceIn(0f, 1f)
            _audioLevel.value = _audioLevel.value * 0.4f + (rms * 1.5f).coerceIn(0f, 1f) * 0.6f

            audioTrack?.write(pcmData, 0, pcmData.size)
            _state.value = State.CONNECTED_SPEAKING
        } catch (_: Exception) {}
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun stopSpeaking() {
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.play()
        _state.value = State.CONNECTED_LISTENING
    }

    fun stopConversation() {
        stopConversationInternal()
        appendMessage(MessageSender.SYSTEM, "Conversation ended.")
    }

    private fun stopConversationInternal() {
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null

        try {
            webSocket?.close(1000, "User ended conversation")
        } catch (_: Exception) {}
        webSocket = null

        MicrophoneCoordinator.releaseMicFromAssistant(context)
        _audioLevel.value = 0f
        _state.value = State.DISCONNECTED
    }

    private fun appendMessage(sender: MessageSender, text: String) {
        _messages.value = _messages.value + ConversationMessage(sender = sender, text = text)
    }
}
