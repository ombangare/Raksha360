package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EvidenceOrange
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.ShieldBlue

@Composable
fun NaariKavachTopBar(
    appName: String,
    currentLang: String,
    isDemoMode: Boolean,
    pendingQueueCount: Int,
    onLanguageChange: (String) -> Unit,
    onSyncClick: () -> Unit,
    onDemoModeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var langMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Branding & Status
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = NavyDark,
                            fontSize = 20.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (isDemoMode) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(EvidenceOrange.copy(alpha = 0.15f))
                                .clickable { onDemoModeClick() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("demo_mode_badge")
                        ) {
                            Text(
                                text = "DEMO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = EvidenceOrange,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }

                // Subtitle / City focus
                Text(
                    text = "Raksha360 • Nagpur Safety Network",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }

            // Right side: Queue sync indicator + Language selector
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Offline Sync Queue Pill
                if (pendingQueueCount > 0) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(EvidenceOrange.copy(alpha = 0.15f))
                            .clickable { onSyncClick() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("sync_queue_pill"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync Queue",
                            tint = EvidenceOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$pendingQueueCount queued",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = EvidenceOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    // Online dot
                    Row(
                        modifier = Modifier
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SafeGreen)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Live",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = SafeGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                // Language Selector
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ShieldBlue.copy(alpha = 0.1f))
                            .clickable { langMenuExpanded = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("language_selector"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Change Language",
                            tint = ShieldBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = currentLang.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = ShieldBlue,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    DropdownMenu(
                        expanded = langMenuExpanded,
                        onDismissRequest = { langMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("English (EN)") },
                            onClick = {
                                onLanguageChange("en")
                                langMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("हिंदी (Hindi)") },
                            onClick = {
                                onLanguageChange("hi")
                                langMenuExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("मराठी (Marathi)") },
                            onClick = {
                                onLanguageChange("mr")
                                langMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
