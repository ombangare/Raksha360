package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.navigation.NavRoutes
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.ShieldBlue

data class TourStepData(
    val stepNumber: Int,
    val targetRoute: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val description: String,
    val keyFeatures: List<String>
)

val APP_TOUR_STEPS = listOf(
    TourStepData(
        stepNumber = 1,
        targetRoute = NavRoutes.HOME,
        icon = Icons.Default.Home,
        title = "Raksha360 Safety Hub",
        subtitle = "Home Screen Command Center",
        description = "Your real-time women and child safety headquarters. Monitor GPS accuracy, trigger emergency panic with one tap, inspect recent activity, and access all safety services.",
        keyFeatures = listOf(
            "Live location card showing GPS precision or Fallback status",
            "Instant Emergency Panic trigger always visible and accessible",
            "Quick access: Check Area, Start Journey, and Threat Analyzer",
            "Real-time Recent Activity feed from local encrypted database"
        )
    ),
    TourStepData(
        stepNumber = 2,
        targetRoute = NavRoutes.MAP,
        icon = Icons.Default.Map,
        title = "Nagpur Dimensional Safety Map",
        subtitle = "Map & Safety Infrastructure",
        description = "Explore Nagpur's verified safety infrastructure. Locates 37 verified police stations with telephone numbers, emergency helplines (112, 1091, 1098, 108, 1930, 14567), and nearby hospitals.",
        keyFeatures = listOf(
            "Pulsing blue radar dot indicating your verified GPS position",
            "37 Verified Nagpur Police Stations with custom teardrop pins",
            "1-Tap 'Nearest Police Station' locator and route preview",
            "Searchable 37-station directory with direct phone calling"
        )
    ),
    TourStepData(
        stepNumber = 3,
        targetRoute = NavRoutes.JOURNEY,
        icon = Icons.Default.Navigation,
        title = "Safe Journey Tracking",
        subtitle = "Automated Journey Protection",
        description = "Track your travel with lifecycle-aware telemetry. Set an estimated arrival time (ETA), configure check-in reminders, and alert your trusted contacts with opt-in SMS progress.",
        keyFeatures = listOf(
            "Set destination and arrival time (5 to 120 minutes)",
            "Dynamic progress bar based on active elapsed time",
            "Verified contact integration with one-tap SMS status dispatch",
            "Lifecycle-aware service that ends cleanly upon safe arrival"
        )
    ),
    TourStepData(
        stepNumber = 4,
        targetRoute = NavRoutes.THREATS,
        icon = Icons.Default.Warning,
        title = "Evidence & Threat Analyzer",
        subtitle = "Multimodal Threat Detection",
        description = "Audit suspicious messages, photos, audio files, or documents. Uses localized heuristic analysis with transparent uncertainty disclosure and SHA-256 evidence hashing.",
        keyFeatures = listOf(
            "4 Modalities: Text, Image, Audio, Document (<10MB size limit)",
            "Cryptographic SHA-256 integrity hash generated for every file",
            "Room-persisted Evidence Vault catalog for legal documentation",
            "Clear disclosure: Hashing proves integrity, not guilt or malice"
        )
    ),
    TourStepData(
        stepNumber = 5,
        targetRoute = NavRoutes.CHILD_SAFETY,
        icon = Icons.Default.FamilyRestroom,
        title = "Child & Family Safety",
        subtitle = "Local-First Family Protection",
        description = "Protect children using privacy-first aliases—never requiring full legal names or birthdates. Configure safe-place geofences and verify school pickups with one-time PINs.",
        keyFeatures = listOf(
            "Child aliases with age-band categorization and guardian labels",
            "Safe-place geofence boundaries with entry and exit alerts",
            "School pickup/drop verification with 6-digit one-time PIN authorization",
            "Local-only database storage with zero unauthorized cloud leakage"
        )
    ),
    TourStepData(
        stepNumber = 6,
        targetRoute = NavRoutes.VOICE_SETTINGS,
        icon = Icons.Default.Mic,
        title = "Voice SOS & Guard Mode",
        subtitle = "Offline Speech Recognition",
        description = "Hands-free distress detection powered by bundled Vosk offline speech recognition. Evaluates emergency wake words on-device without uploading any audio to the cloud.",
        keyFeatures = listOf(
            "100% on-device Vosk AI speech processing (zero cloud audio storage)",
            "Voice enrollment for personalized speaker acoustic verification",
            "Guard Mode standby with foreground notification indicators",
            "Microphone is never activated silently during this tour"
        )
    ),
    TourStepData(
        stepNumber = 7,
        targetRoute = NavRoutes.INCIDENT_REPORT,
        icon = Icons.Default.Emergency,
        title = "Incident & Police Reporting",
        subtitle = "Control Room Intake Workflow",
        description = "Submit structured incident reports directly to the Raksha360 Control Room queue with explicit location consent and SHA-256 evidence attachments.",
        keyFeatures = listOf(
            "Select incident category and provide detailed description",
            "Explicit toggle for attaching verified GPS coordinates",
            "Attach cryptographic evidence digest from Threat Analyzer",
            "Truthful pipeline: Local Draft → Submitted → Received in Queue"
        )
    ),
    TourStepData(
        stepNumber = 8,
        targetRoute = NavRoutes.PRIVACY,
        icon = Icons.Default.Security,
        title = "Privacy Center & Governance",
        subtitle = "Zero-Trust Security Controls",
        description = "Full control over your data, credentials, and hardware permissions. Review evidence vault records, verify backend endpoints, or perform a total local account wipe.",
        keyFeatures = listOf(
            "Audit Location, Microphone, and Notification hardware access",
            "Evidence Vault with cryptographic tamper-evident logs",
            "Account wipe distinguishing local data from backend databases",
            "Replay this 8-Step Interactive Tour anytime from here"
        )
    )
)

@Composable
fun InteractiveTourOverlay(
    currentStep: Int,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stepIndex = (currentStep - 1).coerceIn(0, APP_TOUR_STEPS.size - 1)
    val step = APP_TOUR_STEPS[stepIndex]
    val isFirstStep = stepIndex == 0
    val isLastStep = stepIndex == APP_TOUR_STEPS.size - 1

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .testTag("interactive_tour_overlay")
    ) {
        // Floating Tour Card anchored near the bottom
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Top Header Row: Step Pill + Title + Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ShieldBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = step.icon,
                                contentDescription = null,
                                tint = ShieldBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFEFF6FF))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Step ${step.stepNumber} of ${APP_TOUR_STEPS.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = ShieldBlue,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                            Text(
                                text = step.subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }

                    // Skip Tour Button
                    IconButton(
                        onClick = onSkip,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Skip Tour",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Step Main Title
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = NavyDark,
                        fontSize = 18.sp
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Step Description
                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF334155),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Bulleted Features
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    step.keyFeatures.forEach { feature ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SafeGreen,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = feature,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF1E293B),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Buttons: Back, Progress Dots, Next/Finish
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isFirstStep) {
                        OutlinedButton(
                            onClick = onBack,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(44.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = NavyDark
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Back",
                                color = NavyDark,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        Text(
                            text = "Skip Tour",
                            modifier = Modifier
                                .clickable { onSkip() }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            color = Color(0xFF64748B),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }

                    // Progress Dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        APP_TOUR_STEPS.forEachIndexed { idx, _ ->
                            Box(
                                modifier = Modifier
                                    .size(if (idx == stepIndex) 8.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (idx == stepIndex) ShieldBlue else Color(0xFFCBD5E1)
                                    )
                            )
                        }
                    }

                    // Next or Finish Button
                    Button(
                        onClick = onNext,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLastStep) SafeGreen else ShieldBlue
                        )
                    ) {
                        Text(
                            text = if (isLastStep) "Finish Tour" else "Next",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (isLastStep) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
