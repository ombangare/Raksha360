package com.example.ui.screens.privacy

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.remote.BackendConnectionState
import com.example.ui.viewmodel.MainViewModel

@Composable
fun BackendConnectionScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val savedUrl by viewModel.backendEndpointUrl.collectAsState()
    val savedToken by viewModel.backendAuthToken.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    val currentUser = viewModel.currentUser

    var developerSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var overrideUrl by rememberSaveable { mutableStateOf(savedUrl) }
    var overrideToken by rememberSaveable { mutableStateOf(savedToken) }
    var showToken by rememberSaveable { mutableStateOf(false) }
    var devBusy by remember { mutableStateOf(false) }
    var devMessage by remember { mutableStateOf<String?>(null) }
    var devMessageIsError by remember { mutableStateOf(false) }

    LaunchedEffect(savedUrl) {
        overrideUrl = savedUrl
    }
    LaunchedEffect(savedToken) {
        overrideToken = savedToken
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF1E293B)
                )
            }
            Column {
                Text(
                    text = "Control Room Connection",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "Automatic backend health & secure session sync",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B)
                )
            }
        }

        // Live Connection Status Card (Primary Citizen View)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (connectionState) {
                    is BackendConnectionState.Connected -> Color(0xFFF0FDF4)
                    is BackendConnectionState.Connecting -> Color(0xFFEFF6FF)
                    is BackendConnectionState.Offline -> Color(0xFFFFFBEB)
                    is BackendConnectionState.AuthenticationRequired -> Color(0xFFFAF5FF)
                    is BackendConnectionState.ServiceUnavailable -> Color(0xFFFEF2F2)
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    when (connectionState) {
                                        is BackendConnectionState.Connected -> Color(0xFFDCFCE7)
                                        is BackendConnectionState.Connecting -> Color(0xFFDBEAFE)
                                        is BackendConnectionState.Offline -> Color(0xFFFEF3C7)
                                        is BackendConnectionState.AuthenticationRequired -> Color(0xFFF3E8FF)
                                        is BackendConnectionState.ServiceUnavailable -> Color(0xFFFEE2E2)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            when (connectionState) {
                                is BackendConnectionState.Connected -> Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF16A34A))
                                is BackendConnectionState.Connecting -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Color(0xFF2563EB))
                                is BackendConnectionState.Offline -> Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFD97706))
                                is BackendConnectionState.AuthenticationRequired -> Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF9333EA))
                                is BackendConnectionState.ServiceUnavailable -> Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626))
                            }
                        }

                        Column {
                            Text(
                                text = when (connectionState) {
                                    is BackendConnectionState.Connected -> "Connected to Control Room"
                                    is BackendConnectionState.Connecting -> "Checking Connection…"
                                    is BackendConnectionState.Offline -> "Device is Offline"
                                    is BackendConnectionState.AuthenticationRequired -> "Sign-In Required"
                                    is BackendConnectionState.ServiceUnavailable -> "Service Unavailable"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = when (connectionState) {
                                    is BackendConnectionState.Connected -> Color(0xFF15803D)
                                    is BackendConnectionState.Connecting -> Color(0xFF1D4ED8)
                                    is BackendConnectionState.Offline -> Color(0xFFB45309)
                                    is BackendConnectionState.AuthenticationRequired -> Color(0xFF7E22CE)
                                    is BackendConnectionState.ServiceUnavailable -> Color(0xFFB91C1C)
                                }
                            )
                            Text(
                                text = when (val state = connectionState) {
                                    is BackendConnectionState.Connected -> if (state.isDemoMode) "Demo Isolation Active" else "Live Dispatch Armed"
                                    is BackendConnectionState.Connecting -> "Probing backend reachability"
                                    is BackendConnectionState.Offline -> "Offline Safety Active"
                                    is BackendConnectionState.AuthenticationRequired -> "Guest / Demo Account"
                                    is BackendConnectionState.ServiceUnavailable -> "Retrying with backoff"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.checkBackendConnection(forceRetry = true) },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh status",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color.Black.copy(alpha = 0.06f))

                // Detailed message based on connection state
                when (val state = connectionState) {
                    is BackendConnectionState.Connected -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Production Endpoint: ${state.endpoint}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF334155),
                                fontWeight = FontWeight.Medium
                            )
                            if (state.latencyMs != null) {
                                Text(
                                    text = "Latency: ${state.latencyMs}ms · Database: ${state.database ?: "Supabase Postgres"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64748B)
                                )
                            }
                            if (state.userEmail != null) {
                                Text(
                                    text = "Authorized Account: ${state.userEmail}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF16A34A),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    is BackendConnectionState.Connecting -> {
                        Text(
                            text = "Verifying backend health and validating secure Google user session with Raksha360 servers…",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF475569)
                        )
                    }
                    is BackendConnectionState.Offline -> {
                        Text(
                            text = state.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF92400E)
                        )
                    }
                    is BackendConnectionState.AuthenticationRequired -> {
                        Text(
                            text = state.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF6B21A8)
                        )
                    }
                    is BackendConnectionState.ServiceUnavailable -> {
                        Text(
                            text = "Reason: ${state.error}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF991B1B)
                        )
                    }
                }
            }
        }

        // Production Citizen Notice Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF0F766E),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Zero-Config Public Security",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Text(
                    text = "Public users do not need to enter URLs or device tokens. The application automatically connects to Raksha360's production infrastructure via HTTPS and authorizes requests through your verified Google sign-in session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF475569),
                    lineHeight = 18.sp
                )

                HorizontalDivider(color = Color(0xFFF1F5F9))

                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(18.dp).padding(top = 2.dp)
                    )
                    Text(
                        text = "Safety Dispatch Policy: Offline events and Demo Mode drills remain local on the device and will NEVER be misidentified as live police dispatches.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1E3A8A),
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Developer & Advanced Diagnostics Section (Collapsible)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { developerSectionExpanded = !developerSectionExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Advanced Diagnostics / Developer Overrides",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = if (developerSectionExpanded) "Tap to collapse" else "Custom URL & synthetic test tokens for testing",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Icon(
                        imageVector = if (developerSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color(0xFF64748B)
                    )
                }

                AnimatedVisibility(visible = developerSectionExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Warning: Modifying these values overrides the automatic production connection. Normal citizens should leave these settings at default.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFB45309),
                            fontWeight = FontWeight.Medium
                        )

                        OutlinedTextField(
                            value = overrideUrl,
                            onValueChange = { overrideUrl = it; devMessage = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Custom API Base URL") },
                            placeholder = { Text(BuildConfig.DEFAULT_API_BASE_URL) },
                            singleLine = true,
                            supportingText = {
                                Text("Production: ${BuildConfig.DEFAULT_API_BASE_URL}")
                            }
                        )

                        OutlinedTextField(
                            value = overrideToken,
                            onValueChange = { overrideToken = it; devMessage = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Developer Device Token (Optional override)") },
                            placeholder = { Text("Leave blank to use Google user session") },
                            singleLine = true,
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                TextButton(onClick = { showToken = !showToken }) {
                                    Text(if (showToken) "Hide" else "Show")
                                }
                            },
                            supportingText = {
                                Text("Only needed for automated synthetic drills without Google Sign-In.")
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    devBusy = true
                                    devMessage = "Probing custom endpoint…"
                                    devMessageIsError = false
                                    viewModel.testBackendConnection(overrideUrl, overrideToken) { result ->
                                        devBusy = false
                                        devMessage = result.getOrElse { it.message ?: "Connection probe failed." }
                                        devMessageIsError = result.isFailure
                                    }
                                },
                                enabled = !devBusy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Test URL")
                            }

                            Button(
                                onClick = {
                                    devBusy = true
                                    devMessage = "Applying override…"
                                    devMessageIsError = false
                                    viewModel.connectBackend(overrideUrl, overrideToken) { result ->
                                        devBusy = false
                                        devMessage = result.getOrElse { it.message ?: "Failed to save configuration." }
                                        devMessageIsError = result.isFailure
                                    }
                                },
                                enabled = !devBusy && overrideUrl.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (devBusy) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = Color.White)
                                } else {
                                    Text("Apply Override")
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                devBusy = true
                                devMessage = "Restoring production defaults…"
                                devMessageIsError = false
                                viewModel.resetBackendToDefault {
                                    devBusy = false
                                    overrideUrl = BuildConfig.DEFAULT_API_BASE_URL
                                    overrideToken = ""
                                    devMessage = "Restored to official production URL."
                                }
                            },
                            enabled = !devBusy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0F766E))
                        ) {
                            Text("Reset to Production Default")
                        }

                        if (devMessage != null) {
                            Text(
                                text = devMessage!!,
                                color = if (devMessageIsError) Color(0xFFDC2626) else Color(0xFF16A34A),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
