package com.example.ui.screens.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.localization.LanguageStrings
import com.example.NaariKavachApp
import com.example.data.repository.VoiceProfileRepository
import com.example.ui.components.AudioWaveformVisualizer
import com.example.ui.navigation.NavRoutes
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EvidenceOrange
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.ShieldBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel

@Composable
fun VoiceSosSettingsScreen(
    viewModel: MainViewModel,
    strings: LanguageStrings,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val voiceProfile by viewModel.voiceProfile.collectAsState()
    val isGuardModeActive by viewModel.isGuardModeActive.collectAsState()
    val liveRms by viewModel.liveRms.collectAsState()
    val testVerificationResult by viewModel.testVerificationResult.collectAsState()
    val testScope = rememberCoroutineScope()
    var testRecordingPending by remember { mutableStateOf(false) }

    fun captureAndTestPhrase() {
        if (testRecordingPending) return
        if (isGuardModeActive) {
            viewModel.setVoiceTestMessage("Stop Guard Mode before recording a separate local test clip; the microphone is already in use.")
            return
        }
        testRecordingPending = true
        viewModel.clearTestResult()
        NaariKavachApp.instance.audioRecordManager.startSampleRecording(testScope, 3000L) { sample ->
            testRecordingPending = false
            viewModel.runVoiceSosTestCheck(sample)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startGuardMode()
    }
    val testMicPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) captureAndTestPhrase()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("voice_sos_screen")
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = NavyDark
                )
            }
            Text(
                text = "Voice SOS",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = NavyDark,
                    fontSize = 18.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 1: Guard Mode Active with Audio Waveform
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(SafeGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = SafeGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Guard Mode",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark,
                                    fontSize = 15.sp
                                )
                            )
                            Text(
                                text = if (isGuardModeActive) "Active" else "Standby",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isGuardModeActive) SafeGreen else TextMuted,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    Switch(
                        checked = isGuardModeActive,
                        onCheckedChange = { enable ->
                            if (enable) {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) viewModel.startGuardMode() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                viewModel.stopGuardMode()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SafeGreen
                        ),
                        modifier = Modifier.testTag("guard_mode_toggle_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Listening for: \"${voiceProfile?.phrase ?: VoiceProfileRepository.DEFAULT_PHRASE}\"",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Waveform Bars in Green
                AudioWaveformVisualizer(
                    rmsLevel = if (isGuardModeActive) 450f else 40f,
                    isRecording = isGuardModeActive,
                    barColor = SafeGreen,
                    modifier = Modifier.height(38.dp)
                )

                if (testVerificationResult != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (testVerificationResult!!.contains("passed", ignoreCase = true)) Color(0xFFE8F5E9)
                                else Color(0xFFFFEBEE)
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            text = testVerificationResult!!,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (testVerificationResult!!.contains("passed", ignoreCase = true)) SafeGreen else EmergencyRed,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Card 2: Voice Management Rows
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Row 1: Enrolled Voice
                VoiceControlRow(
                    icon = Icons.Default.Notifications,
                    iconTint = ShieldBlue,
                    iconBg = Color(0xFFE3F2FD),
                    title = "Enrolled Voice",
                    subtitle = when {
                        voiceProfile == null || !voiceProfile!!.isEnrolled -> "Not enrolled"
                        voiceProfile!!.embedding.size <= 32 -> "Legacy 32-d profile · Re-enrollment required"
                        voiceProfile!!.sampleCount < 5 -> "Incomplete (${voiceProfile!!.sampleCount}/5 samples) · Re-enrollment required"
                        else -> "Five samples enrolled (128-d Vosk) · Ready for Guard Mode"
                    },
                    actionLabel = "Manage >",
                    onActionClick = { onNavigate(NavRoutes.VOICE_ENROLL) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Row 2: Emergency Phrase
                VoiceControlRow(
                    icon = Icons.Default.RecordVoiceOver,
                    iconTint = EvidenceOrange,
                    iconBg = Color(0xFFFFF3E0),
                    title = "Emergency Phrase",
                    subtitle = voiceProfile?.phrase ?: VoiceProfileRepository.DEFAULT_PHRASE,
                    actionLabel = "Change >",
                    onActionClick = { onNavigate(NavRoutes.VOICE_ENROLL) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Row 3: Test Voice SOS
                VoiceControlRow(
                    icon = Icons.Default.Security,
                    iconTint = Color(0xFFE53935),
                    iconBg = Color(0xFFFFEBEE),
                    title = "Test Voice SOS",
                    subtitle = if (testRecordingPending) "Recording a three-second local test clip…" else "Check phrase and enrolled speaker (no emergency sent)",
                    actionLabel = if (testRecordingPending) "Recording…" else "Test >",
                    onActionClick = {
                        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (hasMic) captureAndTestPhrase() else testMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
                if (testVerificationResult != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(testVerificationResult!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Section 3: "Permissions"
        Text(
            text = "Permissions",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = NavyDark,
                fontSize = 16.sp
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Microphone permission row
                PermissionStatusRow(
                    icon = Icons.Default.Mic,
                    iconTint = ShieldBlue,
                    iconBg = Color(0xFFE3F2FD),
                    title = "Microphone",
                    subtitle = "Allowed (Guard Mode)",
                    onClick = { /* Check permission */ }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Location permission row
                PermissionStatusRow(
                    icon = Icons.Default.LocationOn,
                    iconTint = SafeGreen,
                    iconBg = Color(0xFFE8F5E9),
                    title = "Location",
                    subtitle = "Allowed (Precise)",
                    onClick = { /* Check permission */ }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun VoiceControlRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    actionLabel: String,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = NavyDark,
                        fontSize = 14.sp
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 11.sp
                    ),
                    maxLines = 2
                )
            }
        }

        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelSmall.copy(
                color = ShieldBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            ),
            modifier = Modifier
                .clickable { onActionClick() }
                .padding(4.dp)
        )
    }
}

@Composable
private fun PermissionStatusRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = NavyDark,
                        fontSize = 14.sp
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = SafeGreen,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    )
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextMuted
        )
    }
}
