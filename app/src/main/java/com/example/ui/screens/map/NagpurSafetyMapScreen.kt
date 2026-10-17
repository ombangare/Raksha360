package com.example.ui.screens.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.List
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.domain.map.GeoCoordinates
import com.example.domain.map.NagpurGeoConfig
import com.example.domain.map.SafetyWaypoint
import com.example.localization.LanguageStrings
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EvidenceOrange
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.ShieldBlue
import com.example.service.location.LocationStatus
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel

@Composable
fun NagpurSafetyMapScreen(
    viewModel: MainViewModel,
    strings: LanguageStrings,
    onStartJourneyClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("Sadar, Nagpur") }
    var selectedFilter by remember { mutableStateOf("Safety View") }
    var selectedWaypointInfo by remember { mutableStateOf<String?>(null) }
    var showAllStationsDialog by remember { mutableStateOf(false) }
    var directorySearchQuery by remember { mutableStateOf("") }

    val rawLocation by viewModel.currentLocation.collectAsState()
    val policeStations by viewModel.policeStations.collectAsState()

    val hasFineLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    // Fallback location if permission denied or GPS not available
    val activeUserCoordinates = if (hasFineLocation && rawLocation.latitude != 0.0) {
        GeoCoordinates(rawLocation.latitude, rawLocation.longitude)
    } else {
        NagpurGeoConfig.NAGPUR_CENTER
    }
    val isLocationFallback = !hasFineLocation || rawLocation.latitude == 0.0

    // Combine all 37 verified Nagpur police stations with verified hospitals and campuses
    val allWaypoints = remember(policeStations) {
        val stationWaypoints = policeStations.mapNotNull { station ->
            val lat = station.latitude
            val lon = station.longitude
            if (lat != null && lon != null && lat != 0.0) {
                SafetyWaypoint(
                    id = station.stationId,
                    name = station.name,
                    category = "POLICE_STATION",
                    coordinates = GeoCoordinates(lat, lon),
                    contactNumber = station.phone ?: "112",
                    address = "${station.address} • ${station.jurisdictionLabel}"
                )
            } else null
        }
        val nonStationPoints = NagpurGeoConfig.VERIFIED_SAFETY_POINTS.filter { it.category != "POLICE_STATION" }
        if (stationWaypoints.isNotEmpty()) {
            stationWaypoints + nonStationPoints
        } else {
            NagpurGeoConfig.VERIFIED_SAFETY_POINTS
        }
    }

    // Determine the nearest verified police station to the user's active coordinates
    val nearestStation = remember(activeUserCoordinates, allWaypoints) {
        allWaypoints.filter { it.category == "POLICE_STATION" }
            .minByOrNull { wp ->
                calculateDistanceKm(
                    activeUserCoordinates.latitude,
                    activeUserCoordinates.longitude,
                    wp.coordinates.latitude,
                    wp.coordinates.longitude
                )
            }
    }
    val nearestDistanceKm = remember(activeUserCoordinates, nearestStation) {
        nearestStation?.let {
            calculateDistanceKm(
                activeUserCoordinates.latitude,
                activeUserCoordinates.longitude,
                it.coordinates.latitude,
                it.coordinates.longitude
            )
        } ?: 0.0
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Map will automatically update with current location
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val text = res.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) {
            searchQuery = text
        }
    }

    var mapViewInstance by remember { mutableStateOf<View?>(null) }
    val safetyAreaContext = remember { viewModel.safetyScoringEngine.computeAreaSafety("Sadar, Nagpur") }

    DisposableEffect(Unit) {
        onDispose {
            mapViewInstance?.let { viewModel.mapProvider.onDestroy(it) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFE5ECE5))
            .testTag("map_screen")
    ) {
        // 1. Real Interactive Native Android OpenStreetMap View
        AndroidView(
            factory = { ctx ->
                val view = viewModel.mapProvider.createMapView(
                    context = ctx,
                    center = NagpurGeoConfig.SADAR_PILOT_CENTER,
                    zoomLevel = 15.0,
                    userLocation = activeUserCoordinates,
                    isUserLocationFallback = isLocationFallback,
                    waypoints = allWaypoints,
                    onWaypointClicked = { wp ->
                        selectedWaypointInfo = "${wp.name}\n${wp.address}\nTel: ${wp.contactNumber ?: "112"}"
                    }
                )
                mapViewInstance = view
                view
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top Bar with Search & Filter Pills
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Search Input with Clear & Voice actions
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        IconButton(onClick = {
                            val speechIntent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Say a place or police station in Nagpur")
                            }
                            try {
                                speechLauncher.launch(speechIntent)
                            } catch (_: Exception) {}
                        }) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Search",
                                tint = ShieldBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                placeholder = {
                    Text("Search Nagpur (e.g. Sadar, Sitabuldi)", fontSize = 13.sp, color = TextMuted)
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = NavyDark,
                    unfocusedTextColor = NavyDark,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = ShieldBlue,
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("map_search_bar")
            )

            // Search Suggestions Dropdown (across all 37 police stations and safety points)
            val searchMatches = if (searchQuery.isNotBlank() && searchQuery != "Sadar, Nagpur") {
                allWaypoints.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.address.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true)
                }.take(5)
            } else emptyList()

            if (searchMatches.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        searchMatches.forEach { match ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = match.name
                                        mapViewInstance?.let {
                                            viewModel.mapProvider.updateCenter(it, match.coordinates, 16.0)
                                        }
                                        selectedWaypointInfo = "${match.name}\n${match.address}\nTel: ${match.contactNumber ?: "112"}"
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (match.category == "POLICE_STATION") Icons.Default.Shield else Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = ShieldBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(match.name, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = NavyDark))
                                    Text(match.address, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontSize = 11.sp), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3 Filter Pills: Safety View, Preferred Route, Police & Help
            Row(modifier = Modifier.fillMaxWidth()) {
                MapFilterPill(
                    title = "Safety View",
                    isSelected = selectedFilter == "Safety View",
                    onClick = { selectedFilter = "Safety View" }
                )
                Spacer(modifier = Modifier.width(8.dp))
                MapFilterPill(
                    title = "Preferred Route",
                    isSelected = selectedFilter == "Preferred Route",
                    onClick = { selectedFilter = "Preferred Route" }
                )
                Spacer(modifier = Modifier.width(8.dp))
                MapFilterPill(
                    title = "Police & Help",
                    isSelected = selectedFilter == "Police & Help",
                    onClick = { selectedFilter = "Police & Help" }
                )
            }

            // Safety Data Status Indicator (Honest disclosure: No fabricated police data)
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.92f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = if (safetyAreaContext.policeDataAvailable) SafeGreen else Color(0xFFD97706),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (safetyAreaContext.policeDataAvailable) {
                            "Nagpur Police Dataset Active"
                        } else {
                            "No police dataset connected • Baseline infrastructure active"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = NavyDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }

        // 3. Floating Action Controls (Right side: Recenter Sadar, My Location)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        ) {
            // Quick Action: Nearest Police Station FAB
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF38BDF8), CircleShape)
                    .clickable {
                        nearestStation?.let { st ->
                            mapViewInstance?.let {
                                viewModel.mapProvider.updateCenter(it, st.coordinates, 16.5)
                            }
                            selectedWaypointInfo = "${st.name}\n${st.address}\nTel: ${st.contactNumber ?: "112"}"
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Nearest Police Station",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Action: All 37 Nagpur Stations Directory FAB
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFCBD5E1), CircleShape)
                    .clickable {
                        showAllStationsDialog = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Nagpur Police Directory",
                    tint = ShieldBlue,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Recenter Map Button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFCBD5E1), CircleShape)
                    .clickable {
                        mapViewInstance?.let {
                            val target = if (!isLocationFallback) activeUserCoordinates else NagpurGeoConfig.NAGPUR_CENTER
                            viewModel.mapProvider.updateCenter(it, target, 15.0)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "Recenter Map",
                    tint = ShieldBlue,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // My Location Button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFCBD5E1), CircleShape)
                    .clickable {
                        if (hasFineLocation) {
                            mapViewInstance?.let {
                                viewModel.mapProvider.updateCenter(it, activeUserCoordinates, 16.0)
                            }
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "My Location",
                    tint = if (hasFineLocation) SafeGreen else NavyDark,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 4. OpenStreetMap Attribution Notice (Mandatory compliance)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 250.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.85f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "© OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    color = Color(0xFF475569)
                )
            )
        }

        // 5. Bottom Sheet Route Card
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("map_route_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Location Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(EvidenceOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = EvidenceOrange,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            val mapLocationTitle = when (rawLocation.status) {
                                LocationStatus.ACTIVE_GPS -> rawLocation.description.ifBlank { "Nagpur, Maharashtra" }
                                LocationStatus.DEMO_MODE -> "Nagpur (Demo Simulation)"
                                LocationStatus.PERMISSION_DENIED -> "Location Permission Denied"
                                LocationStatus.GPS_DISABLED -> "Device GPS Disabled"
                                LocationStatus.STALE -> rawLocation.description.ifBlank { "Last Known Location" }
                                else -> "Nagpur Safety Map"
                            }
                            Text(
                                text = mapLocationTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark,
                                    fontSize = 16.sp
                                )
                            )
                            Text(
                                text = safetyAreaContext.advisoryNotice,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            )
                            if (isLocationFallback) {
                                Text(
                                    text = "● Device GPS offline: Centered on Nagpur reference coordinates",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFFD97706),
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }

                    // External Navigation Intent
                    IconButton(
                        onClick = {
                            val uri = if (!isLocationFallback && activeUserCoordinates.latitude != 0.0) {
                                Uri.parse("geo:${activeUserCoordinates.latitude},${activeUserCoordinates.longitude}?q=${activeUserCoordinates.latitude},${activeUserCoordinates.longitude}")
                            } else {
                                Uri.parse("geo:21.1458,79.0882?q=Nagpur")
                            }
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "Navigate Externally",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Dynamic Nearest Police Station Highlight
                if (nearestStation != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                mapViewInstance?.let {
                                    viewModel.mapProvider.updateCenter(it, nearestStation.coordinates, 16.5)
                                }
                                selectedWaypointInfo = "${nearestStation.name}\n${nearestStation.address}\nTel: ${nearestStation.contactNumber ?: "112"}"
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0F172A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = nearestStation.name,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = NavyDark,
                                            fontSize = 13.sp
                                        )
                                    )
                                    Text(
                                        text = "Nearest Station • ${String.format("%.1f km away", nearestDistanceKm)}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = SafeGreen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Text(
                                        text = "Direct: ${nearestStation.contactNumber ?: "112"}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            // 1-Tap Dial Station
                            Button(
                                onClick = {
                                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${nearestStation.contactNumber ?: "112"}"))
                                    context.startActivity(dialIntent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Call", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Navigation / Directory Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showAllStationsDialog = true }
                    ) {
                        Icon(Icons.Default.List, contentDescription = null, tint = ShieldBlue, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "All 37 Nagpur Stations",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = ShieldBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }

                    TextButton(
                        onClick = {
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                            context.startActivity(dialIntent)
                        }
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = EmergencyRed, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Call 112",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = EmergencyRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Full-width Solid Blue "Start Journey" button
                Button(
                    onClick = onStartJourneyClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("start_journey_map_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ShieldBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Start Safe Journey",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )
                }
            }
        }

        // Waypoint / Safety Point Inspection Dialog
        if (selectedWaypointInfo != null) {
            val info = selectedWaypointInfo ?: ""
            AlertDialog(
                onDismissRequest = { selectedWaypointInfo = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = ShieldBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verified Safety Point", fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Text(info, style = MaterialTheme.typography.bodyMedium.copy(color = NavyDark, lineHeight = 20.sp))
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val phoneRegex = Regex("""(?:Tel:\s*|\+?91)?(\d{7,11})""")
                            val match = phoneRegex.find(info)
                            val number = match?.value?.replace("Tel:", "")?.trim() ?: "112"
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                            context.startActivity(dialIntent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ShieldBlue)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Call Helpline")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedWaypointInfo = null }) {
                        Text("Close")
                    }
                }
            )
        }

        // Nagpur Police Stations Directory Dialog (37 Verified Stations)
        if (showAllStationsDialog) {
            val filteredDirectory = remember(directorySearchQuery, policeStations) {
                if (directorySearchQuery.isBlank()) {
                    policeStations
                } else {
                    policeStations.filter {
                        it.name.contains(directorySearchQuery, ignoreCase = true) ||
                        it.address.contains(directorySearchQuery, ignoreCase = true) ||
                        it.jurisdictionLabel.contains(directorySearchQuery, ignoreCase = true)
                    }
                }
            }

            AlertDialog(
                onDismissRequest = { showAllStationsDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = ShieldBlue, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Nagpur Police Directory", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("37 Verified Police Stations", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = directorySearchQuery,
                            onValueChange = { directorySearchQuery = it },
                            placeholder = { Text("Search by station or jurisdiction", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            shape = RoundedCornerShape(8.dp)
                        )

                        LazyColumn(modifier = Modifier.height(340.dp)) {
                            items(filteredDirectory) { station ->
                                val stLat = station.latitude ?: 21.1458
                                val stLon = station.longitude ?: 79.0882
                                val stationDist = calculateDistanceKm(
                                    activeUserCoordinates.latitude,
                                    activeUserCoordinates.longitude,
                                    stLat,
                                    stLon
                                )
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = station.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = NavyDark,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = String.format("%.1f km", stationDist),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = SafeGreen
                                            )
                                        }

                                        Text(
                                            text = "${station.jurisdictionLabel} • ${station.address}",
                                            fontSize = 11.sp,
                                            color = TextSecondary,
                                            lineHeight = 14.sp
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    showAllStationsDialog = false
                                                    mapViewInstance?.let {
                                                        viewModel.mapProvider.updateCenter(
                                                            it,
                                                            GeoCoordinates(stLat, stLon),
                                                            16.5
                                                        )
                                                    }
                                                    selectedWaypointInfo = "${station.name}\n${station.address}\nTel: ${station.phone ?: "112"}"
                                                }
                                            ) {
                                                Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Locate", fontSize = 11.sp)
                                            }

                                            Button(
                                                onClick = {
                                                    val phone = station.phone ?: "112"
                                                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                                    context.startActivity(dialIntent)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(13.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Call", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAllStationsDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0 // Earth radius km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
}

@Composable
private fun MapFilterPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) ShieldBlue else Color.White)
            .border(1.dp, if (isSelected) ShieldBlue else Color(0xFFCBD5E1), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else NavyDark,
                fontSize = 12.sp
            )
        )
    }
}
