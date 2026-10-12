package com.example.ui.screens.threat

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val location by viewModel.currentLocation.collectAsState()
    val isDemo by viewModel.isDemoMode.collectAsState()
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(reportCategories.first()) }
    var description by rememberSaveable { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reportId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column {
                Text("Incident report", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Raksha360 Control Room intake", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF2FF)), shape = RoundedCornerShape(14.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF1D4ED8))
                Column {
                    Text(if (isDemo) "Demo Mode is enabled" else "Report will be sent to your configured backend", fontWeight = FontWeight.SemiBold, color = Color(0xFF1E3A8A))
                    Text("Submitting stores this report in Raksha360's local control-room queue. It is not an official police complaint filing.", color = Color(0xFF1E40AF), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (resultMessage != null) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF8F0)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF15803D))
                    Text("Report received by Raksha360", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(resultMessage!!, color = Color(0xFF166534), style = MaterialTheme.typography.bodyMedium)
                    reportId?.let { Text("Reference: $it", color = Color(0xFF166534), fontWeight = FontWeight.SemiBold) }
                    Button(onClick = onSubmitSuccess, modifier = Modifier.fillMaxWidth()) { Text("Return to safety home") }
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
                        placeholder = { Text("Briefly describe the concern") },
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
                        label = { Text("What happened?") },
                        placeholder = { Text("Include the details responders need. Avoid unnecessary sensitive details.") },
                        minLines = 5,
                        maxLines = 8,
                        enabled = !busy
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Location context", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (location.isFallback) "No verified live GPS fix. The fallback location will not be used as a map pin."
                            else location.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF64748B)
                        )
                    }
                    if (!errorMessage.isNullOrBlank()) Text(errorMessage!!, color = Color(0xFFB91C1C), style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = {
                            errorMessage = null
                            if (title.trim().length < 3 || description.trim().length < 5) {
                                errorMessage = "Add a title of at least 3 characters and a description of at least 5 characters."
                            } else {
                                busy = true
                                viewModel.submitIncidentReport(title, category, description) { result ->
                                    busy = false
                                    result.onSuccess { response ->
                                        reportId = response.reportId
                                        resultMessage = response.message
                                    }.onFailure { error ->
                                        errorMessage = error.message ?: "Report could not be sent. Check the backend connection in Privacy Center."
                                    }
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(19.dp)) else Icon(Icons.Default.Send, contentDescription = null)
                        Text(if (busy) "Sending report…" else "Send to Raksha360 Control Room", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
