package com.example.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val state by assistantManager.state.collectAsState()
    val messages by assistantManager.messages.collectAsState()
    val isMuted by assistantManager.isMuted.collectAsState()
    val errorMessage by assistantManager.errorMessage.collectAsState()
    val listState = rememberLazyListState()

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
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("assistant_screen")
    ) {
        // Header
        Surface(
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
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
                            text = "Raksha360 AI Assistant",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = NavyDark,
                                fontSize = 16.sp
                            )
                        )
                        Text(
                            text = "Agent ID: agent_3701m51q...66",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                // Status Badge
                StatusPill(state = state)
            }
        }

        // Unconfigured or Error Banner
        AnimatedVisibility(visible = state == ElevenLabsAssistantManager.State.UNCONFIGURED) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = EvidenceOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Assistant Service Status",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E),
                            fontSize = 13.sp
                        )
                        Text(
                            text = errorMessage ?: "Safety assistant service configuration is pending on the backend server.",
                            color = Color(0xFFB45309),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Quick Suggestion Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                    color = Color.White,
                    shadowElevation = 1.dp,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp))
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = ShieldBlue,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    EmptyAssistantState(state = state)
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(msg = msg)
                }
            }
        }

        // Voice Visualizer / Speaking Wave when connected
        if (state == ElevenLabsAssistantManager.State.CONNECTED_LISTENING ||
            state == ElevenLabsAssistantManager.State.CONNECTED_SPEAKING
        ) {
            VoiceWaveIndicator(isSpeaking = state == ElevenLabsAssistantManager.State.CONNECTED_SPEAKING)
        }

        // Bottom Controls Bar
        Surface(
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state == ElevenLabsAssistantManager.State.DISCONNECTED ||
                    state == ElevenLabsAssistantManager.State.UNCONFIGURED ||
                    state == ElevenLabsAssistantManager.State.ERROR
                ) {
                    Button(
                        onClick = { assistantManager.startConversation() },
                        colors = ButtonDefaults.buttonColors(containerColor = ShieldBlue),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_conversation_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Voice Conversation", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                } else {
                    // Mute Toggle
                    IconButton(
                        onClick = { assistantManager.toggleMute() },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) Color(0xFFFEE2E2) else Color(0xFFF1F5F9))
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = if (isMuted) Color(0xFFDC2626) else NavyDark
                        )
                    }

                    // Stop Speaking / Interrupt button
                    if (state == ElevenLabsAssistantManager.State.CONNECTED_SPEAKING) {
                        OutlinedButton(
                            onClick = { assistantManager.stopSpeaking() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = null, tint = NavyDark)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop Speaking", color = NavyDark, fontSize = 12.sp)
                        }
                    }

                    // End Conversation Button
                    Button(
                        onClick = { assistantManager.stopConversation() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("End Session", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(state: ElevenLabsAssistantManager.State) {
    val (label, bg, fg) = when (state) {
        ElevenLabsAssistantManager.State.DISCONNECTED -> Triple("Ready", Color(0xFFE2E8F0), Color(0xFF475569))
        ElevenLabsAssistantManager.State.CONNECTING -> Triple("Connecting...", Color(0xFFFEF3C7), Color(0xFFD97706))
        ElevenLabsAssistantManager.State.CONNECTED_LISTENING -> Triple("Listening", Color(0xFFDCFCE7), SafeGreen)
        ElevenLabsAssistantManager.State.CONNECTED_SPEAKING -> Triple("Speaking", Color(0xFFDBEAFE), ShieldBlue)
        ElevenLabsAssistantManager.State.UNCONFIGURED -> Triple("Setup Needed", Color(0xFFFEE2E2), Color(0xFFDC2626))
        ElevenLabsAssistantManager.State.ERROR -> Triple("Error", Color(0xFFFEE2E2), Color(0xFFDC2626))
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
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
private fun EmptyAssistantState(state: ElevenLabsAssistantManager.State) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFFE0E7FF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Call,
                contentDescription = null,
                tint = ShieldBlue,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Raksha360 Safety Assistant",
            fontWeight = FontWeight.Bold,
            color = NavyDark,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Speak naturally with your conversational safety guide.\nAsk about safety features, check contacts, or draft an incident report.",
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun MessageBubble(msg: ElevenLabsAssistantManager.ConversationMessage) {
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
                    color = TextMuted,
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(vertical = 4.dp)
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
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) ShieldBlue else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth(0.82f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isUser) "You" else "Raksha360 Assistant",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isUser) Color(0xFFBFDBFE) else ShieldBlue,
                        fontSize = 10.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (isUser) Color.White else NavyDark,
                        fontSize = 13.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun VoiceWaveIndicator(isSpeaking: Boolean) {
    val transition = rememberInfiniteTransition(label = "wave")
    val scale by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .scale(if (isSpeaking) scale else 1f)
                .clip(CircleShape)
                .background(if (isSpeaking) ShieldBlue else SafeGreen)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isSpeaking) "Assistant is speaking..." else "Assistant is listening...",
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (isSpeaking) ShieldBlue else SafeGreen,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
