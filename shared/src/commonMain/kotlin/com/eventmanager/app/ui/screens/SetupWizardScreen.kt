package com.eventmanager.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.repository.EventManagerRepository
import com.eventmanager.app.data.sync.GoogleSheetsService
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.data.sync.SyncResult
import com.eventmanager.app.data.sync.settingsManagerFor
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.createDatabase
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import com.eventmanager.app.platform.isDesktop
import com.eventmanager.app.ui.components.BackgroundAnimationSettingsSection
import com.eventmanager.app.ui.platform.ServiceAccountKeyUploadButton
import com.eventmanager.app.ui.platform.SetupLayoutScalePage
import com.eventmanager.app.ui.platform.applyLocaleChange
import com.eventmanager.app.ui.platform.applyLocaleOrThemeChange
import com.eventmanager.app.ui.platform.applyThemeAppearanceChange
import com.eventmanager.app.ui.platform.supportsResolutionScaleStep
import com.eventmanager.app.ui.components.ColorThemePicker
import com.eventmanager.app.ui.components.ThemeModePicker
import com.eventmanager.app.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private enum class SetupStep {
    WELCOME,
    LANGUAGE,
    THEME,
    COLOR_PROFILE,
    LAYOUT,
    CHOOSE_BACKEND,
    GOOGLE_SHEETS,
    SHEETS,
    FIREBASE_SETUP,
    FIRST_SYNC,
}

private enum class SetupPhase {
    WELCOME,
    PERSONALIZE,
    CONNECT,
    FINISH,
}

private val allSetupSteps: List<SetupStep> = buildList {
    add(SetupStep.WELCOME)
    add(SetupStep.LANGUAGE)
    add(SetupStep.THEME)
    add(SetupStep.COLOR_PROFILE)
    if (supportsResolutionScaleStep()) add(SetupStep.LAYOUT)
    add(SetupStep.CHOOSE_BACKEND)
    add(SetupStep.GOOGLE_SHEETS)
    add(SetupStep.SHEETS)
    add(SetupStep.FIREBASE_SETUP)
    add(SetupStep.FIRST_SYNC)
}

private fun SetupStep.phase(): SetupPhase = when (this) {
    SetupStep.WELCOME -> SetupPhase.WELCOME
    SetupStep.LANGUAGE, SetupStep.THEME, SetupStep.COLOR_PROFILE, SetupStep.LAYOUT -> SetupPhase.PERSONALIZE
    SetupStep.CHOOSE_BACKEND, SetupStep.GOOGLE_SHEETS, SetupStep.SHEETS, SetupStep.FIREBASE_SETUP -> SetupPhase.CONNECT
    SetupStep.FIRST_SYNC -> SetupPhase.FINISH
}

@Composable
private fun SetupStep.title(): String = when (this) {
    SetupStep.WELCOME -> stringResource(Res.string.setup_welcome_title)
    SetupStep.LANGUAGE -> stringResource(Res.string.setup_language_title)
    SetupStep.THEME -> stringResource(Res.string.setup_theme_title)
    SetupStep.COLOR_PROFILE -> stringResource(Res.string.setup_color_profile_title)
    SetupStep.LAYOUT -> stringResource(Res.string.setup_layout_size_title)
    SetupStep.CHOOSE_BACKEND -> stringResource(Res.string.setup_choose_backend_title)
    SetupStep.GOOGLE_SHEETS -> stringResource(Res.string.setup_google_sheets_title)
    SetupStep.SHEETS -> stringResource(Res.string.setup_sheets_title)
    SetupStep.FIREBASE_SETUP -> stringResource(Res.string.setup_firebase_title)
    SetupStep.FIRST_SYNC -> stringResource(Res.string.setup_wizard_first_sync_title)
}

@Composable
private fun SetupStep.description(): String = when (this) {
    SetupStep.WELCOME -> stringResource(Res.string.setup_welcome_description)
    SetupStep.LANGUAGE -> stringResource(Res.string.setup_language_description)
    SetupStep.THEME -> stringResource(Res.string.setup_theme_description)
    SetupStep.COLOR_PROFILE -> stringResource(Res.string.setup_color_profile_description)
    SetupStep.LAYOUT -> stringResource(Res.string.setup_layout_size_description)
    SetupStep.CHOOSE_BACKEND -> stringResource(Res.string.setup_choose_backend_description)
    SetupStep.GOOGLE_SHEETS -> stringResource(Res.string.setup_google_sheets_description)
    SetupStep.SHEETS -> stringResource(Res.string.setup_sheets_description)
    SetupStep.FIREBASE_SETUP -> stringResource(Res.string.setup_firebase_description)
    SetupStep.FIRST_SYNC -> stringResource(Res.string.setup_first_sync_ready)
}

private fun SetupStep.icon(): ImageVector = when (this) {
    SetupStep.WELCOME -> Icons.Default.RocketLaunch
    SetupStep.LANGUAGE -> Icons.Default.Language
    SetupStep.THEME -> Icons.Default.DarkMode
    SetupStep.COLOR_PROFILE -> Icons.Default.Palette
    SetupStep.LAYOUT -> Icons.Default.AspectRatio
    SetupStep.CHOOSE_BACKEND -> Icons.Default.Storage
    SetupStep.GOOGLE_SHEETS -> Icons.Default.CloudSync
    SetupStep.SHEETS -> Icons.Default.TableChart
    SetupStep.FIREBASE_SETUP -> Icons.Default.Cloud
    SetupStep.FIRST_SYNC -> Icons.Default.Sync
}

@Composable
private fun SetupPhase.label(): String = when (this) {
    SetupPhase.WELCOME -> ""
    SetupPhase.PERSONALIZE -> stringResource(Res.string.setup_phase_personalize)
    SetupPhase.CONNECT -> stringResource(Res.string.setup_phase_connect)
    SetupPhase.FINISH -> stringResource(Res.string.setup_phase_finish)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    platformContext: PlatformContext,
    onSetupComplete: () -> Unit,
    onThemeModeChanged: (String) -> Unit = {},
    onRequestFirebaseSignIn: (() -> Unit)? = null,
    firebaseAuthEmail: String? = null,
) {
    val settingsManager = remember(platformContext) { settingsManagerFor(platformContext) }
    val scope = rememberCoroutineScope()
    var selectedBackend by remember {
        mutableStateOf(settingsManager.getBackendType())
    }
    val steps = remember(selectedBackend) {
        buildList {
            add(SetupStep.WELCOME)
            add(SetupStep.LANGUAGE)
            add(SetupStep.THEME)
            add(SetupStep.COLOR_PROFILE)
            if (supportsResolutionScaleStep()) add(SetupStep.LAYOUT)
            add(SetupStep.CHOOSE_BACKEND)
            when (selectedBackend) {
                com.eventmanager.app.data.remote.BackendType.SHEETS -> {
                    add(SetupStep.GOOGLE_SHEETS)
                    add(SetupStep.SHEETS)
                }
                com.eventmanager.app.data.remote.BackendType.FIREBASE -> {
                    add(SetupStep.FIREBASE_SETUP)
                }
            }
            add(SetupStep.FIRST_SYNC)
        }
    }

    val pagerState = rememberPagerState(pageCount = { steps.size })
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
    var settingsSheet by remember { mutableStateOf(settingsManager.getSettingsSheet()) }

    var jsonKeyStatus by remember { mutableStateOf<String?>(null) }
    var firebaseConfiguredOrgs by remember {
        mutableStateOf(
            settingsManager.getFirebaseConfiguredOrgs().ifEmpty {
                listOf(
                    com.eventmanager.app.data.remote.FirebaseConfiguredOrg(
                        orgId = settingsManager.getFirebaseOrgId(),
                        colorArgb = com.eventmanager.app.data.remote.FirebaseConfiguredOrgCodec.defaultColorForIndex(0),
                    ),
                )
            },
        )
    }
    var firebaseProjectId by remember { mutableStateOf(settingsManager.getFirebaseProjectId()) }
    var firebaseApplicationId by remember { mutableStateOf(settingsManager.getFirebaseApplicationId()) }
    var firebaseApiKey by remember { mutableStateOf(settingsManager.getFirebaseApiKey()) }
    var firebaseWebClientId by remember { mutableStateOf(settingsManager.getFirebaseWebClientId()) }
    var firebaseWebClientSecret by remember { mutableStateOf(settingsManager.getFirebaseWebClientSecret()) }
    var authEmail by remember {
        mutableStateOf(firebaseAuthEmail ?: settingsManager.getFirebaseAuthEmail().ifBlank { null })
    }
    LaunchedEffect(firebaseAuthEmail) {
        if (!firebaseAuthEmail.isNullOrBlank()) authEmail = firebaseAuthEmail
        else {
            val fromSettings = settingsManager.getFirebaseAuthEmail().ifBlank { null }
            if (fromSettings != null) authEmail = fromSettings
        }
    }
    var firstSyncRunning by remember { mutableStateOf(false) }
    var firstSyncError by remember { mutableStateOf<String?>(null) }
    var firstSyncDone by remember { mutableStateOf(false) }

    fun persistFirebaseFields() {
        settingsManager.setFirebaseConfiguredOrgs(firebaseConfiguredOrgs)
        settingsManager.setFirebaseProjectId(firebaseProjectId.trim())
        settingsManager.setFirebaseApplicationId(firebaseApplicationId.trim())
        settingsManager.setFirebaseApiKey(firebaseApiKey.trim())
        settingsManager.setFirebaseWebClientId(firebaseWebClientId.trim())
        settingsManager.setFirebaseWebClientSecret(firebaseWebClientSecret.trim())
    }

    fun firebaseStepReady(): Boolean =
        com.eventmanager.app.ui.components.firebaseConnectionReady(
            configuredOrgs = firebaseConfiguredOrgs,
            projectId = firebaseProjectId,
            applicationId = firebaseApplicationId,
            apiKey = firebaseApiKey,
            authEmail = authEmail,
            requireAuth = true,
        )

    fun sheetsStepReady(): Boolean =
        spreadsheetId.isNotBlank() && spreadsheetId != "YOUR_SPREADSHEET_ID_HERE"

    fun finishSetup() {
        settingsManager.setSetupWizardCompleted(true)
        onSetupComplete()
    }

    fun runFirstSync() {
        scope.launch {
            firstSyncRunning = true
            firstSyncError = null
            settingsManager.setBackendType(selectedBackend)
            val result = withContext(Dispatchers.IO) {
                try {
                    when (selectedBackend) {
                        com.eventmanager.app.data.remote.BackendType.FIREBASE -> {
                            persistFirebaseFields()
                            if (!firebaseStepReady()) {
                                SyncResult.Error("Complete Firebase org ID, project options, and Google Sign-In first")
                            } else {
                                val auth = com.eventmanager.app.data.remote.createFirebaseAuthService(platformContext)
                                val authResult = when {
                                    auth.isSignedIn() -> com.eventmanager.app.data.remote.FirebaseAuthResult.Success(
                                        uid = auth.currentUserId().orEmpty(),
                                        email = auth.currentUserEmail(),
                                    )
                                    else -> auth.restoreSession() ?: auth.signInWithGoogle()
                                }
                                when (authResult) {
                                    is com.eventmanager.app.data.remote.FirebaseAuthResult.Error ->
                                        SyncResult.Error(authResult.message)
                                    is com.eventmanager.app.data.remote.FirebaseAuthResult.Success -> {
                                        authEmail = authResult.email
                                        settingsManager.setFirebaseAuthEmail(authResult.email.orEmpty())
                                        val gateway = com.eventmanager.app.data.remote.createFirestoreGateway(
                                            platformContext,
                                            settingsManager,
                                        )
                                        val activeOrgId = settingsManager.getFirebaseOrgId().trim()
                                        val bootstrapCode = com.eventmanager.app.data.remote.MemberRoleAdmin
                                            .bootstrapOrgAdmin(
                                                gateway = gateway,
                                                orgId = activeOrgId,
                                                uid = authResult.uid,
                                                email = authResult.email,
                                                allowedEmailDomains = settingsManager.getAllowedEmailDomains(),
                                            )
                                        settingsManager.setFirebaseBootstrapCode(bootstrapCode)
                                        settingsManager.applyLocalInstitutionBackendAnnouncement(
                                            com.eventmanager.app.data.remote.InstitutionBackendAnnouncement(
                                                backendType = com.eventmanager.app.data.remote.BackendType.FIREBASE,
                                                migrationId = "",
                                                migratedAt = System.currentTimeMillis(),
                                                firebaseOrgId = activeOrgId,
                                            )
                                        )
                                        val db = createDatabase(platformContext)
                                        val repository = EventManagerRepository(
                                            db.guestDao(), db.volunteerDao(), db.jobDao(),
                                            db.jobTypeConfigDao(), db.venueDao(), db.salesSheetItemDao(),
                                            db.accountTransferDao()
                                        )
                                        val ledger = com.eventmanager.app.data.remote.FirebaseLedgerService(
                                            repository, settingsManager, gateway,
                                        )
                                        val firebase = com.eventmanager.app.data.remote.FirebaseRemoteBackend(
                                            platformContext = platformContext,
                                            repository = repository,
                                            settingsManager = settingsManager,
                                            firestoreGateway = gateway,
                                            ledgerService = ledger,
                                        )
                                        firebase.performStartupSync()
                                    }
                                }
                            }
                        }
                        com.eventmanager.app.data.remote.BackendType.SHEETS -> {
                            settingsManager.saveSpreadsheetId(spreadsheetId)
                            settingsManager.saveGuestListSheet(guestListSheet)
                            settingsManager.saveVolunteerSheet(volunteerSheet)
                            settingsManager.saveJobsSheet(jobsSheet)
                            settingsManager.saveVolunteerGuestListSheet(volunteerGuestListSheet)
                            settingsManager.saveJobTypesSheet(jobTypesSheet)
                            settingsManager.saveVenuesSheet(venuesSheet)
                            settingsManager.saveSalesItemsSheet(salesItemsSheet)
                            settingsManager.saveTempGuestListSheet(tempGuestListSheet)
                            settingsManager.saveSettingsSheet(settingsSheet)
                            val db = createDatabase(platformContext)
                            val repository = EventManagerRepository(
                                db.guestDao(), db.volunteerDao(), db.jobDao(),
                                db.jobTypeConfigDao(), db.venueDao(), db.salesSheetItemDao(),
                                db.accountTransferDao()
                            )
                            val syncManager = com.eventmanager.app.data.sync.SyncManager(
                                platformContext, repository, GoogleSheetsService(platformContext)
                            )
                            syncManager.repairSheetStructureThenFullDownload()
                        }
                    }
                } catch (e: Exception) {
                    SyncResult.Error(e.message ?: "Sync failed")
                }
            }
            firstSyncRunning = false
            if (result is SyncResult.Success) {
                firstSyncDone = true
            } else {
                firstSyncError = (result as? SyncResult.Error)?.message ?: "Sync failed"
            }
        }
    }

    val currentStep = steps[pagerState.currentPage]
    val progress = (pagerState.currentPage + 1).toFloat() / steps.size

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            Surface(tonalElevation = 4.dp, shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (pagerState.currentPage > 0) {
                            OutlinedButton(
                                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(Res.string.setup_back))
                            }
                        } else {
                            Spacer(Modifier.width(8.dp))
                        }
                        val isLast = pagerState.currentPage == steps.lastIndex
                        Button(
                            onClick = {
                                if (isLast) {
                                    if (firstSyncDone) finishSetup()
                                    else if (!firstSyncRunning) runFirstSync()
                                } else {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                }
                            },
                            enabled = when (currentStep) {
                                SetupStep.FIREBASE_SETUP -> firebaseStepReady()
                                SetupStep.GOOGLE_SHEETS -> sheetsStepReady()
                                SetupStep.FIRST_SYNC -> firstSyncDone || !firstSyncRunning
                                else -> true
                            }
                        ) {
                            Text(
                                when {
                                    currentStep == SetupStep.WELCOME -> stringResource(Res.string.setup_get_started)
                                    isLast && firstSyncDone -> stringResource(Res.string.setup_finish)
                                    isLast -> stringResource(Res.string.setup_start_sync)
                                    else -> stringResource(Res.string.setup_continue)
                                }
                            )
                            if (!isLast || !firstSyncDone) {
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().widthIn(max = 640.dp),
                userScrollEnabled = false
            ) { page ->
                val step = steps[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    SetupWizardHeader(
                        step = step,
                        stepIndex = page,
                        totalSteps = steps.size,
                        onSkip = { finishSetup() },
                        showSkip = step != SetupStep.WELCOME && step != SetupStep.FIRST_SYNC
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            when (step) {
                                SetupStep.WELCOME -> WelcomePage(
                                    settingsManager = settingsManager,
                                    isDesktop = platformContext.isDesktop,
                                )
                                SetupStep.LANGUAGE -> LanguagePage(selectedLanguage) { code ->
                                    selectedLanguage = code
                                    settingsManager.saveLanguage(code)
                                    settingsManager.markSkipNextStartupSync()
                                    applyLocaleChange(platformContext)
                                }
                                SetupStep.THEME -> ThemeModePicker(
                                    selectedMode = selectedTheme,
                                    onSelect = { mode ->
                                        selectedTheme = mode
                                        settingsManager.saveThemeMode(mode.value)
                                        onThemeModeChanged(mode.value)
                                        settingsManager.markSkipNextStartupSync()
                                        applyThemeAppearanceChange(platformContext)
                                    },
                                    showHeader = false,
                                )
                                SetupStep.COLOR_PROFILE -> ColorThemePicker(
                                    selectedThemeKey = selectedColorTheme,
                                    previewDark = when (selectedTheme) {
                                        ThemeMode.LIGHT -> false
                                        ThemeMode.DARK -> true
                                        ThemeMode.DEFAULT -> isSystemInDarkTheme()
                                    },
                                    onSelect = { key ->
                                        selectedColorTheme = key
                                        settingsManager.saveColorTheme(key)
                                        settingsManager.markSkipNextStartupSync()
                                        applyThemeAppearanceChange(platformContext)
                                    },
                                    showHeader = false,
                                )
                                SetupStep.LAYOUT -> SetupLayoutScalePage(
                                    resolutionScale = resolutionScale,
                                    onSave = { scale ->
                                        resolutionScale = scale
                                        settingsManager.saveResolutionScale(scale)
                                        applyLocaleOrThemeChange(platformContext)
                                    },
                                    onUseRecommended = {
                                        resolutionScale = 1.07f
                                        settingsManager.saveResolutionScale(1.07f)
                                        applyLocaleOrThemeChange(platformContext)
                                    }
                                )
                                SetupStep.CHOOSE_BACKEND -> ChooseBackendPage(
                                    selected = selectedBackend,
                                    onSelect = {
                                        selectedBackend = it
                                        settingsManager.setBackendType(it)
                                    }
                                )
                                SetupStep.GOOGLE_SHEETS -> GoogleSheetsPage(
                                    spreadsheetId = spreadsheetId,
                                    onSpreadsheetIdChange = { spreadsheetId = it },
                                    platformContext = platformContext,
                                    jsonKeyStatus = jsonKeyStatus,
                                    onJsonKeyStatus = { jsonKeyStatus = it }
                                )
                                SetupStep.SHEETS -> SheetsPage(
                                    guestListSheet, { guestListSheet = it },
                                    volunteerSheet, { volunteerSheet = it },
                                    jobsSheet, { jobsSheet = it },
                                    volunteerGuestListSheet, { volunteerGuestListSheet = it },
                                    jobTypesSheet, { jobTypesSheet = it },
                                    venuesSheet, { venuesSheet = it },
                                    salesItemsSheet, { salesItemsSheet = it },
                                    tempGuestListSheet, { tempGuestListSheet = it },
                                    settingsSheet, { settingsSheet = it }
                                )
                                SetupStep.FIREBASE_SETUP -> FirebaseSetupPage(
                                    settingsManager = settingsManager,
                                    platformContext = platformContext,
                                    configuredOrgs = firebaseConfiguredOrgs,
                                    onConfiguredOrgsChange = { firebaseConfiguredOrgs = it },
                                    projectId = firebaseProjectId,
                                    onProjectIdChange = { firebaseProjectId = it },
                                    applicationId = firebaseApplicationId,
                                    onApplicationIdChange = { firebaseApplicationId = it },
                                    apiKey = firebaseApiKey,
                                    onApiKeyChange = { firebaseApiKey = it },
                                    webClientId = firebaseWebClientId,
                                    onWebClientIdChange = { firebaseWebClientId = it },
                                    webClientSecret = firebaseWebClientSecret,
                                    onWebClientSecretChange = { firebaseWebClientSecret = it },
                                    authEmail = authEmail,
                                    onSignIn = {
                                        persistFirebaseFields()
                                        if (onRequestFirebaseSignIn != null) {
                                            onRequestFirebaseSignIn()
                                        } else {
                                            scope.launch {
                                                when (val result = com.eventmanager.app.data.remote
                                                    .createFirebaseAuthService(platformContext)
                                                    .signInWithGoogle()
                                                ) {
                                                    is com.eventmanager.app.data.remote.FirebaseAuthResult.Success -> {
                                                        authEmail = result.email
                                                        settingsManager.setFirebaseAuthEmail(result.email.orEmpty())
                                                    }
                                                    is com.eventmanager.app.data.remote.FirebaseAuthResult.Error -> {
                                                        firstSyncError = result.message
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onJoinedFromQr = {
                                        firebaseConfiguredOrgs = settingsManager.getFirebaseConfiguredOrgs()
                                        firebaseProjectId = settingsManager.getFirebaseProjectId()
                                        firebaseApplicationId = settingsManager.getFirebaseApplicationId()
                                        firebaseApiKey = settingsManager.getFirebaseApiKey()
                                        firebaseWebClientId = settingsManager.getFirebaseWebClientId()
                                        firebaseWebClientSecret = settingsManager.getFirebaseWebClientSecret()
                                    },
                                )
                                SetupStep.FIRST_SYNC -> FirstSyncPage(firstSyncRunning, firstSyncDone, firstSyncError)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupWizardHeader(
    step: SetupStep,
    stepIndex: Int,
    totalSteps: Int,
    onSkip: () -> Unit,
    showSkip: Boolean
) {
    val phaseLabel = step.phase().label()
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.setup_step_progress, stepIndex + 1, totalSteps),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            if (showSkip) {
                TextButton(onClick = onSkip) {
                    Text(
                        stringResource(Res.string.setup_skip),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (phaseLabel.isNotBlank()) {
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(phaseLabel) },
                leadingIcon = {
                    Icon(
                        when (step.phase()) {
                            SetupPhase.PERSONALIZE -> Icons.Default.Tune
                            SetupPhase.CONNECT -> Icons.Default.Link
                            SetupPhase.FINISH -> Icons.Default.CheckCircle
                            else -> Icons.Default.Info
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                border = null,
                colors = AssistChipDefaults.assistChipColors(
                    disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                step.icon(),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = step.title(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = step.description(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WelcomePage(
    settingsManager: SettingsManager,
    isDesktop: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SetupFeatureChip(Icons.Default.Language, stringResource(Res.string.setup_phase_personalize))
            SetupFeatureChip(Icons.Default.CloudSync, stringResource(Res.string.setup_phase_connect))
            SetupFeatureChip(Icons.Default.Sync, stringResource(Res.string.setup_phase_finish))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        BackgroundAnimationSettingsSection(
            settingsManager = settingsManager,
            isDesktop = isDesktop,
        )
    }
}

@Composable
private fun RowScope.SetupFeatureChip(icon: ImageVector, label: String) {
    Card(
        modifier = Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GoogleSheetsPage(
    spreadsheetId: String,
    onSpreadsheetIdChange: (String) -> Unit,
    platformContext: PlatformContext,
    jsonKeyStatus: String?,
    onJsonKeyStatus: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = spreadsheetId,
            onValueChange = onSpreadsheetIdChange,
            label = { Text(stringResource(Res.string.spreadsheet_id_label)) },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) },
            supportingText = { Text(stringResource(Res.string.setup_spreadsheet_hint)) },
            minLines = 2
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(Res.string.setup_json_key_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ServiceAccountKeyUploadButton(
            platformContext = platformContext,
            onStatusUpdate = onJsonKeyStatus,
            modifier = Modifier.fillMaxWidth()
        )
        jsonKeyStatus?.let {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            ) {
                Text(
                    it,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun LanguagePage(selectedLanguage: String, onSelect: (String) -> Unit) {
    val options = listOf(
        "en" to Res.string.language_english,
        "fr" to Res.string.language_french,
        "es" to Res.string.language_spanish,
        "zh-TW" to Res.string.language_chinese,
        "zh-CN" to Res.string.language_chinese_simplified,
        "la" to Res.string.language_latin,
        "hi" to Res.string.language_hindi,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (code, labelRes) ->
            val selected = selectedLanguage == code
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(code) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selected) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Spacer(Modifier.size(24.dp))
                    }
                    Text(
                        text = stringResource(labelRes),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
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
    tempGuestListSheet: String, onTempGuestListSheet: (String) -> Unit,
    settingsSheet: String, onSettingsSheet: (String) -> Unit
) {
    var showAdvanced by remember { mutableStateOf(false) }

    @Composable
    fun sheetField(value: String, onValue: (String) -> Unit, labelRes: org.jetbrains.compose.resources.StringResource) {
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            label = { Text(stringResource(labelRes)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(Res.string.setup_sheets_defaults_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        if (!showAdvanced) {
            OutlinedButton(onClick = { showAdvanced = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.setup_sheets_customize))
            }
        } else {
            sheetField(guestListSheet, onGuestListSheet, Res.string.guest_list_sheet_label)
            sheetField(volunteerSheet, onVolunteerSheet, Res.string.volunteer_sheet_label)
            sheetField(jobsSheet, onJobsSheet, Res.string.shifts_sheet_label)
            sheetField(volunteerGuestListSheet, onVolunteerGuestListSheet, Res.string.volunteer_guest_list_sheet_label)
            sheetField(jobTypesSheet, onJobTypesSheet, Res.string.shift_types_sheet_label)
            sheetField(venuesSheet, onVenuesSheet, Res.string.venues_sheet_label)
            sheetField(salesItemsSheet, onSalesItemsSheet, Res.string.sales_items_sheet_label)
            sheetField(tempGuestListSheet, onTempGuestListSheet, Res.string.temp_guest_list_sheet_label)
            sheetField(settingsSheet, onSettingsSheet, Res.string.settings_sheet_label)
        }
    }
}

@Composable
private fun ChooseBackendPage(
    selected: com.eventmanager.app.data.remote.BackendType,
    onSelect: (com.eventmanager.app.data.remote.BackendType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.setup_choose_backend_description))
        listOf(
            com.eventmanager.app.data.remote.BackendType.SHEETS to Res.string.setup_backend_sheets,
            com.eventmanager.app.data.remote.BackendType.FIREBASE to Res.string.setup_backend_firebase,
        ).forEach { (type, label) ->
            val selectedBorder = if (selected == type) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            OutlinedCard(
                onClick = { onSelect(type) },
                border = BorderStroke(if (selected == type) 2.dp else 1.dp, selectedBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(label),
                    modifier = Modifier.padding(16.dp),
                    fontWeight = if (selected == type) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun FirebaseSetupPage(
    settingsManager: SettingsManager,
    platformContext: PlatformContext,
    configuredOrgs: List<com.eventmanager.app.data.remote.FirebaseConfiguredOrg>,
    onConfiguredOrgsChange: (List<com.eventmanager.app.data.remote.FirebaseConfiguredOrg>) -> Unit,
    projectId: String,
    onProjectIdChange: (String) -> Unit,
    applicationId: String,
    onApplicationIdChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    webClientId: String,
    onWebClientIdChange: (String) -> Unit,
    webClientSecret: String,
    onWebClientSecretChange: (String) -> Unit,
    authEmail: String?,
    onSignIn: () -> Unit,
    onJoinedFromQr: () -> Unit,
) {
    var mode by remember {
        mutableStateOf(
            if (projectId.isNotBlank() && applicationId.isNotBlank() && apiKey.isNotBlank()) {
                "joined"
            } else {
                "choose"
            },
        )
    }
    var showScan by remember { mutableStateOf(false) }
    val activeOrgId = settingsManager.getFirebaseOrgId().ifBlank {
        configuredOrgs.firstOrNull { it.orgId.isNotBlank() }?.orgId.orEmpty()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.setup_firebase_description))

        when (mode) {
            "choose" -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { mode = "join" },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(Res.string.firebase_join_wizard_option),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(Res.string.firebase_join_wizard_option_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { mode = "admin" },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(Res.string.firebase_create_new_option),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            "join" -> {
                com.eventmanager.app.ui.components.FirebaseJoinImportSection(
                    settingsManager = settingsManager,
                    onJoined = {
                        onJoinedFromQr()
                        mode = "joined"
                    },
                    onRequestScan = { showScan = true },
                )
                TextButton(onClick = { mode = "choose" }) {
                    Text(stringResource(Res.string.back))
                }
            }
            "joined" -> {
                com.eventmanager.app.ui.components.FirebaseConfigReceivedBanner(orgId = activeOrgId)
                com.eventmanager.app.ui.components.FirebaseConnectionFields(
                    configuredOrgs = configuredOrgs,
                    onConfiguredOrgsChange = onConfiguredOrgsChange,
                    projectId = projectId,
                    onProjectIdChange = onProjectIdChange,
                    applicationId = applicationId,
                    onApplicationIdChange = onApplicationIdChange,
                    apiKey = apiKey,
                    onApiKeyChange = onApiKeyChange,
                    webClientId = webClientId,
                    onWebClientIdChange = onWebClientIdChange,
                    webClientSecret = webClientSecret,
                    onWebClientSecretChange = onWebClientSecretChange,
                    authEmail = authEmail,
                    onSignIn = onSignIn,
                    hideProjectSecrets = true,
                    orgIdReadOnly = activeOrgId.isNotBlank(),
                )
            }
            else -> {
                com.eventmanager.app.ui.components.FirebaseAdminConfigAndJoinQrSection(
                    settingsManager = settingsManager,
                    orgId = activeOrgId,
                    projectId = projectId,
                    applicationId = applicationId,
                    apiKey = apiKey,
                    webClientId = webClientId,
                    webClientSecret = webClientSecret,
                    onOrgIdChange = { newId ->
                        val updated = configuredOrgs.toMutableList()
                        if (updated.isEmpty()) {
                            onConfiguredOrgsChange(
                                listOf(
                                    com.eventmanager.app.data.remote.FirebaseConfiguredOrg(
                                        orgId = newId,
                                        colorArgb = com.eventmanager.app.data.remote.FirebaseConfiguredOrgCodec.defaultColorForIndex(0),
                                    ),
                                ),
                            )
                        } else {
                            updated[0] = updated[0].copy(orgId = newId)
                            onConfiguredOrgsChange(updated)
                        }
                    },
                    onProjectIdChange = onProjectIdChange,
                    onApplicationIdChange = onApplicationIdChange,
                    onApiKeyChange = onApiKeyChange,
                    onWebClientIdChange = onWebClientIdChange,
                    onWebClientSecretChange = onWebClientSecretChange,
                )
                com.eventmanager.app.ui.components.FirebaseConnectionFields(
                    configuredOrgs = configuredOrgs,
                    onConfiguredOrgsChange = onConfiguredOrgsChange,
                    projectId = projectId,
                    onProjectIdChange = onProjectIdChange,
                    applicationId = applicationId,
                    onApplicationIdChange = onApplicationIdChange,
                    apiKey = apiKey,
                    onApiKeyChange = onApiKeyChange,
                    webClientId = webClientId,
                    onWebClientIdChange = onWebClientIdChange,
                    webClientSecret = webClientSecret,
                    onWebClientSecretChange = onWebClientSecretChange,
                    authEmail = authEmail,
                    onSignIn = onSignIn,
                    hideProjectSecrets = true,
                )
                TextButton(onClick = { mode = "choose" }) {
                    Text(stringResource(Res.string.back))
                }
            }
        }

        if (mode != "choose") {
            Text(
                stringResource(Res.string.setup_firebase_auth_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (showScan) {
        com.eventmanager.app.ui.components.RawPayloadQrScannerDialog(
            platformContext = platformContext,
            onDismiss = { showScan = false },
            onPayload = { raw ->
                com.eventmanager.app.data.remote.FirebaseJoinCodec.decode(raw).onSuccess { payload ->
                    settingsManager.applyFirebaseJoinPayload(payload)
                    onJoinedFromQr()
                    mode = "joined"
                }
                showScan = false
            },
        )
    }
}

@Composable
private fun FirstSyncPage(running: Boolean, done: Boolean, error: String?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        when {
            running -> {
                CircularProgressIndicator(modifier = Modifier.size(56.dp), strokeWidth = 4.dp)
                Text(
                    stringResource(Res.string.setup_wizard_first_sync_message),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            done -> {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(Res.string.setup_finish),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(Res.string.setup_wizard_first_sync_message),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            error != null -> {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    stringResource(Res.string.setup_wizard_first_sync_error_title),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Text(
                    error,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            else -> {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    stringResource(Res.string.setup_first_sync_ready),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
