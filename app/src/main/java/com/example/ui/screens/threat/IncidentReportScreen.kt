package com.example.ui.screens.threat

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.NavyDark
import com.example.ui.theme.ShieldBlue
import com.example.ui.viewmodel.MainViewModel

private val reportCategories = listOf(
    "Harassment",
    "Unsafe route",
    "Child safety",
    "Missing child concern",
    "Threatening messages",
    "School handover concern",
    "Other"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentReportScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onSubmitSuccess: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val location by viewModel.currentLocation.collectAsState()
    val isDemo by viewModel.isDemoMode.collectAsState()
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(reportCategories.first()) }
    var description by rememberSaveable { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var shareLocationConsent by rememberSaveable { mutableStateOf(true) }
    var evidenceSha256 by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reportId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text("Incident Report", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = NavyDark)
                Text("Raksha360 Control Room Intake", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
            }
        }

        // 112 Emergency Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Immediate Danger?", fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                    Text(
                        "Reports below enter control room review. For urgent police dispatch across India, dial 112 immediately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB91C1C)
                    )
                }
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" 112", fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF2FF)), shape = RoundedCornerShape(14.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1D4ED8))
                Column {
                    Text(if (isDemo) "Demo Mode Active (Offline Evaluation)" else "Connected to Control Room Backend", fontWeight = FontWeight.SemiBold, color = Color(0xFF1E3A8A))
                    Text("Submitting stores this incident report in Raksha360's control-room review queue. It is not an official police FIR filing until reviewed and registered by authorized law enforcement.", color = Color(0xFF1E40AF), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (resultMessage != null) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF8F0)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF15803D))
                    Text("Report Queued Successfully", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                    Text(resultMessage!!, color = Color(0xFF166534), style = MaterialTheme.typography.bodyMedium)
                    reportId?.let { Text("Reference ID: $it", color = Color(0xFF166534), fontWeight = FontWeight.Bold) }
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(onClick = onSubmitSuccess, modifier = Modifier.fillMaxWidth()) { Text("Return to Safety Home") }
                }
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it; errorMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Report title") },
                        placeholder = { Text("Briefly summarize the concern (e.g. Unsafe route on Sadar St)") },
                        singleLine = true,
                        enabled = !busy
                    )
                    ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = it }) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            enabled = !busy
                        )
                        ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                            reportCategories.forEach { item ->
                                DropdownMenuItem(text = { Text(item) }, onClick = { category = item; categoryExpanded = false })
                            }
                        }
                    }
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it; errorMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Incident details") },
                        placeholder = { Text("Describe what occurred. Avoid unnecessary sensitive personal details.") },
                        minLines = 4,
                        maxLines = 7,
                        enabled = !busy
                    )

                    // Location Consent Section
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Attach verified GPS location", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    if (location.isFallback) "GPS fix unavailable — fallback location will not be pinned"
                                    else location.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Switch(
                                checked = shareLocationConsent && !location.isFallback,
                                onCheckedChange = { shareLocationConsent = it },
                                enabled = !location.isFallback && !busy
                            )
                        }
                    }

                    // Evidence SHA-256 Digest
                    OutlinedTextField(
                        value = evidenceSha256,
                        onValueChange = { evidenceSha256 = it.trim().take(64) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Evidence Integrity SHA-256 (optional)") },
                        placeholder = { Text("Paste digest from Threat Analyzer or Evidence Vault") },
                        singleLine = true,
                        enabled = !busy
                    )

                    if (!errorMessage.isNullOrBlank()) {
                        Text(errorMessage!!, color = Color(0xFFB91C1C), style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            errorMessage = null
                            if (title.trim().length < 3 || description.trim().length < 5) {
                                errorMessage = "Add a title of at least 3 characters and a description of at least 5 characters."
                            } else {
                                busy = true
                                viewModel.submitIncidentReport(
                                    title = title,
                                    category = category,
                                    description = description,
                                    shareLocation = shareLocationConsent,
                                    evidenceSha256 = evidenceSha256.takeIf { it.isNotBlank() }
                                ) { result ->
                                    busy = false
                                    result.onSuccess { response ->
                                        reportId = response.reportId
                                        resultMessage = response.message
                                    }.onFailure { error ->
                                        errorMessage = error.message ?: "Report could not be sent. Check backend connection in Privacy Center."
                                    }
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ShieldBlue)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null)
                        }
                        Text(if (busy) "  Submitting Report…" else "  Submit to Control Room", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
