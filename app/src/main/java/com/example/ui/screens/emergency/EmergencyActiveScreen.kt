package com.example.ui.screens.emergency

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.data.local.entity.EmergencyEventEntity
import com.example.localization.LanguageStrings
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.ShieldBlue
import com.example.ui.viewmodel.MainViewModel

@Composable
fun EmergencyActiveScreen(
    viewModel: MainViewModel,
    strings: LanguageStrings,
    activeEmergency: EmergencyEventEntity?,
    onEmergencyEnded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pendingQueueCount by viewModel.pendingQueueCount.collectAsState()
    val trustedContacts by viewModel.trustedContacts.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()

    // Pulsing siren animation
    val infiniteTransition = rememberInfiniteTransition(label = "siren_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF14080B)) // Deep dark red/black
            .testTag("emergency_activated_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar with Settings gear
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = { /* Settings / info */ }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Siren Icon with pulsing circular glow
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer aura ring
                    Box(
                        modifier = Modifier
                            .size(125.dp * pulseScale)
                            .clip(CircleShape)
                            .background(EmergencyRed.copy(alpha = 0.18f))
                    )
                    // Inner ring
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(EmergencyRed.copy(alpha = 0.35f))
                    )
                    // Center siren button
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(EmergencyRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Emergency Active",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "EMERGENCY ACTIVATED",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        letterSpacing = 1.sp
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Help is on the way",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 14.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Status List (dark translucent cards matching image)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF221115))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Item 1: Live Location Sharing
                    val isGpsActive = !currentLocation.isFallback &&
                        currentLocation.status == com.example.service.location.LocationStatus.ACTIVE_GPS &&
                        currentLocation.latitude in -90.0..90.0 &&
                        currentLocation.longitude in -180.0..180.0 &&
                        (currentLocation.latitude != 0.0 || currentLocation.longitude != 0.0) &&
                        !(currentLocation.latitude == com.example.service.location.AppLocationManager.NAGPUR_CENTER_LAT &&
                          currentLocation.longitude == com.example.service.location.AppLocationManager.NAGPUR_CENTER_LNG)

                    val locationStatusText = when {
                        isGpsActive -> "Transmitting live GPS (${currentLocation.description.take(20)})"
                        currentLocation.status == com.example.service.location.LocationStatus.ACQUIRING -> "Acquiring GPS fix…"
                        currentLocation.status == com.example.service.location.LocationStatus.PERMISSION_DENIED -> "Location permission denied"
                        currentLocation.status == com.example.service.location.LocationStatus.GPS_DISABLED -> "Device GPS disabled"
                        currentLocation.status == com.example.service.location.LocationStatus.TIMEOUT -> "Location timeout"
                        else -> "Location unavailable"
                    }
                    val locationColor = if (isGpsActive) SafeGreen else Color(0xFFFFA726)

                    EmergencyStatusItem(
                        icon = Icons.Default.LocationOn,
                        iconTint = locationColor,
                        iconBg = locationColor.copy(alpha = 0.2f),
                        title = "Live location sharing",
                        statusText = locationStatusText,
                        statusColor = locationColor
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Item 2: Trusted Contacts Notified
                    val activeContacts = trustedContacts.filter { it.isActive }
                    val notifiedCount = activeEmergency?.trustedContactsNotifiedCount ?: 0
                    val contactsStatus = when {
                        activeContacts.isEmpty() -> "No active contacts"
                        notifiedCount > 0 -> "$notifiedCount notified (Confirmed)"
                        pendingQueueCount > 0 -> "Queued offline (${activeContacts.size})"
                        isDemoMode -> "Simulation queued (${activeContacts.size})"
                        else -> "Gateway pending (${activeContacts.size})"
                    }
                    val contactsColor = if (notifiedCount > 0) SafeGreen else Color(0xFFFFA726)

                    EmergencyStatusItem(
                        icon = Icons.Default.People,
                        iconTint = contactsColor,
                        iconBg = contactsColor.copy(alpha = 0.2f),
                        title = "Trusted contacts notified",
                        statusText = contactsStatus,
                        statusColor = contactsColor
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Item 3: Police Control Room Notification
                    val policeAcknowledged = activeEmergency?.policeNotified == true
                    val trackingId = activeEmergency?.policeTrackingId
                    val policeStatus = when {
                        policeAcknowledged -> "Official Gateway Dispatched"
                        trackingId != null && isDemoMode -> "Demo Simulated (Control Room)"
                        trackingId != null -> "Control Room Queued (Dial 112)"
                        pendingQueueCount > 0 -> "Pending transmission (Dial 112)"
                        isDemoMode -> "Demo Simulated (Dial 112)"
                        else -> "Not Dispatched (Dial 112)"
                    }
                    val policeColor = if (policeAcknowledged) SafeGreen else if (trackingId != null && !isDemoMode) Color(0xFF38BDF8) else Color(0xFFFFA726)

                    EmergencyStatusItem(
                        icon = Icons.Default.Shield,
                        iconTint = policeColor,
                        iconBg = policeColor.copy(alpha = 0.2f),
                        title = "Police control room",
                        statusText = policeStatus,
                        statusColor = policeColor
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Item 4: Recording Incident Data
                    EmergencyStatusItem(
                        icon = Icons.Default.GraphicEq,
                        iconTint = Color(0xFFAB47BC),
                        iconBg = Color(0xFFAB47BC).copy(alpha = 0.2f),
                        title = "Recording incident data",
                        statusText = "In progress (Encrypted)",
                        statusColor = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons
            Column(modifier = Modifier.fillMaxWidth()) {
                // Call 112 Red Pill Button
                Button(
                    onClick = {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                        context.startActivity(dialIntent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("call_112_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmergencyRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Call 112",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // End Emergency Button
                Button(
                    onClick = {
                        viewModel.endEmergency("User resolved emergency")
                        onEmergencyEnded()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("end_emergency_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF221115),
                        contentColor = Color.White.copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text(
                        text = "End Emergency",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EmergencyStatusItem(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    statusText: String,
    statusColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
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

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.92f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            )
        }

        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall.copy(
                color = statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        )
    }
}
