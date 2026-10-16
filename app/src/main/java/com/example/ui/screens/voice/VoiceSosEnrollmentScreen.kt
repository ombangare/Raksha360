package com.example.ui.screens.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.NaariKavachApp
import com.example.data.repository.VoiceProfileRepository
import com.example.localization.LanguageStrings
import com.example.ui.theme.NavyDark
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VoiceSosEnrollmentScreen(
    viewModel: MainViewModel,
    strings: LanguageStrings,
    onBack: () -> Unit,
    onEnrollmentComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = NaariKavachApp.instance
    val recording by viewModel.isMicrophoneRecording.collectAsState()
    val isGuardModeActive by viewModel.isGuardModeActive.collectAsState()
    val capturedSamples = remember { mutableStateListOf<ShortArray>() }
    var phrase by remember { mutableStateOf(VoiceProfileRepository.DEFAULT_PHRASE) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Record five separate samples, speaking the exact phrase once per sample.") }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) message = "Microphone permission granted. Tap Record sample to begin."
        else message = "Microphone permission is required for voice enrollment."
    }

    fun recordNextSample() {
        if (busy || recording) return
        if (isGuardModeActive) {
            message = "Stop Guard Mode before enrolling. The background listener currently owns the microphone."
            return
        }
        if (phrase.trim().length !in 6..100) {
            message = "Choose a short emergency phrase between 6 and 100 characters."
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        busy = true
        scope.launch {
            val ready = withContext(Dispatchers.IO) { app.voiceModelEngine.preparePhraseEngine() }
            if (!ready) {
                message = app.voiceModelEngine.modelStatusLabel
                busy = false
                return@launch
            }
            app.audioRecordManager.startSampleRecording(scope, 3000L) { sample ->
                busy = false
                val quality = app.voiceModelEngine.checkAudioQuality(sample)
                if (sample.size < 8000 || !quality.isAcceptable) {
                    message = if (sample.isEmpty()) "No audio was captured. Check microphone permission and try again." else quality.guidanceMessage
                    return@startSampleRecording
                }
                capturedSamples.add(sample)
                message = "Sample ${capturedSamples.size} of 5 captured. ${quality.guidanceMessage}"
                if (capturedSamples.size == 5) {
                    busy = true
                    val samples = capturedSamples.toList()
                    scope.launch {
                        val embedding = withContext(Dispatchers.Default) { app.voiceModelEngine.extractSpeakerEmbedding(samples) }
                        if (embedding.isEmpty()) {
                            message = "Speaker embedding extraction failed. Record the five samples again in a quieter environment."
                            capturedSamples.clear()
                            busy = false
                            return@launch
                        }
                        viewModel.saveVoiceProfile(phrase.trim(), embedding, sampleCount = samples.size, threshold = 0.82f)
                        message = "Five real samples enrolled. The 0.82 threshold is an initial setting and must be calibrated before relying on Voice SOS."
                        busy = false
                        onEnrollmentComplete()
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("voice_sos_enrollment_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NavyDark) }
                Column {
                    Text("Voice SOS enrollment", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = NavyDark))
                    Text("Offline phrase recognition + speaker verification", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("${capturedSamples.size}/5", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Emergency phrase", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = phrase,
                    onValueChange = { phrase = it; capturedSamples.clear(); message = "Phrase changed. Record all five samples again." },
                    label = { Text("Phrase to recognize") },
                    supportingText = { Text("Default: ${VoiceProfileRepository.DEFAULT_PHRASE}") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text("The exact phrase must match, then the enrolled speaker embedding must pass a similarity check. Loudness alone never triggers SOS.")
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (recording) "Recording for 3 seconds…" else "Sample ${capturedSamples.size + 1} of 5", fontWeight = FontWeight.SemiBold)
                Button(onClick = { recordNextSample() }, enabled = !busy && !recording && !isGuardModeActive && capturedSamples.size < 5, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.padding(3.dp))
                    Text(if (recording || busy) "Please wait…" else if (capturedSamples.size == 5) "Enrollment complete" else "Record sample")
                }
                Button(onClick = { capturedSamples.clear(); busy = false; message = "Samples cleared. Begin enrollment again." }, enabled = !recording && !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Start over")
                }
                capturedSamples.forEachIndexed { index, sample ->
                    Text("Sample ${index + 1}: ${sample.size / 16000f} seconds of captured PCM", style = MaterialTheme.typography.bodySmall)
                }
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Text(viewModel.modelStatusLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Keep each sample around three seconds and say the phrase naturally. This prototype stores the encrypted speaker embedding, not the raw enrollment audio. The threshold is not yet statistically calibrated.", style = MaterialTheme.typography.bodySmall)
    }
}
