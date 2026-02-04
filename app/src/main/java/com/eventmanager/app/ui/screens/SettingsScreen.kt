
package com.eventmanager.app.ui.screens

import android.net.Uri
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.border
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import android.webkit.WebView
import com.eventmanager.app.data.sync.GmailAuthService
import kotlinx.coroutines.launch
import android.widget.Toast
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import com.eventmanager.app.data.sync.FileManager
import com.eventmanager.app.data.sync.GoogleSheetsConfig
import com.eventmanager.app.data.sync.JsonKeyInfo
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.data.sync.DateFormatUtils
import com.eventmanager.app.data.utils.VolunteerActivityManager
import com.eventmanager.app.data.utils.AppIconManager
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel
import com.eventmanager.app.ui.components.CleanupInactiveVolunteersDialog
import com.eventmanager.app.ui.components.SyncStatusDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import com.eventmanager.app.R
import com.eventmanager.app.BuildConfig
import com.eventmanager.app.ui.theme.ThemeMode
import com.eventmanager.app.ui.components.ResolutionScaleSlider
import com.eventmanager.app.ui.components.AppRestartDialog
import com.eventmanager.app.ui.components.RetroSynthwaveGameDialog
import com.eventmanager.app.ui.components.FlappyNocturneGameDialog
import com.eventmanager.app.ui.components.DriveNocturneGameDialog
import com.eventmanager.app.utils.ImageUtils
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.asImageBitmap

// Data class for icon options
private data class IconOption(
    val style: String,
    val nameResId: Int,
    val toastResId: Int,
    val iconResId: Int,
    val backgroundColor: Color
)

// Helper composable for expandable settings category
@Composable
private fun ExpandableSettingsCategory(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                content()
            }
        }
    }
}

/**
 * Optimized Email Settings composable - extracted to reduce recomposition scope
 * and improve performance by only loading settings when needed.
 * Now includes tabbed interface for Volunteer and Guest email settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailSettingsContent(
    settingsManager: SettingsManager,
    context: android.content.Context
) {
    // Cache string resources to avoid repeated lookups
    val strings = remember {
        EmailSettingsStrings(
            subjectDefault = context.getString(R.string.email_subject_default),
            contentBeforeDefault = context.getString(R.string.email_content_before_default),
            contentAfterDefault = context.getString(R.string.email_content_after_default),
            signatureDefault = context.getString(R.string.email_signature_default),
            title = context.getString(R.string.email_qr_settings_title),
            description = context.getString(R.string.email_qr_settings_description),
            subjectLabel = context.getString(R.string.email_subject_label),
            subjectHint = context.getString(R.string.email_subject_hint),
            contentBeforeLabel = context.getString(R.string.email_content_before_label),
            contentBeforeHint = context.getString(R.string.email_content_before_hint),
            includeQrLabel = context.getString(R.string.email_include_qr_label),
            includeQrDescription = context.getString(R.string.email_include_qr_description),
            contentAfterLabel = context.getString(R.string.email_content_after_label),
            contentAfterHint = context.getString(R.string.email_content_after_hint),
            signatureLabel = context.getString(R.string.email_signature_label),
            signatureHint = context.getString(R.string.email_signature_hint),
            includeLogoLabel = context.getString(R.string.email_include_logo_label),
            includeLogoDescription = context.getString(R.string.email_include_logo_description),
            logoPreview = context.getString(R.string.email_logo_preview),
            logoChange = context.getString(R.string.email_logo_change),
            logoRemove = context.getString(R.string.email_logo_remove),
            logoUpload = context.getString(R.string.email_logo_upload),
            resetDefaults = context.getString(R.string.email_reset_to_defaults)
        )
    }
    
    // Guest email defaults
    val guestStrings = remember {
        GuestEmailSettingsStrings(
            subjectDefault = context.getString(R.string.guest_email_subject_default),
            contentBeforeDefault = context.getString(R.string.guest_email_content_before_default),
            contentAfterDefault = context.getString(R.string.guest_email_content_after_default)
        )
    }
    
    // Tab state: 0 = Volunteer, 1 = Guest
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabLabels = listOf(
        context.getString(R.string.email_tab_volunteer),
        context.getString(R.string.email_tab_guest)
    )
    
    // Volunteer email settings
    var volunteerSubject by remember { mutableStateOf(strings.subjectDefault) }
    var volunteerContentBefore by remember { mutableStateOf(strings.contentBeforeDefault) }
    var volunteerIncludeQr by remember { mutableStateOf(true) }
    var volunteerContentAfter by remember { mutableStateOf(strings.contentAfterDefault) }
    
    // Guest email settings
    var guestSubject by remember { mutableStateOf(guestStrings.subjectDefault) }
    var guestContentBefore by remember { mutableStateOf(guestStrings.contentBeforeDefault) }
    var guestIncludeQr by remember { mutableStateOf(true) }
    var guestContentAfter by remember { mutableStateOf(guestStrings.contentAfterDefault) }
    
    // Shared settings
    var emailSignature by remember { mutableStateOf(strings.signatureDefault) }
    var emailIncludeLogo by remember { mutableStateOf(false) }
    var emailLogoUri by remember { mutableStateOf("") }
    
    // Load settings asynchronously only once
    LaunchedEffect(Unit) {
        // Volunteer settings
        volunteerSubject = settingsManager.getEmailSubject().ifEmpty { strings.subjectDefault }
        volunteerContentBefore = settingsManager.getEmailContentBefore().ifEmpty { strings.contentBeforeDefault }
        volunteerIncludeQr = settingsManager.isEmailIncludeQrEnabled()
        volunteerContentAfter = settingsManager.getEmailContentAfter().ifEmpty { strings.contentAfterDefault }
        
        // Guest settings
        guestSubject = settingsManager.getGuestEmailSubject().ifEmpty { guestStrings.subjectDefault }
        guestContentBefore = settingsManager.getGuestEmailContentBefore().ifEmpty { guestStrings.contentBeforeDefault }
        guestIncludeQr = settingsManager.isGuestEmailIncludeQrEnabled()
        guestContentAfter = settingsManager.getGuestEmailContentAfter().ifEmpty { guestStrings.contentAfterDefault }
        
        // Shared settings
        emailSignature = settingsManager.getEmailSignature().ifEmpty { strings.signatureDefault }
        emailIncludeLogo = settingsManager.isEmailIncludeLogoEnabled()
        emailLogoUri = settingsManager.getEmailLogoUri()
    }
    
    // Debounce saves to avoid excessive SharedPreferences writes
    val coroutineScope = rememberCoroutineScope()
    var saveJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    
    fun debouncedSave(block: suspend () -> Unit) {
        saveJob?.cancel()
        saveJob = coroutineScope.launch {
            kotlinx.coroutines.delay(500) // 500ms debounce
            block()
        }
    }
    
    // Logo picker launcher
    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if permission already taken or not available
            }
            emailLogoUri = selectedUri.toString()
            settingsManager.saveEmailLogoUri(selectedUri.toString())
        }
    }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        Text(
            text = strings.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = strings.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        HorizontalDivider()
        
        // Tab Row for Volunteer / Guest selection
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
        ) {
            tabLabels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = tabLabels.size),
                    icon = {
                        if (selectedTabIndex == index) {
                            Icon(
                                imageVector = if (index == 0) Icons.Default.VolunteerActivism else Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                ) {
                    Text(label)
                }
            }
        }
        
        // Tab-specific content
        if (selectedTabIndex == 0) {
            // Volunteer Email Settings
            VolunteerEmailFields(
                subject = volunteerSubject,
                onSubjectChange = { 
                    volunteerSubject = it
                    debouncedSave { settingsManager.saveEmailSubject(it) }
                },
                contentBefore = volunteerContentBefore,
                onContentBeforeChange = { 
                    volunteerContentBefore = it
                    debouncedSave { settingsManager.saveEmailContentBefore(it) }
                },
                includeQr = volunteerIncludeQr,
                onIncludeQrChange = {
                    volunteerIncludeQr = it
                    settingsManager.setEmailIncludeQrEnabled(it)
                },
                contentAfter = volunteerContentAfter,
                onContentAfterChange = { 
                    volunteerContentAfter = it
                    debouncedSave { settingsManager.saveEmailContentAfter(it) }
                },
                strings = strings
            )
        } else {
            // Guest Email Settings
            GuestEmailFields(
                subject = guestSubject,
                onSubjectChange = { 
                    guestSubject = it
                    debouncedSave { settingsManager.saveGuestEmailSubject(it) }
                },
                contentBefore = guestContentBefore,
                onContentBeforeChange = { 
                    guestContentBefore = it
                    debouncedSave { settingsManager.saveGuestEmailContentBefore(it) }
                },
                includeQr = guestIncludeQr,
                onIncludeQrChange = {
                    guestIncludeQr = it
                    settingsManager.setGuestEmailIncludeQrEnabled(it)
                },
                contentAfter = guestContentAfter,
                onContentAfterChange = { 
                    guestContentAfter = it
                    debouncedSave { settingsManager.saveGuestEmailContentAfter(it) }
                },
                strings = strings
            )
        }
        
        HorizontalDivider()
        
        // Shared Settings Header
        Text(
            text = context.getString(R.string.email_shared_settings_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = context.getString(R.string.email_shared_settings_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Signature (shared)
        OutlinedTextField(
            value = emailSignature,
            onValueChange = { 
                emailSignature = it
                debouncedSave { settingsManager.saveEmailSignature(it) }
            },
            label = { Text(strings.signatureLabel) },
            placeholder = { Text(strings.signatureHint) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            leadingIcon = { Icon(Icons.Default.Create, contentDescription = null) }
        )
        
        HorizontalDivider()
        
        // Include Logo Toggle (shared)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.includeLogoLabel,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = strings.includeLogoDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = emailIncludeLogo,
                onCheckedChange = {
                    emailIncludeLogo = it
                    settingsManager.setEmailIncludeLogoEnabled(it)
                }
            )
        }
        
        // Logo Upload (only visible when toggle is on)
        if (emailIncludeLogo) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (emailLogoUri.isNotEmpty()) {
                        Text(
                            text = strings.logoPreview,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val logoUri = remember(emailLogoUri) { Uri.parse(emailLogoUri) }
                        Image(
                            painter = rememberAsyncImagePainter(logoUri),
                            contentDescription = strings.logoPreview,
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { logoPickerLauncher.launch(arrayOf("image/*")) }
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.logoChange)
                            }
                            
                            OutlinedButton(
                                onClick = {
                                    emailLogoUri = ""
                                    settingsManager.saveEmailLogoUri("")
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strings.logoRemove)
                            }
                        }
                    } else {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { logoPickerLauncher.launch(arrayOf("image/*")) }
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.logoUpload)
                        }
                    }
                }
            }
        }
        
        HorizontalDivider()
        
        // Gmail OAuth Authentication Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = context.getString(R.string.email_gmail_auth_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = context.getString(R.string.email_gmail_auth_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Gmail Auth Status and Actions
                GmailAuthSection(settingsManager, context)
            }
        }
        
        HorizontalDivider()
        
        // Reset to Defaults Button - resets current tab only
        OutlinedButton(
            onClick = {
                if (selectedTabIndex == 0) {
                    // Reset volunteer settings
                    volunteerSubject = strings.subjectDefault
                    volunteerContentBefore = strings.contentBeforeDefault
                    volunteerIncludeQr = true
                    volunteerContentAfter = strings.contentAfterDefault
                    
                    coroutineScope.launch {
                        settingsManager.saveEmailSubject(volunteerSubject)
                        settingsManager.saveEmailContentBefore(volunteerContentBefore)
                        settingsManager.setEmailIncludeQrEnabled(volunteerIncludeQr)
                        settingsManager.saveEmailContentAfter(volunteerContentAfter)
                    }
                } else {
                    // Reset guest settings
                    guestSubject = guestStrings.subjectDefault
                    guestContentBefore = guestStrings.contentBeforeDefault
                    guestIncludeQr = true
                    guestContentAfter = guestStrings.contentAfterDefault
                    
                    coroutineScope.launch {
                        settingsManager.saveGuestEmailSubject(guestSubject)
                        settingsManager.saveGuestEmailContentBefore(guestContentBefore)
                        settingsManager.setGuestEmailIncludeQrEnabled(guestIncludeQr)
                        settingsManager.saveGuestEmailContentAfter(guestContentAfter)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(strings.resetDefaults + " (${tabLabels[selectedTabIndex]})")
        }
    }
}

/**
 * Volunteer email fields composable
 */
@Composable
private fun VolunteerEmailFields(
    subject: String,
    onSubjectChange: (String) -> Unit,
    contentBefore: String,
    onContentBeforeChange: (String) -> Unit,
    includeQr: Boolean,
    onIncludeQrChange: (Boolean) -> Unit,
    contentAfter: String,
    onContentAfterChange: (String) -> Unit,
    strings: EmailSettingsStrings
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Email Subject
        OutlinedTextField(
            value = subject,
            onValueChange = onSubjectChange,
            label = { Text(strings.subjectLabel) },
            placeholder = { Text(strings.subjectHint) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Subject, contentDescription = null) }
        )
        
        // Content Before QR Code
        OutlinedTextField(
            value = contentBefore,
            onValueChange = onContentBeforeChange,
            label = { Text(strings.contentBeforeLabel) },
            placeholder = { Text(strings.contentBeforeHint) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) }
        )
        
        // Include QR Code Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.includeQrLabel,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = strings.includeQrDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = includeQr,
                onCheckedChange = onIncludeQrChange
            )
        }
        
        // Content After QR Code
        OutlinedTextField(
            value = contentAfter,
            onValueChange = onContentAfterChange,
            label = { Text(strings.contentAfterLabel) },
            placeholder = { Text(strings.contentAfterHint) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) }
        )
    }
}

/**
 * Guest email fields composable
 */
@Composable
private fun GuestEmailFields(
    subject: String,
    onSubjectChange: (String) -> Unit,
    contentBefore: String,
    onContentBeforeChange: (String) -> Unit,
    includeQr: Boolean,
    onIncludeQrChange: (Boolean) -> Unit,
    contentAfter: String,
    onContentAfterChange: (String) -> Unit,
    strings: EmailSettingsStrings
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Email Subject
        OutlinedTextField(
            value = subject,
            onValueChange = onSubjectChange,
            label = { Text(strings.subjectLabel) },
            placeholder = { Text(strings.subjectHint) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Subject, contentDescription = null) }
        )
        
        // Content Before QR Code
        OutlinedTextField(
            value = contentBefore,
            onValueChange = onContentBeforeChange,
            label = { Text(strings.contentBeforeLabel) },
            placeholder = { Text(strings.contentBeforeHint) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) }
        )
        
        // Include QR Code Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.includeQrLabel,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = strings.includeQrDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = includeQr,
                onCheckedChange = onIncludeQrChange
            )
        }
        
        // Content After QR Code
        OutlinedTextField(
            value = contentAfter,
            onValueChange = onContentAfterChange,
            label = { Text(strings.contentAfterLabel) },
            placeholder = { Text(strings.contentAfterHint) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) }
        )
    }
}

/**
 * Data class for guest email settings strings
 */
private data class GuestEmailSettingsStrings(
    val subjectDefault: String,
    val contentBeforeDefault: String,
    val contentAfterDefault: String
)

/**
 * Gmail Authentication Section using AccountManager
 * This approach uses the device's Google accounts directly - like email apps do.
 */
@Composable
private fun GmailAuthSection(
    settingsManager: SettingsManager,
    context: android.content.Context
) {
    val gmailAuthService = remember { GmailAuthService(context) }
    val coroutineScope = rememberCoroutineScope()
    val activity = context as? android.app.Activity
    
    var isAccountSelected by remember { mutableStateOf(gmailAuthService.isAccountSelected()) }
    var selectedEmail by remember { mutableStateOf(gmailAuthService.getSelectedAccountEmail()) }
    var isCredentialReady by remember { mutableStateOf(gmailAuthService.isCredentialReady()) }
    var isLoading by remember { mutableStateOf(false) }
    var needsPermission by remember { mutableStateOf(false) }
    var needsAndroidTVAuth by remember { mutableStateOf(false) }
    
    val androidTVAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLoading = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            isCredentialReady = gmailAuthService.isCredentialReady()
            needsAndroidTVAuth = !isCredentialReady
            if (isCredentialReady) {
                Toast.makeText(context, "Gmail API connected!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Gmail authorization cancelled", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun tryAndroidTVAuth() {
        if (activity == null) return
        
        isLoading = true
        coroutineScope.launch {
            try {
                val authIntent = gmailAuthService.tryGetTokenForAndroidTV(activity)
                if (authIntent != null) {
                    androidTVAuthLauncher.launch(authIntent)
                } else {
                    isCredentialReady = gmailAuthService.isCredentialReady()
                    needsAndroidTVAuth = !isCredentialReady
                    isLoading = false
                    if (isCredentialReady) {
                        Toast.makeText(context, "Gmail API connected!", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                isLoading = false
                Toast.makeText(context, "Gmail connection error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    LaunchedEffect(Unit) {
        val settingsAccount = settingsManager.getGmailAccount()
        if (settingsAccount.isNotEmpty() && selectedEmail == null) {
            selectedEmail = settingsAccount
            isAccountSelected = true
        }
        
        if (isAccountSelected && !isCredentialReady) {
            val delay = if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.N_MR1) 500L else 0L
            if (delay > 0) {
                kotlinx.coroutines.delay(delay)
            }
            
            isCredentialReady = gmailAuthService.refreshCredential()
            if (!isCredentialReady) {
                needsAndroidTVAuth = gmailAuthService.needsAndroidTVAuth()
            }
        }
    }
    
    // Account picker launcher
    val accountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLoading = false
        
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val accountName = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            if (accountName != null) {
                gmailAuthService.setSelectedAccount(accountName)
                settingsManager.saveGmailAccount(accountName)
                isAccountSelected = true
                selectedEmail = accountName
                isCredentialReady = gmailAuthService.isCredentialReady()
                
                if (!isCredentialReady) {
                    coroutineScope.launch {
                        kotlinx.coroutines.delay(500)
                        isCredentialReady = gmailAuthService.refreshCredential()
                        if (!isCredentialReady) {
                            needsAndroidTVAuth = true
                            tryAndroidTVAuth()
                        }
                    }
                }
                
                needsPermission = true
                Toast.makeText(context, context.getString(R.string.email_gmail_auth_success), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, context.getString(R.string.email_gmail_auth_error, "Account selection cancelled"), Toast.LENGTH_SHORT).show()
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        needsPermission = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            Toast.makeText(context, "Gmail permission granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Gmail permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Check and request permission after account selection
    LaunchedEffect(needsPermission, isAccountSelected) {
        if (needsPermission && isAccountSelected && isCredentialReady) {
            val authIntent = gmailAuthService.testPermissionAndGetAuthIntent()
            if (authIntent != null) {
                permissionLauncher.launch(authIntent)
            }
            needsPermission = false
        }
    }
    
    if (isAccountSelected && selectedEmail != null) {
        // Account selected state
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = context.getString(R.string.email_gmail_signed_in_as, selectedEmail!!),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(
                    onClick = {
                        gmailAuthService.clearSelectedAccount()
                        settingsManager.clearGmailAuth()
                        isAccountSelected = false
                        selectedEmail = null
                        isCredentialReady = false
                        needsAndroidTVAuth = false
                        Toast.makeText(context, "Account disconnected", Toast.LENGTH_SHORT).show()
                    },
                    enabled = !isLoading
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(context.getString(R.string.email_gmail_sign_out))
                }
            }
            
            // Show warning if credential is not ready (Android TV issue)
            if (!isCredentialReady) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gmail API Connection Required",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your Google account is saved. On Android TV and older devices, " +
                                "an additional authorization step is needed to connect the Gmail API.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { tryAndroidTVAuth() },
                        enabled = !isLoading && activity != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect Gmail API")
                    }
                    if (activity == null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Cannot connect: Activity context not available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    } else {
        // No account selected
        Button(
            onClick = {
                isLoading = true
                val pickerIntent = gmailAuthService.getAccountPickerIntent()
                accountPickerLauncher.launch(pickerIntent)
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(Icons.Default.AccountCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(context.getString(R.string.email_gmail_sign_in))
        }
        
        Text(
            text = context.getString(R.string.email_gmail_not_signed_in),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Data class to cache string resources
private data class EmailSettingsStrings(
    val subjectDefault: String,
    val contentBeforeDefault: String,
    val contentAfterDefault: String,
    val signatureDefault: String,
    val title: String,
    val description: String,
    val subjectLabel: String,
    val subjectHint: String,
    val contentBeforeLabel: String,
    val contentBeforeHint: String,
    val includeQrLabel: String,
    val includeQrDescription: String,
    val contentAfterLabel: String,
    val contentAfterHint: String,
    val signatureLabel: String,
    val signatureHint: String,
    val includeLogoLabel: String,
    val includeLogoDescription: String,
    val logoPreview: String,
    val logoChange: String,
    val logoRemove: String,
    val logoUpload: String,
    val resetDefaults: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: EventManagerViewModel,
    onNavigateToJobTypeManagement: () -> Unit = {},
    onNavigateToVenueManagement: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val fileManager = remember { FileManager(context) }
    val appIconManager = remember { AppIconManager(context) }
    
    var spreadsheetId by remember { mutableStateOf(settingsManager.getSpreadsheetId()) }
    var guestListSheet by remember { mutableStateOf(settingsManager.getGuestListSheet()) }
    var volunteerSheet by remember { mutableStateOf(settingsManager.getVolunteerSheet()) }
    var jobsSheet by remember { mutableStateOf(settingsManager.getJobsSheet()) }
    var volunteerGuestListSheet by remember { mutableStateOf(settingsManager.getVolunteerGuestListSheet()) }
    var jobTypesSheet by remember { mutableStateOf(settingsManager.getJobTypesSheet()) }
    var venuesSheet by remember { mutableStateOf(settingsManager.getVenuesSheet()) }
    var showInstructions by remember { mutableStateOf(false) }
    var jsonKeyInfo by remember { mutableStateOf<JsonKeyInfo?>(null) }
    var showActiveVolunteersDialog by remember { mutableStateOf(false) }
    var showCleanupDialog by remember { mutableStateOf(false) }
    var uploadStatus by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var syncInterval by remember { mutableStateOf(settingsManager.getSyncInterval()) }
    var showSyncSettings by remember { mutableStateOf(settingsManager.isCategorySyncExpanded()) }
    var showEmailSettings by remember { mutableStateOf(settingsManager.isCategoryEmailExpanded()) }
    var showAppearanceSettings by remember { mutableStateOf(settingsManager.isCategoryAppearanceExpanded()) }
    var showLocalizationSettings by remember { mutableStateOf(settingsManager.isCategoryLocalizationExpanded()) }
    var showAnimationSettings by remember { mutableStateOf(settingsManager.isCategoryAnimationExpanded()) }
    var showDeveloperSettings by remember { mutableStateOf(settingsManager.isCategoryDeveloperExpanded()) }
    var showMaintenanceSettings by remember { mutableStateOf(settingsManager.isCategoryMaintenanceExpanded()) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var currentResolutionScale by remember { mutableStateOf(settingsManager.getResolutionScale()) }
    var pendingResolutionScale by remember { mutableStateOf(settingsManager.getResolutionScale()) }
    var hasUnsavedResolutionChanges by remember { mutableStateOf(false) }
    var showAppIconRestartDialog by remember { mutableStateOf(false) }
    var showUpdateResultDialog by remember { mutableStateOf(false) }
    
    // Easter Egg state
    var easterEggTapCount by remember { mutableStateOf(0) }
    var lastEasterEggTapTime by remember { mutableStateOf(0L) }
    var showEasterEggDialog by remember { mutableStateOf(false) }
    var showFlappyGame by remember { mutableStateOf(false) }
    var showDriveGame by remember { mutableStateOf(false) }
    
    // Sync status dialog state
    val syncStatusMessage by viewModel.syncStatusMessage.collectAsState()
    val showSyncStatusDialog by viewModel.showSyncStatusDialog.collectAsState()
    
    // Check if JSON key file exists on first load
    LaunchedEffect(Unit) {
        if (fileManager.hasServiceAccountKey()) {
            // Try to extract info from existing file
            jsonKeyInfo = JsonKeyInfo(context.getString(R.string.key_file_found), context.getString(R.string.unknown))
        }
    }
    
    // File picker launcher (use OpenDocument with multiple MIME types for wider compatibility on older Android)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            selectedFileUri = selectedUri
        }
    }
    
    LaunchedEffect(selectedFileUri) {
        selectedFileUri?.let { uri ->
            uploadStatus = context.getString(R.string.validating_file)
            fileManager.validateJsonKeyFile(uri)
                .onSuccess { keyInfo ->
                    uploadStatus = context.getString(R.string.file_validated_uploading)
                    fileManager.copyFileToAssets(uri, "service_account_key.json")
                        .onSuccess { _ ->
                            uploadStatus = context.getString(R.string.file_uploaded_successfully)
                            jsonKeyInfo = keyInfo
                        }
                        .onFailure { error ->
                            uploadStatus = context.getString(R.string.upload_failed, error.message ?: "")
                        }
                }
                .onFailure { error ->
                    uploadStatus = context.getString(R.string.validation_failed, error.message ?: "")
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Text(
            text = context.getString(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = context.getString(R.string.settings_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Sync & Data Settings
        ExpandableSettingsCategory(
            title = context.getString(R.string.settings_category_sync),
            icon = Icons.Default.CloudSync,
            isExpanded = showSyncSettings,
            onToggleExpanded = { 
                showSyncSettings = !showSyncSettings
                settingsManager.setCategorySyncExpanded(showSyncSettings)
            }
        ) {
            // Google Sheets Configuration Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CloudSync,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = context.getString(R.string.google_sheets_config_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Instructions Button
                    Button(
                        onClick = { showInstructions = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(context.getString(R.string.view_setup_instructions))
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Spreadsheet ID
                    OutlinedTextField(
                        value = spreadsheetId,
                        onValueChange = { spreadsheetId = it },
                        label = { Text(context.getString(R.string.spreadsheet_id_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { 
                            Text(context.getString(R.string.spreadsheet_id_hint))
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Link, contentDescription = null)
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Sheet Names
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = guestListSheet,
                            onValueChange = { guestListSheet = it },
                            label = { Text(context.getString(R.string.guest_list_sheet_label)) },
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(Icons.Default.People, contentDescription = null)
                            }
                        )
                        
                        OutlinedTextField(
                            value = volunteerSheet,
                            onValueChange = { volunteerSheet = it },
                            label = { Text(context.getString(R.string.volunteer_sheet_label)) },
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(Icons.Default.Group, contentDescription = null)
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = jobsSheet,
                        onValueChange = { jobsSheet = it },
                        label = { Text(context.getString(R.string.shifts_sheet_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.Work, contentDescription = null)
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Additional Sheet Names
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = volunteerGuestListSheet,
                            onValueChange = { volunteerGuestListSheet = it },
                            label = { Text(context.getString(R.string.volunteer_guest_list_sheet_label)) },
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(Icons.Default.People, contentDescription = null)
                            }
                        )
                        
                        OutlinedTextField(
                            value = jobTypesSheet,
                            onValueChange = { jobTypesSheet = it },
                            label = { Text(context.getString(R.string.shift_types_sheet_label)) },
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(Icons.Default.Settings, contentDescription = null)
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = venuesSheet,
                        onValueChange = { venuesSheet = it },
                        label = { Text(context.getString(R.string.venues_sheet_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Default.LocationOn, contentDescription = null)
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Action Buttons
                    Button(
                        onClick = { 
                            settingsManager.saveSpreadsheetId(spreadsheetId)
                            settingsManager.saveGuestListSheet(guestListSheet)
                            settingsManager.saveVolunteerSheet(volunteerSheet)
                            settingsManager.saveJobsSheet(jobsSheet)
                            settingsManager.saveVolunteerGuestListSheet(volunteerGuestListSheet)
                            settingsManager.saveJobTypesSheet(jobTypesSheet)
                            settingsManager.saveVenuesSheet(venuesSheet)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(context.getString(R.string.save_settings))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Service Account Configuration Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = context.getString(R.string.service_account_config_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // JSON Key File Status
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (jsonKeyInfo != null) 
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else 
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (jsonKeyInfo != null) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (jsonKeyInfo != null) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (jsonKeyInfo != null) context.getString(R.string.service_account_key_found) else context.getString(R.string.service_account_key_required),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (jsonKeyInfo != null) {
                                        Text(
                                            text = context.getString(R.string.email_colon, jsonKeyInfo!!.clientEmail),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = context.getString(R.string.project_colon, jsonKeyInfo!!.projectId),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    } else {
                                        Text(
                                            text = context.getString(R.string.upload_key_description),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Upload Button
                            Button(
                                onClick = {
                                    // Accept common fallbacks because some Android 9 pickers gray out application/json
                                    filePickerLauncher.launch(arrayOf(
                                        "application/json",
                                        "application/octet-stream",
                                        "text/plain",
                                        "text/*",
                                        "application/x-json"
                                    ))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (jsonKeyInfo != null) context.getString(R.string.replace_key_file) else context.getString(R.string.upload_key_file))
                            }
                            
                            if (uploadStatus != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = uploadStatus!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (uploadStatus!!.contains(context.getString(R.string.file_uploaded_successfully))) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Sync Configuration Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = context.getString(R.string.sync_config_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Sync Interval
                    OutlinedTextField(
                        value = syncInterval.toString(),
                        onValueChange = { 
                            val newInterval = it.toIntOrNull() ?: 5
                            if (newInterval >= 1 && newInterval <= 60) {
                                syncInterval = newInterval
                                settingsManager.saveSyncInterval(newInterval)
                                // Update the background sync interval with a small delay to prevent rapid changes
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(500) // 500ms debounce
                                    viewModel.updateSyncInterval()
                                }
                            }
                        },
                        label = { Text(context.getString(R.string.sync_interval_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { 
                            Text(context.getString(R.string.sync_interval_hint))
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Timer, contentDescription = null)
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Test Buttons
                    Button(
                        onClick = { viewModel.testSyncStatus() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.BugReport, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(context.getString(R.string.test_sync_status))
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { viewModel.syncWithGoogleSheets() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(context.getString(R.string.manual_sync_now))
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Email Settings Category
        ExpandableSettingsCategory(
            title = context.getString(R.string.settings_category_email),
            icon = Icons.Default.Email,
            isExpanded = showEmailSettings,
            onToggleExpanded = { 
                showEmailSettings = !showEmailSettings
                settingsManager.setCategoryEmailExpanded(showEmailSettings)
            }
        ) {
            EmailSettingsContent(
                settingsManager = settingsManager,
                context = context
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Appearance & Display Settings
        ExpandableSettingsCategory(
            title = context.getString(R.string.settings_category_appearance),
            icon = Icons.Default.Palette,
            isExpanded = showAppearanceSettings,
            onToggleExpanded = { 
                showAppearanceSettings = !showAppearanceSettings
                settingsManager.setCategoryAppearanceExpanded(showAppearanceSettings)
            }
        ) {
            // Theme Selection
            var selectedTheme by remember { mutableStateOf(ThemeMode.fromString(settingsManager.getThemeMode())) }
            var showThemeMenu by remember { mutableStateOf(false) }
            
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = context.getString(R.string.theme_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = context.getString(R.string.theme_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { showThemeMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = when (selectedTheme) {
                                ThemeMode.LIGHT -> context.getString(R.string.theme_light)
                                ThemeMode.DARK -> context.getString(R.string.theme_dark)
                                ThemeMode.DEFAULT -> context.getString(R.string.theme_default)
                            },
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showThemeMenu,
                        onDismissRequest = { showThemeMenu = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.theme_light)) },
                            onClick = {
                                selectedTheme = ThemeMode.LIGHT
                                settingsManager.saveThemeMode(ThemeMode.LIGHT.value)
                                showThemeMenu = false
                                // Trigger activity recreation to apply theme change
                                (context as? android.app.Activity)?.recreate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.theme_dark)) },
                            onClick = {
                                selectedTheme = ThemeMode.DARK
                                settingsManager.saveThemeMode(ThemeMode.DARK.value)
                                showThemeMenu = false
                                // Trigger activity recreation to apply theme change
                                (context as? android.app.Activity)?.recreate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.theme_default)) },
                            onClick = {
                                selectedTheme = ThemeMode.DEFAULT
                                settingsManager.saveThemeMode(ThemeMode.DEFAULT.value)
                                showThemeMenu = false
                                // Trigger activity recreation to apply theme change
                                (context as? android.app.Activity)?.recreate()
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Color Theme Selection
            var selectedColorTheme by remember { mutableStateOf(settingsManager.getColorTheme()) }
            var showColorThemeMenu by remember { mutableStateOf(false) }
            
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = context.getString(R.string.color_theme_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = context.getString(R.string.color_theme_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { showColorThemeMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = when (selectedColorTheme) {
                                "system" -> context.getString(R.string.color_theme_system)
                                "professional_blue" -> context.getString(R.string.color_theme_professional_blue)
                                "neutral_green" -> context.getString(R.string.color_theme_neutral_green)
                                "warm_gray" -> context.getString(R.string.color_theme_warm_gray)
                                "neutral_purple" -> context.getString(R.string.color_theme_neutral_purple)
                                "rich_brown" -> context.getString(R.string.color_theme_rich_brown)
                                else -> context.getString(R.string.color_theme_system)
                            },
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showColorThemeMenu,
                        onDismissRequest = { showColorThemeMenu = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                                DropdownMenuItem(
                                    text = { Text(context.getString(R.string.color_theme_system)) },
                                    onClick = {
                                        if (selectedColorTheme != "system") {
                                            selectedColorTheme = "system"
                                            settingsManager.saveColorTheme("system")
                                            showColorThemeMenu = false
                                            showRestartDialog = true
                                        } else {
                                            showColorThemeMenu = false
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(context.getString(R.string.color_theme_professional_blue)) },
                                    onClick = {
                                        if (selectedColorTheme != "professional_blue") {
                                            selectedColorTheme = "professional_blue"
                                            settingsManager.saveColorTheme("professional_blue")
                                            showColorThemeMenu = false
                                            showRestartDialog = true
                                        } else {
                                            showColorThemeMenu = false
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(context.getString(R.string.color_theme_neutral_green)) },
                                    onClick = {
                                        if (selectedColorTheme != "neutral_green") {
                                            selectedColorTheme = "neutral_green"
                                            settingsManager.saveColorTheme("neutral_green")
                                            showColorThemeMenu = false
                                            showRestartDialog = true
                                        } else {
                                            showColorThemeMenu = false
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(context.getString(R.string.color_theme_warm_gray)) },
                                    onClick = {
                                        if (selectedColorTheme != "warm_gray") {
                                            selectedColorTheme = "warm_gray"
                                            settingsManager.saveColorTheme("warm_gray")
                                            showColorThemeMenu = false
                                            showRestartDialog = true
                                        } else {
                                            showColorThemeMenu = false
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(context.getString(R.string.color_theme_neutral_purple)) },
                                    onClick = {
                                        if (selectedColorTheme != "neutral_purple") {
                                            selectedColorTheme = "neutral_purple"
                                            settingsManager.saveColorTheme("neutral_purple")
                                            showColorThemeMenu = false
                                            showRestartDialog = true
                                        } else {
                                            showColorThemeMenu = false
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(context.getString(R.string.color_theme_rich_brown)) },
                                    onClick = {
                                        if (selectedColorTheme != "rich_brown") {
                                            selectedColorTheme = "rich_brown"
                                            settingsManager.saveColorTheme("rich_brown")
                                            showColorThemeMenu = false
                                            showRestartDialog = true
                                        } else {
                                            showColorThemeMenu = false
                                        }
                                    }
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Resolution Scale Selection
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = context.getString(R.string.resolution_scale_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = context.getString(R.string.resolution_scale_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Custom slider component
                        ResolutionScaleSlider(
                            value = pendingResolutionScale,
                            onValueChange = { newValue ->
                                pendingResolutionScale = newValue
                                hasUnsavedResolutionChanges = newValue != currentResolutionScale
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Save button for resolution changes
                        if (hasUnsavedResolutionChanges) {
                            Button(
                                onClick = {
                                    currentResolutionScale = pendingResolutionScale
                                    settingsManager.saveResolutionScale(pendingResolutionScale)
                                    hasUnsavedResolutionChanges = false
                                    showRestartDialog = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(context.getString(R.string.save_resolution_scale))
                            }
                        }
                    }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // App Icon Style Selection - Carousel
            var selectedIconStyle by remember { mutableStateOf(settingsManager.getAppIconStyle()) }
            var scrollBehavior by remember { mutableStateOf(settingsManager.getScrollBehavior()) }
            
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                        Text(
                            text = context.getString(R.string.app_icon_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = context.getString(R.string.app_icon_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Icon Carousel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Define all icon options with their properties
                            val iconOptions = listOf(
                                IconOption("light", R.string.app_icon_light, R.string.app_icon_light_applied, R.mipmap.ic_launcher_foreground, Color.White),
                                IconOption("dark", R.string.app_icon_dark, R.string.app_icon_dark_applied, R.mipmap.ic_launcher_foreground_dark, Color(0xFF1A1A1A)),
                                IconOption("deep_blue", R.string.app_icon_deep_blue, R.string.app_icon_deep_blue_applied, R.mipmap.ic_launcher_foreground_deep_blue, Color(0xFF00283C)),
                                IconOption("blue_ocean", R.string.app_icon_blue_ocean, R.string.app_icon_blue_ocean_applied, R.mipmap.ic_launcher_foreground_blue_ocean, Color(0xFF000A3C)),
                                IconOption("braun", R.string.app_icon_braun, R.string.app_icon_braun_applied, R.mipmap.ic_launcher_foreground_braun, Color(0xFF3C1400)),
                                IconOption("purple", R.string.app_icon_purple, R.string.app_icon_purple_applied, R.mipmap.ic_launcher_foreground_purple, Color(0xFF321E32)),
                                IconOption("violet", R.string.app_icon_violet, R.string.app_icon_violet_applied, R.mipmap.ic_launcher_foreground_violet, Color(0xFF1E0A32))
                            )
                            
                            iconOptions.forEach { iconOption ->
                                val isSelected = selectedIconStyle == iconOption.style
                                
                            Card(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clickable {
                                            selectedIconStyle = iconOption.style
                                            println("🔄 Saving ${iconOption.style} icon style")
                                            settingsManager.saveAppIconStyle(iconOption.style)
                                            println("✅ ${iconOption.style} icon style saved")
                                        println("🔍 Verification - saved style is now: ${settingsManager.getAppIconStyle()}")
                                            println("🔄 Applying ${iconOption.style} icon")
                                            appIconManager.setAppIcon(iconOption.style)
                                            println("✅ ${iconOption.style} icon applied")
                                            // Show restart dialog
                                            showAppIconRestartDialog = true
                                        // Show toast to user
                                        android.widget.Toast.makeText(
                                            context,
                                                context.getString(iconOption.toastResId),
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                elevation = CardDefaults.cardElevation(
                                        defaultElevation = if (isSelected) 12.dp else 4.dp
                                ),
                                colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                        // Display icon preview - using actual app icon foreground
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .background(
                                                    color = iconOption.backgroundColor,
                                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Load mipmap resource using ImageUtils (painterResource doesn't support mipmap)
                                        val iconBitmap = remember(iconOption.iconResId) {
                                            ImageUtils.loadScaledImageBitmap(
                                                context = context,
                                                resId = iconOption.iconResId,
                                                maxWidthDp = 64.dp,
                                                maxHeightDp = 64.dp
                                            )
                                        }
                                        
                                        iconBitmap?.let { bitmap ->
                                            Image(
                                                bitmap = bitmap,
                                                contentDescription = context.getString(iconOption.nameResId),
                                                modifier = Modifier.size(64.dp)
                                            )
                                        } ?: run {
                                            // Fallback: try loading via ContextCompat
                                            val fallbackBitmap = remember(iconOption.iconResId) {
                                                ContextCompat.getDrawable(context, iconOption.iconResId)?.let { d ->
                                                    android.graphics.Bitmap.createBitmap(
                                                        d.intrinsicWidth.coerceAtLeast(1),
                                                        d.intrinsicHeight.coerceAtLeast(1),
                                                        android.graphics.Bitmap.Config.ARGB_8888
                                                    ).apply {
                                                        val canvas = android.graphics.Canvas(this)
                                                        d.setBounds(0, 0, width, height)
                                                        d.draw(canvas)
                                                    }.asImageBitmap()
                                                }
                                            }
                                            fallbackBitmap?.let { bitmap ->
                                                Image(
                                                    bitmap = bitmap,
                                                    contentDescription = context.getString(iconOption.nameResId),
                                                    modifier = Modifier.size(64.dp)
                                                )
                                            }
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                        Text(
                                            text = context.getString(iconOption.nameResId),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                                        if (isSelected) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = context.getString(R.string.app_icon_selected),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                                        }
                        }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Auto-Adapt Icon Based on System Theme
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var autoAdaptEnabled by remember { mutableStateOf(settingsManager.isAppIconAutoAdapt()) }
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = context.getString(R.string.app_icon_auto_adapt_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = context.getString(R.string.app_icon_auto_adapt_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (autoAdaptEnabled) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (appIconManager.isSystemDarkMode()) 
                                        context.getString(R.string.system_is_in_dark_mode)
                                    else 
                                        context.getString(R.string.system_is_in_light_mode),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Switch(
                            checked = autoAdaptEnabled,
                            onCheckedChange = {
                                autoAdaptEnabled = it
                                settingsManager.setAppIconAutoAdapt(it)
                                if (it) {
                                    // Apply the system-adapted icon immediately
                                    val adaptedIcon = appIconManager.getSystemAdaptedIcon()
                                    appIconManager.setAppIcon(adaptedIcon)
                                    selectedIconStyle = adaptedIcon
                                    settingsManager.saveAppIconStyle(adaptedIcon)
                                    // Show restart dialog
                                    showAppIconRestartDialog = true
                                    // Show toast to user
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.app_icon_auto_adapt_enabled),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // People Counter Visibility Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var peopleCounterVisible by remember { mutableStateOf(settingsManager.isPeopleCounterVisible()) }
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = context.getString(R.string.people_counter_visibility_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = context.getString(R.string.people_counter_visibility_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = peopleCounterVisible,
                            onCheckedChange = {
                                peopleCounterVisible = it
                                settingsManager.setPeopleCounterVisible(it)
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Statistics Visibility Toggle (Show Graphs)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var statisticsVisible by remember { mutableStateOf(settingsManager.isStatisticsVisible()) }
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = context.getString(R.string.statistics_visibility_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = context.getString(R.string.statistics_visibility_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = statisticsVisible,
                            onCheckedChange = {
                                statisticsVisible = it
                                settingsManager.setStatisticsVisible(it)
                            }
                        )
                    }
                    // (old statistics toggle moved above; this block kept)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Scroll Behavior for Manager Pages - Card selector
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = context.getString(R.string.scroll_behavior_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = context.getString(R.string.scroll_behavior_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val isHeaderPinned = scrollBehavior == SettingsManager.HEADER_PINNED
                        val isFullScroll = scrollBehavior == SettingsManager.FULL_SCROLL || scrollBehavior == SettingsManager.STICKY_FILTERS
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Option 1: Keep header fixed (only list scrolls)
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (!isHeaderPinned) {
                                            scrollBehavior = SettingsManager.HEADER_PINNED
                                            settingsManager.setScrollBehavior(SettingsManager.HEADER_PINNED)
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isHeaderPinned)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = if (isHeaderPinned) 8.dp else 2.dp
                                ),
                                border = BorderStroke(
                                    width = if (isHeaderPinned) 2.dp else 1.dp,
                                    color = if (isHeaderPinned)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ViewAgenda,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = context.getString(R.string.scroll_behavior_list_only_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = context.getString(R.string.scroll_behavior_list_only_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            // Option 2: Scroll whole page (header scrolls with list)
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (!isFullScroll) {
                                            scrollBehavior = SettingsManager.FULL_SCROLL
                                            settingsManager.setScrollBehavior(SettingsManager.FULL_SCROLL)
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isFullScroll)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = if (isFullScroll) 8.dp else 2.dp
                                ),
                                border = BorderStroke(
                                    width = if (isFullScroll) 2.dp else 1.dp,
                                    color = if (isFullScroll)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ViewDay,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = context.getString(R.string.scroll_behavior_full_page_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = context.getString(R.string.scroll_behavior_full_page_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        // Sub-option: Sticky filters (only visible when full scroll is selected)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isFullScroll,
                            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val newBehavior = if (scrollBehavior == SettingsManager.STICKY_FILTERS) {
                                                SettingsManager.FULL_SCROLL
                                            } else {
                                                SettingsManager.STICKY_FILTERS
                                            }
                                            scrollBehavior = newBehavior
                                            settingsManager.setScrollBehavior(newBehavior)
                                        }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = context.getString(R.string.scroll_behavior_sticky_filters_option),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = context.getString(R.string.scroll_behavior_sticky_filters_description),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = scrollBehavior == SettingsManager.STICKY_FILTERS,
                                        onCheckedChange = { checked ->
                                            val newBehavior = if (checked) {
                                                SettingsManager.STICKY_FILTERS
                                            } else {
                                                SettingsManager.FULL_SCROLL
                                            }
                                            scrollBehavior = newBehavior
                                            settingsManager.setScrollBehavior(newBehavior)
                                        },
                                        modifier = Modifier.scale(0.8f)
                                    )
                                }
                            }
                        }
                    }
                    
                    }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Localization Settings
        ExpandableSettingsCategory(
            title = context.getString(R.string.settings_category_localization),
            icon = Icons.Default.Language,
            isExpanded = showLocalizationSettings,
            onToggleExpanded = { 
                showLocalizationSettings = !showLocalizationSettings
                settingsManager.setCategoryLocalizationExpanded(showLocalizationSettings)
            }
        ) {
            // Language Selection
            var selectedLanguage by remember { mutableStateOf(settingsManager.getLanguage()) }
            var showLanguageMenu by remember { mutableStateOf(false) }
            
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = context.getString(R.string.language_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = context.getString(R.string.language_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { showLanguageMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = when (selectedLanguage) {
                                "fr" -> context.getString(R.string.language_french)
                                "es" -> context.getString(R.string.language_spanish)
                                "zh-TW" -> context.getString(R.string.language_chinese)
                                "zh-CN" -> context.getString(R.string.language_chinese_simplified)
                                else -> context.getString(R.string.language_english)
                            },
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showLanguageMenu,
                        onDismissRequest = { showLanguageMenu = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.language_english)) },
                            onClick = {
                                selectedLanguage = "en"
                                settingsManager.saveLanguage("en")
                                showLanguageMenu = false
                                (context as? android.app.Activity)?.recreate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.language_french)) },
                            onClick = {
                                selectedLanguage = "fr"
                                settingsManager.saveLanguage("fr")
                                showLanguageMenu = false
                                (context as? android.app.Activity)?.recreate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.language_spanish)) },
                            onClick = {
                                selectedLanguage = "es"
                                settingsManager.saveLanguage("es")
                                showLanguageMenu = false
                                (context as? android.app.Activity)?.recreate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.language_chinese)) },
                            onClick = {
                                selectedLanguage = "zh-TW"
                                settingsManager.saveLanguage("zh-TW")
                                showLanguageMenu = false
                                (context as? android.app.Activity)?.recreate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.language_chinese_simplified)) },
                            onClick = {
                                selectedLanguage = "zh-CN"
                                settingsManager.saveLanguage("zh-CN")
                                showLanguageMenu = false
                                (context as? android.app.Activity)?.recreate()
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Date and Time Format Selection
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = context.getString(R.string.date_time_format_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = context.getString(R.string.date_time_format_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                var selectedDateFormat by remember { mutableStateOf(settingsManager.getDateFormat()) }
                var showDateFormatMenu by remember { mutableStateOf(false) }
                
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { showDateFormatMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = when (selectedDateFormat) {
                                "dd/MM/yyyy" -> context.getString(R.string.date_format_dd_mm_yyyy)
                                "MM/dd/yyyy" -> context.getString(R.string.date_format_mm_dd_yyyy)
                                "yyyy-MM-dd" -> context.getString(R.string.date_format_yyyy_mm_dd)
                                else -> context.getString(R.string.date_format_dd_mm_yyyy)
                            },
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showDateFormatMenu,
                        onDismissRequest = { showDateFormatMenu = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.date_format_dd_mm_yyyy)) },
                            onClick = {
                                selectedDateFormat = "dd/MM/yyyy"
                                settingsManager.saveDateFormat(selectedDateFormat)
                                showDateFormatMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.date_format_mm_dd_yyyy)) },
                            onClick = {
                                selectedDateFormat = "MM/dd/yyyy"
                                settingsManager.saveDateFormat(selectedDateFormat)
                                showDateFormatMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.date_format_yyyy_mm_dd)) },
                            onClick = {
                                selectedDateFormat = "yyyy-MM-dd"
                                settingsManager.saveDateFormat(selectedDateFormat)
                                showDateFormatMenu = false
                            }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                var selectedTimeFormat by remember { mutableStateOf(settingsManager.getTimeFormat()) }
                var showTimeFormatMenu by remember { mutableStateOf(false) }
                
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { showTimeFormatMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = when (selectedTimeFormat) {
                                "HH:mm" -> context.getString(R.string.time_format_hh_mm)
                                "HH:mm:ss" -> context.getString(R.string.time_format_hh_mm_ss)
                                "HH:mm:ss.SSS" -> context.getString(R.string.time_format_hh_mm_ss_sss)
                                else -> context.getString(R.string.time_format_hh_mm)
                            },
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Start
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showTimeFormatMenu,
                        onDismissRequest = { showTimeFormatMenu = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.time_format_hh_mm)) },
                            onClick = {
                                selectedTimeFormat = "HH:mm"
                                settingsManager.saveTimeFormat(selectedTimeFormat)
                                showTimeFormatMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.time_format_hh_mm_ss)) },
                            onClick = {
                                selectedTimeFormat = "HH:mm:ss"
                                settingsManager.saveTimeFormat(selectedTimeFormat)
                                showTimeFormatMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(context.getString(R.string.time_format_hh_mm_ss_sss)) },
                            onClick = {
                                selectedTimeFormat = "HH:mm:ss.SSS"
                                settingsManager.saveTimeFormat(selectedTimeFormat)
                                showTimeFormatMenu = false
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Date Change Offset Setting
            var dateChangeOffsetHours by remember { mutableStateOf(settingsManager.getDateChangeOffsetHours()) }
            
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = context.getString(R.string.date_change_offset_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = context.getString(R.string.date_change_offset_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (dateChangeOffsetHours > -12) {
                                dateChangeOffsetHours--
                                settingsManager.saveDateChangeOffsetHours(dateChangeOffsetHours)
                            }
                        },
                        enabled = dateChangeOffsetHours > -12
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = context.getString(R.string.date_change_offset_decrease)
                        )
                    }
                    
                    Text(
                        text = if (dateChangeOffsetHours == 0) {
                            context.getString(R.string.date_change_offset_zero)
                        } else if (dateChangeOffsetHours > 0) {
                            context.getString(R.string.date_change_offset_hours, dateChangeOffsetHours) + " (${context.getString(R.string.date_change_offset_time, dateChangeOffsetHours)})"
                        } else {
                            context.getString(R.string.date_change_offset_hours, dateChangeOffsetHours) + " (${context.getString(R.string.date_change_offset_previous_day, 24 + dateChangeOffsetHours)})"
                        },
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    IconButton(
                        onClick = {
                            if (dateChangeOffsetHours < 12) {
                                dateChangeOffsetHours++
                                settingsManager.saveDateChangeOffsetHours(dateChangeOffsetHours)
                            }
                        },
                        enabled = dateChangeOffsetHours < 12
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = context.getString(R.string.date_change_offset_increase)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Animations & Effects Settings
        ExpandableSettingsCategory(
            title = context.getString(R.string.settings_category_animations),
            icon = Icons.Default.PlayArrow,
            isExpanded = showAnimationSettings,
            onToggleExpanded = { 
                showAnimationSettings = !showAnimationSettings
                settingsManager.setCategoryAnimationExpanded(showAnimationSettings)
            }
        ) {
            // Animated Background Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var animatedEnabled by remember { mutableStateOf(settingsManager.isAnimatedBackgroundEnabled()) }
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = context.getString(R.string.animated_background_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = context.getString(R.string.animated_background_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = animatedEnabled,
                    onCheckedChange = {
                        animatedEnabled = it
                        settingsManager.setAnimatedBackgroundEnabled(it)
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Page Animations Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var pageAnimationsEnabled by remember { mutableStateOf(settingsManager.isPageAnimationsEnabled()) }
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = context.getString(R.string.page_animations_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = context.getString(R.string.page_animations_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = pageAnimationsEnabled,
                    onCheckedChange = {
                        pageAnimationsEnabled = it
                        settingsManager.setPageAnimationsEnabled(it)
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Seasonal Fun Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var seasonalFunEnabled by remember { mutableStateOf(settingsManager.isSeasonalFunEnabled()) }
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = context.getString(R.string.seasonal_fun_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = context.getString(R.string.seasonal_fun_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = seasonalFunEnabled,
                    onCheckedChange = {
                        seasonalFunEnabled = it
                        settingsManager.setSeasonalFunEnabled(it)
                    }
                )
            }
            
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Developer & Debug Settings
        ExpandableSettingsCategory(
            title = context.getString(R.string.settings_category_developer),
            icon = Icons.Default.BugReport,
            isExpanded = showDeveloperSettings,
            onToggleExpanded = { 
                showDeveloperSettings = !showDeveloperSettings
                settingsManager.setCategoryDeveloperExpanded(showDeveloperSettings)
            }
        ) {
            // Debug Mode Toggle
            var debugModeEnabled by remember { mutableStateOf(settingsManager.getDebugMode()) }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = context.getString(R.string.debug_mode_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = context.getString(R.string.debug_mode_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = debugModeEnabled,
                    onCheckedChange = {
                        debugModeEnabled = it
                        settingsManager.saveDebugMode(it)
                        com.eventmanager.app.data.sync.AppLogger.setIntercepting(it)
                        com.eventmanager.app.data.sync.AppLogger.i("SettingsScreen", "Debug mode ${if (it) "enabled" else "disabled"}")
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Log Files Management (shown when debug mode is enabled)
            if (debugModeEnabled) {
                var logFilesState by remember { mutableStateOf(com.eventmanager.app.data.sync.AppLogger.getAllLogFiles()) }
                var totalLogSizeState by remember { mutableStateOf(com.eventmanager.app.data.sync.AppLogger.getTotalLogSize()) }
                var showLogViewer by remember { mutableStateOf(false) }
                var selectedLogContent by remember { mutableStateOf<String?>(null) }
                val logsDirectoryPath = remember { com.eventmanager.app.data.sync.AppLogger.getLogsDirectoryPath() }
                val logCoroutineScope = rememberCoroutineScope()
                
                LaunchedEffect(debugModeEnabled) {
                    if (debugModeEnabled) {
                        logFilesState = com.eventmanager.app.data.sync.AppLogger.getAllLogFiles()
                        totalLogSizeState = com.eventmanager.app.data.sync.AppLogger.getTotalLogSize()
                    }
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = context.getString(R.string.debug_logs_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = context.getString(R.string.debug_logs_location, logsDirectoryPath),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = context.getString(
                            R.string.debug_logs_files_size,
                            logFilesState.size,
                            String.format("%.2f", totalLogSizeState / (1024.0 * 1024.0))
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (logFilesState.isNotEmpty()) {
                        Text(
                            text = context.getString(R.string.debug_logs_recent_files),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        logFilesState.takeLast(3).reversed().forEach { logFile ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = logFile.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${String.format("%.2f KB", logFile.length() / 1024.0)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = {
                                            logCoroutineScope.launch {
                                                selectedLogContent = try {
                                                    logFile.readText()
                                                } catch (e: Exception) {
                                                    context.getString(R.string.debug_logs_error_reading, e.message ?: "")
                                                }
                                                showLogViewer = true
                                            }
                                        }
                                    ) {
                                        Text(context.getString(R.string.debug_logs_view), style = MaterialTheme.typography.labelSmall)
                                    }
                                    
                                    TextButton(
                                        onClick = {
                                            try {
                                                val uri = FileProvider.getUriForFile(
                                                    context,
                                                    "com.eventmanager.app.fileprovider",
                                                    logFile
                                                )
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.debug_logs_share_file)))
                                            } catch (e: Exception) {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, try {
                                                        logFile.readText()
                                                    } catch (ex: Exception) {
                                                        context.getString(R.string.debug_logs_error_reading_generic)
                                                    })
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.debug_logs_share_file)))
                                            }
                                        }
                                    ) {
                                        Text(context.getString(R.string.debug_logs_share), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (logFilesState.isNotEmpty()) {
                            Button(
                                onClick = {
                                    logCoroutineScope.launch {
                                        selectedLogContent = com.eventmanager.app.data.sync.AppLogger.getLatestLogContent()
                                        showLogViewer = true
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Text(
                                    text = context.getString(R.string.debug_logs_view_latest),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        
                        Button(
                            onClick = {
                                com.eventmanager.app.data.sync.AppLogger.clearAllLogs()
                                logFilesState = com.eventmanager.app.data.sync.AppLogger.getAllLogFiles()
                                totalLogSizeState = com.eventmanager.app.data.sync.AppLogger.getTotalLogSize()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = context.getString(R.string.debug_logs_clear_all),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
                
                if (showLogViewer && selectedLogContent != null) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { showLogViewer = false }) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .fillMaxHeight(0.8f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = context.getString(R.string.debug_logs_content),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(onClick = { showLogViewer = false }) {
                                        Icon(Icons.Default.Close, contentDescription = context.getString(R.string.close))
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                val scrollState = rememberScrollState()
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .verticalScroll(scrollState)
                                ) {
                                    Text(
                                        text = selectedLogContent ?: context.getString(R.string.debug_logs_no_content),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.fillMaxWidth(),
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Maintenance Settings
        var showUpdateSourcesDialog by remember { mutableStateOf(false) }
        
        ExpandableSettingsCategory(
            title = context.getString(R.string.settings_category_maintenance),
            icon = Icons.Default.Build,
            isExpanded = showMaintenanceSettings,
            onToggleExpanded = { 
                showMaintenanceSettings = !showMaintenanceSettings
                settingsManager.setCategoryMaintenanceExpanded(showMaintenanceSettings)
            }
        ) {
            // View Active Volunteers Button
            Button(
                onClick = { showActiveVolunteersDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.People, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(context.getString(R.string.view_active_volunteers))
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Cleanup Inactive Volunteers Button
            Button(
                onClick = { showCleanupDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(context.getString(R.string.cleanup_inactive_volunteers))
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Check for updates
            Button(
                onClick = {
                    viewModel.checkForAppUpdates()
                    showUpdateResultDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(context.getString(R.string.check_for_updates))
            }
            
            // Change update sources link
            TextButton(
                onClick = { showUpdateSourcesDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = context.getString(R.string.change_update_sources),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Clear Cache Button
            OutlinedButton(
                onClick = { 
                    // Clear app data cache (database contents) but keep settings and key file
                    viewModel.clearAppData()
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.app_cache_cleared),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Clear, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(context.getString(R.string.clear_app_cache))
            }
        }
        
        // Update Sources Dialog
        if (showUpdateSourcesDialog) {
            UpdateSourcesDialog(
                settingsManager = settingsManager,
                onDismiss = { showUpdateSourcesDialog = false }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Job Type Management Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Work,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = context.getString(R.string.shift_type_management_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = context.getString(R.string.shift_type_management_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onNavigateToJobTypeManagement,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.manage_shift_types))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Venue Management Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = context.getString(R.string.venue_management_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = context.getString(R.string.venue_management_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onNavigateToVenueManagement,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(context.getString(R.string.manage_venues))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // App Information Section with Easter Egg
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val currentTime = System.currentTimeMillis()
                    // Reset if more than 500ms between taps
                    if (currentTime - lastEasterEggTapTime > 500) {
                        easterEggTapCount = 1
                    } else {
                        easterEggTapCount++
                    }
                    lastEasterEggTapTime = currentTime
                    
                    // Trigger Easter Egg after 10 rapid taps
                    if (easterEggTapCount >= 10) {
                        showEasterEggDialog = true
                        easterEggTapCount = 0
                    }
                },
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = context.getString(R.string.app_info_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${context.getString(R.string.app_name)} ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = context.getString(R.string.app_designed_for),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = context.getString(R.string.app_developed_full),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = context.getString(
                            R.string.last_sync,
                            if (settingsManager.getLastSyncTime() > 0)
                                com.eventmanager.app.data.sync.DateFormatUtils.formatDateTime(
                                    settingsManager.getLastSyncTime(),
                                    context
                                )
                            else context.getString(R.string.never)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Update result dialog
    if (showUpdateResultDialog) {
        val updateResult = viewModel.updateCheckState.collectAsState().value
        val downloadState = viewModel.updateDownloadState.collectAsState().value
        
        when (updateResult) {
            is com.eventmanager.app.data.update.UpdateCheckResult.UpdateAvailable -> {
                val manifest = updateResult.manifest
                val isRequired = updateResult.isRequired
                
                // Show download progress if downloading
                when (downloadState) {
                    is com.eventmanager.app.data.update.DownloadState.Downloading -> {
                        AlertDialog(
                            onDismissRequest = { },
                            title = {
                                Text(text = context.getString(R.string.downloading_update))
                            },
                            text = {
                                Column {
                                    LinearProgressIndicator(
                                        progress = { downloadState.progress / 100f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = context.getString(R.string.download_progress, downloadState.progress),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            },
                            confirmButton = {}
                        )
                    }
                    is com.eventmanager.app.data.update.DownloadState.Downloaded -> {
                        AlertDialog(
                            onDismissRequest = if (isRequired) { {} } else { { showUpdateResultDialog = false } },
                            properties = if (isRequired) {
                                DialogProperties(
                                    dismissOnBackPress = false,
                                    dismissOnClickOutside = false
                                )
                            } else {
                                DialogProperties()
                            },
                            title = {
                                Text(text = context.getString(R.string.download_complete))
                            },
                            text = {
                                Text(text = context.getString(R.string.update_available_message))
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.installUpdate(downloadState.file)
                                    showUpdateResultDialog = false
                                }) {
                                    Text(context.getString(R.string.install_update))
                                }
                            },
                            dismissButton = if (!isRequired) {
                                {
                                    TextButton(onClick = { showUpdateResultDialog = false }) {
                                        Text(context.getString(R.string.later))
                                    }
                                }
                            } else null
                        )
                    }
                    is com.eventmanager.app.data.update.DownloadState.Error -> {
                        AlertDialog(
                            onDismissRequest = if (isRequired) { {} } else { { showUpdateResultDialog = false } },
                            title = {
                                Text(text = context.getString(R.string.download_error_title))
                            },
                            text = {
                                Text(text = context.getString(R.string.download_error_message, downloadState.message))
                            },
                            confirmButton = {
                                TextButton(onClick = { showUpdateResultDialog = false }) {
                                    Text(context.getString(R.string.ok))
                                }
                            }
                        )
                    }
                    else -> {
                        // Show update available dialog
                        AlertDialog(
                            onDismissRequest = if (isRequired) { {} } else { { showUpdateResultDialog = false } },
                            title = {
                                Text(text = context.getString(R.string.update_available_title, manifest.latestVersionName))
                            },
                            text = {
                                Text(
                                    text = manifest.changelogShort
                                        ?: if (isRequired) {
                                            context.getString(R.string.update_required_message)
                                        } else {
                                            context.getString(R.string.update_available_message)
                                        }
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    // Start download instead of opening browser
                                    val downloadUrl = manifest.downloadUrl
                                    if (downloadUrl != null) {
                                        viewModel.downloadUpdate(downloadUrl)
                                    } else {
                                        // Fallback to browser if no download URL
                                        val targetUrl = manifest.storeUrl
                                            ?: settingsManager.getUpdateStoreUrl()
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                        context.startActivity(intent)
                                        showUpdateResultDialog = false
                                    }
                                }) {
                                    Text(context.getString(R.string.update_now))
                                }
                            },
                            dismissButton = {
                                if (!isRequired) {
                                    TextButton(onClick = { showUpdateResultDialog = false }) {
                                        Text(context.getString(R.string.later))
                                    }
                                }
                            }
                        )
                    }
                }
            }
            is com.eventmanager.app.data.update.UpdateCheckResult.NoUpdate -> {
                AlertDialog(
                    onDismissRequest = { showUpdateResultDialog = false },
                    title = {
                        Text(text = context.getString(R.string.up_to_date_title))
                    },
                    text = {
                        Text(text = context.getString(R.string.up_to_date_message))
                    },
                    confirmButton = {
                        TextButton(onClick = { showUpdateResultDialog = false }) {
                            Text(context.getString(R.string.ok))
                        }
                    }
                )
            }
            is com.eventmanager.app.data.update.UpdateCheckResult.Error -> {
                AlertDialog(
                    onDismissRequest = { showUpdateResultDialog = false },
                    title = {
                        Text(text = context.getString(R.string.update_error_title))
                    },
                    text = {
                        Text(text = context.getString(R.string.update_error_message, updateResult.message))
                    },
                    confirmButton = {
                        TextButton(onClick = { showUpdateResultDialog = false }) {
                            Text(context.getString(R.string.ok))
                        }
                    }
                )
            }
            else -> {
                // Still loading or no result yet: simple loading dialog
                AlertDialog(
                    onDismissRequest = { showUpdateResultDialog = false },
                    title = {
                        Text(text = context.getString(R.string.checking_for_updates_title))
                    },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = context.getString(R.string.checking_for_updates_message))
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showUpdateResultDialog = false }) {
                            Text(context.getString(R.string.cancel))
                        }
                    }
                )
            }
        }
    }
    
    // Instructions Dialog
    if (showInstructions) {
        GoogleSheetsInstructionsDialog(
            onDismiss = { showInstructions = false }
        )
    }
    
    // Sync Status Dialog (used by test connection button)
    SyncStatusDialog(
        isVisible = showSyncStatusDialog,
        onDismiss = { viewModel.dismissSyncStatusDialog() },
        statusMessage = syncStatusMessage
    )
    
    // Active Volunteers Dialog
    if (showActiveVolunteersDialog) {
        ActiveVolunteersDialog(
            volunteers = viewModel.volunteers.collectAsState().value,
            onDismiss = { showActiveVolunteersDialog = false }
        )
    }
    
    // Cleanup Inactive Volunteers Dialog
    if (showCleanupDialog) {
        CleanupInactiveVolunteersDialog(
            volunteers = viewModel.volunteers.collectAsState().value,
            onConfirm = { yearsInactive ->
                viewModel.cleanupInactiveVolunteers(yearsInactive)
                showCleanupDialog = false
            },
            onDismiss = { showCleanupDialog = false }
        )
    }
    
    // Restart App Dialog
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = {
                Text(
                    text = context.getString(R.string.restart_required_title),
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    text = context.getString(R.string.restart_required_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestartDialog = false
                        // Restart the app
                        (context as? android.app.Activity)?.recreate()
                    }
                ) {
                    Text(context.getString(R.string.restart_now))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestartDialog = false }
                ) {
                    Text(context.getString(R.string.restart_later))
                }
            }
        )
    }
    
    // App Icon Restart Dialog
    AppRestartDialog(
        isVisible = showAppIconRestartDialog,
        onDismiss = { showAppIconRestartDialog = false }
    )
    
    // Easter Egg Dialog
    if (showEasterEggDialog) {
        RetroSynthwaveGameDialog(
            onDismiss = { showEasterEggDialog = false },
            onDriveSelected = {
                showEasterEggDialog = false
                showDriveGame = true
            },
            onFlappySelected = {
                showEasterEggDialog = false
                showFlappyGame = true
            }
        )
    }
    
    // Flappy Nocturne Game Dialog
    if (showFlappyGame) {
        FlappyNocturneGameDialog(
            onDismiss = { showFlappyGame = false }
        )
    }
    
    // Drive Nocturne Game Dialog
    if (showDriveGame) {
        DriveNocturneGameDialog(
            onDismiss = { showDriveGame = false }
        )
    }
    
}

@Composable
fun GoogleSheetsInstructionsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                context.getString(R.string.google_sheets_setup_instructions),
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = context.getString(R.string.setup_instructions_intro),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                item {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://console.cloud.google.com/"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(context.getString(R.string.open_google_cloud_console))
                    }
                }
                
                item {
                    InstructionStep(
                        number = "1",
                        title = context.getString(R.string.step_1_title),
                        description = context.getString(R.string.step_1_description)
                    )
                }
                
                item {
                    InstructionStep(
                        number = "2",
                        title = context.getString(R.string.step_2_title),
                        description = context.getString(R.string.step_2_description)
                    )
                }
                
                item {
                    InstructionStep(
                        number = "3",
                        title = context.getString(R.string.step_3_title),
                        description = context.getString(R.string.step_3_description)
                    )
                }
                
                item {
                    InstructionStep(
                        number = "4",
                        title = context.getString(R.string.step_4_title),
                        description = context.getString(R.string.step_4_description)
                    )
                }
                
                item {
                    InstructionStep(
                        number = "5",
                        title = context.getString(R.string.step_5_title),
                        description = context.getString(R.string.step_5_description)
                    )
                }
                
                item {
                    InstructionStep(
                        number = "6",
                        title = context.getString(R.string.step_6_title),
                        description = context.getString(R.string.step_6_description)
                    )
                }
                
                item {
                    InstructionStep(
                        number = "7",
                        title = context.getString(R.string.step_7_title),
                        description = context.getString(R.string.step_7_description)
                    )
                }
                
                item {
                    InstructionStep(
                        number = "8",
                        title = context.getString(R.string.step_8_title),
                        description = context.getString(R.string.step_8_description)
                    )
                }
                
                item {
                    InstructionStep(
                        number = "9",
                        title = context.getString(R.string.step_9_title),
                        description = context.getString(R.string.step_9_description)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.got_it))
            }
        }
    )
}

@Composable
fun InstructionStep(
    number: String,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top
    ) {
        Card(
            modifier = Modifier.size(24.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ActiveVolunteersDialog(
    volunteers: List<Volunteer>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activeVolunteers = volunteers.filter { VolunteerActivityManager.isVolunteerActive(it) }
    val inactiveVolunteers = volunteers.filter { !VolunteerActivityManager.isVolunteerActive(it) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(context.getString(R.string.volunteer_activity_status))
        },
        text = {
            Column {
                Text(
                    text = context.getString(R.string.active_volunteers_count, activeVolunteers.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = context.getString(R.string.inactive_volunteers_count, inactiveVolunteers.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF9E9E9E),
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = activeVolunteers,
                        key = { volunteer -> volunteer.id }
                    ) { volunteer ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Circle,
                                contentDescription = "Active",
                                modifier = Modifier.size(8.dp),
                                tint = Color(0xFF4CAF50)
                            )
                            Text(
                                text = volunteer.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = VolunteerActivityManager.getActivityStatusText(volunteer, context),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                    
                    if (inactiveVolunteers.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = context.getString(R.string.inactive_volunteers_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF9E9E9E),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        items(
                            items = inactiveVolunteers,
                            key = { volunteer -> volunteer.id }
                        ) { volunteer ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Circle,
                                    contentDescription = "Inactive",
                                    modifier = Modifier.size(8.dp),
                                    tint = Color(0xFF9E9E9E)
                                )
                                Text(
                                    text = volunteer.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF9E9E9E)
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = VolunteerActivityManager.getActivityStatusText(volunteer, context),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF9E9E9E)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.close))
            }
        }
    )
}

@Composable
fun UpdateSourcesDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    var githubUrl by remember { mutableStateOf(settingsManager.getUpdateManifestUrl()) }
    var storeUrl by remember { mutableStateOf(settingsManager.getUpdateStoreUrl()) }
    
    val defaultGithubUrl = BuildConfig.UPDATE_MANIFEST_URL
    val defaultStoreUrl = BuildConfig.UPDATE_FALLBACK_STORE_URL
    
    val isGithubUrlChanged = githubUrl != defaultGithubUrl
    val isStoreUrlChanged = storeUrl != defaultStoreUrl
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = context.getString(R.string.update_sources_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Warning card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = context.getString(R.string.update_sources_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                
                // GitHub URL field
                Column {
                    Text(
                        text = context.getString(R.string.update_sources_github_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = githubUrl,
                        onValueChange = { githubUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { 
                            Text(
                                text = defaultGithubUrl,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (isGithubUrlChanged) {
                                IconButton(
                                    onClick = { githubUrl = defaultGithubUrl }
                                ) {
                                    Icon(
                                        Icons.Default.Restore,
                                        contentDescription = context.getString(R.string.update_sources_reset),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = false,
                        maxLines = 2,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    if (isGithubUrlChanged) {
                        Text(
                            text = context.getString(R.string.update_sources_custom_url),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }
                
                // Store URL field
                Column {
                    Text(
                        text = context.getString(R.string.update_sources_store_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = storeUrl,
                        onValueChange = { storeUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { 
                            Text(
                                text = defaultStoreUrl,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Store,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (isStoreUrlChanged) {
                                IconButton(
                                    onClick = { storeUrl = defaultStoreUrl }
                                ) {
                                    Icon(
                                        Icons.Default.Restore,
                                        contentDescription = context.getString(R.string.update_sources_reset),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = false,
                        maxLines = 2,
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    if (isStoreUrlChanged) {
                        Text(
                            text = context.getString(R.string.update_sources_custom_url),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    settingsManager.saveUpdateManifestUrl(githubUrl)
                    settingsManager.saveUpdateStoreUrl(storeUrl)
                    onDismiss()
                }
            ) {
                Text(context.getString(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.cancel))
            }
        }
    )
}
