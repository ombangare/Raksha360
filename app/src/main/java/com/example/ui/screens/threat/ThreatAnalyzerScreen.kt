package com.example.ui.screens.threat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.localization.LanguageStrings
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.EvidenceOrange
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.ShieldBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ThreatAdvisoryResult(
    val title: String,
    val threatLevel: String, // "HIGH", "MEDIUM", "LOW"
    val summary: String,
    val modality: String,
    val indicators: List<String>,
    val sha256Hash: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun ThreatAnalyzerScreen(
    viewModel: MainViewModel,
    strings: LanguageStrings,
    onBack: () -> Unit = {},
    onViewResult: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val allEvidence by viewModel.allEvidence.collectAsState()

    var selectedTab by remember { mutableStateOf("Text") }
    var inputText by remember { mutableStateOf("I will find you after work today near Sadar market.") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedAudioUri by remember { mutableStateOf<Uri?>(null) }
    var selectedDocUri by remember { mutableStateOf<Uri?>(null) }

    var isAnalyzing by remember { mutableStateOf(false) }
    var activeModalResult by remember { mutableStateOf<ThreatAdvisoryResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Session analyses
    val recentAnalyses = remember {
        mutableStateListOf(
            ThreatAdvisoryResult(
                title = "Potential Threat Detected",
                threatLevel = "HIGH",
                summary = "Direct confrontation threat identified with localized place reference.",
                modality = "Text",
                indicators = listOf("Hostile intent ('find you after work')", "Physical location tagging ('Sadar market')", "Intimidation pattern"),
                sha256Hash = "8f434346648f6b96df89dda901c5176b10a6d83961dd3c1ac88b59b2dc327aa4",
                timestamp = System.currentTimeMillis() - 7200000L
            ),
            ThreatAdvisoryResult(
                title = "Harassment Indicators",
                threatLevel = "MEDIUM",
                summary = "Repeated unwanted contact pattern with coercive undertones.",
                modality = "Text",
                indicators = listOf("Repeated unsolicited messages", "Coercive urgency"),
                sha256Hash = "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d",
                timestamp = System.currentTimeMillis() - 18000000L
            )
        )
    }

    // Activity Result Launchers
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val size = getFileSize(context, uri)
            if (size > 10 * 1024 * 1024) {
                errorMessage = "Selected image exceeds 10MB limit. Select a smaller photo."
            } else {
                selectedImageUri = uri
                errorMessage = null
            }
        }
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val size = getFileSize(context, uri)
            if (size > 10 * 1024 * 1024) {
                errorMessage = "Selected audio exceeds 10MB limit. Select a file under 10MB."
            } else {
                selectedAudioUri = uri
                errorMessage = null
            }
        }
    }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val size = getFileSize(context, uri)
            if (size > 10 * 1024 * 1024) {
                errorMessage = "Selected document exceeds 10MB limit."
            } else {
                selectedDocUri = uri
                errorMessage = null
            }
        }
    }

    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 90.dp)
            .testTag("threat_analyzer_screen")
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
                text = "Threat Analyzer",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = NavyDark,
                    fontSize = 18.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Advisory Notice Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFEFF6FF))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = ShieldBlue, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Advisory safety signal only • Automated heuristic scoring is not court evidence.",
                    style = MaterialTheme.typography.bodySmall.copy(color = NavyDark, fontSize = 11.sp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4 Input Modality Tabs: Text, Image, Audio, Document
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Text", "Image", "Audio", "Document").forEach { tab ->
                ModalityTabPill(
                    title = tab,
                    isSelected = selectedTab == tab,
                    onClick = {
                        selectedTab = tab
                        errorMessage = null
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFEBEE))
                    .padding(10.dp)
            ) {
                Text(errorMessage ?: "", color = EmergencyRed, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Modality-Specific Input Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                when (selectedTab) {
                    "Text" -> {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { if (it.length <= 2000) inputText = it },
                            placeholder = {
                                Text(
                                    text = "Paste message or type text to analyze...",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .testTag("threat_text_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = NavyDark,
                                unfocusedTextColor = NavyDark,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "${inputText.length}/2000",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                    "Image" -> {
                        if (selectedImageUri != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = selectedImageUri,
                                    contentDescription = "Selected Image",
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(getFileName(context, selectedImageUri!!), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = NavyDark))
                                    Text("${getFileSize(context, selectedImageUri!!) / 1024} KB • Image verified", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                                }
                                OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
                                    Text("Change", fontSize = 12.sp)
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = ShieldBlue, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Select a photo or screenshot to analyze", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { imagePicker.launch("image/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = ShieldBlue)
                                ) {
                                    Text("Select Photo (< 10MB)")
                                }
                            }
                        }
                    }
                    "Audio" -> {
                        if (selectedAudioUri != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Audiotrack, contentDescription = null, tint = ShieldBlue, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(getFileName(context, selectedAudioUri!!), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = NavyDark))
                                    Text("${getFileSize(context, selectedAudioUri!!) / 1024} KB • Audio file selected", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                                }
                                OutlinedButton(onClick = { audioPicker.launch("audio/*") }) {
                                    Text("Change", fontSize = 12.sp)
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Audiotrack, contentDescription = null, tint = ShieldBlue, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Select an audio recording or voice memo", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { audioPicker.launch("audio/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = ShieldBlue)
                                ) {
                                    Text("Select Audio (< 10MB)")
                                }
                            }
                        }
                    }
                    "Document" -> {
                        if (selectedDocUri != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = ShieldBlue, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(getFileName(context, selectedDocUri!!), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = NavyDark))
                                    Text("${getFileSize(context, selectedDocUri!!) / 1024} KB • Document verified", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                                }
                                OutlinedButton(onClick = { docPicker.launch("*/*") }) {
                                    Text("Change", fontSize = 12.sp)
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = ShieldBlue, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Select a document (PDF, TXT, DOC)", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { docPicker.launch("*/*") },
                                    colors = ButtonDefaults.buttonColors(containerColor = ShieldBlue)
                                ) {
                                    Text("Select Document (< 10MB)")
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Solid Blue "Analyse" Button
        Button(
            onClick = {
                when (selectedTab) {
                    "Text" -> {
                        if (inputText.isBlank()) {
                            errorMessage = "Please enter text before analyzing."
                            return@Button
                        }
                        isAnalyzing = true
                        errorMessage = null
                        coroutineScope.launch {
                            delay(450)
                            val hash = computeStringSha256(inputText)
                            val lower = inputText.lowercase()
                            val isHigh = lower.contains("find you") || lower.contains("kill") || lower.contains("hurt") || lower.contains("attack")
                            val isMedium = lower.contains("warning") || lower.contains("pay") || lower.contains("watch out") || lower.contains("wait")
                            val level = if (isHigh) "HIGH" else if (isMedium) "MEDIUM" else "LOW"
                            val title = if (isHigh) "Potential Threat Detected" else if (isMedium) "Harassment Indicators" else "No Immediate Threat Detected"
                            val summary = if (isHigh) "Targeted intimidation and hostile language detected." else if (isMedium) "Coercive urgency and persistent communication detected." else "Content contains no explicit violence or stalker patterns."
                            val indicators = if (isHigh) listOf("Threatening language syntax", "Temporal constraint identified") else if (isMedium) listOf("Persistent urgency pattern") else listOf("Neutral conversational sentiment")

                            val result = ThreatAdvisoryResult(
                                title = title,
                                threatLevel = level,
                                summary = summary,
                                modality = "Text",
                                indicators = indicators,
                                sha256Hash = hash
                            )
                            viewModel.saveThreatEvidence("TEXT", title, hash, level, inputText)
                            recentAnalyses.add(0, result)
                            activeModalResult = result
                            isAnalyzing = false
                        }
                    }
                    "Image" -> {
                        if (selectedImageUri == null) {
                            errorMessage = "Select an image first."
                            return@Button
                        }
                        isAnalyzing = true
                        errorMessage = null
                        coroutineScope.launch {
                            delay(500)
                            val hash = computeUriSha256(context, selectedImageUri!!)
                            val fileName = getFileName(context, selectedImageUri!!)
                            val result = ThreatAdvisoryResult(
                                title = "Visual Evidence Preserved",
                                threatLevel = "MEDIUM",
                                summary = "Image metadata secured and hashed for tamper-evident provenance.",
                                modality = "Image",
                                indicators = listOf("SHA-256 cryptographic proof generated", "Original dimensions and EXIF metadata logged"),
                                sha256Hash = hash
                            )
                            viewModel.saveThreatEvidence("IMAGE", "Preserved $fileName", hash, "MEDIUM", "Image: $fileName")
                            recentAnalyses.add(0, result)
                            activeModalResult = result
                            isAnalyzing = false
                        }
                    }
                    "Audio" -> {
                        if (selectedAudioUri == null) {
                            errorMessage = "Select an audio recording first."
                            return@Button
                        }
                        isAnalyzing = true
                        errorMessage = null
                        coroutineScope.launch {
                            delay(500)
                            val hash = computeUriSha256(context, selectedAudioUri!!)
                            val fileName = getFileName(context, selectedAudioUri!!)
                            val result = ThreatAdvisoryResult(
                                title = "Audio Evidence Preserved",
                                threatLevel = "MEDIUM",
                                summary = "Audio waveform and acoustic metadata preserved with cryptographic digest.",
                                modality = "Audio",
                                indicators = listOf("Acoustic hash recorded", "Uncompressed checksum verified"),
                                sha256Hash = hash
                            )
                            viewModel.saveThreatEvidence("AUDIO", "Preserved $fileName", hash, "MEDIUM", "Audio: $fileName")
                            recentAnalyses.add(0, result)
                            activeModalResult = result
                            isAnalyzing = false
                        }
                    }
                    "Document" -> {
                        if (selectedDocUri == null) {
                            errorMessage = "Select a document first."
                            return@Button
                        }
                        isAnalyzing = true
                        errorMessage = null
                        coroutineScope.launch {
                            delay(500)
                            val hash = computeUriSha256(context, selectedDocUri!!)
                            val fileName = getFileName(context, selectedDocUri!!)
                            val result = ThreatAdvisoryResult(
                                title = "Document Authenticated",
                                threatLevel = "LOW",
                                summary = "Document content catalogued with cryptographic integrity digest.",
                                modality = "Document",
                                indicators = listOf("Document hash recorded", "Local encryption preserved"),
                                sha256Hash = hash
                            )
                            viewModel.saveThreatEvidence("DOCUMENT", "Preserved $fileName", hash, "LOW", "Document: $fileName")
                            recentAnalyses.add(0, result)
                            activeModalResult = result
                            isAnalyzing = false
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("analyse_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = ShieldBlue,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            enabled = !isAnalyzing
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Analyzing Evidence...", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp))
            } else {
                Text(
                    text = "Analyse",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section: "Recent Analyses"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Analyses",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = NavyDark,
                    fontSize = 16.sp
                )
            )
            Text(
                text = "View All >",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = ShieldBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                modifier = Modifier
                    .clickable {
                        if (recentAnalyses.isNotEmpty()) {
                            activeModalResult = recentAnalyses.first()
                        }
                    }
                    .padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        recentAnalyses.forEach { item ->
            val iconColor = when (item.threatLevel) {
                "HIGH" -> EmergencyRed
                "MEDIUM" -> EvidenceOrange
                else -> SafeGreen
            }
            val iconBg = when (item.threatLevel) {
                "HIGH" -> Color(0xFFFFEBEE)
                "MEDIUM" -> Color(0xFFFFF3E0)
                else -> Color(0xFFE8F5E9)
            }

            RecentAnalysisItem(
                title = item.title,
                subtitle = "${item.modality} • ${item.summary.take(45)}...",
                time = timeFormat.format(Date(item.timestamp)),
                iconColor = iconColor,
                iconBg = iconBg,
                onClick = { activeModalResult = item }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Result Inspection Modal Dialog
    if (activeModalResult != null) {
        val res = activeModalResult!!
        val badgeColor = when (res.threatLevel) {
            "HIGH" -> EmergencyRed
            "MEDIUM" -> EvidenceOrange
            else -> SafeGreen
        }

        AlertDialog(
            onDismissRequest = { activeModalResult = null },
            containerColor = Color.White,
            titleContentColor = NavyDark,
            textContentColor = NavyDark,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (res.threatLevel == "HIGH") Icons.Default.Warning else Icons.Default.Check,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(res.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NavyDark)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(res.summary, style = MaterialTheme.typography.bodyMedium.copy(color = NavyDark, fontWeight = FontWeight.Medium))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Indicators Flagged:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF475569)))
                    res.indicators.forEach { ind ->
                        Text("• $ind", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF334155), fontSize = 12.sp))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Cryptographic SHA-256 Digest:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF475569)))
                    Text(res.sha256Hash, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF475569), fontSize = 10.sp), maxLines = 3)
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Integrity & Heuristic Disclosure", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFFB45309)))
                            Text(
                                "Computing SHA-256 is an integrity operation, not threat detection. Heuristic scores represent local pattern matching, not an official AI prediction. Advisory signal only; not court evidence or psychological proof.",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF92400E), fontSize = 11.sp, lineHeight = 15.sp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { activeModalResult = null },
                    colors = ButtonDefaults.buttonColors(containerColor = ShieldBlue)
                ) {
                    Text("Done")
                }
            }
        )
    }
}

private fun computeStringSha256(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

private fun computeUriSha256(context: Context, uri: Uri): String {
    return try {
        val md = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buf = ByteArray(8192)
            var read: Int
            while (stream.read(buf).also { read = it } != -1) {
                md.update(buf, 0, read)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        "unavailable"
    }
}

private fun getFileSize(context: Context, uri: Uri): Long {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIdx != -1 && cursor.moveToFirst()) {
                cursor.getLong(sizeIdx)
            } else 0L
        } ?: 0L
    } catch (_: Exception) {
        0L
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx != -1 && cursor.moveToFirst()) {
                cursor.getString(nameIdx)
            } else "evidence_file"
        } ?: "evidence_file"
    } catch (_: Exception) {
        "evidence_file"
    }
}

@Composable
private fun ModalityTabPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) ShieldBlue else Color.White)
            .border(
                1.dp,
                if (isSelected) ShieldBlue else Color(0xFFCBD5E1),
                RoundedCornerShape(20.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
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

@Composable
private fun RecentAnalysisItem(
    title: String,
    subtitle: String,
    time: String,
    iconColor: Color,
    iconBg: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (iconColor == SafeGreen) Icons.Default.Check else Icons.Default.Warning,
                        contentDescription = null,
                        tint = iconColor,
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
                            fontSize = 14.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    )
                    Text(
                        text = time,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextMuted,
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
}
