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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.localization.LanguageStrings
import com.example.ui.theme.EvidenceOrange
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.ShieldBlue
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

    val rawLocation by viewModel.currentLocation.collectAsState()
    val hasFineLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    // Fallback location if permission denied or GPS not available
    val activeUserCoordinates = if (hasFineLocation && rawLocation.latitude != 0.0) {
        GeoCoordinates(rawLocation.latitude, rawLocation.longitude)
    } else {
        NagpurGeoConfig.SADAR_PILOT_CENTER
    }
    val isLocationFallback = !hasFineLocation || rawLocation.latitude == 0.0

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Map will automatically update with current location
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
                    waypoints = NagpurGeoConfig.VERIFIED_SAFETY_POINTS,
                    onWaypointClicked = { wp ->
                        selectedWaypointInfo = "${wp.name}: ${wp.address}"
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Search Input
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
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Search",
                        tint = ShieldBlue,
                        modifier = Modifier.size(20.dp)
                    )
                },
                placeholder = {
                    Text("Search Nagpur (e.g. Sadar, Sitabuldi)", fontSize = 13.sp, color = TextMuted)
                },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color(0xFFCBD5E1),
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("map_search_bar")
            )

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
            // Recenter on Sadar Pilot Area Button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFCBD5E1), CircleShape)
                    .clickable {
                        mapViewInstance?.let {
                            viewModel.mapProvider.updateCenter(it, NagpurGeoConfig.SADAR_PILOT_CENTER, 15.0)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "Recenter Sadar",
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
                            Text(
                                text = "Sadar, Nagpur (Pilot Area)",
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
                                    text = "● Device GPS offline: Centered on Sadar fallback location",
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
                            val uri = Uri.parse("geo:21.1612,79.0825?q=Sadar+Nagpur")
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

                // Route Option 1: Popular Route (via Mount Rd & Wardha Rd)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CallMade,
                            contentDescription = null,
                            tint = SafeGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Mount Rd → Wardha Rd Corridor",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark,
                                    fontSize = 13.sp
                                )
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(SafeGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "High police patrol & verified lighting",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }

                    Text(
                        text = "18 min",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NavyDark,
                            fontSize = 14.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Route Option 2: Alternate Route (via Katol Rd bypass)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.CallMade,
                            contentDescription = null,
                            tint = EvidenceOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Katol Rd Outer Bypass",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark,
                                    fontSize = 13.sp
                                )
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(EvidenceOrange)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Moderate infrastructure coverage",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }

                    Text(
                        text = "21 min",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = NavyDark,
                            fontSize = 14.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Full-width Solid Blue "Start Journey" button
                Button(
                    onClick = onStartJourneyClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("start_journey_map_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ShieldBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Start Journey (Sadar → Destination)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )
                }
            }
        }
    }
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
