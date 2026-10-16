package com.example.assistant

import android.content.Context
import com.example.service.GuardModeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MicrophoneCoordinator
 * Explicitly mediates microphone hardware ownership between the offline Vosk Voice SOS service
 * and the conversational ElevenLabs AI assistant so they never compete for or lock the mic.
 */
object MicrophoneCoordinator {

    enum class MicrophoneOwner {
        IDLE,
        VOSK_GUARD,
        ELEVENLABS_ASSISTANT,
        EMERGENCY_SOS
    }

    private val _currentOwner = MutableStateFlow(MicrophoneOwner.IDLE)
    val currentOwner: StateFlow<MicrophoneOwner> = _currentOwner.asStateFlow()

    @Synchronized
    fun requestMicForAssistant(context: Context): Boolean {
        if (_currentOwner.value == MicrophoneOwner.EMERGENCY_SOS) {
            // Never preempt an active emergency
            return false
        }
        if (GuardModeService.isServiceRunning) {
            GuardModeService.pauseListening(context)
        }
        _currentOwner.value = MicrophoneOwner.ELEVENLABS_ASSISTANT
        return true
    }

    @Synchronized
    fun releaseMicFromAssistant(context: Context) {
        if (_currentOwner.value == MicrophoneOwner.ELEVENLABS_ASSISTANT) {
            if (GuardModeService.isServiceRunning) {
                GuardModeService.resumeListening(context)
                _currentOwner.value = MicrophoneOwner.VOSK_GUARD
            } else {
                _currentOwner.value = MicrophoneOwner.IDLE
            }
        }
    }

    @Synchronized
    fun preemptForEmergency(context: Context, onPreemptAssistant: () -> Unit) {
        _currentOwner.value = MicrophoneOwner.EMERGENCY_SOS
        onPreemptAssistant()
    }

    @Synchronized
    fun resetToDefault(context: Context) {
        if (GuardModeService.isServiceRunning) {
            GuardModeService.resumeListening(context)
            _currentOwner.value = MicrophoneOwner.VOSK_GUARD
        } else {
            _currentOwner.value = MicrophoneOwner.IDLE
        }
    }
}
