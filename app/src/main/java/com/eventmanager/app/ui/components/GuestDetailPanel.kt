package com.eventmanager.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import com.eventmanager.app.data.models.*
import com.eventmanager.app.ui.utils.*
import com.eventmanager.app.utils.QRCodeUtils
import com.eventmanager.app.R
import com.google.gson.Gson
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.data.sync.GmailAuthService
import com.eventmanager.app.data.sync.GmailSendService
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes

@Composable
private fun getStringResource(@StringRes stringRes: Int, vararg args: Any): String {
    val context = LocalContext.current
    return context.getString(stringRes, *args)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestDetailPanel(
    guest: Guest,
    venues: List<VenueEntity>,
    onEdit: (Guest) -> Unit,
    onDelete: (Guest) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isPhone = !isTablet()
    val responsivePadding = if (isPhone) getPhonePortraitPadding() else getResponsivePadding()
    var showQrDialog by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Background
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(if (isPhone) 12.dp else 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Modern header with guest name (FIXED AT TOP)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(responsivePadding)
                        .padding(bottom = if (isPhone) 8.dp else 12.dp),
                    shape = RoundedCornerShape(if (isPhone) 12.dp else 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(if (isPhone) 12.dp else 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = guest.name,
                                    style = if (isPhone) getPhonePortraitTypography() else getResponsiveTypography(),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                
                                if (guest.lastNameAbbreviation.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(if (isPhone) 2.dp else 4.dp))
                                    
                                    Text(
                                        text = guest.lastNameAbbreviation,
                                        style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            
                            IconButton(onClick = onClose) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = context.getString(R.string.close),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
                
                // Scrollable content (SCROLLS BELOW HEADER)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(if (isPhone) 8.dp else 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = responsivePadding,
                        end = responsivePadding,
                        bottom = responsivePadding
                    )
                ) {
                    // Guest Information Section
                    item {
                        GuestInformationSection(
                            guest = guest,
                            venues = venues,
                            isPhone = isPhone
                        )
                    }
                    
                    // Action Buttons Section
                    item {
                        ActionButtonsSection(
                            guest = guest,
                            onEdit = onEdit,
                            onDelete = onDelete,
                            onShowQr = { showQrDialog = true },
                            isPhone = isPhone
                        )
                    }
                }
            }
        }
    }
    
    // Email confirmation dialog state
    var showEmailConfirmDialog by remember { mutableStateOf(false) }
    var showEmailInputDialog by remember { mutableStateOf(false) }
    var emailInputValue by remember { mutableStateOf("") }

    if (showQrDialog) {
        val tabletMaxWidth = getTabletConstrainedDialogMaxWidth()
        val tabletQrSize = getTabletConstrainedQRCodeSize()
        val tabletDialogPadding = getTabletConstrainedDialogPadding()
        val isTabletDevice = isTablet()
        
        Dialog(
            onDismissRequest = { showQrDialog = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = !isTabletDevice,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Card(
                modifier = Modifier
                    .then(
                        if (isTabletDevice) {
                            Modifier.widthIn(max = tabletMaxWidth)
                        } else {
                            Modifier.fillMaxWidth(0.92f)
                        }
                    )
                    .padding(tabletDialogPadding),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title
                    Text(
                        text = getStringResource(R.string.guest_qr_code),
                        style = if (isPhone) getPhonePortraitTypography() else getTabletConstrainedTitleTypography(),
                        fontWeight = FontWeight.Bold
                    )
                    
                    val payload = remember(guest) {
                        val data = mapOf(
                            "type" to "guest",
                            "version" to 1,
                            "name" to guest.name,
                            "abbr" to guest.lastNameAbbreviation
                        )
                        val json = Gson().toJson(data)
                        println("🔍 Generating QR code for guest: ${guest.name}")
                        println("🔍 QR code JSON: '$json'")
                        json
                    }
                    val qrImage = remember(payload) { QRCodeUtils.generateQrImageBitmap(payload, 1024) }
                    val qrContext = LocalContext.current
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (qrImage != null) {
                            Image(
                                bitmap = qrImage,
                                contentDescription = getStringResource(R.string.guest_qr_code),
                                modifier = Modifier
                                    .then(
                                        if (isTabletDevice) {
                                            Modifier.size(tabletQrSize)
                                        } else {
                                            Modifier.fillMaxWidth().aspectRatio(1f)
                                        }
                                    )
                                    .clip(RoundedCornerShape(if (isPhone) 8.dp else 12.dp))
                                    .background(Color.White)
                            )
                            Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))
                            Text(
                                text = guest.name,
                                style = if (isPhone) getPhonePortraitBodyTypography() else getTabletConstrainedBodyTypography(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(if (isTabletDevice) 8.dp else 12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        qrImage?.let { bitmap ->
                                            try {
                                                val file = File(qrContext.cacheDir, "qr_code_guest_${guest.id}.png")
                                                val outputStream = FileOutputStream(file)
                                                bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                                                outputStream.close()
                                                
                                                val uri = FileProvider.getUriForFile(
                                                    qrContext,
                                                    "${qrContext.packageName}.fileprovider",
                                                    file
                                                )
                                                
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "image/png"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    putExtra(Intent.EXTRA_SUBJECT, qrContext.getString(R.string.qr_code_subject_guest, guest.name))
                                                    putExtra(Intent.EXTRA_TEXT, qrContext.getString(R.string.qr_code_for_guest, guest.name))
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                qrContext.startActivity(Intent.createChooser(shareIntent, qrContext.getString(R.string.share_qr_code)))
                                            } catch (e: Exception) {
                                                // Fallback to text sharing if image sharing fails
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, "Guest QR")
                                                    putExtra(
                                                        Intent.EXTRA_TEXT,
                                                        "Guest: ${guest.name}\nPayload: $payload"
                                                    )
                                                }
                                                qrContext.startActivity(Intent.createChooser(shareIntent, qrContext.getString(R.string.share_qr_code)))
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (isTabletDevice) 48.dp else 64.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(getStringResource(R.string.share))
                                }
                                OutlinedButton(
                                    onClick = {
                                        // Check if guest has email
                                        if (guest.email.isNotBlank()) {
                                            showEmailConfirmDialog = true
                                        } else {
                                            // Ask for email address
                                            emailInputValue = ""
                                            showEmailInputDialog = true
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (isTabletDevice) 48.dp else 64.dp)
                                ) {
                                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(getStringResource(R.string.send_by_mail))
                                }
                            }
                        } else {
                            Text(
                                text = getStringResource(R.string.failed_to_generate_qr_code),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    
                    // Close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showQrDialog = false }) {
                            Text(getStringResource(R.string.close))
                        }
                    }
                }
            }
        }
    }
    
    // Email Input Dialog (for guests without email)
    if (showEmailInputDialog) {
        AlertDialog(
            onDismissRequest = { showEmailInputDialog = false },
            icon = {
                Icon(
                    Icons.Default.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = getStringResource(R.string.guest_email_input_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = getStringResource(R.string.guest_email_input_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    OutlinedTextField(
                        value = emailInputValue,
                        onValueChange = { emailInputValue = it },
                        label = { Text(getStringResource(R.string.guest_email)) },
                        placeholder = { Text("example@email.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (emailInputValue.isNotBlank() && emailInputValue.contains("@")) {
                            showEmailInputDialog = false
                            showEmailConfirmDialog = true
                        }
                    },
                    enabled = emailInputValue.isNotBlank() && emailInputValue.contains("@")
                ) {
                    Text(getStringResource(R.string.continue_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmailInputDialog = false }) {
                    Text(getStringResource(R.string.cancel))
                }
            }
        )
    }
    
    // Email Confirmation Dialog
    if (showEmailConfirmDialog) {
        val emailContext = LocalContext.current
        val settingsManager = remember { SettingsManager(emailContext) }
        val gmailAuthService = remember { GmailAuthService(emailContext) }
        val gmailSendService = remember { GmailSendService(emailContext) }
        val coroutineScope = rememberCoroutineScope()
        val isGmailAuthenticated = remember { gmailAuthService.isAccountSelected() }
        
        // Use either the guest's email or the manually entered email
        val targetEmail = if (guest.email.isNotBlank()) guest.email else emailInputValue
        
        // Holder for authLauncher - needed to break circular dependency
        val authLauncherHolder = remember { mutableStateOf<androidx.activity.result.ActivityResultLauncher<Intent>?>(null) }
        
        // State for showing loading during email send
        var isSendingEmail by remember { mutableStateOf(false) }
        
        // API email send function - Multipart/related via Gmail API
        suspend fun sendGuestEmailViaApi(
            emailContext: android.content.Context,
            settingsManager: SettingsManager,
            gmailAuthService: GmailAuthService,
            gmailSendService: GmailSendService,
            guest: Guest,
            targetEmail: String,
            onSuccess: () -> Unit
        ) {
            try {
                // Get Gmail service
                val gmailService = gmailAuthService.createGmailService()
                if (gmailService == null) {
                    Toast.makeText(
                        emailContext,
                        emailContext.getString(R.string.email_api_not_authenticated),
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
                
                // Get email settings
                val subject = settingsManager.getGuestEmailSubject().ifEmpty { 
                    emailContext.getString(R.string.guest_email_subject_default) 
                }
                val contentBefore = settingsManager.getGuestEmailContentBefore().ifEmpty { 
                    emailContext.getString(R.string.guest_email_content_before_default) 
                }
                val includeQr = settingsManager.isGuestEmailIncludeQrEnabled()
                val contentAfter = settingsManager.getGuestEmailContentAfter().ifEmpty { 
                    emailContext.getString(R.string.guest_email_content_after_default) 
                }
                val signature = settingsManager.getGuestEmailSignature().ifEmpty { 
                    emailContext.getString(R.string.email_signature_default) 
                }
                val includeLogo = settingsManager.isEmailIncludeLogoEnabled()
                val logoUriString = settingsManager.getEmailLogoUri()
                
                // Generate QR code for guest (with name only)
                val payload = mapOf(
                    "type" to "guest",
                    "version" to 1,
                    "name" to guest.name,
                    "abbr" to guest.lastNameAbbreviation
                )
                val json = Gson().toJson(payload)
                val qrBitmap = QRCodeUtils.generateQrImageBitmap(json, 512)
                
                // Save QR code file
                var qrFile: File? = null
                if (includeQr && qrBitmap != null) {
                    qrFile = File(emailContext.cacheDir, "qr_code_guest_${guest.id}.png")
                    val outputStream = FileOutputStream(qrFile)
                    qrBitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.close()
                }
                
                // Save logo file
                var logoFile: File? = null
                if (includeLogo && logoUriString.isNotBlank()) {
                    try {
                        val logoBitmap = BitmapFactory.decodeStream(
                            emailContext.contentResolver.openInputStream(Uri.parse(logoUriString))
                        )
                        if (logoBitmap != null) {
                            logoFile = File(emailContext.cacheDir, "logo_guest_${guest.id}.png")
                            val outputStream = FileOutputStream(logoFile)
                            logoBitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
                            outputStream.close()
                            logoBitmap.recycle()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // Build HTML email with Content-ID references
                val htmlEmail = buildGuestEmailHtml(
                    guestName = guest.name,
                    contentBefore = contentBefore,
                    contentAfter = contentAfter,
                    signature = signature,
                    includeQr = includeQr,
                    headerText = emailContext.getString(R.string.guest_email_html_header),
                    footerText = emailContext.getString(R.string.guest_email_html_footer),
                    qrAttachmentText = emailContext.getString(R.string.email_qr_attachment_text),
                    qrAttachmentNote = emailContext.getString(R.string.email_qr_attachment_note),
                    includeLogo = includeLogo,
                    useContentId = true
                )
                
                // Plain text fallback
                val plainTextEmail = buildString {
                    append(contentBefore)
                    append("\n\n")
                    if (includeQr) {
                        append("[ QR Code - See attachment ]\n\n")
                    }
                    append(contentAfter)
                    append("\n\n")
                    append(signature)
                }
                
                // Send via Gmail API
                val result = gmailSendService.sendEmail(
                    gmailService = gmailService,
                    to = targetEmail,
                    subject = subject,
                    htmlContent = htmlEmail,
                    plainText = plainTextEmail,
                    qrFile = qrFile,
                    logoFile = logoFile
                )
                
                result.fold(
                    onSuccess = {
                        Toast.makeText(
                            emailContext,
                            emailContext.getString(R.string.email_api_success),
                            Toast.LENGTH_SHORT
                        ).show()
                        // Close dialog on success
                        onSuccess()
                    },
                    onFailure = { e ->
                        android.util.Log.d("GmailAuth", "Email send failed: ${e.javaClass.simpleName} - ${e.message}")
                        // Check if authorization is required
                        if (e is com.eventmanager.app.data.sync.GmailAuthorizationRequiredException) {
                            // Launch authorization dialog
                            android.util.Log.d("GmailAuth", "Launching OAuth consent screen")
                            val launcher = authLauncherHolder.value
                            if (launcher != null) {
                                try {
                                    launcher.launch(e.authIntent)
                                    android.util.Log.d("GmailAuth", "OAuth consent screen launched successfully")
                                } catch (ex: Exception) {
                                    android.util.Log.e("GmailAuth", "Error launching OAuth consent screen", ex)
                                    Toast.makeText(
                                        emailContext,
                                        "Error launching authorization screen: ${ex.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            } else {
                                android.util.Log.e("GmailAuth", "Auth launcher is null! Cannot launch OAuth consent")
                                Toast.makeText(
                                    emailContext,
                                    "Error: Authorization launcher not ready. Please try again.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else if (e is com.eventmanager.app.data.sync.GmailNotConfiguredException) {
                            Toast.makeText(
                                emailContext,
                                e.message,
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                emailContext,
                                emailContext.getString(R.string.email_api_error_message, e.message ?: "Unknown error"),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(
                    emailContext,
                    emailContext.getString(R.string.email_api_error_message, e.message ?: "Unknown error"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        
        // Launcher for Gmail authorization
        val authLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            // After authorization, retry sending email
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                android.util.Log.d("GmailAuth", "OAuth authorization successful, retrying email send")
                isSendingEmail = true
                coroutineScope.launch {
                    sendGuestEmailViaApi(
                        emailContext,
                        settingsManager,
                        gmailAuthService,
                        gmailSendService,
                        guest,
                        targetEmail,
                        onSuccess = {
                            isSendingEmail = false
                            showEmailConfirmDialog = false
                        }
                    )
                    isSendingEmail = false
                }
            } else {
                android.util.Log.w("GmailAuth", "OAuth authorization cancelled or failed")
            }
        }
        
        // Initialize the holder immediately when launcher is created
        LaunchedEffect(authLauncher) {
            authLauncherHolder.value = authLauncher
            android.util.Log.d("GmailAuth", "Auth launcher initialized")
        }
        
        // Manual email send function
        fun sendGuestEmailManually(
            emailContext: android.content.Context,
            settingsManager: SettingsManager,
            guest: Guest,
            targetEmail: String
        ) {
            try {
                // Get email settings (use volunteer settings as fallback)
                val subject = settingsManager.getGuestEmailSubject().ifEmpty { 
                    emailContext.getString(R.string.guest_email_subject_default) 
                }
                val contentBefore = settingsManager.getGuestEmailContentBefore().ifEmpty { 
                    emailContext.getString(R.string.guest_email_content_before_default) 
                }
                val includeQr = settingsManager.isGuestEmailIncludeQrEnabled()
                val contentAfter = settingsManager.getGuestEmailContentAfter().ifEmpty { 
                    emailContext.getString(R.string.guest_email_content_after_default) 
                }
                val signature = settingsManager.getGuestEmailSignature().ifEmpty { 
                    emailContext.getString(R.string.email_signature_default) 
                }
                
                // Generate QR code for guest (with name only)
                val payload = mapOf(
                    "type" to "guest",
                    "version" to 1,
                    "name" to guest.name,
                    "abbr" to guest.lastNameAbbreviation
                )
                val json = Gson().toJson(payload)
                val qrBitmap = QRCodeUtils.generateQrImageBitmap(json, 512)
                
                // Build simple HTML email
                val htmlEmail = buildGuestEmailHtml(
                    guestName = guest.name,
                    contentBefore = contentBefore,
                    contentAfter = contentAfter,
                    signature = signature,
                    includeQr = false, // Don't include QR in HTML for manual send
                    headerText = emailContext.getString(R.string.guest_email_html_header),
                    footerText = emailContext.getString(R.string.guest_email_html_footer),
                    qrAttachmentText = emailContext.getString(R.string.email_qr_attachment_text),
                    qrAttachmentNote = emailContext.getString(R.string.email_qr_attachment_note),
                    includeLogo = false,
                    useContentId = false
                )
                
                // Plain text fallback
                val plainTextEmail = buildString {
                    append(contentBefore)
                    append("\n\n")
                    if (includeQr) {
                        append("[ QR Code - See attachment ]\n\n")
                    }
                    append(contentAfter)
                    append("\n\n")
                    append(signature)
                }
                
                // Save QR code file for attachment
                var qrUri: Uri? = null
                if (includeQr && qrBitmap != null) {
                    val qrFile = File(emailContext.cacheDir, "qr_code_guest_${guest.id}.png")
                    val outputStream = FileOutputStream(qrFile)
                    qrBitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.close()
                    
                    qrUri = FileProvider.getUriForFile(
                        emailContext,
                        "${emailContext.packageName}.fileprovider",
                        qrFile
                    )
                }
                
                // Create email intent with HTML and QR attachment
                val emailIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/html"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(targetEmail))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, plainTextEmail)
                    putExtra("android.intent.extra.HTML_TEXT", htmlEmail)
                    
                    if (qrUri != null) {
                        putExtra(Intent.EXTRA_STREAM, qrUri)
                    }
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                emailContext.startActivity(Intent.createChooser(emailIntent, emailContext.getString(R.string.send_by_mail)))
            } catch (e: Exception) {
                Toast.makeText(
                    emailContext,
                    emailContext.getString(R.string.email_error_message, e.message ?: "Unknown error"),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        
        AlertDialog(
            onDismissRequest = { showEmailConfirmDialog = false },
            icon = {
                Icon(
                    Icons.Default.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = getStringResource(R.string.email_confirm_send_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = getStringResource(R.string.email_confirm_send_message, targetEmail),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    HorizontalDivider()
                    
                    // Manual Send Option
                    OutlinedButton(
                        onClick = {
                            showEmailConfirmDialog = false
                            sendGuestEmailManually(emailContext, settingsManager, guest, targetEmail)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = getStringResource(R.string.email_send_manual),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = getStringResource(R.string.email_send_manual_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // API Send Option (only if authenticated)
                    if (isGmailAuthenticated) {
                        Button(
                            onClick = {
                                isSendingEmail = true
                                coroutineScope.launch {
                                    sendGuestEmailViaApi(
                                        emailContext,
                                        settingsManager,
                                        gmailAuthService,
                                        gmailSendService,
                                        guest,
                                        targetEmail,
                                        onSuccess = {
                                            isSendingEmail = false
                                            showEmailConfirmDialog = false
                                        }
                                    )
                                    isSendingEmail = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSendingEmail
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                if (isSendingEmail) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isSendingEmail) getStringResource(R.string.email_sending) else getStringResource(R.string.email_send_api),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = getStringResource(R.string.email_send_api_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                )
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = getStringResource(R.string.email_api_not_authenticated),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showEmailConfirmDialog = false }) {
                    Text(getStringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun GuestInformationSection(
    guest: Guest,
    venues: List<VenueEntity>,
    isPhone: Boolean
) {
    val responsivePadding = if (isPhone) getPhonePortraitCardPadding() else getResponsiveCardPadding()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isPhone) 12.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(responsivePadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(if (isPhone) 20.dp else 24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = LocalContext.current.getString(R.string.guest_information),
                    style = if (isPhone) getPhonePortraitTypography() else getResponsiveTitleTypography(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 12.dp))
            
            // Name
            InfoRow(
                label = LocalContext.current.getString(R.string.name),
                value = guest.name,
                isPhone = isPhone
            )
            
            // Last name abbreviation (if exists)
            if (guest.lastNameAbbreviation.isNotEmpty()) {
                InfoRow(
                    label = LocalContext.current.getString(R.string.abbreviation),
                    value = guest.lastNameAbbreviation,
                    isPhone = isPhone
                )
            }
            
            // Email (if exists)
            if (guest.email.isNotEmpty()) {
                InfoRow(
                    label = LocalContext.current.getString(R.string.guest_email),
                    value = guest.email,
                    isPhone = isPhone
                )
            }
            
            // Phone Number (if exists)
            if (guest.phoneNumber.isNotEmpty()) {
                InfoRow(
                    label = LocalContext.current.getString(R.string.guest_phone_number),
                    value = guest.phoneNumber,
                    isPhone = isPhone
                )
            }
            
            // Invitations
            InfoRow(
                label = LocalContext.current.getString(R.string.invitations),
                value = guest.invitations.toString(),
                isPhone = isPhone
            )
            
            // Venue
            InfoRow(
                label = LocalContext.current.getString(R.string.venue),
                value = getVenueDisplayString(guest.venueName, venues),
                isPhone = isPhone
            )
            
            // Notes (if exists)
            if (guest.notes.isNotEmpty()) {
                InfoRow(
                    label = LocalContext.current.getString(R.string.notes),
                    value = guest.notes,
                    isPhone = isPhone
                )
            }
        }
    }
}

@Composable
private fun ActionButtonsSection(
    guest: Guest,
    onEdit: (Guest) -> Unit,
    onDelete: (Guest) -> Unit,
    onShowQr: () -> Unit,
    isPhone: Boolean
) {
    val responsivePadding = if (isPhone) getPhonePortraitCardPadding() else getResponsiveCardPadding()
    val responsiveSpacing = if (isPhone) getPhonePortraitSpacing() else getResponsiveSpacing()
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isPhone) 12.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(responsivePadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(if (isPhone) 20.dp else 24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = context.getString(R.string.actions),
                    style = if (isPhone) getPhonePortraitTypography() else getResponsiveTitleTypography(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(responsiveSpacing))
            
            if (isPhone) {
                // Stack vertically on phones
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onShowQr,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(context.getString(R.string.qr_code))
                    }
                    
                    OutlinedButton(
                        onClick = { onEdit(guest) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(context.getString(R.string.edit_guest))
                    }
                    
                    OutlinedButton(
                        onClick = { onDelete(guest) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(context.getString(R.string.delete_guest))
                    }
                }
            } else {
                // Row layout on tablets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onShowQr,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(context.getString(R.string.qr_code))
                    }
                    
                    OutlinedButton(
                        onClick = { onEdit(guest) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(context.getString(R.string.edit_guest))
                    }
                    
                    OutlinedButton(
                        onClick = { onDelete(guest) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(context.getString(R.string.delete_guest))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    isPhone: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = value,
            style = if (isPhone) getPhonePortraitBodyTypography() else getResponsiveBodyTypography(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

/**
 * Builds a professional HTML email for guests with embedded QR code
 */
private fun buildGuestEmailHtml(
    guestName: String,
    contentBefore: String,
    contentAfter: String,
    signature: String,
    qrCodeBase64: String? = null,
    includeQr: Boolean,
    headerText: String,
    footerText: String,
    qrAttachmentText: String,
    qrAttachmentNote: String,
    logoBase64: String? = null,
    includeLogo: Boolean,
    useContentId: Boolean = false
): String {
    // Convert newlines to HTML breaks and escape HTML
    fun String.toHtmlParagraphs(): String {
        return this.split("\n\n")
            .filter { it.isNotBlank() }
            .joinToString("") { paragraph ->
                "<p style=\"margin: 0 0 16px 0; line-height: 1.6;\">${paragraph.replace("\n", "<br>")}</p>"
            }
    }
    
    // Convert signature to HTML with bold formatting
    fun String.toHtmlSignature(): String {
        return this.split("\n")
            .filter { it.isNotBlank() }
            .joinToString("<br>") { line ->
                "<strong style=\"color: #1f2937; font-weight: 600;\">${line}</strong>"
            }
    }
    
    val qrSection = if (includeQr) {
        """
        <tr>
            <td style="padding: 40px 40px; text-align: center; background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);">
                <table role="presentation" cellpadding="0" cellspacing="0" style="margin: 0 auto; width: 100%; max-width: 300px;">
                    <tr>
                        <td style="background-color: #ffffff; padding: 32px; border-radius: 16px; box-shadow: 0 4px 12px rgba(0,0,0,0.08); border: 2px solid #e2e8f0;">
                            <div style="background: linear-gradient(135deg, #10b981 0%, #059669 100%); padding: 24px; border-radius: 12px; margin-bottom: 20px;">
                                <table role="presentation" cellpadding="0" cellspacing="0" style="width: 200px; height: 200px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                                    <tr>
                                        <td style="text-align: center; vertical-align: middle; padding: 10px;">
                                            ${if (useContentId && includeQr) {
                                                """<img src="cid:qrcode" 
                                                     alt="QR Code" 
                                                     width="180" 
                                                     height="180" 
                                                     style="display: block; margin: 0 auto; border: none; max-width: 180px; max-height: 180px;">"""
                                            } else if (qrCodeBase64 != null) {
                                                """<img src="data:image/png;base64,$qrCodeBase64" 
                                                     alt="QR Code" 
                                                     width="180" 
                                                     height="180" 
                                                     style="display: block; margin: 0 auto; border: none; max-width: 180px; max-height: 180px;">"""
                                            } else {
                                                """<div style="color: #94a3b8; font-size: 14px; text-align: center; padding: 20px;">
                                                    <table role="presentation" cellpadding="0" cellspacing="0" style="width: 120px; height: 120px; margin: 0 auto 12px; border: 3px dashed #cbd5e1; border-radius: 8px; background-color: #f8fafc;">
                                                        <tr>
                                                            <td style="text-align: center; vertical-align: middle;">
                                                                <div style="font-size: 32px; color: #94a3b8; font-weight: 600;">QR</div>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                    <div style="color: #64748b; font-weight: 500;">${qrAttachmentText.replace("\n", "<br>")}</div>
                                                 </div>"""
                                            }}
                                        </td>
                                    </tr>
                                </table>
                            </div>
                            <p style="margin: 0; font-size: 14px; color: #475569; font-weight: 500; letter-spacing: 0.3px;">
                                $guestName
                            </p>
                            ${if (qrCodeBase64 == null) {
                                """<p style="margin: 12px 0 0 0; font-size: 12px; color: #64748b; font-style: italic;">
                                    $qrAttachmentNote
                                </p>"""
                            } else ""}
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        """
    } else ""
    
    return """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Your QR Code</title>
    </head>
    <body style="margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; background-color: #f4f4f5;">
        <table role="presentation" cellpadding="0" cellspacing="0" width="100%" style="min-width: 100%; background-color: #f4f4f5;">
            <tr>
                <td align="center" style="padding: 40px 20px;">
                    <!-- Main Container -->
                    <table role="presentation" cellpadding="0" cellspacing="0" width="600" style="max-width: 600px; width: 100%; background-color: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.07);">
                        
                        <!-- Header -->
                        <tr>
                            <td style="background: linear-gradient(135deg, #10b981 0%, #059669 50%, #047857 100%); padding: 48px 40px; text-align: center; position: relative; overflow: hidden;">
                                <div style="position: absolute; top: -50%; left: -50%; width: 200%; height: 200%; background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 70%); pointer-events: none;"></div>
                                <h1 style="margin: 0; color: #ffffff; font-size: 28px; font-weight: 700; letter-spacing: -0.8px; text-shadow: 0 2px 8px rgba(0,0,0,0.15); position: relative; z-index: 1;">
                                    $headerText
                                </h1>
                                <div style="margin-top: 12px; height: 3px; width: 60px; background-color: rgba(255,255,255,0.6); margin-left: auto; margin-right: auto; border-radius: 2px; position: relative; z-index: 1;"></div>
                            </td>
                        </tr>
                        
                        <!-- Content Before -->
                        <tr>
                            <td style="padding: 40px 40px 24px 40px; color: #1f2937; font-size: 16px; line-height: 1.7;">
                                ${contentBefore.toHtmlParagraphs()}
                            </td>
                        </tr>
                        
                        <!-- QR Code Section -->
                        $qrSection
                        
                        <!-- Content After -->
                        <tr>
                            <td style="padding: 24px 40px 40px 40px; color: #1f2937; font-size: 16px; line-height: 1.7;">
                                ${contentAfter.toHtmlParagraphs()}
                            </td>
                        </tr>
                        
                        <!-- Signature -->
                        <tr>
                            <td style="padding: 32px 40px; border-top: 2px solid #f1f5f9; background-color: #fafbfc;">
                                <table role="presentation" cellpadding="0" cellspacing="0" width="100%">
                                    <tr>
                                        <td style="vertical-align: ${if (includeLogo && logoBase64 != null) "top" else "middle"};">
                                            <div style="color: #1f2937; font-size: 15px; line-height: 1.8;">
                                                ${signature.toHtmlSignature()}
                                            </div>
                                        </td>
                                        ${if (includeLogo) {
                                            if (useContentId) {
                                                """<td style="text-align: right; padding-left: 24px; vertical-align: middle;">
                                                    <img src="cid:logo" 
                                                         alt="Logo" 
                                                         style="max-width: 120px; max-height: 80px; display: block; border: none; height: auto;">
                                                </td>"""
                                            } else if (logoBase64 != null) {
                                                """<td style="text-align: right; padding-left: 24px; vertical-align: middle;">
                                                    <img src="data:image/png;base64,$logoBase64" 
                                                         alt="Logo" 
                                                         style="max-width: 120px; max-height: 80px; display: block; border: none; height: auto;">
                                                </td>"""
                                            } else ""
                                        } else ""}
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        
                    </table>
                    
                    <!-- Footer -->
                    <table role="presentation" cellpadding="0" cellspacing="0" width="600" style="max-width: 600px; width: 100%;">
                        <tr>
                            <td style="padding: 32px 40px; text-align: center;">
                                <div style="width: 40px; height: 1px; background: linear-gradient(90deg, transparent, #e5e7eb, transparent); margin: 0 auto 20px;"></div>
                                <p style="margin: 0; font-size: 12px; color: #9ca3af; line-height: 1.6;">
                                    $footerText
                                </p>
                            </td>
                        </tr>
                    </table>
                    
                </td>
            </tr>
        </table>
    </body>
    </html>
    """.trimIndent()
}
