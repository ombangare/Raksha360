package com.example.ui.screens.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.MainViewModel

@Composable
fun BackendConnectionScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val savedUrl by viewModel.backendEndpointUrl.collectAsState()
    val savedToken by viewModel.backendAuthToken.collectAsState()
    val configured by viewModel.backendEndpointConfigured.collectAsState()
    val demoMode by viewModel.isDemoMode.collectAsState()
    var url by rememberSaveable { mutableStateOf(savedUrl) }
    var token by rememberSaveable { mutableStateOf(savedToken) }
    var showToken by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text("Backend connection", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (com.example.BuildConfig.IS_PRODUCTION) "Connect this phone to the Raksha360 control room"
                    else "Connect this phone to the laptop control room",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B)
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = if (configured && !demoMode) Color(0xFFEAF8F0) else Color(0xFFFFF7E6)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    imageVector = if (configured && !demoMode) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = if (configured && !demoMode) Color(0xFF15803D) else Color(0xFFB45309)
                )
                Column {
                    Text(if (configured && !demoMode) "Backend configured" else "Demo or unconfigured mode", fontWeight = FontWeight.Bold)
                    Text(
                        if (configured && !demoMode) "SOS and reports will use the configured API. External dispatch and contact notifications remain unconfigured unless separately integrated."
                        else "Testing the connection does not send an SOS. Demo events stay local.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; message = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Backend API base URL") },
                    placeholder = { Text(com.example.BuildConfig.DEFAULT_API_BASE_URL) },
                    singleLine = true,
                    supportingText = {
                        Text(
                            if (com.example.BuildConfig.IS_PRODUCTION) "Production endpoint: https://raksha360-api.onrender.com/api/v1 (HTTPS enforced)"
                            else "Physical phone: use laptop LAN IPv4. Emulator: http://10.0.2.2:4000/api/v1"
                        )
                    }
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it; message = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (com.example.BuildConfig.IS_PRODUCTION) "Device token (from backend environment)" else "Device token from backend .env") },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showToken = !showToken }) { Text(if (showToken) "Hide" else "Show") } }
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = {
                            busy = true
                            message = "Checking health and device authentication…"
                            messageIsError = false
                            viewModel.testBackendConnection(url, token) { result ->
                                busy = false
                                message = result.getOrElse { it.message ?: "Connection test failed." }
                                messageIsError = result.isFailure
                            }
                        },
                        enabled = !busy
                    ) { Text("Test connection") }
                    Button(
                        onClick = {
                            busy = true
                            message = "Verifying and saving settings…"
                            messageIsError = false
                            viewModel.connectBackend(url, token) { result ->
                                busy = false
                                message = result.getOrElse { it.message ?: "Could not connect." }
                                messageIsError = result.isFailure
                            }
                        },
                        enabled = !busy && url.isNotBlank() && token.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(18.dp)) else Text("Connect phone")
                    }
                }
                if (message != null) {
                    Text(message!!, color = if (messageIsError) Color(0xFFB91C1C) else Color(0xFF166534), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF2FF)), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1D4ED8))
                Column {
                    Text(if (com.example.BuildConfig.IS_PRODUCTION) "Production connection" else "Laptop setup", fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                    Text(
                        if (com.example.BuildConfig.IS_PRODUCTION) "Connects to Raksha360's production control-room API on Render. Requires the authorized device token configured on the server. All traffic uses TLS encryption."
                        else "Start the backend on the laptop, keep the phone and laptop on the same trusted Wi-Fi, allow port 4000 on your private Windows network, then enter the laptop's IPv4 address here. Do not expose this development server directly to the public internet.",
                        color = Color(0xFF1E40AF), style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("A control-room acknowledgment means stored in Raksha360's queue. It does not mean the police received a dispatch.", color = Color(0xFF1E40AF), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
