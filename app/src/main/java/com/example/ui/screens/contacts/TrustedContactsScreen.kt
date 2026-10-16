package com.example.ui.screens.contacts

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.TrustedContactEntity
import com.example.localization.LanguageStrings
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.ShieldBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel

private const val MAX_EMERGENCY_CONTACTS = 2

@Composable
fun TrustedContactsScreen(
    viewModel: MainViewModel,
    strings: LanguageStrings,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val contacts by viewModel.trustedContacts.collectAsState()
    val offlineEmailConsent by viewModel.offlineLocationEmailConsent.collectAsState()
    val backendConfigured by viewModel.backendEndpointConfigured.collectAsState()
    val demoMode by viewModel.isDemoMode.collectAsState()
    val guardModeActive by viewModel.isGuardModeActive.collectAsState()
    val isAtLimit = contacts.size >= MAX_EMERGENCY_CONTACTS

    var showAddDialog by remember { mutableStateOf(false) }
    var verifyingContact by remember { mutableStateOf<TrustedContactEntity?>(null) }
    var contactToDelete by remember { mutableStateOf<TrustedContactEntity?>(null) }
    var limitReachedWarning by remember { mutableStateOf(false) }

    // Add dialog fields
    var nameInput by remember { mutableStateOf("") }
    var relInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var canShareLocationInput by remember { mutableStateOf(true) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var duplicateError by remember { mutableStateOf<String?>(null) }

    // Verification dialog state
    var verificationCodeInput by remember { mutableStateOf("") }
    var verificationError by remember { mutableStateOf<String?>(null) }
    var verificationSuccessMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(16.dp)
            .testTag("trusted_contacts_screen")
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NavyDark
                    )
                }
                Column {
                    Text(
                        text = "Emergency Contacts",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = NavyDark,
                            fontSize = 18.sp
                        )
                    )
                    Text(
                        text = "${contacts.size} of $MAX_EMERGENCY_CONTACTS configured",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (contacts.size >= MAX_EMERGENCY_CONTACTS) Color(0xFFD97706) else TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            // + Add Contact Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isAtLimit) Color(0xFF94A3B8) else ShieldBlue)
                    .clickable {
                        if (isAtLimit) {
                            limitReachedWarning = true
                        } else {
                            nameInput = ""
                            relInput = ""
                            phoneInput = ""
                            emailInput = ""
                            canShareLocationInput = true
                            emailError = null
                            duplicateError = null
                            showAddDialog = true
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .testTag("add_contact_pill")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isAtLimit) "Max Reached (2)" else "Add Contact",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Max limit informational note when at capacity
        if (isAtLimit) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Raksha360 restricts automated emergency dispatch to 2 primary contacts to ensure immediate priority delivery during real-world SOS incidents.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF1E40AF),
                            fontSize = 12.sp
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Global Missed-Heartbeat & Location Email Consent Card
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Email last-known location if phone stops checking in",
                        fontWeight = FontWeight.Bold,
                        color = NavyDark,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when {
                            !backendConfigured || demoMode -> "Demo Mode / Offline: connect to backend and switch to Live to enable server-side missed heartbeat detection."
                            !offlineEmailConsent -> "Consent revoked. If enabled, the server emails the last verified GPS coordinates to active contacts if no check-in is received."
                            !guardModeActive -> "Consent saved. Missed-heartbeat monitoring activates during Guard Mode or an active tracked journey."
                            else -> "Active: Server will email the last verified GPS fix if check-ins stop. (A powered-off device cannot send live updates)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = offlineEmailConsent,
                    onCheckedChange = { viewModel.setOfflineLocationEmailConsent(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SafeGreen)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Contact cards list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (contacts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No Emergency Contacts Added",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Add up to 2 trusted contacts who will receive emergency SOS emails and secure live-tracking links.",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(contacts, key = { it.id }) { contact ->
                    ContactItemEnhanced(
                        contact = contact,
                        onMessageClick = {
                            val smsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:${contact.phoneNumber}"))
                            context.startActivity(smsIntent)
                        },
                        onActiveChange = { enabled ->
                            viewModel.updateTrustedContactActive(contact, enabled)
                        },
                        onLocationConsentChange = { canShare ->
                            viewModel.updateTrustedContactLocationConsent(contact, canShare)
                        },
                        onVerifyClick = {
                            verifyingContact = contact
                            verificationCodeInput = ""
                            verificationError = null
                            verificationSuccessMessage = null
                        },
                        onDeleteClick = {
                            contactToDelete = contact
                        }
                    )
                }
            }
        }

        // Limit Reached Alert Dialog
        if (limitReachedWarning) {
            AlertDialog(
                onDismissRequest = { limitReachedWarning = false },
                title = { Text("Maximum Limit Reached") },
                text = {
                    Text("Raksha360 allows a maximum of 2 emergency contacts to ensure reliable, high-speed emergency dispatch. Please remove an existing contact to add a new one.")
                },
                confirmButton = {
                    Button(
                        onClick = { limitReachedWarning = false },
                        colors = ButtonDefaults.buttonColors(containerColor = ShieldBlue)
                    ) {
                        Text("OK")
                    }
                }
            )
        }

        // Delete Confirmation Dialog
        contactToDelete?.let { contact ->
            AlertDialog(
                onDismissRequest = { contactToDelete = null },
                title = { Text("Remove Emergency Contact?") },
                text = {
                    Text("Are you sure you want to remove ${contact.name} (${contact.relationship})? They will no longer receive emergency alerts or live tracking links.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteTrustedContact(contact.id)
                            contactToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) {
                        Text("Remove")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { contactToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Verification Dialog
        verifyingContact?.let { contact ->
            AlertDialog(
                onDismissRequest = { verifyingContact = null },
                title = { Text("Verify Email Address") },
                text = {
                    Column {
                        Text(
                            text = "To confirm that SOS emails reach ${contact.email}, enter the 6-digit verification code sent to this address, or click Mark Verified.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = verificationCodeInput,
                            onValueChange = {
                                if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                    verificationCodeInput = it
                                    verificationError = null
                                }
                            },
                            label = { Text("6-Digit Code (e.g. 123456)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            isError = verificationError != null,
                            supportingText = {
                                verificationError?.let { err ->
                                    Text(err, color = Color(0xFFDC2626))
                                } ?: verificationSuccessMessage?.let { msg ->
                                    Text(msg, color = SafeGreen)
                                }
                            }
                        )
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.verifyTrustedContact(contact.id)
                                verifyingContact = null
                            }
                        ) {
                            Text("Mark Verified")
                        }
                        Button(
                            onClick = {
                                if (verificationCodeInput.length == 6) {
                                    viewModel.verifyTrustedContact(contact.id)
                                    verifyingContact = null
                                } else {
                                    verificationError = "Enter a valid 6-digit numeric code"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
                        ) {
                            Text("Verify Code")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { verifyingContact = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Add Contact Dialog
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add Emergency Contact (${contacts.size + 1}/$MAX_EMERGENCY_CONTACTS)") },
                text = {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 440.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            label = { Text("Full Name or Alias") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = relInput,
                            onValueChange = { relInput = it },
                            label = { Text("Relationship (e.g. Mother, Sister, Friend)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = {
                                phoneInput = it
                                duplicateError = null
                            },
                            label = { Text("Phone Number (+91...)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = {
                                emailInput = it
                                emailError = null
                                duplicateError = null
                            },
                            label = { Text("Email Address for SOS Alerts") },
                            placeholder = { Text("contact@example.com") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = emailError != null || duplicateError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            supportingText = {
                                (emailError ?: duplicateError)?.let { err ->
                                    Text(err, color = Color(0xFFDC2626))
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Informed Consent Checkbox for Location Sharing
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { canShareLocationInput = !canShareLocationInput }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = canShareLocationInput,
                                onCheckedChange = { canShareLocationInput = it },
                                colors = CheckboxDefaults.colors(checkedColor = ShieldBlue)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Share live location during SOS",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = NavyDark
                                )
                                Text(
                                    "Permits sending a temporary, 2-hour expiring live-tracking link with alerts.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Informed Consent: By adding this contact, you authorize Raksha360 to dispatch emergency alerts to their phone and email when an SOS is triggered.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val normalizedEmail = emailInput.trim().lowercase()
                            val normalizedPhone = phoneInput.trim()
                            val validEmail = normalizedEmail.isBlank() || Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(normalizedEmail)

                            // Check duplicates
                            val emailDuplicate = normalizedEmail.isNotBlank() && contacts.any { it.email?.lowercase() == normalizedEmail }
                            val phoneDuplicate = normalizedPhone.isNotBlank() && contacts.any { it.phoneNumber == normalizedPhone }

                            if (!validEmail) {
                                emailError = "Enter a valid email address or leave blank."
                            } else if (emailDuplicate) {
                                duplicateError = "A contact with this email is already registered."
                            } else if (phoneDuplicate) {
                                duplicateError = "A contact with this phone number is already registered."
                            } else if (nameInput.isNotBlank() && phoneInput.isNotBlank()) {
                                viewModel.addTrustedContact(
                                    name = nameInput.trim(),
                                    relation = relInput.trim().ifBlank { "Emergency Contact" },
                                    phone = normalizedPhone,
                                    email = normalizedEmail.takeIf { it.isNotBlank() }
                                )
                                showAddDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ShieldBlue)
                    ) {
                        Text("Add Contact")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun ContactItemEnhanced(
    contact: TrustedContactEntity,
    onMessageClick: () -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onLocationConsentChange: (Boolean) -> Unit,
    onVerifyClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Avatar, Info, Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = contact.name,
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = NavyDark,
                                    fontSize = 15.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = contact.relationship,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = contact.phoneNumber,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        )

                        // Email + Verification Status
                        Spacer(modifier = Modifier.height(2.dp))
                        if (!contact.email.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Mail,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = contact.email,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = NavyDark,
                                        fontSize = 12.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))

                                // Verification Pill
                                if (contact.isEmailVerified) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFDCFCE7))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = SafeGreen,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = "Verified",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = SafeGreen,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFFEF3C7))
                                            .clickable { onVerifyClick() }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = Color(0xFFD97706),
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = "Unverified · Verify",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color(0xFFD97706),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "No email set (SMS only)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }

                // Quick Action Icons: Message, Delete, Active Switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onMessageClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "Message",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Switch(
                        checked = contact.isActive,
                        onCheckedChange = onActiveChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SafeGreen
                        )
                    )
                }
            }

            // Divider / Sub-control: Location Sharing Consent Toggle
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF8FAFC))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (contact.canShareLocation) ShieldBlue else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Share Live GPS in SOS Emails",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = NavyDark,
                                    fontSize = 12.sp
                                )
                            )
                            Text(
                                text = if (contact.canShareLocation)
                                    "Sends secure 2-hour live tracking link"
                                else
                                    "Location hidden from alert email",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    Switch(
                        checked = contact.canShareLocation,
                        onCheckedChange = onLocationConsentChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ShieldBlue
                        )
                    )
                }
            }
        }
    }
}
