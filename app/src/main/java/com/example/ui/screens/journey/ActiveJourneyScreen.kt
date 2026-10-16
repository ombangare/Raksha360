package com.example.ui.screens.journey

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.ShieldBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActiveJourneyScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onEndJourney: () -> Unit,
    onEmergencyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isJourneyActivePref by viewModel.isJourneyActive.collectAsState()
    val destinationPref by viewModel.journeyDestination.collectAsState()
    val etaPref by viewModel.journeyEtaMinutes.collectAsState()
    val allJourneys by viewModel.childSafetyJourneys.collectAsState()
    val trustedContacts by viewModel.trustedContacts.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()

    val activeJourneyRecord = allJourneys.firstOrNull { it.status == "ACTIVE" || it.status == "OVERDUE" }
    val isTracking = isJourneyActivePref || activeJourneyRecord != null

    var showEndDialog by remember { mutableStateOf(false) }

    // Start Journey Planner State
    var originInput by remember {
        mutableStateOf(if (!currentLocation.isFallback && currentLocation.description.isNotBlank()) currentLocation.description else "Sadar, Nagpur")
    }
    var destinationInput by remember { mutableStateOf(if (destinationPref.isNotBlank()) destinationPref else "VNIT Campus, Nagpur") }
    var selectedEtaMinutes by remember { mutableStateOf(if (etaPref > 0) etaPref else 25) }

    val activeContactsCount = trustedContacts.count { it.isActive }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("active_journey_screen")
    ) {
        // Top Header
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
                Text(
                    text = if (isTracking) "Active Journey" else "Plan Safe Journey",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = NavyDark,
                        fontSize = 18.sp
                    )
                )
            }

            if (isTracking) {
                // Red End Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(EmergencyRed)
                        .clickable { showEndDialog = true }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("end_journey_pill")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "End",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (isTracking) {
            // ---------------- ACTIVE JOURNEY TRACKING VIEW ----------------
            val originDisplay = activeJourneyRecord?.originLabel ?: "Sadar, Nagpur"
            val destinationDisplay = activeJourneyRecord?.destinationLabel ?: destinationPref.ifBlank { "VNIT Campus, Nagpur" }
            val startTimeMs = activeJourneyRecord?.startedAt ?: System.currentTimeMillis()
            val expectedArrivalMs = activeJourneyRecord?.expectedArrivalAt ?: (startTimeMs + etaPref * 60_000L)
            val nowMs = System.currentTimeMillis()
            val elapsedMs = (nowMs - startTimeMs).coerceAtLeast(0L)
            val totalDurationMs = (expectedArrivalMs - startTimeMs).coerceAtLeast(60_000L)
            val progressFraction = (elapsedMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0.08f, 0.98f)
            val minutesRemaining = ((expectedArrivalMs - nowMs) / 60_000L).coerceAtLeast(0L)

            // Main Journey Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Route Origin -> Destination
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ShieldBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NearMe,
                                contentDescription = null,
                                tint = ShieldBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "$originDisplay → $destinationDisplay",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark,
                                    fontSize = 15.sp
                                )
                            )
                            Text(
                                text = "Started at ${timeFormat.format(Date(startTimeMs))}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Estimated Arrival & Progress Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = ShieldBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Estimated Arrival",
                                    style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                                )
                                Text(
                                    text = timeFormat.format(Date(expectedArrivalMs)),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = NavyDark,
                                        fontSize = 15.sp
                                    )
                                )
                            }
                        }

                        Text(
                            text = if (nowMs > expectedArrivalMs) "Overdue by ${((nowMs - expectedArrivalMs) / 60_000L)} min" else "$minutesRemaining min left",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (nowMs > expectedArrivalMs) EmergencyRed else TextSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Horizontal blue progress bar
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = ShieldBlue,
                        trackColor = Color(0xFFE2E8F0)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Location row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(SafeGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = SafeGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Live Location",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = NavyDark,
                                        fontSize = 13.sp
                                    )
                                )
                                Text(
                                    text = if (activeContactsCount > 0) "Sharing with $activeContactsCount trusted contact${if (activeContactsCount == 1) "" else "s"}" else "No contacts configured • Local GPS tracking",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Next Check-in row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(ShieldBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = ShieldBlue,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Automated Monitoring",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = NavyDark,
                                        fontSize = 13.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = SafeGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Text(
                                text = "WorkManager safety supervisor running in background",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Safety Status row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(SafeGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = SafeGreen,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Safety Status",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = NavyDark,
                                        fontSize = 13.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "On Route",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = SafeGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Text(
                                text = "Corridor verified • Emergency panic armed",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mini Map Preview Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE5ECE5)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Roads
                        drawLine(Color.White, Offset(w * 0.1f, h * 0.3f), Offset(w * 0.9f, h * 0.3f), strokeWidth = 14f)
                        drawLine(Color.White, Offset(w * 0.2f, h * 0.7f), Offset(w * 0.8f, h * 0.7f), strokeWidth = 12f)
                        drawLine(Color.White, Offset(w * 0.5f, h * 0.1f), Offset(w * 0.5f, h * 0.9f), strokeWidth = 16f)

                        // Path
                        val p = Path().apply {
                            moveTo(w * 0.25f, h * 0.3f)
                            lineTo(w * 0.45f, h * 0.3f)
                            lineTo(w * 0.5f, h * 0.5f)
                            lineTo(w * 0.65f, h * 0.7f)
                        }
                        drawPath(p, ShieldBlue, style = Stroke(width = 8f, cap = StrokeCap.Round))

                        // Start pin
                        drawCircle(Color.White, radius = 9f, center = Offset(w * 0.25f, h * 0.3f))
                        drawCircle(ShieldBlue, radius = 5f, center = Offset(w * 0.25f, h * 0.3f))

                        // Current location on path
                        val curX = w * (0.25f + 0.40f * progressFraction)
                        val curY = h * (0.3f + 0.40f * progressFraction)
                        drawCircle(Color.White, radius = 12f, center = Offset(curX, curY))
                        drawCircle(ShieldBlue, radius = 7f, center = Offset(curX, curY))

                        // End pin
                        drawCircle(Color.White, radius = 9f, center = Offset(w * 0.65f, h * 0.7f))
                        drawCircle(Color(0xFFE53935), radius = 5f, center = Offset(w * 0.65f, h * 0.7f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons Row: "Message Contact" + "Emergency"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = {
                        val firstContact = trustedContacts.firstOrNull { it.isActive } ?: trustedContacts.firstOrNull()
                        val targetPhone = firstContact?.phoneNumber ?: "112"
                        val smsBody = Uri.encode("I am currently on a safe journey to $destinationDisplay. Raksha360 tracking is active.")
                        val smsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$targetPhone?body=$smsBody"))
                        context.startActivity(smsIntent)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("message_contact_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NavyDark)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = ShieldBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Message Contact",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onEmergencyClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("journey_emergency_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmergencyRed,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Emergency",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        } else {
            // ---------------- NEW JOURNEY PLANNER VIEW ----------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(ShieldBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.DirectionsWalk, contentDescription = null, tint = ShieldBlue)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Start a Safe Journey", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavyDark))
                            Text("Share live progress and automated check-ins", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text("Origin", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = originInput,
                        onValueChange = { originInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = NavyDark,
                            unfocusedTextColor = NavyDark,
                            focusedBorderColor = ShieldBlue,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Destination", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = destinationInput,
                        onValueChange = { destinationInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = NavyDark,
                            unfocusedTextColor = NavyDark,
                            focusedBorderColor = ShieldBlue,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Estimated Travel Time", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted, fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 25, 35, 45).forEach { mins ->
                            val isSelected = selectedEtaMinutes == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) ShieldBlue else Color(0xFFF1F5F9))
                                    .clickable { selectedEtaMinutes = mins }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$mins min",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = if (isSelected) Color.White else NavyDark,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Contact notification badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF8FAFC))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = if (activeContactsCount > 0) SafeGreen else Color(0xFFD97706), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (activeContactsCount > 0) "Alerting $activeContactsCount trusted contact${if (activeContactsCount == 1) "" else "s"} on departure & arrival." else "No trusted contacts active • Journey will be recorded locally.",
                            style = MaterialTheme.typography.bodySmall.copy(color = NavyDark, fontSize = 12.sp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            viewModel.startJourney(
                                origin = originInput.ifBlank { "Sadar, Nagpur" },
                                destination = destinationInput.ifBlank { "VNIT Campus, Nagpur" },
                                etaMinutes = selectedEtaMinutes
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_journey_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ShieldBlue, contentColor = Color.White)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Journey", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Past Journeys Section
            if (allJourneys.isNotEmpty()) {
                Text(
                    text = "Recent Journey History",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = NavyDark,
                        fontSize = 16.sp
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))

                allJourneys.take(5).forEach { journey ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(SafeGreen.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SafeGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "${journey.originLabel} → ${journey.destinationLabel}",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = NavyDark)
                                    )
                                    Text(
                                        text = dateFormat.format(Date(journey.startedAt)),
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp)
                                    )
                                }
                            }

                            Text(
                                text = journey.status.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall.copy(color = SafeGreen, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // End Journey Confirmation Dialog
    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("End Journey?") },
            text = { Text("Are you safe at your destination? This will finalize your journey check-in and disarm automatic arrival alerts.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.endJourney()
                        showEndDialog = false
                        onEndJourney()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
                ) {
                    Text("I'm Safe • End Journey")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
