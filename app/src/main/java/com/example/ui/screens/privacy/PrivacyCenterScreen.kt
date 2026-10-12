package com.example.ui.screens.privacy

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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localization.LanguageStrings
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.ShieldBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel

@Composable
fun PrivacyCenterScreen(
    viewModel: MainViewModel,
    strings: LanguageStrings,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDeleteVoiceDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    val currentUser = viewModel.currentUser

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("privacy_center_screen")
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
                text = "Privacy Center",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = NavyDark,
                    fontSize = 18.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Authenticated Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE2E8F0)),
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
                            contentDescription = "Avatar",
                            tint = Color(0xFF4A5568),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentUser?.displayName ?: "Signed In User",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NavyDark,
                            fontSize = 15.sp
                        )
                    )
                    Text(
                        text = currentUser?.email ?: "No email linked",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = if (currentUser?.isDemoUser == true) "● Demo Mode Session (Offline)" else "● Google Authenticated",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (currentUser?.isDemoUser == true) Color(0xFFD97706) else SafeGreen,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card Container with Privacy Rows (Matching image exactly)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // 1. Location Access
                PrivacyControlRow(
                    icon = Icons.Default.LocationOn,
                    iconTint = SafeGreen,
                    iconBg = SafeGreen.copy(alpha = 0.12f),
                    title = "Location Access",
                    subtitle = "Allowed (Precise)",
                    onClick = { /* Manage location */ }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Microphone Access
                PrivacyControlRow(
                    icon = Icons.Default.Mic,
                    iconTint = ShieldBlue,
                    iconBg = ShieldBlue.copy(alpha = 0.12f),
                    title = "Microphone Access",
                    subtitle = "Allowed (Guard Mode)",
                    onClick = { /* Manage mic */ }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Evidence Storage
                PrivacyControlRow(
                    icon = Icons.Default.Lock,
                    iconTint = Color(0xFF8E24AA), // Purple
                    iconBg = Color(0xFFF3E5F5),
                    title = "Evidence Storage",
                    subtitle = "Encrypted on device",
                    onClick = { /* View storage */ }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 4. Data Sharing
                PrivacyControlRow(
                    icon = Icons.Default.People,
                    iconTint = Color(0xFF00897B), // Teal
                    iconBg = Color(0xFFE0F2F1),
                    title = "Data Sharing",
                    subtitle = "Trusted contacts only",
                    onClick = { /* Manage sharing */ }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 5. Emergency Sharing
                PrivacyControlRow(
                    icon = Icons.Default.Security,
                    iconTint = EmergencyRed,
                    iconBg = Color(0xFFFFEBEE),
                    title = "Emergency Sharing",
                    subtitle = "Police & trusted contacts",
                    onClick = { /* Manage emergency sharing */ }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 6. Data Retention
                PrivacyControlRow(
                    icon = Icons.Default.Schedule,
                    iconTint = Color(0xFF546E7A), // Slate
                    iconBg = Color(0xFFECEFF1),
                    title = "Data Retention",
                    subtitle = "User controlled",
                    onClick = { /* Manage retention */ }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 7. Delete Voice Profile
                PrivacyControlRow(
                    icon = Icons.Default.Delete,
                    iconTint = EmergencyRed,
                    iconBg = Color(0xFFFFEBEE),
                    title = "Delete Voice Profile",
                    subtitle = "Remove all voice data",
                    onClick = { showDeleteVoiceDialog = true }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 8. Delete Account / All Data
                PrivacyControlRow(
                    icon = Icons.Default.Person,
                    iconTint = EmergencyRed,
                    iconBg = Color(0xFFFFEBEE),
                    title = "Delete Account / All Data",
                    subtitle = "Permanently remove your data",
                    onClick = { showDeleteAccountDialog = true }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Backend endpoint controls the connection between this phone and the laptop control room.
                PrivacyControlRow(
                    icon = Icons.Default.Cloud,
                    iconTint = ShieldBlue,
                    iconBg = ShieldBlue.copy(alpha = 0.12f),
                    title = "Backend & Control Room Connection",
                    subtitle = if (viewModel.backendEndpointConfigured.value && !viewModel.isDemoMode.value) "Local server configured" else "Not connected · Demo mode may be active",
                    onClick = { onNavigate(com.example.ui.navigation.NavRoutes.BACKEND_SETTINGS) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 9. Sign Out
                PrivacyControlRow(
                    icon = Icons.Default.ExitToApp,
                    iconTint = NavyDark,
                    iconBg = Color(0xFFE2E8F0),
                    title = "Sign Out",
                    subtitle = "Clear session credentials on this device",
                    onClick = { showSignOutDialog = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Delete Voice Profile Dialog
    if (showDeleteVoiceDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteVoiceDialog = false },
            title = { Text("Delete Voice Profile?") },
            text = { Text("This will erase your enrolled speaker embedding and local voice data from this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteVoiceProfile()
                        showDeleteVoiceDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteVoiceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Account Dialog
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete All Data?") },
            text = { Text("This will wipe all local caches, evidence files, and reset Raksha360 on this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteVoiceProfile()
                        showDeleteAccountDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)
                ) {
                    Text("Permanently Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Sign Out Dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out of Raksha360?") },
            text = { Text("You will be returned to the Sign In screen. Your local safety records remain encrypted on this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.signOut()
                        showSignOutDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyDark)
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PrivacyControlRow(
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
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
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
                        fontSize = 13.5.sp
                    )
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
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
