package com.eventmanager.app.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eventmanager.app.R
import com.eventmanager.app.data.repository.EventManagerRepository
import com.eventmanager.app.data.sync.FileManager
import com.eventmanager.app.data.sync.GoogleSheetsService
import com.eventmanager.app.data.sync.SyncManager
import com.eventmanager.app.data.sync.settingsManagerFor
import com.eventmanager.app.platform.createDatabase
import com.eventmanager.app.platform.createPlatformContext
import com.eventmanager.app.ui.components.ResolutionScaleSlider
import com.eventmanager.app.ui.components.isNvidiaShieldTablet
import com.eventmanager.app.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SetupStep(val titleRes: Int, val descRes: Int) {
    LANGUAGE(R.string.setup_language_title, R.string.setup_language_description),
    THEME(R.string.setup_theme_title, R.string.setup_theme_description),
    COLOR_PROFILE(R.string.setup_color_profile_title, R.string.setup_color_profile_description),
    LAYOUT(R.string.setup_layout_size_title, R.string.setup_layout_size_description),
    SPREADSHEET(R.string.setup_spreadsheet_title, R.string.setup_spreadsheet_description),
    JSON_KEY(R.string.setup_json_key_title, R.string.setup_json_key_description),
    SHEETS(R.string.setup_sheets_title, R.string.setup_sheets_description),
    FIRST_SYNC(R.string.setup_wizard_first_sync_title, R.string.setup_wizard_first_sync_message)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(onSetupComplete: () -> Unit, onThemeModeChanged: (String) -> Unit = {}) {
    val context = LocalContext.current
    val settingsManager = remember { settingsManagerFor(context) }
    val fileManager = remember { FileManager(context) }
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(pageCount = { SetupStep.entries.size })
    var selectedLanguage by remember { mutableStateOf(settingsManager.getLanguage()) }
    var selectedTheme by remember { mutableStateOf(ThemeMode.fromString(settingsManager.getThemeMode())) }
    var selectedColorTheme by remember { mutableStateOf(settingsManager.getColorTheme()) }
    var resolutionScale by remember { mutableStateOf(settingsManager.getResolutionScale()) }

    var spreadsheetId by remember { mutableStateOf(settingsManager.getSpreadsheetId()) }
    var guestListSheet by remember { mutableStateOf(settingsManager.getGuestListSheet()) }
    var volunteerSheet by remember { mutableStateOf(settingsManager.getVolunteerSheet()) }
    var jobsSheet by remember { mutableStateOf(settingsManager.getJobsSheet()) }
    var volunteerGuestListSheet by remember { mutableStateOf(settingsManager.getVolunteerGuestListSheet()) }
    var jobTypesSheet by remember { mutableStateOf(settingsManager.getJobTypesSheet()) }
    var venuesSheet by remember { mutableStateOf(settingsManager.getVenuesSheet()) }
    var salesItemsSheet by remember { mutableStateOf(settingsManager.getSalesItemsSheet()) }
    var tempGuestListSheet by remember { mutableStateOf(settingsManager.getTempGuestListSheet()) }

    var jsonKeyStatus by remember { mutableStateOf<String?>(null) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var firstSyncRunning by remember { mutableStateOf(false) }
    var firstSyncError by remember { mutableStateOf<String?>(null) }
    var firstSyncDone by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { selectedFileUri = it } }

    LaunchedEffect(selectedFileUri) {
        selectedFileUri?.let { uri ->
            jsonKeyStatus = context.getString(R.string.validating_file)
            fileManager.validateJsonKeyFile(uri)
                .onSuccess {
                    jsonKeyStatus = context.getString(R.string.file_validated_uploading)
                    fileManager.copyFileToAssets(uri, "service_account_key.json")
                        .onSuccess { jsonKeyStatus = context.getString(R.string.file_uploaded_successfully) }
                        .onFailure { e -> jsonKeyStatus = context.getString(R.string.upload_failed, e.message ?: "") }
                }
                .onFailure { e -> jsonKeyStatus = context.getString(R.string.validation_failed, e.message ?: "") }
        }
    }

    fun recreateForLocaleOrTheme() {
        settingsManager.markSkipNextStartupSync()
        (context as? Activity)?.recreate()
    }

    fun finishSetup() {
        settingsManager.setSetupWizardCompleted(true)
        onSetupComplete()
    }

    fun runFirstSync() {
        scope.launch {
            firstSyncRunning = true
            firstSyncError = null
            settingsManager.saveSpreadsheetId(spreadsheetId)
            settingsManager.saveGuestListSheet(guestListSheet)
            settingsManager.saveVolunteerSheet(volunteerSheet)
            settingsManager.saveJobsSheet(jobsSheet)
            settingsManager.saveVolunteerGuestListSheet(volunteerGuestListSheet)
            settingsManager.saveJobTypesSheet(jobTypesSheet)
            settingsManager.saveVenuesSheet(venuesSheet)
            settingsManager.saveSalesItemsSheet(salesItemsSheet)
            settingsManager.saveTempGuestListSheet(tempGuestListSheet)
            val result = withContext(Dispatchers.IO) {
                try {
                    val platformCtx = createPlatformContext(context.applicationContext)
                    val db = createDatabase(platformCtx)
                    val repository = EventManagerRepository(
                        db.guestDao(),
                        db.volunteerDao(),
                        db.jobDao(),
                        db.jobTypeConfigDao(),
                        db.venueDao(),
                        db.salesSheetItemDao()
                    )
                    val syncManager = SyncManager(platformCtx, repository, GoogleSheetsService(platformCtx))
                    syncManager.repairSheetStructureThenFullDownload()
                } catch (e: Exception) {
                    com.eventmanager.app.data.sync.SyncResult.Error(e.message ?: "Sync failed")
                }
            }
            firstSyncRunning = false
            if (result is com.eventmanager.app.data.sync.SyncResult.Success) {
                firstSyncDone = true
            } else {
                firstSyncError = (result as? com.eventmanager.app.data.sync.SyncResult.Error)?.message
                    ?: context.getString(R.string.setup_wizard_first_sync_error_generic)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(SetupStep.entries[pagerState.currentPage].titleRes)) },
                actions = {
                    TextButton(onClick = { finishSetup() }) {
                        Text(context.getString(R.string.setup_skip))
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pagerState.currentPage > 0) {
                        OutlinedButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                            Text(context.getString(R.string.setup_back))
                        }
                    } else {
                        Spacer(Modifier.width(8.dp))
                    }
                    val isLast = pagerState.currentPage == SetupStep.entries.lastIndex
                    Button(
                        onClick = {
                            if (isLast) {
                                if (firstSyncDone) finishSetup()
                                else if (!firstSyncRunning) runFirstSync()
                            } else {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        },
                        enabled = when (SetupStep.entries[pagerState.currentPage]) {
                            SetupStep.FIRST_SYNC -> firstSyncDone || !firstSyncRunning
                            else -> true
                        }
                    ) {
                        Text(
                            when {
                                isLast && firstSyncDone -> context.getString(R.string.setup_finish)
                                isLast -> context.getString(R.string.setup_continue)
                                pagerState.currentPage == SetupStep.entries.lastIndex - 1 -> context.getString(R.string.setup_continue)
                                else -> context.getString(R.string.setup_continue)
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            userScrollEnabled = false
        ) { page ->
            val step = SetupStep.entries[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = context.getString(step.descRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (step) {
                    SetupStep.LANGUAGE -> LanguagePage(
                        selectedLanguage = selectedLanguage,
                        onSelect = { code ->
                            selectedLanguage = code
                            settingsManager.saveLanguage(code)
                            recreateForLocaleOrTheme()
                        }
                    )
                    SetupStep.THEME -> ThemePage(
                        selectedTheme = selectedTheme,
                        onSelect = { mode ->
                            selectedTheme = mode
                            settingsManager.saveThemeMode(mode.value)
                            onThemeModeChanged(mode.value)
                            recreateForLocaleOrTheme()
                        }
                    )
                    SetupStep.COLOR_PROFILE -> ColorProfilePage(
                        selectedColorTheme = selectedColorTheme,
                        previewDark = when (selectedTheme) {
                            ThemeMode.LIGHT -> false
                            ThemeMode.DARK -> true
                            ThemeMode.DEFAULT -> isSystemInDarkTheme()
                        },
                        onSelect = { key ->
                            selectedColorTheme = key
                            settingsManager.saveColorTheme(key)
                            recreateForLocaleOrTheme()
                        }
                    )
                    SetupStep.LAYOUT -> LayoutPage(
                        resolutionScale = resolutionScale,
                        onSave = { scale ->
                            resolutionScale = scale
                            settingsManager.saveResolutionScale(scale)
                            (context as? Activity)?.recreate()
                        },
                        onUseRecommended = {
                            resolutionScale = 1.07f
                            settingsManager.saveResolutionScale(1.07f)
                            (context as? Activity)?.recreate()
                        }
                    )
                    SetupStep.SPREADSHEET -> OutlinedTextField(
                        value = spreadsheetId,
                        onValueChange = { spreadsheetId = it },
                        label = { Text(context.getString(R.string.spreadsheet_id_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text(context.getString(R.string.setup_spreadsheet_hint)) }
                    )
                    SetupStep.JSON_KEY -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { filePickerLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                            Icon(Icons.Default.Upload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(context.getString(R.string.upload_key_file))
                        }
                        jsonKeyStatus?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    SetupStep.SHEETS -> SheetsPage(
                        guestListSheet = guestListSheet,
                        onGuestListSheet = { guestListSheet = it },
                        volunteerSheet = volunteerSheet,
                        onVolunteerSheet = { volunteerSheet = it },
                        jobsSheet = jobsSheet,
                        onJobsSheet = { jobsSheet = it },
                        volunteerGuestListSheet = volunteerGuestListSheet,
                        onVolunteerGuestListSheet = { volunteerGuestListSheet = it },
                        jobTypesSheet = jobTypesSheet,
                        onJobTypesSheet = { jobTypesSheet = it },
                        venuesSheet = venuesSheet,
                        onVenuesSheet = { venuesSheet = it },
                        salesItemsSheet = salesItemsSheet,
                        onSalesItemsSheet = { salesItemsSheet = it },
                        tempGuestListSheet = tempGuestListSheet,
                        onTempGuestListSheet = { tempGuestListSheet = it }
                    )
                    SetupStep.FIRST_SYNC -> FirstSyncPage(
                        running = firstSyncRunning,
                        done = firstSyncDone,
                        error = firstSyncError
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguagePage(selectedLanguage: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val options = listOf(
        "en" to R.string.language_english,
        "fr" to R.string.language_french,
        "es" to R.string.language_spanish,
        "zh-TW" to R.string.language_chinese,
        "zh-CN" to R.string.language_chinese_simplified
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (code, labelRes) ->
            val selected = selectedLanguage == code
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(code) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Text(
                    text = context.getString(labelRes),
                    modifier = Modifier.padding(16.dp),
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ThemePage(selectedTheme: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val context = LocalContext.current
    val options = listOf(
        ThemeMode.LIGHT to (Icons.Default.WbSunny to R.string.theme_light),
        ThemeMode.DARK to (Icons.Default.NightlightRound to R.string.theme_dark),
        ThemeMode.DEFAULT to (Icons.Default.AutoAwesome to R.string.theme_default)
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        options.forEach { (mode, iconLabel) ->
            val (icon, labelRes) = iconLabel
            val selected = selectedTheme == mode
            Card(
                modifier = Modifier
                    .width(150.dp)
                    .clickable { onSelect(mode) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(
                    if (selected) 1.5.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(context.getString(labelRes), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun ColorProfilePage(selectedColorTheme: String, previewDark: Boolean, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val options = listOf(
        "system" to R.string.color_theme_system,
        "professional_blue" to R.string.color_theme_professional_blue,
        "neutral_green" to R.string.color_theme_neutral_green,
        "warm_gray" to R.string.color_theme_warm_gray,
        "neutral_purple" to R.string.color_theme_neutral_purple,
        "rich_brown" to R.string.color_theme_rich_brown,
        "sunset_mist" to R.string.color_theme_sunset_mist
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (previewDark) context.getString(R.string.theme_dark) else context.getString(R.string.theme_light),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        options.forEach { (key, labelRes) ->
            val selected = selectedColorTheme == key
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(key) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Text(
                    text = context.getString(labelRes),
                    modifier = Modifier.padding(16.dp),
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun LayoutPage(
    resolutionScale: Float,
    onSave: (Float) -> Unit,
    onUseRecommended: () -> Unit
) {
    val context = LocalContext.current
    var pendingScale by remember(resolutionScale) { mutableStateOf(resolutionScale) }
    val hasUnsavedChanges = pendingScale != resolutionScale
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ResolutionScaleSlider(
            value = pendingScale,
            onValueChange = { pendingScale = it },
            modifier = Modifier.fillMaxWidth()
        )
        if (isNvidiaShieldTablet()) {
            Text(
                text = context.getString(R.string.setup_layout_nvidia_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onUseRecommended, modifier = Modifier.fillMaxWidth()) {
                Text(context.getString(R.string.setup_layout_use_recommended))
            }
        }
        if (hasUnsavedChanges) {
            Button(onClick = { onSave(pendingScale) }, modifier = Modifier.fillMaxWidth()) {
                Text(context.getString(R.string.save_resolution_scale))
            }
        }
    }
}

@Composable
private fun SheetsPage(
    guestListSheet: String, onGuestListSheet: (String) -> Unit,
    volunteerSheet: String, onVolunteerSheet: (String) -> Unit,
    jobsSheet: String, onJobsSheet: (String) -> Unit,
    volunteerGuestListSheet: String, onVolunteerGuestListSheet: (String) -> Unit,
    jobTypesSheet: String, onJobTypesSheet: (String) -> Unit,
    venuesSheet: String, onVenuesSheet: (String) -> Unit,
    salesItemsSheet: String, onSalesItemsSheet: (String) -> Unit,
    tempGuestListSheet: String, onTempGuestListSheet: (String) -> Unit
) {
    val context = LocalContext.current
    @Composable
    fun sheetField(value: String, onValue: (String) -> Unit, labelRes: Int) {
        OutlinedTextField(value = value, onValueChange = onValue, label = { Text(context.getString(labelRes)) }, modifier = Modifier.fillMaxWidth())
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        sheetField(guestListSheet, onGuestListSheet, R.string.guest_list_sheet_label)
        sheetField(volunteerSheet, onVolunteerSheet, R.string.volunteer_sheet_label)
        sheetField(jobsSheet, onJobsSheet, R.string.shifts_sheet_label)
        sheetField(volunteerGuestListSheet, onVolunteerGuestListSheet, R.string.volunteer_guest_list_sheet_label)
        sheetField(jobTypesSheet, onJobTypesSheet, R.string.shift_types_sheet_label)
        sheetField(venuesSheet, onVenuesSheet, R.string.venues_sheet_label)
        sheetField(salesItemsSheet, onSalesItemsSheet, R.string.sales_items_sheet_label)
        sheetField(tempGuestListSheet, onTempGuestListSheet, R.string.temp_guest_list_sheet_label)
    }
}

@Composable
private fun FirstSyncPage(running: Boolean, done: Boolean, error: String?) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        when {
            running -> {
                CircularProgressIndicator()
                Text(context.getString(R.string.setup_wizard_first_sync_message), textAlign = TextAlign.Center)
            }
            done -> {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Text(context.getString(R.string.setup_finish), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            error != null -> {
                Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                Text(context.getString(R.string.setup_wizard_first_sync_error_title), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Text(error, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                Text(context.getString(R.string.setup_wizard_first_sync_message), textAlign = TextAlign.Center)
            }
        }
    }
}
