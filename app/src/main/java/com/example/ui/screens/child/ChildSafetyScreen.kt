package com.example.ui.screens.child

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.local.entity.ChildProfileEntity
import com.example.data.local.entity.SafetyJourneyEntity
import com.example.data.local.entity.SchoolHandoverEntity
import com.example.ui.viewmodel.MainViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun ChildSafetyScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onTriggerChildSos: (String) -> Unit
) {
    val context = LocalContext.current
    val children by viewModel.childProfiles.collectAsState()
    val journeys by viewModel.childSafetyJourneys.collectAsState()
    val handovers by viewModel.childSchoolHandovers.collectAsState()
    val alerts by viewModel.childSafetyAlerts.collectAsState()

    var alias by remember { mutableStateOf("") }
    var ageBand by remember { mutableStateOf("8–12") }
    var school by remember { mutableStateOf("") }
    var guardian by remember { mutableStateOf("") }
    var safePlace by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("150") }
    var journeyChild by remember { mutableStateOf("") }
    var origin by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var etaMinutes by remember { mutableStateOf("30") }
    var handoverChild by remember { mutableStateOf("") }
    var handoverSchool by remember { mutableStateOf("") }
    var handoverGuardian by remember { mutableStateOf("") }
    var handoverMinutes by remember { mutableStateOf("30") }
    var verifyPin by remember { mutableStateOf("") }
    var oneTimePinMessage by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingSosAlias by remember { mutableStateOf<String?>(null) }
    var pendingGeofenceId by remember { mutableStateOf<String?>(null) }
    var pendingConsentChildId by remember { mutableStateOf<String?>(null) }

    fun showResult(result: Result<*>, success: String) {
        message = if (result.isSuccess) success else (result.exceptionOrNull()?.message ?: "The action could not be completed.")
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val id = pendingGeofenceId
        if (granted && id != null) {
            viewModel.enableSafePlaceGeofence(id, true) { result -> showResult(result, "Safe-place geofence registered on this device. Verify the child's status; a boundary event alone does not prove safety.") }
        } else if (id != null) {
            message = "Background location is required for automatic arrival/exit alerts. Enable it in Android Settings to continue."
        }
        pendingGeofenceId = null
    }
    val fineLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val id = pendingGeofenceId
        if (!granted) {
            message = "Precise location permission is required to register a safe-place geofence."
            pendingGeofenceId = null
        } else if (id != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else if (id != null) {
            viewModel.enableSafePlaceGeofence(id, true) { result -> showResult(result, "Safe-place geofence registered on this device. Verify the child's status; a boundary event alone does not prove safety.") }
            pendingGeofenceId = null
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Column {
                Text("Child & Family Safety", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Text("Local-first profile, journey and school-handover tools", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Privacy boundary", fontWeight = FontWeight.SemiBold)
                Text("Use child aliases, not full legal names or birth dates. These records currently stay on this device and are not yet synced to the backend or another guardian's phone.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (message != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message!!, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { message = null }) { Text("Dismiss") }
                }
            }
        }

        SectionCard("1 · Add a child profile alias", Icons.Default.BabyChangingStation) {
            OutlinedTextField(alias, { alias = it }, label = { Text("Child alias / profile label") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(ageBand, { ageBand = it }, label = { Text("Age band (e.g. 8–12)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(school, { school = it }, label = { Text("School (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(guardian, { guardian = it }, label = { Text("Guardian label") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(safePlace, { safePlace = it }, label = { Text("Safe-place name (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(latitude, { latitude = it }, label = { Text("Latitude") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(longitude, { longitude = it }, label = { Text("Longitude") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            OutlinedTextField(radius, { radius = it }, label = { Text("Geofence radius in metres (100–1000)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = {
                viewModel.createChildProfile(alias, ageBand, school, guardian, safePlace, latitude, longitude, radius) { result ->
                    if (result.isSuccess) {
                        message = "Child profile saved locally."
                        alias = ""; ageBand = "8–12"; school = ""; guardian = ""; safePlace = ""; latitude = ""; longitude = ""; radius = "150"
                    } else message = result.exceptionOrNull()?.message ?: "Profile could not be saved."
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Save profile") }
        }

        SectionCard("2 · Linked local profiles", Icons.Default.BabyChangingStation) {
            if (children.isEmpty()) Text("No child profiles have been saved on this device yet.", style = MaterialTheme.typography.bodySmall)
            children.forEach { child -> ChildProfileCard(
                child = child,
                onSos = { pendingSosAlias = child.displayName },
                onGeofence = {
                    pendingConsentChildId = child.id
                }
            ) }
        }

        SectionCard("3 · Safe journey check-ins", Icons.Default.LocationOn) {
            OutlinedTextField(journeyChild, { journeyChild = it }, label = { Text("Child alias (must match profile) ") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(origin, { origin = it }, label = { Text("Journey start") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(destination, { destination = it }, label = { Text("Destination") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(etaMinutes, { etaMinutes = it }, label = { Text("Expected arrival in minutes (5–1440)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = {
                viewModel.createSafetyJourney(journeyChild, origin, destination, etaMinutes) { result ->
                    showResult(result, "Journey saved. Missed arrival will raise a local alert; it does not prove an emergency.")
                    if (result.isSuccess) { origin = ""; destination = "" }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Start safe journey") }
            if (journeys.isEmpty()) Text("No journeys saved.", style = MaterialTheme.typography.bodySmall)
            journeys.forEach { journey -> JourneyCard(journey) { id -> viewModel.markSafetyJourneySafe(id) { result -> showResult(result, "Journey check-in recorded.") } } }
        }

        SectionCard("4 · School pickup / drop verification", Icons.Default.School) {
            OutlinedTextField(handoverChild, { handoverChild = it }, label = { Text("Existing child profile alias") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(handoverSchool, { handoverSchool = it }, label = { Text("School / pickup point") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(handoverGuardian, { handoverGuardian = it }, label = { Text("Authorized guardian label") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(handoverMinutes, { handoverMinutes = it }, label = { Text("Pickup time in minutes from now") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = {
                viewModel.createSchoolHandover(handoverChild, handoverSchool, handoverGuardian, handoverMinutes) { result ->
                    if (result.isSuccess) {
                        val created = result.getOrThrow()
                        oneTimePinMessage = "Handover: ${created.first.id}\nOne-time PIN: ${created.second}\nShown once. Share only with the authorized guardian."
                        message = "Pickup handover saved locally. It is not connected to a real school system."
                    } else message = result.exceptionOrNull()?.message ?: "Handover could not be created."
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Create handover and generate one-time PIN") }
            OutlinedTextField(verifyPin, { verifyPin = it.take(6) }, label = { Text("Six-digit pickup PIN") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (handovers.isEmpty()) Text("No handovers created.", style = MaterialTheme.typography.bodySmall)
            handovers.forEach { handover -> HandoverCard(handover, verifyPin) { id ->
                viewModel.verifySchoolHandover(id, verifyPin) { result ->
                    showResult(result, "PIN accepted once. School and guardian identity still require real-world confirmation.")
                    if (result.isSuccess) verifyPin = ""
                }
            } }
        }

        SectionCard("5 · Safety event history", Icons.Default.NotificationsActive) {
            if (alerts.isEmpty()) Text("No safety alerts yet.", style = MaterialTheme.typography.bodySmall)
            alerts.take(30).forEach { alert ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(alert.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(alert.severity, style = MaterialTheme.typography.labelSmall, color = if (alert.severity == "HIGH") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(alert.message, style = MaterialTheme.typography.bodySmall)
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(alert.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!alert.isRead) TextButton(onClick = { viewModel.markChildSafetyAlertRead(alert.id) }) { Text("Mark read") }
                    Divider()
                }
            }
        }
        Text("Native geofences depend on Android background-location permission and device availability. This local screen does not sync to the remote police dashboard yet. Child SOS uses the existing app emergency pipeline if that backend endpoint is configured.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (pendingConsentChildId != null) {
        val selectedChild = children.firstOrNull { it.id == pendingConsentChildId }
        AlertDialog(
            onDismissRequest = { pendingConsentChildId = null },
            title = { Text("Confirm guardian consent") },
            text = {
                Text(
                    "Confirm that the child's authorized guardian has agreed to location monitoring for ${selectedChild?.displayName ?: "this child"}. Raksha360 stores this acknowledgement locally. Android may also require precise and background-location permissions."
                )
            },
            confirmButton = {
                Button(onClick = {
                    val childId = pendingConsentChildId
                    pendingConsentChildId = null
                    if (childId != null) {
                        pendingGeofenceId = childId
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            fineLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            viewModel.enableSafePlaceGeofence(childId, true) { result ->
                                showResult(result, "Safe-place geofence registered on this device. Verify the child's status; a boundary event alone does not prove safety.")
                            }
                            pendingGeofenceId = null
                        }
                    }
                }) { Text("Confirm and continue") }
            },
            dismissButton = {
                TextButton(onClick = { pendingConsentChildId = null }) { Text("Cancel") }
            }
        )
    }

    if (pendingSosAlias != null) {
        AlertDialog(
            onDismissRequest = { pendingSosAlias = null },
            title = { Text("Trigger Child SOS?") },
            text = { Text("This will create an emergency event for the existing configured emergency pipeline. Only use for a real emergency, or cancel now." ) },
            confirmButton = { Button(onClick = { val child = pendingSosAlias; pendingSosAlias = null; child?.let(onTriggerChildSos) }) { Text("Trigger emergency SOS") } },
            dismissButton = { TextButton(onClick = { pendingSosAlias = null }) { Text("Cancel") } }
        )
    }
    if (oneTimePinMessage != null) {
        AlertDialog(
            onDismissRequest = { oneTimePinMessage = null },
            title = { Text("One-time pickup PIN") },
            text = { Text(oneTimePinMessage!!) },
            confirmButton = { Button(onClick = { oneTimePinMessage = null }) { Text("I have saved it") } }
        )
    }
}

@Composable
private fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            content()
        }
    }
}

@Composable
private fun ChildProfileCard(child: ChildProfileEntity, onSos: () -> Unit, onGeofence: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(child.displayName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            Text("Age band: ${child.ageBand} · School: ${child.schoolName.ifBlank { "Not set" }}", style = MaterialTheme.typography.bodySmall)
            Text("Guardian: ${child.guardianLabel.ifBlank { "Not set" }} · Geofence: ${if (child.locationSharingEnabled) "enabled on this device" else "not enabled"}", style = MaterialTheme.typography.bodySmall)
            Text(
                if (child.locationConsentAt != null) "Guardian consent acknowledgement recorded locally: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(child.locationConsentAt))}"
                else "Guardian location consent: not recorded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (child.safePlaceLatitude == null || child.safePlaceLongitude == null) Text("Add safe-place coordinates when creating the profile to enable geofence alerts.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSos, modifier = Modifier.weight(1f)) { Text("Child SOS") }
                OutlinedButton(onClick = onGeofence, modifier = Modifier.weight(1f), enabled = child.safePlaceLatitude != null && child.safePlaceLongitude != null) { Text("Enable geofence") }
            }
        }
    }
}

@Composable
private fun JourneyCard(journey: SafetyJourneyEntity, onCheckIn: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("${journey.childLabel}: ${journey.originLabel} → ${journey.destinationLabel}", fontWeight = FontWeight.SemiBold)
            Text("Status: ${journey.status} · Arrival expected ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(journey.expectedArrivalAt))}", style = MaterialTheme.typography.bodySmall)
            if (journey.status == "ACTIVE" || journey.status == "OVERDUE") OutlinedButton(onClick = { onCheckIn(journey.id) }, modifier = Modifier.fillMaxWidth()) { Text("Mark safe / check in") }
        }
    }
}

@Composable
private fun HandoverCard(handover: SchoolHandoverEntity, pin: String, onVerify: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("${handover.childLabel} · ${handover.schoolName}", fontWeight = FontWeight.SemiBold)
            Text("Guardian: ${handover.authorizedGuardian} · Status: ${handover.status}", style = MaterialTheme.typography.bodySmall)
            Text("Pickup: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(handover.scheduledAt))}", style = MaterialTheme.typography.bodySmall)
            if (handover.status != "VERIFIED" && handover.status != "EXPIRED" && handover.status != "REVIEW_REQUIRED") {
                OutlinedButton(onClick = { onVerify(handover.id) }, enabled = pin.matches(Regex("[0-9]{6}")), modifier = Modifier.fillMaxWidth()) { Text("Verify one-time PIN") }
            }
        }
    }
}
