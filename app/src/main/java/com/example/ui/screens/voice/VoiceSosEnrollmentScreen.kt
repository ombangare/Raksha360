package com.example.ui.screens.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.NaariKavachApp
import com.example.data.repository.VoiceProfileRepository
import com.example.localization.LanguageStrings
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EmergencyRedContainer
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyLight
import com.example.ui.theme.OutlineBorder
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.SafeGreenContainer
import com.example.ui.theme.ShieldBlue
import com.example.ui.theme.ShieldBlueContainer
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SampleValidationStatus {
    IDLE,
    RECORDING,
    VALIDATING,
    VALID,
    INVALID,
    SAVING,
    ENROLLED
}

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
    val liveRms by viewModel.liveRms.collectAsState()
    val existingProfile by viewModel.voiceProfile.collectAsState()

    val capturedSamples = remember { mutableStateListOf<ShortArray>() }
    var phrase by remember { mutableStateOf(existingProfile?.phrase ?: VoiceProfileRepository.DEFAULT_PHRASE) }
    var enrollmentStatus by remember { mutableStateOf(SampleValidationStatus.IDLE) }
    var statusMessage by remember {
        mutableStateOf("Record 5 separate voice samples saying the exact emergency phrase once per sample.")
    }
    var isStatusError by remember { mutableStateOf(false) }
    var isEnrollmentSaved by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            statusMessage = "Microphone permission granted. Tap Record Sample to begin."
            isStatusError = false
        } else {
            statusMessage = "Microphone permission is required to enroll your voice profile."
            isStatusError = true
        }
    }

    fun stopCurrentRecording() {
        app.audioRecordManager.stopRecordingEarly()
    }

    fun recordNextSample() {
        if (enrollmentStatus == SampleValidationStatus.RECORDING || enrollmentStatus == SampleValidationStatus.VALIDATING || enrollmentStatus == SampleValidationStatus.SAVING) return
        if (isGuardModeActive) {
            statusMessage = "Please stop Guard Mode before enrolling. The background listener currently owns the microphone."
            isStatusError = true
            return
        }
        if (phrase.trim().length !in 6..100) {
            statusMessage = "Please choose an emergency phrase between 6 and 100 characters."
            isStatusError = true
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        enrollmentStatus = SampleValidationStatus.RECORDING
        statusMessage = "Listening... Speak the phrase: \"${phrase.trim()}\""
        isStatusError = false

        scope.launch {
            val ready = withContext(Dispatchers.IO) { app.voiceModelEngine.preparePhraseEngine() }
            if (!ready) {
                statusMessage = app.voiceModelEngine.modelStatusLabel
                isStatusError = true
                enrollmentStatus = SampleValidationStatus.IDLE
                return@launch
            }

            app.audioRecordManager.startSampleRecording(scope, 3200L) { sample ->
                enrollmentStatus = SampleValidationStatus.VALIDATING
                statusMessage = "Validating sample quality and speaker characteristics..."

                scope.launch {
                    val quality = withContext(Dispatchers.Default) {
                        app.voiceModelEngine.checkAudioQuality(sample)
                    }

                    if (sample.size < 8000 || !quality.isAcceptable) {
                        enrollmentStatus = SampleValidationStatus.INVALID
                        isStatusError = true
                        statusMessage = if (sample.isEmpty()) {
                            "No audio was captured. Check microphone permission and retry."
                        } else {
                            "Sample ${capturedSamples.size + 1} rejected: ${quality.guidanceMessage}. Please retry this sample."
                        }
                        return@launch
                    }

                    // Verify sample produces a valid speaker vector using Vosk speaker engine
                    val vector = withContext(Dispatchers.Default) {
                        app.voiceModelEngine.extractSingleSpeakerVector(sample)
                    }

                    if (vector.isEmpty() || vector.size <= 32) {
                        enrollmentStatus = SampleValidationStatus.INVALID
                        isStatusError = true
                        statusMessage = "Sample ${capturedSamples.size + 1} voice vector could not be extracted clearly. Speak naturally in a quieter room and retry."
                        return@launch
                    }

                    // Sample accepted!
                    capturedSamples.add(sample)
                    isStatusError = false

                    if (capturedSamples.size >= 5) {
                        enrollmentStatus = SampleValidationStatus.VALID
                        statusMessage = "All 5 voice samples accepted! Tap 'Finish & Save Voice Profile' below to complete enrollment."
                    } else {
                        enrollmentStatus = SampleValidationStatus.IDLE
                        statusMessage = "Sample ${capturedSamples.size} of 5 accepted! Ready to record sample ${capturedSamples.size + 1}."
                    }
                }
            }
        }
    }

    fun finishAndSaveEnrollment() {
        if (capturedSamples.size < 5 || enrollmentStatus == SampleValidationStatus.SAVING) return
        enrollmentStatus = SampleValidationStatus.SAVING
        statusMessage = "Generating 128-dimensional speaker embedding and securing in Android Keystore..."
        isStatusError = false

        scope.launch {
            val samples = capturedSamples.toList()
            val embedding = withContext(Dispatchers.Default) {
                app.voiceModelEngine.extractSpeakerEmbedding(samples)
            }

            if (embedding.isEmpty() || embedding.size <= 32) {
                enrollmentStatus = SampleValidationStatus.INVALID
                isStatusError = true
                statusMessage = "Failed to generate a compatible 128-dimensional Vosk speaker embedding. Please retry the enrollment in a quiet environment."
                return@launch
            }

            val saved = withContext(Dispatchers.IO) {
                viewModel.saveVoiceProfile(
                    phrase = phrase.trim(),
                    embedding = embedding,
                    sampleCount = samples.size,
                    threshold = 0.72f
                )
            }

            if (!saved) {
                enrollmentStatus = SampleValidationStatus.INVALID
                isStatusError = true
                statusMessage = "Could not save encrypted voice profile to device storage. Please check device security settings and retry."
                return@launch
            }

            enrollmentStatus = SampleValidationStatus.ENROLLED
            isEnrollmentSaved = true
            isStatusError = false
            statusMessage = "Voice profile successfully enrolled! Vosk 128-dimensional profile secured. You can now enable Guard Mode."
            onEnrollmentComplete()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("voice_sos_enrollment_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NavyDark
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Voice SOS Enrollment",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = NavyDark,
                            fontSize = 18.sp
                        )
                    )
                    Text(
                        text = "Offline Vosk phrase + speaker verification",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            // Progress Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(ShieldBlueContainer)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${capturedSamples.size}/5 Samples",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = ShieldBlue,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        // 2. Emergency Phrase Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, OutlineBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = ShieldBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Emergency Phrase",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = NavyDark,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                OutlinedTextField(
                    value = phrase,
                    onValueChange = {
                        phrase = it
                        if (capturedSamples.isNotEmpty()) {
                            capturedSamples.clear()
                            statusMessage = "Phrase changed. Please record all 5 samples for the new phrase."
                            isStatusError = false
                        }
                    },
                    label = { Text("Phrase to recognize", color = TextSecondary) },
                    supportingText = {
                        Text(
                            text = "Standard: ${VoiceProfileRepository.DEFAULT_PHRASE}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ShieldBlue,
                        unfocusedBorderColor = OutlineBorder,
                        focusedLabelColor = ShieldBlue,
                        unfocusedLabelColor = TextSecondary
                    )
                )

                Text(
                    text = "Say this exact phrase clearly during enrollment. Guard Mode triggers only when both the phrase matches and speaker verification passes.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                )
            }
        }

        // 3. Audio Recording & Validation Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, OutlineBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (enrollmentStatus == SampleValidationStatus.RECORDING) {
                            "Recording Sample ${capturedSamples.size + 1} of 5..."
                        } else if (capturedSamples.size >= 5) {
                            "5 Samples Ready for Saving"
                        } else {
                            "Sample ${capturedSamples.size + 1} of 5"
                        },
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = NavyDark,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    if (enrollmentStatus == SampleValidationStatus.VALIDATING || enrollmentStatus == SampleValidationStatus.SAVING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = ShieldBlue
                        )
                    }
                }

                // Interactive Record / Stop Button
                if (recording || enrollmentStatus == SampleValidationStatus.RECORDING) {
                    Button(
                        onClick = { stopCurrentRecording() },
                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Stop Recording (Tap to finish)",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                } else {
                    Button(
                        onClick = { recordNextSample() },
                        enabled = capturedSamples.size < 5 &&
                                enrollmentStatus != SampleValidationStatus.VALIDATING &&
                                enrollmentStatus != SampleValidationStatus.SAVING &&
                                !isGuardModeActive,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ShieldBlue,
                            disabledContainerColor = Color(0xFFCBD5E1)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (capturedSamples.size >= 5) {
                                "All 5 Samples Captured"
                            } else {
                                "Record Sample ${capturedSamples.size + 1} of 5"
                            },
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Reset / Clear Samples Button
                if (capturedSamples.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            capturedSamples.clear()
                            enrollmentStatus = SampleValidationStatus.IDLE
                            statusMessage = "All samples cleared. Ready to start fresh."
                            isStatusError = false
                        },
                        enabled = !recording && enrollmentStatus != SampleValidationStatus.VALIDATING && enrollmentStatus != SampleValidationStatus.SAVING,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, OutlineBorder)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Start Over (Clear Samples)", color = TextSecondary, fontSize = 13.sp)
                    }
                }

                // List of accepted samples
                if (capturedSamples.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Captured & Validated Samples:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = NavyDark,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    capturedSamples.forEachIndexed { index, sample ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SafeGreenContainer.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SafeGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Sample ${index + 1}: Validated (${String.format(java.util.Locale.US, "%.1fs", sample.size / 16000f)})",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = NavyDark,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                            Text(
                                text = "128-d vector",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = SafeGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                // Status & Guidance Feedback Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isStatusError) EmergencyRedContainer.copy(alpha = 0.7f)
                            else if (capturedSamples.size >= 5) SafeGreenContainer.copy(alpha = 0.7f)
                            else Color(0xFFF1F5F9)
                        )
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = if (isStatusError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isStatusError) EmergencyRed else if (capturedSamples.size >= 5) SafeGreen else ShieldBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isStatusError) EmergencyRed else NavyDark,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                // Model status notice
                Text(
                    text = viewModel.modelStatusLabel,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                )
            }
        }

        // 4. Finish & Save Voice Profile Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (capturedSamples.size >= 5) SafeGreenContainer.copy(alpha = 0.35f) else SurfaceCard
            ),
            border = BorderStroke(
                1.5.dp,
                if (capturedSamples.size >= 5) SafeGreen else OutlineBorder
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Finish & Save Voice Profile",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = NavyDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )

                Text(
                    text = if (capturedSamples.size < 5) {
                        "Record all 5 samples above before saving your profile. The enrolled profile is encrypted on-device via Android Keystore."
                    } else {
                        "All 5 samples validated! Tap below to generate and persist your 128-dimensional Vosk voice profile."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                )

                Button(
                    onClick = { finishAndSaveEnrollment() },
                    enabled = capturedSamples.size == 5 && enrollmentStatus != SampleValidationStatus.SAVING,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafeGreen,
                        disabledContainerColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_voice_profile_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (enrollmentStatus == SampleValidationStatus.SAVING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Encrypting & Saving Profile...", color = Color.White, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isEnrollmentSaved) "Voice Profile Saved Successfully ✓" else "Save & Finish Enrollment",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                if (isEnrollmentSaved) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SafeGreen)
                    ) {
                        Text("Return to Voice SOS Settings", color = SafeGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Privacy & technical note
        Text(
            text = "Voice SOS runs entirely offline on your device using embedded Vosk models. Audio is processed locally and never uploaded to any remote server.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextMuted,
                fontSize = 11.sp
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
