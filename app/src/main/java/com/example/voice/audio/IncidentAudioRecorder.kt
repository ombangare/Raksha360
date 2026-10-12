package com.example.voice.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.data.security.KeystoreCryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.UUID

/**
 * Handles automatic audio recording during an emergency incident.
 * Produces a standard 16kHz Mono WAV file in app internal storage,
 * and calculates SHA-256 for cryptographic integrity in the Evidence Vault.
 */
class IncidentAudioRecorder(
    private val context: Context,
    private val cryptoManager: KeystoreCryptoManager
) {
    private var isRecording = false
    private var recordingJob: Job? = null
    private var currentFile: File? = null
    private var startTimeMillis: Long = 0

    data class RecordingResult(
        val evidenceId: String,
        val filePath: String,
        val sha256Checksum: String,
        val durationSeconds: Int,
        val fileSizeBytes: Long
    )

    @SuppressLint("MissingPermission")
    fun startIncidentRecording(eventId: String, onStarted: () -> Unit = {}) {
        if (isRecording) return

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        if (bufferSize <= 0) return

        val evidenceDir = File(context.filesDir, "evidence").apply { mkdirs() }
        val fileName = "incident_${eventId.take(8)}_${System.currentTimeMillis()}.wav"
        val targetFile = File(evidenceDir, fileName)
        currentFile = targetFile
        startTimeMillis = System.currentTimeMillis()
        isRecording = true

        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            var audioRecord: AudioRecord? = null
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize * 2
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    isRecording = false
                    return@launch
                }

                audioRecord.startRecording()
                onStarted()

                FileOutputStream(targetFile).use { fos ->
                    // Write 44-byte placeholder header for WAV
                    fos.write(ByteArray(44))

                    val buffer = ShortArray(bufferSize / 2)
                    val byteBuffer = ByteArray(bufferSize)

                    while (isActive && isRecording) {
                        val read = audioRecord.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            var byteIdx = 0
                            for (i in 0 until read) {
                                val s = buffer[i]
                                byteBuffer[byteIdx++] = (s.toInt() and 0x00FF).toByte()
                                byteBuffer[byteIdx++] = ((s.toInt() shr 8) and 0x00FF).toByte()
                            }
                            fos.write(byteBuffer, 0, byteIdx)
                        }
                    }
                }

                // Finalize WAV Header
                finalizeWavHeader(targetFile, sampleRate, 1, 16)
            } catch (_: Exception) {
                // Ignore recording interrupt
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun stopIncidentRecording(): RecordingResult? = withContext(Dispatchers.IO) {
        if (!isRecording) return@withContext null
        isRecording = false
        recordingJob?.cancel()
        recordingJob?.join()

        val file = currentFile ?: return@withContext null
        if (!file.exists() || file.length() <= 44) return@withContext null

        val sha256 = cryptoManager.computeSha256(file)
        val durationSec = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()

        RecordingResult(
            evidenceId = UUID.randomUUID().toString(),
            filePath = file.absolutePath,
            sha256Checksum = sha256,
            durationSeconds = maxOf(1, durationSec),
            fileSizeBytes = file.length()
        )
    }

    private fun finalizeWavHeader(file: File, sampleRate: Int, channels: Int, bitsPerSample: Int) {
        try {
            RandomAccessFile(file, "rw").use { raf ->
                val totalAudioLen = file.length() - 44
                val totalDataLen = totalAudioLen + 36
                val byteRate = sampleRate * channels * bitsPerSample / 8

                raf.seek(0)
                raf.writeBytes("RIFF")
                raf.writeInt(Integer.reverseBytes(totalDataLen.toInt()))
                raf.writeBytes("WAVE")
                raf.writeBytes("fmt ")
                raf.writeInt(Integer.reverseBytes(16)) // subchunk1 size 16 for PCM
                raf.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt()) // PCM = 1
                raf.writeShort(java.lang.Short.reverseBytes(channels.toShort()).toInt())
                raf.writeInt(Integer.reverseBytes(sampleRate))
                raf.writeInt(Integer.reverseBytes(byteRate))
                raf.writeShort(java.lang.Short.reverseBytes((channels * bitsPerSample / 8).toShort()).toInt())
                raf.writeShort(java.lang.Short.reverseBytes(bitsPerSample.toShort()).toInt())
                raf.writeBytes("data")
                raf.writeInt(Integer.reverseBytes(totalAudioLen.toInt()))
            }
        } catch (_: Exception) {}
    }
}
