package com.example.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.ShieldBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.EvidenceOrange

@Composable
fun AssistantScreen(
    assistantManager: ElevenLabsAssistantManager,
    onBack: () -> Unit,
    onNavigateTarget: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by assistantManager.state.collectAsState()
    val messages by assistantManager.messages.collectAsState()
    val isMuted by assistantManager.isMuted.collectAsState()
    val errorMessage by assistantManager.errorMessage.collectAsState()
    val audioLevel by assistantManager.audioLevel.collectAsState()
    val listState = rememberLazyListState()

    // Track auto-start to prevent duplicate triggers on recomposition
    var autoStarted by rememberSaveable { mutableStateOf(false) }
    var micPermissionDenied by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            micPermissionDenied = false
            if (!autoStarted) {
                autoStarted = true
                assistantManager.startConversation()
            }
        } else {
            micPermissionDenied = true
        }
    }

    // Auto-start once when screen opens
    LaunchedEffect(Unit) {
        if (!autoStarted) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                autoStarted = true
                assistantManager.startConversation()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Clean up audio resources when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            assistantManager.stopConversation()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        assistantManager.onNavigateToScreen = { screen ->
            onNavigateTarget(screen)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0F172A),
                        Color(0xFF0B132B),
                        Color(0xFF070B19)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("assistant_screen")
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x20FFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Raksha360 Safety Assistant",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 17.sp
                        )
                    )
                    Text(
                        text = "Real-Time AI Voice Guide",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    )
                }
            }

            // Real-time State Pill
            StatusPill(state = state, isMuted = isMuted)
        }

        // Permission Denied Warning Banner
        AnimatedVisibility(visible = micPermissionDenied) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x35DC2626)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .border(1.dp, Color(0x60DC2626), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFF87171),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Microphone permission is required to talk with the safety assistant. Tap retry to grant access.",
                        color = Color(0xFFFCA5A5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Error or Setup Banner
        AnimatedVisibility(
            visible = (state == ElevenLabsAssistantManager.State.UNCONFIGURED ||
                       state == ElevenLabsAssistantManager.State.ERROR) && !micPermissionDenied
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x25F59E0B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .border(1.dp, Color(0x50F59E0B), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = EvidenceOrange,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (state == ElevenLabsAssistantManager.State.UNCONFIGURED) "Configuration Notice" else "Connection Notice",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFCD34D),
                            fontSize = 13.sp
                        )
                        Text(
                            text = errorMessage ?: "Assistant service is currently connecting or awaiting credentials.",
                            color = Color(0xFFFDE68A),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Center Stage: 3D Animated Voice Orb
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                VoiceOrb(
                    state = state,
                    isMuted = isMuted,
                    audioLevel = audioLevel,
                    size = 230.dp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Orb State Description
                val stateHeadline = when {
                    isMuted -> "Microphone Muted"
                    state == ElevenLabsAssistantManager.State.CONNECTING -> "Connecting..."
                    state == ElevenLabsAssistantManager.State.CONNECTED_LISTENING -> "Listening..."
                    state == ElevenLabsAssistantManager.State.CONNECTED_SPEAKING -> "Assistant Speaking"
                    state == ElevenLabsAssistantManager.State.ERROR -> "Connection Interrupted"
                    state == ElevenLabsAssistantManager.State.UNCONFIGURED -> "Setup Required"
                    else -> "Ready to Assist"
                }

                val stateSubtext = when {
                    isMuted -> "Tap the microphone button below to unmute."
                    state == ElevenLabsAssistantManager.State.CONNECTING -> "Establishing secure conversational voice channel..."
                    state == ElevenLabsAssistantManager.State.CONNECTED_LISTENING -> "Speak naturally, Raksha360 is listening."
                    state == ElevenLabsAssistantManager.State.CONNECTED_SPEAKING -> "Listen to voice guidance or tap stop speaking to interrupt."
                    state == ElevenLabsAssistantManager.State.ERROR -> errorMessage ?: "Tap retry to reconnect to the safety assistant."
                    state == ElevenLabsAssistantManager.State.UNCONFIGURED -> "Safety assistant backend credentials are pending verification."
                    else -> "Tap the button below to start voice conversation."
                }

                Text(
                    text = stateHeadline,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stateSubtext,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }

        // Quick Suggestion Prompts
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val suggestions = listOf(
                "How do SOS emails work?",
                "Open Nagpur Safety Map",
                "Explain Voice SOS",
                "Check Location Permission",
                "Call 112 directly"
            )
            items(suggestions) { prompt ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0x18FFFFFF),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0x25FFFFFF), RoundedCornerShape(16.dp))
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = Color(0xFFE2E8F0),
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Transcript / Messages (Glass Card)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.9f)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x12FFFFFF)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1FFFFFFF))
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Your live conversation transcript will appear here.",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        DarkMessageBubble(msg = msg)
                    }
                }
            }
        }

        // Bottom Controls Bar (Glassmorphic)
        Surface(
            color = Color(0xFF0F172A),
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Color(0x20FFFFFF),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state == ElevenLabsAssistantManager.State.DISCONNECTED ||
                    state == ElevenLabsAssistantManager.State.UNCONFIGURED ||
                    state == ElevenLabsAssistantManager.State.ERROR
                ) {
                    // Retry / Start Button
                    Button(
                        onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!hasPermission) {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                assistantManager.startConversation()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ShieldBlue),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_conversation_btn")
                    ) {
                        Icon(
                            imageVector = if (state == ElevenLabsAssistantManager.State.ERROR) Icons.Default.Refresh else Icons.Default.Mic,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (state == ElevenLabsAssistantManager.State.ERROR) "Retry Voice Connection" else "Start Voice Conversation",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                    }
                } else {
                    // Mute / Unmute Button
                    IconButton(
                        onClick = { assistantManager.toggleMute() },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) Color(0x35EF4444) else Color(0x20FFFFFF))
                            .border(1.dp, if (isMuted) Color(0x80EF4444) else Color(0x30FFFFFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = if (isMuted) Color(0xFFF87171) else Color.White
                        )
                    }

                    // Stop Speaking button (visible when agent is actively speaking)
                    if (state == ElevenLabsAssistantManager.State.CONNECTED_SPEAKING) {
                        OutlinedButton(
                            onClick = { assistantManager.stopSpeaking() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF38BDF8)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8))
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = null, tint = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Speaking", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // End Session Button
                    Button(
                        onClick = { assistantManager.stopConversation() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Text("End Session", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(state: ElevenLabsAssistantManager.State, isMuted: Boolean) {
    val (label, bg, fg) = when {
        isMuted -> Triple("Muted", Color(0x30EF4444), Color(0xFFFCA5A5))
        state == ElevenLabsAssistantManager.State.DISCONNECTED -> Triple("Ready", Color(0x25FFFFFF), Color(0xFFCBD5E1))
        state == ElevenLabsAssistantManager.State.CONNECTING -> Triple("Connecting", Color(0x30F59E0B), Color(0xFFFCD34D))
        state == ElevenLabsAssistantManager.State.CONNECTED_LISTENING -> Triple("Listening", Color(0x3010B981), Color(0xFF6EE7B7))
        state == ElevenLabsAssistantManager.State.CONNECTED_SPEAKING -> Triple("Speaking", Color(0x350284C7), Color(0xFF38BDF8))
        state == ElevenLabsAssistantManager.State.UNCONFIGURED -> Triple("Setup", Color(0x30F59E0B), Color(0xFFFCD34D))
        state == ElevenLabsAssistantManager.State.ERROR -> Triple("Error", Color(0x30DC2626), Color(0xFFFCA5A5))
        else -> Triple("Ready", Color(0x25FFFFFF), Color(0xFFCBD5E1))
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun DarkMessageBubble(msg: ElevenLabsAssistantManager.ConversationMessage) {
    val isUser = msg.sender == ElevenLabsAssistantManager.MessageSender.USER
    val isSystem = msg.sender == ElevenLabsAssistantManager.MessageSender.SYSTEM

    if (isSystem) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = msg.text,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(vertical = 3.dp)
            )
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 14.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) ShieldBlue else Color(0x25FFFFFF)
            ),
            border = if (isUser) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0x20FFFFFF)),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = if (isUser) "You" else "Raksha360 Assistant",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) Color(0xFFBFDBFE) else Color(0xFF38BDF8),
                        fontSize = 10.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontSize = 13.sp
                    )
                )
            }
        }
    }
}
