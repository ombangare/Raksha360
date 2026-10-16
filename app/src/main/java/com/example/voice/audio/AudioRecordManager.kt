package com.example.voice.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Captures short, local-only PCM samples for enrollment and explicit self-tests. */
class AudioRecordManager(private val context: Context) {
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    private val _liveRmsLevel = MutableStateFlow(0f)
    val liveRmsLevel: StateFlow<Float> = _liveRmsLevel.asStateFlow()
    private var recordJob: Job? = null
    @Volatile private var activeAudioRecord: AudioRecord? = null
    @Volatile private var stopRequested = false

    @SuppressLint("MissingPermission")
    fun startSampleRecording(scope: CoroutineScope, durationMillis: Long = 3000, onFinished: (ShortArray) -> Unit) {
        if (_isRecording.value) return
        stopRequested = false
        recordJob?.cancel()
        recordJob = scope.launch(Dispatchers.IO) {
            val captured = ArrayList<Short>()
            var recorder: AudioRecord? = null
            try {
                if (minBufferSize <= 0) throw IllegalStateException("This device does not support 16 kHz microphone capture.")
                val bufferSize = maxOf(minBufferSize, 2048)
                recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize * 2)
                if (recorder.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("Microphone recorder could not initialize.")
                activeAudioRecord = recorder
                recorder.startRecording()
                _isRecording.value = true
                val buffer = ShortArray(bufferSize)
                val start = System.currentTimeMillis()
                while (isActive && !stopRequested && System.currentTimeMillis() - start < durationMillis) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        for (index in 0 until read) captured.add(buffer[index])
                        _liveRmsLevel.value = AudioFeatureExtractor.computeRms(buffer.copyOf(read))
                    } else if (read < 0) {
                        throw IllegalStateException("Microphone read failed with code $read.")
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                // Empty sample is returned below so the UI can show a retry message.
            } catch (_: Exception) {
                // Empty sample is returned below so the UI can show a retry message.
            } finally {
                _isRecording.value = false
                _liveRmsLevel.value = 0f
                stopRequested = false
                try { if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() } catch (_: Exception) {}
                try { recorder?.release() } catch (_: Exception) {}
                activeAudioRecord = null
                withContext(Dispatchers.Main) { onFinished(captured.toShortArray()) }
            }
        }
    }

    /** Request the active recording loop to stop early and return whatever audio was captured so far. */
    fun stopRecordingEarly() {
        stopRequested = true
    }

    fun cancelRecording() {
        stopRequested = true
        recordJob?.cancel()
        try { activeAudioRecord?.stop() } catch (_: Exception) {}
        try { activeAudioRecord?.release() } catch (_: Exception) {}
        activeAudioRecord = null
        _isRecording.value = false
        _liveRmsLevel.value = 0f
    }
}
