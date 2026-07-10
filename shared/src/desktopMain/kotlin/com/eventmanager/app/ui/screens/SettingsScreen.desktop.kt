package com.eventmanager.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventmanager.app.data.sync.FileAppLogger
import com.eventmanager.app.data.sync.ServiceAccountJsonParser
import com.eventmanager.app.data.sync.ServiceAccountKeyInfo
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.data.sync.settingsManagerFor
import com.eventmanager.app.ui.components.AppInformationPanel
import com.eventmanager.app.ui.components.AppUpdateFlowDialog
import com.eventmanager.app.ui.components.BackgroundAnimationSettingsSection
import com.eventmanager.app.ui.components.UpdateSourcesDialog
import com.eventmanager.app.ui.components.BackgroundAnimationSettingsTarget
import com.eventmanager.app.ui.components.ColorThemePicker
import com.eventmanager.app.ui.components.DesktopAdminNavLayoutPicker
import com.eventmanager.app.ui.components.ThemeModePicker
import com.eventmanager.app.ui.components.toBiometricAdminProfileLink
import com.eventmanager.app.platform.LocalPlatformContext
import com.eventmanager.app.platform.createAppStorage
import com.eventmanager.app.platform.createBiometricAuth
import com.eventmanager.app.platform.createGmailAuth
import com.eventmanager.app.platform.AppBuildInfo
import com.eventmanager.app.platform.hardware.DesktopBleReaderScanner
import com.eventmanager.app.platform.hardware.DesktopExternalNfcReader
import com.eventmanager.app.platform.openUrl
import com.eventmanager.app.ui.components.BleReaderPickerDialog
import com.eventmanager.app.ui.components.BiometricAdminVerificationDialog
import com.eventmanager.app.ui.components.ScannerMatch
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import com.eventmanager.app.ui.components.CleanupInactiveVolunteersDialog
import com.eventmanager.app.ui.components.SyncStatusDialog
import com.eventmanager.app.ui.platform.ServiceAccountKeyUploadButton
import com.eventmanager.app.ui.platform.GmailOAuthClientUploadButton
import com.eventmanager.app.ui.platform.EmailLogoUploadSection
import com.eventmanager.app.platform.PlatformFileManager
import com.eventmanager.app.ui.platform.AppAppearanceState
import com.eventmanager.app.ui.desktop.AdminNavLayout
import com.eventmanager.app.ui.platform.applyLocaleChange
import com.eventmanager.app.ui.platform.applyThemeAppearanceChange
import com.eventmanager.app.ui.platform.isAppIconChangeSupported
import com.eventmanager.app.ui.platform.showPlatformToast
import com.eventmanager.app.ui.theme.ThemeMode
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun SettingsScreen(
    viewModel: EventManagerViewModel,
    onNavigateToJobTypeManagement: () -> Unit,
    onNavigateToVenueManagement: () -> Unit,
    onNavigateToSalesSheetItemManagement: () -> Unit,
    variant: SettingsScreenVariant,
    modifier: Modifier,
    onDesktopAdminNavLayoutChanged: () -> Unit,
) {
    val platformContext = LocalPlatformContext.current
    val settingsManager = remember(platformContext) { SettingsManager(createAppStorage(platformContext)) }
    val scope = rememberCoroutineScope()
    val gmailAuth = remember(platformContext) { createGmailAuth(platformContext) }

    var spreadsheetId by remember { mutableStateOf(settingsManager.getSpreadsheetId()) }
    var guestListSheet by remember { mutableStateOf(settingsManager.getGuestListSheet()) }
    var volunteerSheet by remember { mutableStateOf(settingsManager.getVolunteerSheet()) }
    var jobsSheet by remember { mutableStateOf(settingsManager.getJobsSheet()) }
    var jobTypesSheet by remember { mutableStateOf(settingsManager.getJobTypesSheet()) }
    var venuesSheet by remember { mutableStateOf(settingsManager.getVenuesSheet()) }
    var salesItemsSheet by remember { mutableStateOf(settingsManager.getSalesItemsSheet()) }
    var transfersSheet by remember { mutableStateOf(settingsManager.getTransfersSheet()) }
    var tempGuestListSheet by remember { mutableStateOf(settingsManager.getTempGuestListSheet()) }
    var syncInterval by remember { mutableStateOf(settingsManager.getSyncInterval()) }
    var uploadStatus by remember { mutableStateOf<String?>(null) }
    var gmailLoading by remember { mutableStateOf(false) }
    var gmailSignedIn by remember { mutableStateOf(gmailAuth.isSignedIn) }
    var gmailEmail by remember { mutableStateOf(gmailAuth.accountEmail) }
    var gmailOAuthConfigured by remember(platformContext) {
        mutableStateOf(PlatformFileManager(platformContext).getGmailOAuthClientFile() != null)
    }
    var gmailOAuthUploadStatus by remember { mutableStateOf<String?>(null) }
    var gmailUseServiceAccount by remember { mutableStateOf(settingsManager.isGmailUseServiceAccount()) }
    var gmailServiceAccountSender by remember { mutableStateOf(settingsManager.getGmailServiceAccountSenderEmail()) }
    val platformFileManager = remember(platformContext) { PlatformFileManager(platformContext) }
    val serviceAccountConfigured = platformFileManager.getServiceAccountFile() != null

    var showSyncSettings by remember { mutableStateOf(settingsManager.isCategorySyncExpanded()) }
    val serviceAccountKeyInfo = remember(uploadStatus, showSyncSettings) {
        platformFileManager.readServiceAccountJson()?.let { ServiceAccountJsonParser.parse(it) }
    }
    var showEmailSettings by remember { mutableStateOf(settingsManager.isCategoryEmailExpanded()) }
    var showAppearanceSettings by remember { mutableStateOf(settingsManager.isCategoryAppearanceExpanded()) }
    var showMaintenanceSettings by remember { mutableStateOf(settingsManager.isCategoryMaintenanceExpanded()) }
    var showLocalizationSettings by remember { mutableStateOf(settingsManager.isCategoryLocalizationExpanded()) }
    var showAnnouncementsSettings by remember { mutableStateOf(settingsManager.isCategoryAnnouncementsExpanded()) }
    var showDeveloperSettings by remember { mutableStateOf(settingsManager.isCategoryDeveloperExpanded()) }
    var showUpdateResultDialog by remember { mutableStateOf(false) }
    var showUpdateSourcesDialog by remember { mutableStateOf(false) }
    var showExternalReaderSettings by remember { mutableStateOf(settingsManager.isCategoryExternalReaderExpanded()) }
    var showBleReaderPicker by remember { mutableStateOf(false) }
    var externalBleReaderMac by remember { mutableStateOf(settingsManager.getExternalBleReaderMac()) }
    var externalBleReaderName by remember { mutableStateOf(settingsManager.getExternalBleReaderName()) }
    var externalReaderTestRunning by remember { mutableStateOf(false) }
    var externalReaderTestDialogMessage by remember { mutableStateOf<String?>(null) }
    var externalReaderTestDialogSuccess by remember { mutableStateOf(false) }
    var showPcscReadersDialog by remember { mutableStateOf(false) }
    var pcscReadersListing by remember { mutableStateOf("") }
    var usbReaderConnected by remember { mutableStateOf(DesktopExternalNfcReader.isUsbConnected()) }
    var usbReaderName by remember { mutableStateOf(DesktopExternalNfcReader.readerDescription(settingsManager)) }
    var bleReaderLinked by remember { mutableStateOf(DesktopExternalNfcReader.isBleLinkActive(settingsManager)) }

    var showInstructions by remember { mutableStateOf(false) }
    var showGmailOAuthHelp by remember { mutableStateOf(false) }
    var showCleanupDialog by remember { mutableStateOf(false) }

    val syncStatusMessage by viewModel.syncStatusMessage.collectAsState()
    val showSyncStatusDialog by viewModel.showSyncStatusDialog.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val announcementsVenues by viewModel.venues.collectAsState()

    val saveLabel = stringResource(Res.string.save)
    val gmailAuthSuccessMsg = stringResource(Res.string.email_gmail_auth_success)
    val gmailNotSignedInMsg = stringResource(Res.string.email_gmail_not_signed_in)
    val gmailRevokedMsg = stringResource(Res.string.email_gmail_auth_revoked)
    val gmailOAuthMissingMsg = stringResource(Res.string.email_gmail_oauth_client_missing)
    val gmailOAuthInvalidMsg = stringResource(Res.string.invalid_gmail_oauth_client_json)
    val gmailServiceAccountMissingMsg = stringResource(Res.string.email_gmail_service_account_missing)
    val gmailSenderMissingMsg = stringResource(Res.string.email_gmail_service_account_sender_label)
    val gmailInvalidServiceAccountMsg = stringResource(Res.string.invalid_service_account_json)
    val readerClearedToast = stringResource(Res.string.external_reader_cleared_toast)

    fun resolveGmailSignInErrorMessage(errorCode: String?): String = when (errorCode) {
        "missing_oauth_client" -> gmailOAuthMissingMsg
        "invalid_oauth_client" -> gmailOAuthInvalidMsg
        "missing_service_account" -> gmailServiceAccountMissingMsg
        "missing_sender_email" -> gmailSenderMissingMsg
        "invalid_service_account" -> gmailInvalidServiceAccountMsg
        null, "" -> gmailNotSignedInMsg
        else -> errorCode
    }

    fun saveSheetsConfig() {
        settingsManager.saveSpreadsheetId(spreadsheetId.trim())
        settingsManager.saveGuestListSheet(guestListSheet.trim())
        settingsManager.saveVolunteerSheet(volunteerSheet.trim())
        settingsManager.saveJobsSheet(jobsSheet.trim())
        settingsManager.saveJobTypesSheet(jobTypesSheet.trim())
        settingsManager.saveVenuesSheet(venuesSheet.trim())
        settingsManager.saveSalesItemsSheet(salesItemsSheet.trim())
        settingsManager.saveTransfersSheet(transfersSheet.trim())
        settingsManager.saveTempGuestListSheet(tempGuestListSheet.trim())
        showPlatformToast(platformContext, saveLabel)
    }

    LaunchedEffect(showExternalReaderSettings) {
        if (!showExternalReaderSettings) return@LaunchedEffect
        while (isActive) {
            usbReaderConnected = DesktopExternalNfcReader.isUsbConnected()
            usbReaderName = DesktopExternalNfcReader.readerDescription(settingsManager)
            bleReaderLinked = DesktopExternalNfcReader.isBleLinkActive(settingsManager)
            delay(1500)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (variant == SettingsScreenVariant.BilleterieBasic) {
            BackgroundAnimationSettingsSection(
                settingsManager = settingsManager,
                isDesktop = true,
                target = BackgroundAnimationSettingsTarget.Billeterie,
            )
        }

        if (variant == SettingsScreenVariant.PosBasic) {
            BackgroundAnimationSettingsSection(
                settingsManager = settingsManager,
                isDesktop = true,
                target = BackgroundAnimationSettingsTarget.Pos,
            )
        }

        if (variant == SettingsScreenVariant.Full) {
            Text(
                text = stringResource(Res.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(Res.string.settings_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DesktopSettingsCategory(
            title = stringResource(Res.string.settings_category_sync),
            icon = Icons.Default.CloudSync,
            expanded = showSyncSettings,
            onToggle = {
                showSyncSettings = !showSyncSettings
                settingsManager.setCategorySyncExpanded(showSyncSettings)
            }
        ) {
            OutlinedButton(
                onClick = { showInstructions = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.Help, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.view_setup_instructions))
            }

            if (variant == SettingsScreenVariant.Full) {
                OutlinedTextField(
                    value = spreadsheetId,
                    onValueChange = { spreadsheetId = it },
                    label = { Text(stringResource(Res.string.spreadsheet_id_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                SheetNameField(stringResource(Res.string.guest_list_sheet_label), guestListSheet) { guestListSheet = it }
                SheetNameField(stringResource(Res.string.volunteer_sheet_label), volunteerSheet) { volunteerSheet = it }
                SheetNameField(stringResource(Res.string.shifts_sheet_label), jobsSheet) { jobsSheet = it }
                SheetNameField(stringResource(Res.string.shift_types_sheet_label), jobTypesSheet) { jobTypesSheet = it }
                SheetNameField(stringResource(Res.string.venues_sheet_label), venuesSheet) { venuesSheet = it }
                SheetNameField(stringResource(Res.string.sales_items_sheet_label), salesItemsSheet) { salesItemsSheet = it }
                SheetNameField(stringResource(Res.string.transfers_sheet_label), transfersSheet) { transfersSheet = it }
                SheetNameField(stringResource(Res.string.temp_guest_list_sheet_label), tempGuestListSheet) { tempGuestListSheet = it }

                ServiceAccountKeyUploadButton(
                    platformContext = platformContext,
                    onStatusUpdate = { uploadStatus = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                DesktopServiceAccountKeyInfoPanel(keyInfo = serviceAccountKeyInfo)
                uploadStatus?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { saveSheetsConfig() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.save_sheets_config))
                }
            }

            if (variant == SettingsScreenVariant.Full) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            DesktopSyncIntervalSettings(
                settingsManager = settingsManager,
                viewModel = viewModel,
                syncInterval = syncInterval,
                onSyncIntervalChange = { syncInterval = it }
            )

            if (variant == SettingsScreenVariant.Full) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.performFullSync() },
                        enabled = !isSyncing,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.manual_sync_now))
                    }
                    OutlinedButton(
                        onClick = { viewModel.testSyncStatus() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.test_sync_status))
                    }
                }
            } else if (variant == SettingsScreenVariant.BilleterieBasic || variant == SettingsScreenVariant.PosBasic) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.syncWithGoogleSheets() },
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.manual_sync_now))
                }
            }
        }

        if (variant == SettingsScreenVariant.Full || variant == SettingsScreenVariant.BilleterieBasic || variant == SettingsScreenVariant.PosBasic) {
            DesktopSettingsCategory(
                title = stringResource(Res.string.settings_category_email),
                icon = Icons.Default.Email,
                expanded = showEmailSettings,
                onToggle = {
                    showEmailSettings = !showEmailSettings
                    settingsManager.setCategoryEmailExpanded(showEmailSettings)
                }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.email_gmail_auth_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showGmailOAuthHelp = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Help,
                            contentDescription = stringResource(Res.string.email_gmail_oauth_help_title),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = stringResource(Res.string.email_gmail_auth_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                DesktopSettingsToggleRow(
                    title = stringResource(Res.string.email_gmail_use_service_account),
                    description = stringResource(Res.string.email_gmail_use_service_account_description),
                    checked = gmailUseServiceAccount,
                    onCheckedChange = { enabled ->
                        gmailUseServiceAccount = enabled
                        settingsManager.setGmailUseServiceAccount(enabled)
                        scope.launch {
                            gmailAuth.signOut()
                            settingsManager.clearGmailAuth()
                            gmailSignedIn = false
                            gmailEmail = null
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
                if (gmailUseServiceAccount) {
                    Text(
                        text = if (serviceAccountConfigured) {
                            stringResource(Res.string.email_gmail_service_account_configured)
                        } else {
                            stringResource(Res.string.email_gmail_service_account_missing)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (serviceAccountConfigured) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = gmailServiceAccountSender,
                        onValueChange = {
                            gmailServiceAccountSender = it
                            settingsManager.saveGmailServiceAccountSenderEmail(it)
                        },
                        label = { Text(stringResource(Res.string.email_gmail_service_account_sender_label)) },
                        supportingText = {
                            Text(stringResource(Res.string.email_gmail_service_account_sender_description))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !gmailSignedIn
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.email_gmail_oauth_client_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    GmailOAuthClientUploadButton(
                        platformContext = platformContext,
                        onStatusUpdate = { gmailOAuthUploadStatus = it },
                        onConfiguredChanged = { gmailOAuthConfigured = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = if (gmailOAuthConfigured) {
                            stringResource(Res.string.email_gmail_oauth_client_configured)
                        } else {
                            stringResource(Res.string.email_gmail_oauth_client_missing)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (gmailOAuthConfigured) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    gmailOAuthUploadStatus?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (gmailSignedIn && gmailEmail != null) {
                    Text(
                        text = stringResource(Res.string.email_gmail_signed_in_as, gmailEmail!!),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                gmailLoading = true
                                gmailAuth.signOut()
                                settingsManager.clearGmailAuth()
                                gmailSignedIn = false
                                gmailEmail = null
                                gmailLoading = false
                                showPlatformToast(platformContext, gmailRevokedMsg)
                            }
                        },
                        enabled = !gmailLoading
                    ) {
                        Text(stringResource(Res.string.email_gmail_sign_out))
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                gmailLoading = true
                                if (gmailUseServiceAccount) {
                                    settingsManager.saveGmailServiceAccountSenderEmail(gmailServiceAccountSender.trim())
                                }
                                val ok = gmailAuth.signIn()
                                gmailSignedIn = ok
                                gmailEmail = gmailAuth.accountEmail
                                gmailLoading = false
                                if (ok) {
                                    gmailEmail?.let { settingsManager.saveGmailAccount(it) }
                                    showPlatformToast(platformContext, gmailAuthSuccessMsg)
                                } else {
                                    val errorMsg = resolveGmailSignInErrorMessage(gmailAuth.lastSignInError)
                                    showPlatformToast(platformContext, errorMsg)
                                }
                            }
                        },
                        enabled = !gmailLoading && (
                            if (gmailUseServiceAccount) {
                                serviceAccountConfigured && gmailServiceAccountSender.isNotBlank()
                            } else {
                                gmailOAuthConfigured
                            }
                            )
                    ) {
                        if (gmailLoading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(Res.string.email_gmail_sign_in))
                        }
                    }
                }

                HorizontalDivider()

                DesktopEmailTemplateSettings(settingsManager)
            }
        }

        if (variant == SettingsScreenVariant.Full) {
            DesktopSettingsCategory(
                title = stringResource(Res.string.settings_category_maintenance),
                icon = Icons.Default.Build,
                expanded = showMaintenanceSettings,
                onToggle = {
                    showMaintenanceSettings = !showMaintenanceSettings
                    settingsManager.setCategoryMaintenanceExpanded(showMaintenanceSettings)
                }
            ) {
                OutlinedButton(onClick = onNavigateToJobTypeManagement, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.manage_shift_types))
                }
                OutlinedButton(onClick = onNavigateToVenueManagement, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.manage_venues))
                }
                OutlinedButton(onClick = onNavigateToSalesSheetItemManagement, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.manage_sales_items))
                }
                OutlinedButton(
                    onClick = { showCleanupDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.cleanup_inactive_volunteers))
                }
            }
        }

        DesktopSettingsCategory(
            title = stringResource(Res.string.settings_category_external_reader),
            icon = Icons.Default.Bluetooth,
            expanded = showExternalReaderSettings,
            onToggle = {
                showExternalReaderSettings = !showExternalReaderSettings
                settingsManager.setCategoryExternalReaderExpanded(showExternalReaderSettings)
            }
        ) {
            Text(
                text = stringResource(Res.string.desktop_external_reader_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (DesktopBleReaderScanner.isBlePcscSupportedOnPlatform()) {
                    stringResource(Res.string.desktop_ble_supported_windows)
                } else {
                    val os = System.getProperty("os.name").orEmpty().lowercase()
                    if (os.contains("mac") || os.contains("darwin")) {
                        stringResource(Res.string.desktop_ble_unsupported_macos)
                    } else {
                        stringResource(Res.string.desktop_ble_unsupported_linux)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    pcscReadersListing = DesktopExternalNfcReader.listPcscReadersReport()
                    showPcscReadersDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.List, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.desktop_list_pcsc_readers))
            }
            Spacer(Modifier.height(12.dp))

            if (usbReaderConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Usb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                stringResource(Res.string.settings_category_pcsc_reader),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(usbReaderName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (externalReaderTestRunning) return@OutlinedButton
                        externalReaderTestRunning = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                DesktopExternalNfcReader.runDiagnostic()
                            }
                            externalReaderTestDialogSuccess = result.success
                            externalReaderTestDialogMessage = result.details
                            externalReaderTestRunning = false
                        }
                    },
                    enabled = !externalReaderTestRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (externalReaderTestRunning) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.external_reader_testing))
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.desktop_usb_reader_test_button))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (externalBleReaderMac.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = if (bleReaderLinked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(Res.string.external_reader_connected_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (bleReaderLinked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = externalBleReaderName.ifBlank { stringResource(Res.string.ble_reader_unnamed) },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = externalBleReaderMac,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                        if (!bleReaderLinked) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(Res.string.scanner_ble_reader_footer_disconnected),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showBleReaderPicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.BluetoothSearching, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.external_reader_change))
                    }
                    OutlinedButton(
                        onClick = {
                            DesktopExternalNfcReader.shutdownBle()
                            settingsManager.clearExternalBleReader()
                            externalBleReaderMac = ""
                            externalBleReaderName = ""
                            showPlatformToast(platformContext, readerClearedToast)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.external_reader_forget))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (externalReaderTestRunning) return@OutlinedButton
                        externalReaderTestRunning = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                DesktopExternalNfcReader.runBleDiagnostic(settingsManager)
                            }
                            externalReaderTestDialogSuccess = result.success
                            externalReaderTestDialogMessage = result.details
                            externalReaderTestRunning = false
                        }
                    },
                    enabled = !externalReaderTestRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (externalReaderTestRunning) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.external_reader_testing))
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.external_reader_test_button))
                    }
                }
            } else {
                Button(
                    onClick = { showBleReaderPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.BluetoothSearching, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.external_reader_pick_button))
                }
            }
        }

        if (showBleReaderPicker) {
            BleReaderPickerDialog(
                platformContext = platformContext,
                onDismiss = { showBleReaderPicker = false },
                onPicked = { mac, name ->
                    settingsManager.saveExternalBleReaderMac(mac)
                    settingsManager.saveExternalBleReaderName(name)
                    externalBleReaderMac = mac
                    externalBleReaderName = name
                    showBleReaderPicker = false
                    scope.launch {
                        showPlatformToast(
                            platformContext,
                            getString(Res.string.external_reader_saved_toast, name)
                        )
                    }
                }
            )
        }

        DesktopSettingsCategory(
            title = stringResource(Res.string.settings_category_appearance),
            icon = Icons.Default.Palette,
            expanded = showAppearanceSettings,
            onToggle = {
                showAppearanceSettings = !showAppearanceSettings
                settingsManager.setCategoryAppearanceExpanded(showAppearanceSettings)
            }
        ) {
            var selectedThemeMode by remember { mutableStateOf(ThemeMode.fromString(settingsManager.getThemeMode())) }

            ThemeModePicker(
                selectedMode = selectedThemeMode,
                onSelect = { mode ->
                    selectedThemeMode = mode
                    settingsManager.saveThemeMode(mode.value)
                    applyThemeAppearanceChange(platformContext)
                },
            )

            Spacer(Modifier.height(16.dp))

            var selectedColorTheme by remember { mutableStateOf(settingsManager.getColorTheme()) }
            LaunchedEffect(Unit) {
                if (selectedColorTheme == "custom") {
                    selectedColorTheme = "system"
                    settingsManager.saveColorTheme("system")
                    applyThemeAppearanceChange(platformContext)
                }
            }
            ColorThemePicker(
                selectedThemeKey = selectedColorTheme,
                previewDark = when (selectedThemeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.DEFAULT -> isSystemInDarkTheme()
                },
                onSelect = { key ->
                    selectedColorTheme = key
                    settingsManager.saveColorTheme(key)
                    applyThemeAppearanceChange(platformContext)
                },
            )

            Spacer(Modifier.height(16.dp))

            if (variant == SettingsScreenVariant.Full) {
            var adminNavLayout by remember {
                mutableStateOf(settingsManager.getDesktopAdminNavLayout())
            }
            DesktopAdminNavLayoutPicker(
                selectedLayout = adminNavLayout,
                onSelect = { layout ->
                    adminNavLayout = layout
                    settingsManager.setDesktopAdminNavLayout(layout)
                    onDesktopAdminNavLayoutChanged()
                },
            )

            Spacer(Modifier.height(8.dp))

            BackgroundAnimationSettingsSection(
                settingsManager = settingsManager,
                isDesktop = true,
                target = BackgroundAnimationSettingsTarget.Admin,
            )
            }

            var peopleCounterVisible by remember { mutableStateOf(settingsManager.isPeopleCounterVisible()) }
            DesktopSettingsToggleRow(
                title = stringResource(Res.string.people_counter_visibility_title),
                description = stringResource(Res.string.people_counter_visibility_description),
                checked = peopleCounterVisible,
                onCheckedChange = {
                    peopleCounterVisible = it
                    settingsManager.setPeopleCounterVisible(it)
                    if (it) scope.launch { viewModel.refreshVenuesForPeopleCounterQuietly() }
                }
            )

            if (variant == SettingsScreenVariant.Full) {
                var statisticsVisible by remember { mutableStateOf(settingsManager.isStatisticsVisible()) }
                DesktopSettingsToggleRow(
                    title = stringResource(Res.string.statistics_visibility_title),
                    description = stringResource(Res.string.statistics_visibility_description),
                    checked = statisticsVisible,
                    onCheckedChange = {
                        statisticsVisible = it
                        settingsManager.setStatisticsVisible(it)
                    }
                )
            }
        }

        DesktopSettingsCategory(
            title = stringResource(Res.string.settings_category_announcements),
            icon = Icons.Default.Campaign,
            expanded = showAnnouncementsSettings,
            onToggle = {
                showAnnouncementsSettings = !showAnnouncementsSettings
                settingsManager.setCategoryAnnouncementsExpanded(showAnnouncementsSettings)
            }
        ) {
            var receptionEnabled by remember { mutableStateOf(settingsManager.isAnnouncementsReceptionEnabled()) }
            DesktopSettingsToggleRow(
                title = stringResource(Res.string.announcements_reception_title),
                description = stringResource(Res.string.announcements_reception_description),
                checked = receptionEnabled,
                onCheckedChange = {
                    receptionEnabled = it
                    settingsManager.setAnnouncementsReceptionEnabled(it)
                }
            )

            Text(
                text = stringResource(Res.string.announcements_tracked_venues_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(Res.string.announcements_tracked_venues_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val activeVenues = remember(announcementsVenues) { announcementsVenues.filter { it.isActive } }
            var trackedVenueIds by remember { mutableStateOf(settingsManager.getAnnouncementsTrackedVenueIds()) }
            val allTracked = trackedVenueIds.isEmpty()

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = allTracked,
                    onCheckedChange = {
                        trackedVenueIds = emptySet()
                        settingsManager.setAnnouncementsTrackedVenueIds(emptySet())
                    }
                )
                Text(stringResource(Res.string.announcements_tracked_venues_all))
            }
            activeVenues.forEach { venue ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val venueIdStr = venue.id.toString()
                    val isChecked = allTracked || trackedVenueIds.contains(venueIdStr)
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            val newSet = if (allTracked) {
                                if (checked) emptySet()
                                else activeVenues.map { it.id.toString() }.toSet() - venueIdStr
                            } else {
                                if (checked) trackedVenueIds + venueIdStr
                                else trackedVenueIds - venueIdStr
                            }
                            val finalSet = if (newSet.size == activeVenues.size) emptySet() else newSet
                            trackedVenueIds = finalSet
                            settingsManager.setAnnouncementsTrackedVenueIds(finalSet)
                        }
                    )
                    Text(venue.name)
                }
            }
        }

        DesktopSettingsCategory(
            title = stringResource(Res.string.settings_category_localization),
            icon = Icons.Default.Language,
            expanded = showLocalizationSettings,
            onToggle = {
                showLocalizationSettings = !showLocalizationSettings
                settingsManager.setCategoryLocalizationExpanded(showLocalizationSettings)
            }
        ) {
            val languageCode by AppAppearanceState::localeCode
            val currentLanguage = when (variant) {
                SettingsScreenVariant.PosBasic -> settingsManager.getPosLanguage()
                else -> languageCode ?: settingsManager.getLanguage()
            }
            DesktopLanguagePicker(
                selectedLanguage = currentLanguage,
                onLanguageSelected = { code ->
                    when (variant) {
                        SettingsScreenVariant.PosBasic -> {
                            settingsManager.savePosLanguage(code)
                            AppAppearanceState.notifyLocaleChanged(code)
                        }
                        else -> {
                            settingsManager.saveLanguage(code)
                            applyLocaleChange(platformContext)
                        }
                    }
                },
            )

            Spacer(Modifier.height(12.dp))
            DesktopDateTimeFormatSettings(settingsManager)
            Spacer(Modifier.height(12.dp))
            DesktopDateChangeOffset(settingsManager)
            Spacer(Modifier.height(12.dp))
            DesktopCurrencyPicker(
                selectedCurrency = settingsManager.getCurrencyCode(),
                onCurrencySelected = { settingsManager.saveCurrencyCode(it) }
            )
        }

        if (variant == SettingsScreenVariant.Full) {
            DesktopSettingsCategory(
                title = stringResource(Res.string.settings_category_developer),
                icon = Icons.Default.BugReport,
                expanded = showDeveloperSettings,
                onToggle = {
                    showDeveloperSettings = !showDeveloperSettings
                    settingsManager.setCategoryDeveloperExpanded(showDeveloperSettings)
                }
            ) {
                DesktopDeveloperSettings(
                    settingsManager = settingsManager,
                    viewModel = viewModel,
                    platformContext = platformContext,
                    onCheckForUpdates = {
                        viewModel.checkForAppUpdates()
                        showUpdateResultDialog = true
                    },
                    onConfigureUpdateSources = { showUpdateSourcesDialog = true },
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        AppInformationPanel(
            versionName = AppBuildInfo.VERSION_NAME,
            lastSyncTimeMs = settingsManager.getLastSyncTime(),
        )

        if (isAppIconChangeSupported()) {
            Text(stringResource(Res.string.desktop_app_icon_unavailable), style = MaterialTheme.typography.bodySmall)
        }
    }

    if (showInstructions) {
        DesktopGoogleSheetsInstructionsDialog(onDismiss = { showInstructions = false })
    }

    if (showGmailOAuthHelp) {
        DesktopGmailOAuthInstructionsDialog(onDismiss = { showGmailOAuthHelp = false })
    }

    if (showCleanupDialog) {
        val volunteers by viewModel.volunteers.collectAsState()
        CleanupInactiveVolunteersDialog(
            volunteers = volunteers,
            onConfirm = { yearsInactive ->
                viewModel.cleanupInactiveVolunteers(yearsInactive)
                showCleanupDialog = false
            },
            onDismiss = { showCleanupDialog = false }
        )
    }

    externalReaderTestDialogMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { externalReaderTestDialogMessage = null },
            icon = {
                Icon(
                    imageVector = if (externalReaderTestDialogSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (externalReaderTestDialogSuccess) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            },
            title = {
                Text(
                    text = stringResource(
                        if (externalReaderTestDialogSuccess) {
                            Res.string.external_reader_test_success_title
                        } else {
                            Res.string.external_reader_test_failure_title
                        }
                    ),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            },
            confirmButton = {
                Button(onClick = { externalReaderTestDialogMessage = null }) {
                    Text(stringResource(Res.string.external_reader_test_close))
                }
            }
        )
    }

    if (showPcscReadersDialog) {
        AlertDialog(
            onDismissRequest = { showPcscReadersDialog = false },
            title = { Text(stringResource(Res.string.desktop_list_pcsc_readers)) },
            text = {
                Text(
                    text = pcscReadersListing,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            },
            confirmButton = {
                Button(onClick = { showPcscReadersDialog = false }) {
                    Text(stringResource(Res.string.external_reader_test_close))
                }
            }
        )
    }

    SyncStatusDialog(
        isVisible = showSyncStatusDialog,
        onDismiss = { viewModel.dismissSyncStatusDialog() },
        statusMessage = syncStatusMessage
    )

    val updateCheckResult by viewModel.updateCheckState.collectAsState()
    val updateDownloadState by viewModel.updateDownloadState.collectAsState()
    AppUpdateFlowDialog(
        visible = showUpdateResultDialog,
        updateResult = updateCheckResult,
        downloadState = updateDownloadState,
        fallbackStoreUrl = settingsManager.getUpdateStoreUrl(),
        onDismiss = { showUpdateResultDialog = false },
        onDownload = { url -> viewModel.downloadUpdate(url) },
        onInstall = { path ->
            viewModel.installUpdate(path)
            showUpdateResultDialog = false
        },
    )

    if (showUpdateSourcesDialog) {
        UpdateSourcesDialog(
            settingsManager = settingsManager,
            onDismiss = { showUpdateSourcesDialog = false },
        )
    }
}

@Composable
private fun DesktopLanguagePicker(selectedLanguage: String, onLanguageSelected: (String) -> Unit) {
    var currentLanguage by remember(selectedLanguage) { mutableStateOf(selectedLanguage) }
    var showMenu by remember { mutableStateOf(false) }

    val options = listOf(
        "en" to Res.string.language_english,
        "fr" to Res.string.language_french,
        "es" to Res.string.language_spanish,
        "zh-TW" to Res.string.language_chinese,
        "zh-CN" to Res.string.language_chinese_simplified,
        "la" to Res.string.language_latin,
        "hi" to Res.string.language_hindi,
    )

    Text(stringResource(Res.string.language_title), style = MaterialTheme.typography.titleMedium)
    Text(
        text = stringResource(Res.string.language_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))

    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { showMenu = true }, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = options.firstOrNull { it.first == currentLanguage }?.let { stringResource(it.second) }
                        ?: stringResource(Res.string.language_english),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            options.forEach { (code, labelRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    onClick = {
                        currentLanguage = code
                        onLanguageSelected(code)
                        showMenu = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DesktopCurrencyPicker(selectedCurrency: String, onCurrencySelected: (String) -> Unit) {
    var currentCurrency by remember(selectedCurrency) { mutableStateOf(selectedCurrency) }
    var showMenu by remember { mutableStateOf(false) }
    val options = listOf("CHF", "EUR", "USD", "GBP")

    Text(stringResource(Res.string.currency_setting_label), style = MaterialTheme.typography.titleMedium)
    Text(
        text = stringResource(Res.string.currency_setting_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))

    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { showMenu = true }, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = currentCurrency,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            options.forEach { code ->
                DropdownMenuItem(
                    text = { Text(code) },
                    onClick = {
                        currentCurrency = code
                        onCurrencySelected(code)
                        showMenu = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DesktopDateTimeFormatSettings(settingsManager: SettingsManager) {
    var selectedDateFormat by remember { mutableStateOf(settingsManager.getDateFormat()) }
    var showDateFormatMenu by remember { mutableStateOf(false) }
    var selectedTimeFormat by remember { mutableStateOf(settingsManager.getTimeFormat()) }
    var showTimeFormatMenu by remember { mutableStateOf(false) }

    Text(stringResource(Res.string.date_time_format_title), style = MaterialTheme.typography.titleMedium)
    Text(
        text = stringResource(Res.string.date_time_format_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))

    val dateOptions = listOf(
        "dd/MM/yyyy" to Res.string.date_format_dd_mm_yyyy,
        "MM/dd/yyyy" to Res.string.date_format_mm_dd_yyyy,
        "yyyy-MM-dd" to Res.string.date_format_yyyy_mm_dd
    )
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { showDateFormatMenu = true }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dateOptions.firstOrNull { it.first == selectedDateFormat }?.let { stringResource(it.second) }
                        ?: stringResource(Res.string.date_format_dd_mm_yyyy),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = showDateFormatMenu, onDismissRequest = { showDateFormatMenu = false }) {
            dateOptions.forEach { (format, labelRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    onClick = {
                        selectedDateFormat = format
                        settingsManager.saveDateFormat(format)
                        showDateFormatMenu = false
                    }
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    val timeOptions = listOf(
        "HH:mm" to Res.string.time_format_hh_mm,
        "HH:mm:ss" to Res.string.time_format_hh_mm_ss,
        "HH:mm:ss.SSS" to Res.string.time_format_hh_mm_ss_sss
    )
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { showTimeFormatMenu = true }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeOptions.firstOrNull { it.first == selectedTimeFormat }?.let { stringResource(it.second) }
                        ?: stringResource(Res.string.time_format_hh_mm),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        DropdownMenu(expanded = showTimeFormatMenu, onDismissRequest = { showTimeFormatMenu = false }) {
            timeOptions.forEach { (format, labelRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes)) },
                    onClick = {
                        selectedTimeFormat = format
                        settingsManager.saveTimeFormat(format)
                        showTimeFormatMenu = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DesktopSyncIntervalSettings(
    settingsManager: SettingsManager,
    viewModel: EventManagerViewModel,
    syncInterval: Int,
    onSyncIntervalChange: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    fun applyInterval(newValue: Int) {
        val clamped = newValue.coerceIn(1, 60)
        onSyncIntervalChange(clamped)
        settingsManager.saveSyncInterval(clamped)
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(500)
            viewModel.updateSyncInterval()
        }
    }

    Text(
        text = stringResource(Res.string.sync_config_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        text = stringResource(Res.string.sync_interval_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(Res.string.sync_interval_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = { applyInterval(syncInterval - 1) },
                    enabled = syncInterval > 1,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(Res.string.date_change_offset_decrease))
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = syncInterval.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(Res.string.minutes_short),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalIconButton(
                    onClick = { applyInterval(syncInterval + 1) },
                    enabled = syncInterval < 60,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.date_change_offset_increase))
                }
            }

            Slider(
                value = syncInterval.toFloat(),
                onValueChange = { applyInterval(it.toInt()) },
                valueRange = 1f..60f,
                steps = 58,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DesktopDateChangeOffset(settingsManager: SettingsManager) {
    var dateChangeOffsetHours by remember { mutableStateOf(settingsManager.getDateChangeOffsetHours()) }

    Text(stringResource(Res.string.date_change_offset_title), style = MaterialTheme.typography.titleMedium)
    Text(
        text = stringResource(Res.string.date_change_offset_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))

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
            Icon(Icons.Default.Remove, contentDescription = stringResource(Res.string.date_change_offset_decrease))
        }

        val offsetLabel = when {
            dateChangeOffsetHours == 0 -> stringResource(Res.string.date_change_offset_zero)
            dateChangeOffsetHours > 0 -> {
                val switchTime = dateChangeOffsetHours.toString().padStart(2, '0') + ":00"
                stringResource(Res.string.date_change_offset_hours, dateChangeOffsetHours) +
                    " (${stringResource(Res.string.date_change_offset_time, switchTime)})"
            }
            else -> {
                val switchTime = (24 + dateChangeOffsetHours).toString().padStart(2, '0') + ":00"
                stringResource(Res.string.date_change_offset_hours, dateChangeOffsetHours) +
                    " (${stringResource(Res.string.date_change_offset_previous_day, switchTime)})"
            }
        }
        Text(
            text = offsetLabel,
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
            Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.date_change_offset_increase))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesktopEmailTemplateSettings(settingsManager: SettingsManager) {
    val platformContext = LocalPlatformContext.current
    val subjectDefault = stringResource(Res.string.email_subject_default)
    val contentBeforeDefault = stringResource(Res.string.email_content_before_default)
    val contentAfterDefault = stringResource(Res.string.email_content_after_default)
    val signatureDefault = stringResource(Res.string.email_signature_default)
    val guestSubjectDefault = stringResource(Res.string.guest_email_subject_default)
    val guestContentBeforeDefault = stringResource(Res.string.guest_email_content_before_default)
    val guestContentAfterDefault = stringResource(Res.string.guest_email_content_after_default)
    val associationNameDefault = stringResource(Res.string.email_association_name_hint)

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabLabels = listOf(
        stringResource(Res.string.email_tab_volunteer),
        stringResource(Res.string.email_tab_guest)
    )

    var volunteerSubject by remember { mutableStateOf(subjectDefault) }
    var volunteerContentBefore by remember { mutableStateOf(contentBeforeDefault) }
    var volunteerContentAfter by remember { mutableStateOf(contentAfterDefault) }
    var volunteerIncludeQr by remember { mutableStateOf(true) }
    var guestSubject by remember { mutableStateOf(guestSubjectDefault) }
    var guestContentBefore by remember { mutableStateOf(guestContentBeforeDefault) }
    var guestContentAfter by remember { mutableStateOf(guestContentAfterDefault) }
    var guestIncludeQr by remember { mutableStateOf(true) }
    var emailSignature by remember { mutableStateOf(signatureDefault) }
    var emailAssociationName by remember { mutableStateOf(associationNameDefault) }
    var emailLogoUri by remember { mutableStateOf("") }
    var includeWalletPass by remember { mutableStateOf(true) }
    var includeLogo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        volunteerSubject = settingsManager.getEmailSubject().ifEmpty { subjectDefault }
        volunteerContentBefore = settingsManager.getEmailContentBefore().ifEmpty { contentBeforeDefault }
        volunteerContentAfter = settingsManager.getEmailContentAfter().ifEmpty { contentAfterDefault }
        volunteerIncludeQr = settingsManager.isEmailIncludeQrEnabled()
        guestSubject = settingsManager.getGuestEmailSubject().ifEmpty { guestSubjectDefault }
        guestContentBefore = settingsManager.getGuestEmailContentBefore().ifEmpty { guestContentBeforeDefault }
        guestContentAfter = settingsManager.getGuestEmailContentAfter().ifEmpty { guestContentAfterDefault }
        guestIncludeQr = settingsManager.isGuestEmailIncludeQrEnabled()
        emailSignature = settingsManager.getEmailSignature().ifEmpty { signatureDefault }
        emailAssociationName = settingsManager.getEmailAssociationName().ifEmpty { associationNameDefault }
        includeWalletPass = settingsManager.isEmailIncludeDigitalWalletPassEnabled()
        includeLogo = settingsManager.isEmailIncludeLogoEnabled()
        emailLogoUri = settingsManager.getEmailLogoUri()
    }

    Text(stringResource(Res.string.email_qr_settings_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(
        text = stringResource(Res.string.email_qr_settings_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        tabLabels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = selectedTab == index,
                onClick = { selectedTab = index },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabLabels.size)
            ) {
                Text(label)
            }
        }
    }

    if (selectedTab == 0) {
        OutlinedTextField(
            value = volunteerSubject,
            onValueChange = { volunteerSubject = it; settingsManager.saveEmailSubject(it) },
            label = { Text(stringResource(Res.string.email_subject_label)) },
            placeholder = { Text(stringResource(Res.string.email_subject_hint)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = volunteerContentBefore,
            onValueChange = { volunteerContentBefore = it; settingsManager.saveEmailContentBefore(it) },
            label = { Text(stringResource(Res.string.email_content_before_label)) },
            placeholder = { Text(stringResource(Res.string.email_content_before_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        DesktopSettingsToggleRow(
            title = stringResource(Res.string.email_include_qr_label),
            description = stringResource(Res.string.email_include_qr_description),
            checked = volunteerIncludeQr,
            onCheckedChange = {
                volunteerIncludeQr = it
                settingsManager.setEmailIncludeQrEnabled(it)
            }
        )
        OutlinedTextField(
            value = volunteerContentAfter,
            onValueChange = { volunteerContentAfter = it; settingsManager.saveEmailContentAfter(it) },
            label = { Text(stringResource(Res.string.email_content_after_label)) },
            placeholder = { Text(stringResource(Res.string.email_content_after_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
    } else {
        OutlinedTextField(
            value = guestSubject,
            onValueChange = { guestSubject = it; settingsManager.saveGuestEmailSubject(it) },
            label = { Text(stringResource(Res.string.email_subject_label)) },
            placeholder = { Text(stringResource(Res.string.email_subject_hint)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = guestContentBefore,
            onValueChange = { guestContentBefore = it; settingsManager.saveGuestEmailContentBefore(it) },
            label = { Text(stringResource(Res.string.email_content_before_label)) },
            placeholder = { Text(stringResource(Res.string.email_content_before_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        DesktopSettingsToggleRow(
            title = stringResource(Res.string.email_include_qr_label),
            description = stringResource(Res.string.email_include_qr_description),
            checked = guestIncludeQr,
            onCheckedChange = {
                guestIncludeQr = it
                settingsManager.setGuestEmailIncludeQrEnabled(it)
            }
        )
        OutlinedTextField(
            value = guestContentAfter,
            onValueChange = { guestContentAfter = it; settingsManager.saveGuestEmailContentAfter(it) },
            label = { Text(stringResource(Res.string.email_content_after_label)) },
            placeholder = { Text(stringResource(Res.string.email_content_after_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
    }

    HorizontalDivider()

    Text(
        text = stringResource(Res.string.email_shared_settings_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        text = stringResource(Res.string.email_shared_settings_description),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    OutlinedTextField(
        value = emailSignature,
        onValueChange = { emailSignature = it; settingsManager.saveEmailSignature(it) },
        label = { Text(stringResource(Res.string.email_signature_label)) },
        placeholder = { Text(stringResource(Res.string.email_signature_hint)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2
    )
    OutlinedTextField(
        value = emailAssociationName,
        onValueChange = { emailAssociationName = it; settingsManager.saveEmailAssociationName(it) },
        label = { Text(stringResource(Res.string.email_association_name_label)) },
        placeholder = { Text(stringResource(Res.string.email_association_name_hint)) },
        supportingText = { Text(stringResource(Res.string.email_association_name_description)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    DesktopSettingsToggleRow(
        title = stringResource(Res.string.email_include_digital_wallet_pass_label),
        description = stringResource(Res.string.email_include_digital_wallet_pass_description),
        checked = includeWalletPass,
        onCheckedChange = {
            includeWalletPass = it
            settingsManager.setEmailIncludeDigitalWalletPassEnabled(it)
        }
    )
    if (includeWalletPass) {
        DesktopWalletPassCertificateSettings(settingsManager = settingsManager, platformContext = platformContext)
    }
    DesktopSettingsToggleRow(
        title = stringResource(Res.string.email_include_logo_label),
        description = stringResource(Res.string.email_include_logo_description),
        checked = includeLogo,
        onCheckedChange = {
            includeLogo = it
            settingsManager.setEmailIncludeLogoEnabled(it)
        }
    )

    if (includeLogo) {
        EmailLogoUploadSection(
            platformContext = platformContext,
            logoPath = emailLogoUri,
            onLogoPathChanged = { path ->
                emailLogoUri = path
                settingsManager.saveEmailLogoUri(path)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }

    OutlinedButton(
        onClick = {
            if (selectedTab == 0) {
                volunteerSubject = subjectDefault
                volunteerContentBefore = contentBeforeDefault
                volunteerIncludeQr = true
                volunteerContentAfter = contentAfterDefault
                settingsManager.saveEmailSubject(volunteerSubject)
                settingsManager.saveEmailContentBefore(volunteerContentBefore)
                settingsManager.setEmailIncludeQrEnabled(volunteerIncludeQr)
                settingsManager.saveEmailContentAfter(volunteerContentAfter)
            } else {
                guestSubject = guestSubjectDefault
                guestContentBefore = guestContentBeforeDefault
                guestIncludeQr = true
                guestContentAfter = guestContentAfterDefault
                settingsManager.saveGuestEmailSubject(guestSubject)
                settingsManager.saveGuestEmailContentBefore(guestContentBefore)
                settingsManager.setGuestEmailIncludeQrEnabled(guestIncludeQr)
                settingsManager.saveGuestEmailContentAfter(guestContentAfter)
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("${stringResource(Res.string.email_reset_to_defaults)} (${tabLabels[selectedTab]})")
    }
}

@Composable
private fun DesktopWalletPassCertificateSettings(
    settingsManager: SettingsManager,
    platformContext: com.eventmanager.app.platform.PlatformContext,
) {
    val scope = rememberCoroutineScope()
    val fileManager = remember(platformContext) { com.eventmanager.app.platform.PlatformFileManager(platformContext) }
    var certPassword by remember { mutableStateOf(settingsManager.getWalletPassCertificatePassword()) }
    var certConfigured by remember { mutableStateOf(fileManager.getWalletPassCertificateFile()?.exists() == true) }
    val signingReady = remember(certConfigured, certPassword) {
        com.eventmanager.app.wallet.WalletPassService.isAppleWalletSigningConfigured(
            settingsManager,
            fileManager.getWalletPassCertificateFile()?.readBytes(),
        )
    }
    val signingConfig = remember(signingReady, certPassword) {
        if (!signingReady) null else com.eventmanager.app.wallet.WalletPassSigningConfigLoader.fromSettings(
            settingsManager,
            fileManager.getWalletPassCertificateFile()?.readBytes(),
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(Res.string.email_wallet_pass_cert_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(Res.string.email_wallet_pass_cert_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val bytes = fileManager.pickWalletPassCertificateFile()
                        if (bytes != null && fileManager.saveWalletPassCertificate(bytes)) {
                            certConfigured = true
                            val info = com.eventmanager.app.wallet.PkPassCertificateParser.loadPkcs12(
                                bytes,
                                certPassword.toCharArray(),
                            )
                            info?.passTypeIdentifier?.let {
                                if (settingsManager.getWalletPassTypeIdentifier().isBlank()) {
                                    settingsManager.saveWalletPassTypeIdentifier(it)
                                }
                            }
                            info?.teamIdentifier?.let {
                                if (settingsManager.getWalletPassTeamIdentifier().isBlank()) {
                                    settingsManager.saveWalletPassTeamIdentifier(it)
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.email_wallet_pass_cert_upload))
            }
            OutlinedTextField(
                value = certPassword,
                onValueChange = {
                    certPassword = it
                    settingsManager.saveWalletPassCertificatePassword(it)
                },
                label = { Text(stringResource(Res.string.email_wallet_pass_cert_password_label)) },
                placeholder = { Text(stringResource(Res.string.email_wallet_pass_cert_password_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            val statusText = if (signingReady && signingConfig != null) {
                stringResource(
                    Res.string.email_wallet_pass_cert_configured,
                    signingConfig.passTypeIdentifier,
                    signingConfig.teamIdentifier,
                )
            } else {
                stringResource(Res.string.email_wallet_pass_cert_missing)
            }
            Text(
                statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (signingReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun DesktopDeveloperSettings(
    settingsManager: SettingsManager,
    viewModel: EventManagerViewModel,
    platformContext: com.eventmanager.app.platform.PlatformContext,
    onCheckForUpdates: () -> Unit,
    onConfigureUpdateSources: () -> Unit,
) {
    var debugModeEnabled by remember { mutableStateOf(settingsManager.getDebugMode()) }
    var logFiles by remember { mutableStateOf(FileAppLogger.getAllLogFiles()) }
    var totalLogSize by remember { mutableStateOf(FileAppLogger.getTotalLogSize()) }
    var showLogViewer by remember { mutableStateOf(false) }
    var selectedLogContent by remember { mutableStateOf<String?>(null) }
    val logsDirectoryPath = remember { FileAppLogger.getLogsDirectoryPath() }
    val scope = rememberCoroutineScope()
    val errorReadingGeneric = stringResource(Res.string.debug_logs_error_reading_generic)

    LaunchedEffect(debugModeEnabled) {
        if (debugModeEnabled) {
            logFiles = FileAppLogger.getAllLogFiles()
            totalLogSize = FileAppLogger.getTotalLogSize()
        }
    }

    DesktopSettingsToggleRow(
        title = stringResource(Res.string.debug_mode_title),
        description = stringResource(Res.string.debug_mode_description),
        checked = debugModeEnabled,
        onCheckedChange = {
            debugModeEnabled = it
            settingsManager.saveDebugMode(it)
            FileAppLogger.i("SettingsScreen", "Debug mode ${if (it) "enabled" else "disabled"}")
            if (it) {
                logFiles = FileAppLogger.getAllLogFiles()
                totalLogSize = FileAppLogger.getTotalLogSize()
            }
        }
    )

    Spacer(Modifier.height(8.dp))

    Button(
        onClick = onCheckForUpdates,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.SystemUpdate, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.check_for_updates))
    }

    TextButton(
        onClick = onConfigureUpdateSources,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(Res.string.change_update_sources),
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (debugModeEnabled) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(Res.string.debug_logs_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = stringResource(Res.string.debug_logs_location, logsDirectoryPath),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    Res.string.debug_logs_files_size,
                    logFiles.size,
                    String.format("%.2f", totalLogSize / (1024.0 * 1024.0))
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (logFiles.isNotEmpty()) {
                Text(stringResource(Res.string.debug_logs_recent_files), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                logFiles.takeLast(3).reversed().forEach { logFile ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(logFile.name, style = MaterialTheme.typography.labelSmall)
                            Text("${String.format("%.2f KB", logFile.length() / 1024.0)}", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = {
                            scope.launch {
                                selectedLogContent = withContext(Dispatchers.IO) {
                                    try {
                                        logFile.readText()
                                    } catch (e: Exception) {
                                        e.message ?: errorReadingGeneric
                                    }
                                }
                                showLogViewer = true
                            }
                        }) {
                            Text(stringResource(Res.string.debug_logs_view))
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (logFiles.isNotEmpty()) {
                    Button(
                        onClick = {
                            scope.launch {
                                selectedLogContent = withContext(Dispatchers.IO) {
                                    FileAppLogger.getLatestLogContent()
                                }
                                showLogViewer = true
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(Res.string.debug_logs_view_latest))
                    }
                }
                Button(
                    onClick = {
                        FileAppLogger.clearAllLogs()
                        logFiles = FileAppLogger.getAllLogFiles()
                        totalLogSize = FileAppLogger.getTotalLogSize()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        stringResource(Res.string.debug_logs_clear_all),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }

    if (showLogViewer) {
        DesktopLogViewerDialog(
            content = selectedLogContent ?: errorReadingGeneric,
            onDismiss = { showLogViewer = false }
        )
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    val biometricAuth = remember(platformContext) { createBiometricAuth(platformContext) }
    var biometricEnabled by remember { mutableStateOf(settingsManager.isBiometricAdminLoginEnabled()) }
    var showBiometricWarningDialog by remember { mutableStateOf(false) }
    var showBiometricAdminVerifyDialog by remember { mutableStateOf(false) }
    var pendingBiometricEnrollmentMatch by remember { mutableStateOf<ScannerMatch?>(null) }

    val noneEnrolled = biometricAuth.isNoneEnrolled
    val biometricAvailable = biometricAuth.isAvailable
    val enrollmentTitle = stringResource(Res.string.biometric_enrollment_prompt_title)
    val enrollmentSubtitle = stringResource(Res.string.biometric_enrollment_prompt_subtitle)
    val enrollmentSuccessMsg = stringResource(Res.string.biometric_enrollment_success)
    val biometricDisabledMsg = stringResource(Res.string.biometric_admin_login_disabled)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.biometric_admin_login_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = when {
                    noneEnrolled -> stringResource(Res.string.biometric_admin_login_none_enrolled)
                    !biometricAvailable -> stringResource(Res.string.biometric_admin_login_not_available)
                    else -> stringResource(Res.string.biometric_admin_login_description)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (!biometricAvailable) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = biometricEnabled,
            onCheckedChange = { wantsEnabled ->
                if (wantsEnabled) {
                    showBiometricWarningDialog = true
                } else {
                    biometricEnabled = false
                    settingsManager.setBiometricAdminLoginEnabled(false)
                    showPlatformToast(platformContext, biometricDisabledMsg)
                }
            },
            enabled = biometricAvailable
        )
    }

    if (showBiometricWarningDialog) {
        AlertDialog(
            onDismissRequest = { showBiometricWarningDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(Res.string.biometric_warning_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(Res.string.biometric_warning_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        showBiometricWarningDialog = false
                        showBiometricAdminVerifyDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(Res.string.biometric_warning_accept))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBiometricWarningDialog = false }) {
                    Text(stringResource(Res.string.biometric_warning_cancel))
                }
            }
        )
    }

    if (showBiometricAdminVerifyDialog) {
        BiometricAdminVerificationDialog(
            platformContext = platformContext,
            viewModel = viewModel,
            onVerified = { match ->
                showBiometricAdminVerifyDialog = false
                pendingBiometricEnrollmentMatch = match
            },
            onDismiss = { showBiometricAdminVerifyDialog = false }
        )
    }

    LaunchedEffect(pendingBiometricEnrollmentMatch) {
        val match = pendingBiometricEnrollmentMatch ?: return@LaunchedEffect
        pendingBiometricEnrollmentMatch = null
        val ok = biometricAuth.authenticate(enrollmentTitle, enrollmentSubtitle)
        if (ok) {
            settingsManager.setBiometricAdminProfileLink(match.toBiometricAdminProfileLink())
            biometricEnabled = true
            showPlatformToast(platformContext, enrollmentSuccessMsg)
        }
    }
}

@Composable
private fun DesktopGmailOAuthInstructionsDialog(onDismiss: () -> Unit) {
    var selectedTab by remember { mutableStateOf(GmailHelpTab.OAuth) }
    val scrollState = rememberScrollState()

    LaunchedEffect(selectedTab) {
        scrollState.scrollTo(0)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .fillMaxHeight(0.75f)
        ) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.email_gmail_oauth_help_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                GmailHelpTabRow(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedTab) {
                        GmailHelpTab.OAuth -> GmailHelpOAuthTab()
                        GmailHelpTab.ServiceAccount -> GmailHelpServiceAccountTab()
                    }
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.got_it))
                    }
                }
            }
        }
    }
}

private enum class GmailHelpTab { OAuth, ServiceAccount }

@Composable
private fun GmailHelpTabRow(
    selectedTab: GmailHelpTab,
    onTabSelected: (GmailHelpTab) -> Unit,
) {
    val tabs = listOf(
        GmailHelpTab.OAuth to stringResource(Res.string.email_gmail_help_tab_oauth),
        GmailHelpTab.ServiceAccount to stringResource(Res.string.email_gmail_help_tab_service_account),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEach { (tab, label) ->
            GmailHelpTabChip(
                label = label,
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GmailHelpTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun GmailHelpOAuthTab() {
    Text(
        text = stringResource(Res.string.email_gmail_oauth_help_intro),
        fontWeight = FontWeight.SemiBold
    )
    OutlinedButton(
        onClick = { openUrl("https://console.cloud.google.com/") },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.open_google_cloud_console))
    }
    val steps = listOf(
        Res.string.email_gmail_oauth_step_1_title to Res.string.email_gmail_oauth_step_1_description,
        Res.string.email_gmail_oauth_step_2_title to Res.string.email_gmail_oauth_step_2_description,
        Res.string.email_gmail_oauth_step_3_title to Res.string.email_gmail_oauth_step_3_description,
        Res.string.email_gmail_oauth_step_4_title to Res.string.email_gmail_oauth_step_4_description,
        Res.string.email_gmail_oauth_step_5_title to Res.string.email_gmail_oauth_step_5_description,
        Res.string.email_gmail_oauth_step_6_title to Res.string.email_gmail_oauth_step_6_description,
    )
    steps.forEachIndexed { index, (titleRes, descRes) ->
        DesktopInstructionStep(
            number = (index + 1).toString(),
            title = stringResource(titleRes),
            description = stringResource(descRes)
        )
    }
}

@Composable
private fun GmailHelpServiceAccountTab() {
    Text(
        text = stringResource(Res.string.email_gmail_service_account_help_intro),
        fontWeight = FontWeight.SemiBold
    )
    GmailHelpSection(
        title = stringResource(Res.string.email_gmail_service_account_help_sender_title),
        body = stringResource(Res.string.email_gmail_service_account_help_sender_body)
    )
    GmailHelpSection(
        title = stringResource(Res.string.email_gmail_service_account_help_not_title),
        body = stringResource(Res.string.email_gmail_service_account_help_not_body)
    )
    GmailHelpSection(
        title = stringResource(Res.string.email_gmail_service_account_help_requirements_title),
        body = stringResource(Res.string.email_gmail_service_account_help_requirements_body)
    )
    val steps = listOf(
        Res.string.email_gmail_service_account_help_step_1_title to Res.string.email_gmail_service_account_help_step_1_description,
        Res.string.email_gmail_service_account_help_step_2_title to Res.string.email_gmail_service_account_help_step_2_description,
        Res.string.email_gmail_service_account_help_step_3_title to Res.string.email_gmail_service_account_help_step_3_description,
    )
    steps.forEachIndexed { index, (titleRes, descRes) ->
        DesktopInstructionStep(
            number = (index + 1).toString(),
            title = stringResource(titleRes),
            description = stringResource(descRes)
        )
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Text(
            text = stringResource(Res.string.email_gmail_service_account_help_no_workspace),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun GmailHelpSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DesktopGoogleSheetsInstructionsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(Res.string.google_sheets_setup_instructions), fontWeight = FontWeight.Bold)
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(stringResource(Res.string.setup_instructions_intro), fontWeight = FontWeight.SemiBold)
                }
                item {
                    OutlinedButton(
                        onClick = { openUrl("https://console.cloud.google.com/") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.open_google_cloud_console))
                    }
                }
                val steps = listOf(
                    Res.string.step_1_title to Res.string.step_1_description,
                    Res.string.step_2_title to Res.string.step_2_description,
                    Res.string.step_3_title to Res.string.step_3_description,
                    Res.string.step_4_title to Res.string.step_4_description,
                    Res.string.step_5_title to Res.string.step_5_description,
                    Res.string.step_6_title to Res.string.step_6_description,
                    Res.string.step_7_title to Res.string.step_7_description,
                    Res.string.step_8_title to Res.string.step_8_description,
                    Res.string.step_9_title to Res.string.step_9_description
                )
                steps.forEachIndexed { index, (titleRes, descRes) ->
                    item {
                        DesktopInstructionStep(
                            number = (index + 1).toString(),
                            title = stringResource(titleRes),
                            description = stringResource(descRes)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.got_it))
            }
        }
    )
}

@Composable
private fun DesktopInstructionStep(number: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Card(
            modifier = Modifier.size(24.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(number, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DesktopLogViewerDialog(content: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(Res.string.debug_logs_content), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.close))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopServiceAccountKeyInfoPanel(
    keyInfo: ServiceAccountKeyInfo?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (keyInfo != null) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = if (keyInfo != null) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (keyInfo != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (keyInfo != null) {
                        stringResource(Res.string.service_account_key_found)
                    } else {
                        stringResource(Res.string.service_account_key_required)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (keyInfo != null) {
                    Text(
                        text = stringResource(Res.string.email_colon, keyInfo.clientEmail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(Res.string.project_colon, keyInfo.projectId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.upload_key_file_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopSettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SheetNameField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun DesktopSettingsCategory(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            if (expanded) content()
        }
    }
}
