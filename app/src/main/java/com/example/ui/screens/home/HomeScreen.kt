package com.example.ui.screens.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.localization.LanguageStrings
import com.example.data.repository.VoiceProfileRepository
import com.example.ui.navigation.NavRoutes
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EvidenceOrange
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.ShieldBlue
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    strings: LanguageStrings,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val voiceProfile by viewModel.voiceProfile.collectAsState()
    val isGuardModeActive by viewModel.isGuardModeActive.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val recentJourneys by viewModel.childSafetyJourneys.collectAsState()
    val recentAuditLogs by viewModel.recentAuditLogs.collectAsState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startGuardMode()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .padding(bottom = 96.dp)
        ) {
            // 1. Top Profile Bar
        val currentUser = viewModel.currentUser
        val displayName = currentUser?.displayName?.trim()?.ifBlank { "User" } ?: "User"
        val subtitleText = if (currentUser?.isDemoUser == true) "Demo Mode • Stay Safe" else "Stay Safe, Stay Strong"

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onNavigate(NavRoutes.PRIVACY) }
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar circle with border
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE2E8F0))
                        .border(1.5.dp, ShieldBlue.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!currentUser?.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = currentUser?.photoUrl,
                            contentDescription = "User Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "User Avatar",
                            tint = Color(0xFF4A5568),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Hello, $displayName",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NavyDark,
                            fontSize = 17.sp
                        )
                    )
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (currentUser?.isDemoUser == true) Color(0xFFD97706) else TextSecondary,
                            fontWeight = if (currentUser?.isDemoUser == true) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            // Notification Bell with Unread Indicator
            Box {
                IconButton(
                    onClick = { onNavigate(NavRoutes.PRIVACY) },
                    modifier = Modifier.testTag("notification_bell_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = NavyDark,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(EmergencyRed)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 2. Current Location Card - Dynamic and Honest Location
        val isFallbackLocation = currentLocation.isFallback || currentLocation.latitude == 0.0
        val locationTitle = if (isFallbackLocation) "Sadar, Nagpur (Estimated)" else currentLocation.description.ifBlank { "Nagpur, Maharashtra" }
        val locationSubtitle = if (isFallbackLocation) "Baseline public infrastructure • GPS signal inactive" else "Live device coordinates verified • Accuracy ±${currentLocation.accuracy.toInt()}m"
        val badgeLabel = if (isFallbackLocation) "Pilot Area" else "GPS Active"
        val badgeColor = if (isFallbackLocation) Color(0xFFD97706) else SafeGreen

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(NavRoutes.MAP) }
                .testTag("current_location_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ShieldBlue.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = ShieldBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Current Location",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text = locationTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = NavyDark,
                                fontSize = 16.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = locationSubtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        )
                    }
                }

                // Safe Area / Status Pill Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = badgeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. 2x2 Quick Action Grid
        Row(modifier = Modifier.fillMaxWidth()) {
            QuickActionSquare(
                title = "Check Area",
                subtitle = "View safety signals",
                icon = Icons.Default.LocationOn,
                iconColor = Color(0xFF00897B), // Teal
                iconBg = Color(0xFFE0F2F1),
                modifier = Modifier
                    .weight(1f)
                    .testTag("action_check_area"),
                onClick = { onNavigate(NavRoutes.MAP) }
            )
            Spacer(modifier = Modifier.width(12.dp))
            QuickActionSquare(
                title = "Start Journey",
                subtitle = "Plan a safe route",
                icon = Icons.Default.NearMe,
                iconColor = Color(0xFF1E88E5), // Blue
                iconBg = Color(0xFFE3F2FD),
                modifier = Modifier
                    .weight(1f)
                    .testTag("action_start_journey"),
                onClick = { onNavigate(NavRoutes.ACTIVE_JOURNEY) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            QuickActionSquare(
                title = "Analyse Threat",
                subtitle = "Scan messages/files",
                icon = Icons.Default.Policy,
                iconColor = Color(0xFFE53935), // Red/Coral
                iconBg = Color(0xFFFFEBEE),
                modifier = Modifier
                    .weight(1f)
                    .testTag("action_analyze_threat"),
                onClick = { onNavigate(NavRoutes.THREATS) }
            )
            Spacer(modifier = Modifier.width(12.dp))
            QuickActionSquare(
                title = "Emergency",
                subtitle = "Get immediate help",
                icon = Icons.Default.Warning,
                iconColor = Color(0xFFD32F2F), // Red
                iconBg = Color(0xFFFFCDD2),
                modifier = Modifier
                    .weight(1f)
                    .testTag("action_emergency_panic"),
                onClick = { viewModel.triggerManualPanic() }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(NavRoutes.CHILD_SAFETY) }
                .testTag("home_child_safety_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(ShieldBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.FamilyRestroom, contentDescription = null, tint = ShieldBlue)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Child & Family Safety", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavyDark))
                    Text("Profiles, safe journeys, geofence alerts and school pickup PINs", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                }
                Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Open child safety", tint = TextMuted)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. Voice SOS Guard Mode Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigate(NavRoutes.VOICE_SETTINGS) }
                .testTag("home_guard_mode_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SafeGreen.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Voice SOS",
                            tint = SafeGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Voice SOS",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                        )
                        Text(
                            text = if (isGuardModeActive) "Guard Mode is Active" else "Guard Mode is Standby",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = NavyDark,
                                fontSize = 15.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Listening for: \"${voiceProfile?.phrase ?: VoiceProfileRepository.DEFAULT_PHRASE}\"",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
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

                            if (hasMic) {
                                viewModel.startGuardMode()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        } else {
                            viewModel.stopGuardMode()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SafeGreen
                    ),
                    modifier = Modifier.testTag("home_guard_mode_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 5. Recent Activity Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Activity",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = NavyDark,
                    fontSize = 16.sp
                )
            )
            Text(
                text = "View All >",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = ShieldBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                modifier = Modifier
                    .clickable { onNavigate(NavRoutes.JOURNEY) }
                    .padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Dynamic Recent Activity Card (Real journeys or audit logs, or truthful standby state)
        val latestJourney = recentJourneys.firstOrNull()
        val latestLog = recentAuditLogs.firstOrNull()
        val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

        if (latestJourney != null) {
            val isSafe = latestJourney.status == "SAFE_ARRIVAL"
            val titleText = if (isSafe) "Journey Completed" else if (latestJourney.status == "ACTIVE") "Active Journey" else "Journey ${latestJourney.status.lowercase().replaceFirstChar { it.uppercase() }}"
            val statusColor = if (isSafe) SafeGreen else ShieldBlue

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(NavRoutes.ACTIVE_JOURNEY) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(statusColor.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSafe) Icons.Default.Check else Icons.Default.NearMe,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark,
                                    fontSize = 14.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${latestJourney.originLabel} → ${latestJourney.destinationLabel}",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                            )
                            Text(
                                text = dateFormat.format(Date(latestJourney.lastCheckInAt ?: latestJourney.startedAt)),
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
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
        } else if (latestLog != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(NavRoutes.JOURNEY) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ShieldBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = ShieldBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = latestLog.action.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark,
                                    fontSize = 14.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = latestLog.detail.take(65) + if (latestLog.detail.length > 65) "..." else "",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
                            )
                            Text(
                                text = dateFormat.format(Date(latestLog.timestamp)),
                                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
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
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigate(NavRoutes.ACTIVE_JOURNEY) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF1F5F9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "No Recent Activity",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark,
                                    fontSize = 14.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Start a safe journey or test Voice SOS",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp)
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
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Floating circular safety assistant microphone button
    FloatingAssistantMicrophoneButton(
        onClick = { onNavigate(NavRoutes.ASSISTANT) },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 20.dp, bottom = 20.dp)
            .navigationBarsPadding()
    )
    }
}

@Composable
private fun QuickActionSquare(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    iconBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(108.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = NavyDark,
                        fontSize = 13.sp
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextMuted,
                        fontSize = 10.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun FloatingAssistantMicrophoneButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_idle_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_alpha"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Outer subtle pulsing halo for idle breathing effect
        Box(
            modifier = Modifier
                .size(68.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(ShieldBlue.copy(alpha = haloAlpha))
        )

        // Floating circular action button
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = ShieldBlue,
            shadowElevation = 6.dp,
            tonalElevation = 2.dp,
            modifier = Modifier
                .size(56.dp)
                .testTag("floating_assistant_button")
                .semantics {
                    contentDescription = "Safety Assistant Voice Guide"
                }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
