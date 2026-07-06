package com.eventmanager.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventmanager.app.data.models.*
import com.eventmanager.app.data.repository.EventManagerRepository
import com.eventmanager.app.data.sync.GoogleSheetsService
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.data.sync.settingsManagerFor
import com.eventmanager.app.data.update.UpdateCheckResult
import com.eventmanager.app.data.sync.DateFormatUtils
import com.eventmanager.app.platform.LocalPlatformContext
import com.eventmanager.app.platform.PlatformBackHandler
import com.eventmanager.app.platform.createAppStorage
import com.eventmanager.app.platform.createDatabase
import com.eventmanager.app.platform.elapsedRealtimeMs
import com.eventmanager.app.platform.openDateSettings
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import com.eventmanager.app.ui.components.*
import com.eventmanager.app.ui.components.BackgroundAnimationStyle
import com.eventmanager.app.ui.desktop.AdminNavLayout
import com.eventmanager.app.ui.platform.AppAppearanceState
import com.eventmanager.app.ui.desktop.DesktopAdminShell
import com.eventmanager.app.ui.desktop.DesktopNavigationHooks
import com.eventmanager.app.ui.desktop.LocalDesktopNavigation
import com.eventmanager.app.ui.navigation.AdminTab
import com.eventmanager.app.ui.navigation.BilleterieSection
import com.eventmanager.app.ui.screens.*
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun AppRootContent(
    platformContext: com.eventmanager.app.platform.PlatformContext,
    onThemeModeChanged: (String) -> Unit
) {
    CompositionLocalProvider(LocalPlatformContext provides platformContext) {
        val settingsManager = remember(platformContext) { SettingsManager(createAppStorage(platformContext)) }
        val skipStartupSync = remember { settingsManager.consumeSkipNextStartupSync() }
        val uiRefreshNonce by AppAppearanceState::refreshNonce
        val backgroundAnimationStyle = uiRefreshNonce.let { settingsManager.getBackgroundAnimationStyle() }
        val backgroundAnimationOpacity = uiRefreshNonce.let { settingsManager.getBackgroundAnimationOpacity() }
        val billeterieBackgroundAnimationStyle = uiRefreshNonce.let { settingsManager.getBilleterieBackgroundAnimationStyle() }
        val billeterieBackgroundAnimationOpacity = uiRefreshNonce.let { settingsManager.getBilleterieBackgroundAnimationOpacity() }

        var adminNavLayout by remember {
            mutableStateOf(AdminNavLayout.fromString(settingsManager.getDesktopAdminNavLayout()))
        }
        var adminNavRailExpanded by remember {
            mutableStateOf(settingsManager.isDesktopAdminNavRailExpanded())
        }
        fun refreshAdminNavPreferences() {
            adminNavLayout = AdminNavLayout.fromString(settingsManager.getDesktopAdminNavLayout())
            adminNavRailExpanded = settingsManager.isDesktopAdminNavRailExpanded()
        }

        val nav = LocalDesktopNavigation.current
            ?: error("DesktopNavigationHolder must be provided above AppRootContent on desktop")

        var showWelcome by nav::showWelcome
        var showSetupWizard by nav::showSetupWizard
        var showAdminAuth by nav::showAdminAuth
        var showTicketCheck by nav::showTicketCheck
        var selectedTab by nav::selectedTab
        var previousTab by nav::previousTab
        var showJobTypeManagement by nav::showJobTypeManagement
        var showVenueManagement by nav::showVenueManagement
        var showSalesSheetItemManagement by nav::showSalesSheetItemManagement
        var showQRScanner by nav::showQRScanner
        var showVolunteerBenefits by remember { mutableStateOf<Volunteer?>(null) }
        var showScannedGuestDetail by remember { mutableStateOf<Guest?>(null) }
        var searchFocusTick by remember { mutableIntStateOf(0) }
        var lastAdminInteraction by remember { mutableLongStateOf(elapsedRealtimeMs()) }

        fun touchAdminSession() {
            lastAdminInteraction = elapsedRealtimeMs()
        }

        if (showSetupWizard) {
            SetupWizardScreen(
                platformContext = platformContext,
                onSetupComplete = {
                    settingsManager.setSetupWizardCompleted(true)
                    showSetupWizard = false
                    showWelcome = true
                    onThemeModeChanged(settingsManager.getThemeMode())
                },
                onThemeModeChanged = onThemeModeChanged
            )
            return@CompositionLocalProvider
        }

        var databaseReady by remember { mutableStateOf(false) }
        LaunchedEffect(platformContext) {
            withContext(Dispatchers.IO) { createDatabase(platformContext) }
            databaseReady = true
        }

        if (!databaseReady) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@CompositionLocalProvider
        }

        val db = remember { createDatabase(platformContext) }
        val repository = remember(db) {
            EventManagerRepository(
                db.guestDao(), db.volunteerDao(), db.jobDao(),
                db.jobTypeConfigDao(), db.venueDao(), db.salesSheetItemDao()
            )
        }
        val sheets = remember { GoogleSheetsService(platformContext) }
        val viewModel: EventManagerViewModel = viewModel {
            EventManagerViewModel(repository, sheets, platformContext)
        }

        var showAdminSetup by nav::showAdminSetup
        var adminCheckDone by nav::adminCheckDone
        var adminPrecheckComplete by remember { mutableStateOf(false) }
        var adminPrecheckSucceeded by remember { mutableStateOf(false) }

        val adminCheckGuests by viewModel.guests.collectAsState()
        val adminCheckVolunteers by viewModel.volunteers.collectAsState()

        LaunchedEffect(Unit) {
            if (skipStartupSync) {
                adminPrecheckSucceeded = true
                adminPrecheckComplete = true
                return@LaunchedEffect
            }
            try {
                val result = viewModel.performFullSyncAwait(suppressSyncErrorDialog = true)
                adminPrecheckSucceeded = result.isSuccess
                if (adminPrecheckSucceeded) delay(250)
            } catch (_: Exception) {
                adminPrecheckSucceeded = false
            }
            adminPrecheckComplete = true
        }

        LaunchedEffect(adminPrecheckComplete, adminPrecheckSucceeded, adminCheckGuests, adminCheckVolunteers) {
            if (!adminPrecheckComplete || adminCheckDone) return@LaunchedEffect
            if (!adminPrecheckSucceeded) {
                adminCheckDone = true
                return@LaunchedEffect
            }
            val hasAdmin = adminCheckGuests.any { it.isAdmin } || adminCheckVolunteers.any { it.isAdmin }
            showAdminSetup = !hasAdmin
            adminCheckDone = true
        }

        when {
            showAdminSetup -> {
                val adminSetupVenues by viewModel.venues.collectAsState()
                LaunchedEffect(Unit) {
                    if (!skipStartupSync) {
                        delay(400)
                        try { viewModel.performFullSyncAwait(suppressSyncErrorDialog = true) } catch (_: Exception) { }
                    }
                }
                AdminSetupScreen(
                    platformContext = platformContext,
                    venues = adminSetupVenues,
                    onCreateAdminGuest = { guest, cb -> viewModel.createAdminGuest(guest, cb) },
                    onCreateAdminVolunteer = { vol, cb -> viewModel.createAdminVolunteer(vol, cb) },
                    onAssignNfcUid = { adminType, entityId, uid ->
                        viewModel.assignNfcUidToAdmin(
                            isGuest = adminType == AdminType.GUEST,
                            entityId = entityId,
                            uid = uid
                        )
                    },
                    onComplete = { showAdminSetup = false },
                    onSkip = { showAdminSetup = false }
                )
            }
            showWelcome -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    AppBackgroundAnimation(
                        style = backgroundAnimationStyle,
                        opacity = backgroundAnimationOpacity,
                        settingsManager = settingsManager,
                        isDesktop = true,
                    )
                    DesktopWelcomeScreen(
                        onAdminSelected = {
                            showWelcome = false
                            showAdminAuth = true
                        },
                        onTicketCheckSelected = {
                            showWelcome = false
                            showTicketCheck = true
                        },
                        showAdminAccessSyncIndicator = !adminPrecheckComplete
                    )
                }
            }
            else -> {
                val guests by viewModel.guests.collectAsState()
                val volunteers by viewModel.volunteers.collectAsState()
                val jobs by viewModel.jobs.collectAsState()
                val jobTypeConfigs by viewModel.jobTypeConfigs.collectAsState()
                val venues by viewModel.venues.collectAsState()
                val syncError by viewModel.syncError.collectAsState()
                val showSyncErrorDialog by viewModel.showSyncErrorDialog.collectAsState()
                val isSyncing by viewModel.isSyncing.collectAsState()
                val pendingAnnouncements by viewModel.pendingAnnouncements.collectAsState()
                val showSendAnnouncementDialog by viewModel.showSendAnnouncementDialog.collectAsState()
                val isAnnouncementSending by viewModel.isAnnouncementSending.collectAsState()
                val updateCheckResult by viewModel.updateCheckState.collectAsState()

                var showDeviceTimeErrorDialog by remember { mutableStateOf(false) }
                var showUpdateDialog by remember { mutableStateOf(false) }
                var hasCheckedUpdate by remember { mutableStateOf(false) }

                val adminSurfaceActive = !showWelcome && !showAdminAuth && !showTicketCheck
                val endAdminSession by rememberUpdatedState {
                    showWelcome = true
                    showAdminAuth = false
                    showTicketCheck = false
                    selectedTab = AdminTab.Dashboard.index
                    showJobTypeManagement = false
                    showVenueManagement = false
                    showSalesSheetItemManagement = false
                }

                LaunchedEffect(adminSurfaceActive) {
                    if (!adminSurfaceActive) return@LaunchedEffect
                    while (adminSurfaceActive) {
                        delay(15_000)
                        if (elapsedRealtimeMs() - lastAdminInteraction >= ADMIN_SESSION_IDLE_TIMEOUT_MS) {
                            endAdminSession()
                            break
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    if (!hasCheckedUpdate) {
                        hasCheckedUpdate = true
                        viewModel.checkForAppUpdates()
                    }
                }
                LaunchedEffect(updateCheckResult) {
                    if (updateCheckResult is UpdateCheckResult.UpdateAvailable) showUpdateDialog = true
                }
                LaunchedEffect(Unit) {
                    if (skipStartupSync) return@LaunchedEffect
                    delay(1200)
                    withContext(Dispatchers.IO) { viewModel.performFullSync() }
                }
                LaunchedEffect(showAdminAuth) {
                    if (showAdminAuth) viewModel.prepareForAdminAuthentication()
                }
                LaunchedEffect(syncError) {
                    if (syncError != null && isDeviceTimeError(syncError)) {
                        showDeviceTimeErrorDialog = true
                    }
                }

                DisposableEffect(showAdminAuth, showTicketCheck, selectedTab, showQRScanner, showSyncErrorDialog) {
                    val inAdmin = !showAdminAuth && !showTicketCheck
                    DesktopNavigationHooks.openSettingsTab = if (inAdmin) {
                        { selectedTab = AdminTab.Settings.index; touchAdminSession() }
                    } else null
                    DesktopNavigationHooks.focusListSearch = if (inAdmin && selectedTab in listOf(AdminTab.Guests.index, AdminTab.Volunteers.index)) {
                        { searchFocusTick++ }
                    } else null
                    DesktopNavigationHooks.dismissOverlay = {
                        if (showQRScanner) showQRScanner = false
                        if (showSyncErrorDialog) viewModel.dismissSyncErrorDialog()
                        showVolunteerBenefits = null
                        showScannedGuestDetail = null
                    }
                    DesktopNavigationHooks.cycleAdminTab = if (inAdmin) {
                        { forward ->
                            showJobTypeManagement = false
                            showVenueManagement = false
                            showSalesSheetItemManagement = false
                            if (showQRScanner) showQRScanner = false
                            if (showSyncErrorDialog) viewModel.dismissSyncErrorDialog()
                            showVolunteerBenefits = null
                            showScannedGuestDetail = null
                            val tabs = AdminTab.entries
                            val currentIdx = tabs.indexOf(AdminTab.fromIndex(selectedTab))
                            val nextIdx = if (forward) {
                                (currentIdx + 1) % tabs.size
                            } else {
                                (currentIdx - 1 + tabs.size) % tabs.size
                            }
                            previousTab = selectedTab
                            selectedTab = tabs[nextIdx].index
                            touchAdminSession()
                        }
                    } else null
                    onDispose {
                        DesktopNavigationHooks.openSettingsTab = null
                        DesktopNavigationHooks.focusListSearch = null
                        DesktopNavigationHooks.dismissOverlay = null
                        DesktopNavigationHooks.cycleAdminTab = null
                    }
                }

                if (showAdminAuth) {
                    AdminAuthScreen(
                        platformContext = platformContext,
                        viewModel = viewModel,
                        volunteers = volunteers,
                        guests = guests,
                        isSyncing = isSyncing,
                        onAuthSuccess = { showAdminAuth = false },
                        onBack = {
                            showAdminAuth = false
                            showWelcome = true
                        }
                    )
                } else if (showTicketCheck) {
                    DesktopBilleterieFlow(
                        viewModel = viewModel,
                        guests = guests,
                        volunteers = volunteers,
                        jobs = jobs,
                        jobTypeConfigs = jobTypeConfigs,
                        settingsManager = settingsManager,
                        backgroundAnimationStyle = billeterieBackgroundAnimationStyle,
                        backgroundAnimationOpacity = billeterieBackgroundAnimationOpacity,
                        onExit = {
                            showTicketCheck = false
                            showWelcome = true
                        }
                    )
                } else {
                    LaunchedEffect(selectedTab) {
                        if (selectedTab == previousTab) return@LaunchedEffect
                        delay(250)
                        when (AdminTab.fromIndex(selectedTab)) {
                            AdminTab.Dashboard -> viewModel.syncGuestsWithTargetedUpdates()
                            AdminTab.Guests -> viewModel.syncGuestsWithTargetedUpdates()
                            AdminTab.Volunteers -> viewModel.syncVolunteersWithTargetedUpdates()
                            AdminTab.Shifts -> viewModel.syncJobsWithTargetedUpdates()
                            AdminTab.Benefits -> {
                                viewModel.syncJobsWithTargetedUpdates()
                                delay(50)
                                viewModel.syncVolunteersWithTargetedUpdates()
                                delay(50)
                                viewModel.syncJobTypesWithTargetedUpdates()
                            }
                            AdminTab.Settings -> {
                                viewModel.syncJobTypesWithTargetedUpdates()
                                delay(50)
                                viewModel.syncVenuesWithTargetedUpdates()
                            }
                        }
                        previousTab = selectedTab
                    }

                    DesktopAdminShell(
                        navLayout = adminNavLayout,
                        navRailExpanded = adminNavRailExpanded,
                        onNavRailExpandedChange = { expanded ->
                            adminNavRailExpanded = expanded
                            settingsManager.setDesktopAdminNavRailExpanded(expanded)
                        },
                        selectedTab = selectedTab,
                        onTabSelected = { tab -> selectedTab = tab.index },
                        onBack = { endAdminSession() },
                        onSync = { viewModel.performFullSync() },
                        isSyncing = isSyncing,
                        onTouchSession = { touchAdminSession() },
                        onClearOverlays = {
                            showJobTypeManagement = false
                            showVenueManagement = false
                            showSalesSheetItemManagement = false
                        },
                        modifier = Modifier.fillMaxSize()
                    ) { padding ->
                        Box(
                            Modifier
                                .padding(padding)
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            AppBackgroundAnimation(
                                style = backgroundAnimationStyle,
                                opacity = backgroundAnimationOpacity,
                                settingsManager = settingsManager,
                                isDesktop = true,
                            )
                            when {
                                showJobTypeManagement -> DesktopJobTypeManagement(viewModel) { showJobTypeManagement = false }
                                showVenueManagement -> DesktopVenueManagement(viewModel) { showVenueManagement = false }
                                showSalesSheetItemManagement -> DesktopSalesSheetManagement(viewModel) { showSalesSheetItemManagement = false }
                                else -> when (AdminTab.fromIndex(selectedTab)) {
                                    AdminTab.Dashboard -> DashboardScreen(
                                        guests = guests,
                                        volunteers = volunteers,
                                        jobs = jobs,
                                        venues = venues,
                                        jobTypeConfigs = jobTypeConfigs,
                                        viewModel = viewModel,
                                        isPhone = false,
                                        onLogout = {
                                            touchAdminSession()
                                            showWelcome = true
                                            showAdminAuth = false
                                            selectedTab = AdminTab.Dashboard.index
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    AdminTab.Guests -> DesktopGuestListWithViewModel(viewModel, searchFocusTick)
                                    AdminTab.Volunteers -> VolunteerScreen(
                                        volunteers = volunteers,
                                        volunteerJobs = jobs,
                                        venues = venues,
                                        jobTypeConfigs = jobTypeConfigs,
                                        onConfirmFutureEntry = { job, invites -> viewModel.markBenefitAsUsed(job, invites) },
                                        scrollBehavior = settingsManager.getScrollBehavior(),
                                        onAddVolunteer = { viewModel.addVolunteer(it) },
                                        onUpdateVolunteer = { viewModel.updateVolunteer(it) },
                                        onDeleteVolunteer = { volunteer, deleteShifts ->
                                            viewModel.deleteVolunteer(volunteer, deleteShifts)
                                        }
                                    )
                                    AdminTab.Shifts -> JobTrackingScreen(
                                        jobs = jobs,
                                        volunteers = volunteers,
                                        jobTypeConfigs = jobTypeConfigs,
                                        venues = venues,
                                        scrollBehavior = settingsManager.getScrollBehavior(),
                                        onAddJob = { viewModel.addJob(it) },
                                        onUpdateJob = { viewModel.updateJob(it) },
                                        onDeleteJob = { viewModel.deleteJob(it) }
                                    )
                                    AdminTab.Benefits -> BenefitsScreen(
                                        volunteers = volunteers,
                                        jobs = jobs,
                                        jobTypeConfigs = jobTypeConfigs,
                                        scrollBehavior = settingsManager.getScrollBehavior()
                                    )
                                    AdminTab.Settings -> SettingsScreen(
                                        viewModel = viewModel,
                                        onNavigateToJobTypeManagement = {
                                            viewModel.syncJobTypesOnly()
                                            showJobTypeManagement = true
                                        },
                                        onNavigateToVenueManagement = {
                                            viewModel.syncVenuesWithTargetedUpdates()
                                            showVenueManagement = true
                                        },
                                        onNavigateToSalesSheetItemManagement = {
                                            viewModel.syncSalesSheetItemsWithTargetedUpdates()
                                            showSalesSheetItemManagement = true
                                        },
                                        onDesktopAdminNavLayoutChanged = { refreshAdminNavPreferences() },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            FloatingActionButton(
                                onClick = { touchAdminSession(); showQRScanner = true },
                                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                            }

                            DesktopSyncPill(
                                viewModel = viewModel,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                                onClick = { touchAdminSession(); viewModel.performDifferentialFullSync() }
                            )
                        }
                    }
                }

                if (showQRScanner) {
                    QRScannerDialog(
                        platformContext = platformContext,
                        onDismiss = { showQRScanner = false },
                        onMatchFound = { match ->
                            when (match) {
                                is ScannerMatch.VolunteerMatch -> {
                                    showVolunteerBenefits = match.volunteer
                                    showScannedGuestDetail = null
                                }
                                is ScannerMatch.GuestMatch -> {
                                    showScannedGuestDetail = match.guest
                                    showVolunteerBenefits = null
                                }
                            }
                            showQRScanner = false
                        },
                        volunteers = volunteers,
                        guests = guests
                    )
                }

                showVolunteerBenefits?.let { volunteer ->
                    val offsetHours = remember { settingsManager.getDateChangeOffsetHours() }
                    val benefitStatus = remember(volunteer.id, jobs, jobTypeConfigs, offsetHours) {
                        BenefitCalculator.calculateVolunteerBenefitStatus(volunteer, jobs, jobTypeConfigs, offsetHours = offsetHours)
                    }
                    val volunteerJobs = remember(volunteer.id, jobs) { jobs.filter { it.volunteerId == volunteer.id } }
                    Dialog(onDismissRequest = { showVolunteerBenefits = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                        VolunteerBenefitsPanel(
                            modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 720.dp),
                            volunteer = volunteer,
                            volunteerBenefitStatus = benefitStatus,
                            volunteerJobs = volunteerJobs,
                            venues = venues,
                            jobTypeConfigs = jobTypeConfigs,
                            onClose = { showVolunteerBenefits = null },
                            onConfirmEntry = { job, invites -> viewModel.markBenefitAsUsed(job, invites) },
                            onAssignNfcUid = { updated, uid ->
                                viewModel.updateVolunteer(updated.copy(nfcCardUid = uid, lastModified = System.currentTimeMillis()))
                                showVolunteerBenefits = updated.copy(nfcCardUid = uid)
                            }
                        )
                    }
                }

                showScannedGuestDetail?.let { guest ->
                    Dialog(onDismissRequest = { showScannedGuestDetail = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                        GuestDetailPanel(
                            modifier = Modifier.fillMaxWidth(0.9f).heightIn(max = 720.dp),
                            guest = guest,
                            venues = venues,
                            onEdit = { updated ->
                                viewModel.updateGuest(updated)
                                showScannedGuestDetail = updated
                            },
                            onAssignNfcUid = { updatedGuest, uid ->
                                val withUid = updatedGuest.copy(nfcCardUid = uid, lastModified = System.currentTimeMillis())
                                viewModel.updateGuest(withUid)
                                showScannedGuestDetail = withUid
                            },
                            onDelete = { toDelete ->
                                viewModel.deleteGuest(toDelete)
                                showScannedGuestDetail = null
                            },
                            onClose = { showScannedGuestDetail = null }
                        )
                    }
                }

                SyncErrorDialog(
                    isVisible = showSyncErrorDialog && !showDeviceTimeErrorDialog,
                    onDismiss = { viewModel.dismissSyncErrorDialog() },
                    onRetry = { viewModel.performFullSync() },
                    errorMessage = syncError.orEmpty(),
                    onDontTellTodayChanged = { suppress ->
                        if (suppress) viewModel.setSyncErrorSuppressedToday()
                        viewModel.dismissSyncErrorDialog()
                    },
                    isSyncing = isSyncing
                )

                DeviceTimeErrorDialog(
                    isVisible = showDeviceTimeErrorDialog,
                    onDismiss = {
                        showDeviceTimeErrorDialog = false
                        viewModel.dismissSyncErrorDialog()
                    },
                    onOpenSettings = { openDateSettings(platformContext) },
                    onDontTellTodayChanged = { suppress ->
                        if (suppress) viewModel.setSyncErrorSuppressedToday()
                    }
                )

                if (showSendAnnouncementDialog) {
                    SendAnnouncementDialog(
                        venues = venues,
                        isSending = isAnnouncementSending,
                        onDismiss = { viewModel.closeSendAnnouncementDialog() },
                        onSend = { targetVenueIds, title, message ->
                            viewModel.sendAnnouncement(targetVenueIds, title, message)
                        }
                    )
                }

                pendingAnnouncements.firstOrNull()?.let { announcement ->
                    AnnouncementPopup(
                        announcement = announcement,
                        onDismiss = { viewModel.dismissCurrentAnnouncement() }
                    )
                }

                val downloadState by viewModel.updateDownloadState.collectAsState()

                AppUpdateFlowDialog(
                    visible = showUpdateDialog && updateCheckResult is UpdateCheckResult.UpdateAvailable,
                    updateResult = updateCheckResult,
                    downloadState = downloadState,
                    fallbackStoreUrl = settingsManager.getUpdateStoreUrl(),
                    onDismiss = { showUpdateDialog = false },
                    onDownload = { url -> viewModel.downloadUpdate(url) },
                    onInstall = { path ->
                        viewModel.installUpdate(path)
                        showUpdateDialog = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DesktopWelcomeScreen(
    onAdminSelected: () -> Unit,
    onTicketCheckSelected: () -> Unit,
    showAdminAccessSyncIndicator: Boolean
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        WelcomeForegroundPanel(modifier = Modifier.fillMaxWidth(0.46f)) {
            WelcomeTitleText("NoctuList")
            if (showAdminAccessSyncIndicator) {
                Spacer(Modifier.height(20.dp))
                AdminStartupSyncBanner(Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(28.dp))
            WelcomePrimaryButton(
                onClick = onAdminSelected,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.start_admin))
            }
            Spacer(Modifier.height(12.dp))
            WelcomeSecondaryButton(
                onClick = onTicketCheckSelected,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.ConfirmationNumber, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.ticket_check_mode))
            }
        }
    }
}

@Composable
private fun DesktopBilleterieFlow(
    viewModel: EventManagerViewModel,
    guests: List<Guest>,
    volunteers: List<Volunteer>,
    jobs: List<Job>,
    jobTypeConfigs: List<JobTypeConfig>,
    settingsManager: SettingsManager,
    backgroundAnimationStyle: String,
    backgroundAnimationOpacity: Float,
    onExit: () -> Unit
) {
    val nav = LocalDesktopNavigation.current
        ?: error("DesktopNavigationHolder must be provided above AppRootContent on desktop")
    var section by nav::billeterieSection
    var showBilleterieSettings by nav::showBilleterieSettings
    var scannerReturnSection by rememberSaveable { mutableStateOf(BilleterieSection.Home.name) }
    val dashboardScrollState = rememberScrollState(0)

    LaunchedEffect(Unit) {
        delay(250)
        viewModel.updateSyncInterval()
        viewModel.syncGuestsWithTargetedUpdates()
    }

    PlatformBackHandler(enabled = true) {
        when {
            showBilleterieSettings -> showBilleterieSettings = false
            section == BilleterieSection.Scanner.name -> section = scannerReturnSection
            section == BilleterieSection.GuestList.name -> section = BilleterieSection.Home.name
            else -> onExit()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AppBackgroundAnimation(
            style = backgroundAnimationStyle,
            opacity = backgroundAnimationOpacity,
            settingsManager = settingsManager,
            isDesktop = true,
        )
        when (section) {
            BilleterieSection.Home.name -> {
                if (showBilleterieSettings) {
                    BilleterieSettingsScreen(viewModel = viewModel, onBack = { showBilleterieSettings = false })
                } else {
                    BilleterieHomeScreen(
                        guests = guests,
                        repository = viewModel.repository,
                        viewModel = viewModel,
                        dashboardScrollState = dashboardScrollState,
                        onBack = onExit,
                        onOpenGuestList = { section = BilleterieSection.GuestList.name },
                        onOpenScanner = {
                            scannerReturnSection = BilleterieSection.Home.name
                            section = BilleterieSection.Scanner.name
                        },
                        onOpenSettings = { showBilleterieSettings = true }
                    )
                }
            }
            BilleterieSection.Scanner.name -> {
                BilleterieScannerScreen(
                    volunteers = volunteers,
                    guests = guests,
                    jobs = jobs,
                    jobTypeConfigs = jobTypeConfigs,
                    onBack = { section = scannerReturnSection },
                    onConfirmEntry = { job, invites -> viewModel.markBenefitAsUsed(job, invites) }
                )
            }
            else -> {
                Scaffold(
                    containerColor = if (BackgroundAnimationStyle.isEnabled(backgroundAnimationStyle)) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(Res.string.nav_guests)) },
                            navigationIcon = {
                                IconButton(onClick = { section = BilleterieSection.Home.name }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.setup_back))
                                }
                            }
                        )
                    }
                ) { padding ->
                    Box(Modifier.padding(padding).fillMaxSize()) {
                        DesktopGuestListWithViewModel(viewModel, readOnly = true)
                        FloatingActionButton(
                            onClick = {
                                scannerReturnSection = BilleterieSection.GuestList.name
                                section = BilleterieSection.Scanner.name
                            },
                            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                        ) {
                            Icon(
                                Icons.Default.QrCodeScanner,
                                contentDescription = stringResource(Res.string.billeterie_button_scanner)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopGuestListWithViewModel(
    viewModel: EventManagerViewModel,
    searchFocusTick: Int = 0,
    readOnly: Boolean = false
) {
    val guests by viewModel.guests.collectAsState()
    val volunteers by viewModel.volunteers.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val jobTypeConfigs by viewModel.jobTypeConfigs.collectAsState()
    val venues by viewModel.venues.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val settingsManager = settingsManagerFor(LocalPlatformContext.current)
    val scope = rememberCoroutineScope()

    GuestListScreen(
        guests = guests,
        volunteers = volunteers,
        jobs = jobs,
        jobTypeConfigs = jobTypeConfigs,
        venues = venues,
        isSyncing = isSyncing,
        lastSyncTime = settingsManager.getLastSyncTime(),
        scrollBehavior = settingsManager.getScrollBehavior(),
        readOnly = readOnly,
        onAddGuest = { scope.launch { viewModel.addGuest(it) } },
        onAddTemporaryGuests = { scope.launch { viewModel.addTemporaryGuestBatch(it) } },
        onUpdateGuest = { scope.launch { viewModel.updateGuest(it) } },
        onUpdateVolunteer = { scope.launch { viewModel.updateVolunteer(it) } },
        onDeleteGuest = { scope.launch { viewModel.deleteGuest(it) } },
        onRefreshTemporaryGuests = { viewModel.refreshTemporaryGuests() },
        onConfirmEntry = { job, invites -> scope.launch { viewModel.markBenefitAsUsed(job, invites) } },
        searchFocusTick = searchFocusTick
    )
}

@Composable
private fun DesktopSyncPill(viewModel: EventManagerViewModel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val platformContext = LocalPlatformContext.current
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    var syncPillTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(lastSyncTime) {
        if (lastSyncTime <= 0L) return@LaunchedEffect
        while (true) {
            delay(30_000L)
            syncPillTick++
        }
    }
    val lastUpdateLabel = remember(lastSyncTime, syncPillTick) {
        if (lastSyncTime <= 0L) {
            null
        } else {
            DateFormatUtils.formatSyncPillTimeAgo(platformContext, lastSyncTime)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !isSyncing) 0.95f else 1f,
        animationSpec = tween(100),
        label = "sync_pill_scale"
    )

    Card(
        modifier = modifier
            .padding(4.dp)
            .clickable(
                enabled = !isSyncing,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSyncing) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPressed && !isSyncing) 4.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .scale(scale),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(Res.string.syncing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = stringResource(Res.string.manual_sync_now),
                    modifier = Modifier.size(16.dp),
                    tint = if (lastSyncTime > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = if (lastSyncTime <= 0L) {
                        stringResource(Res.string.last_update_none_line)
                    } else {
                        stringResource(Res.string.last_update_line, lastUpdateLabel.orEmpty())
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (lastSyncTime > 0) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun DesktopJobTypeManagement(viewModel: EventManagerViewModel, onBack: () -> Unit) {
    val jobTypeConfigs by viewModel.jobTypeConfigs.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.syncJobTypesWithTargetedUpdates() }
    JobTypeManagementScreen(
        jobTypeConfigs = jobTypeConfigs,
        onAddJobTypeConfig = { scope.launch { viewModel.addJobTypeConfig(it) } },
        onUpdateJobTypeConfig = { scope.launch { viewModel.updateJobTypeConfig(it) } },
        onDeleteJobTypeConfig = { scope.launch { viewModel.deleteJobTypeConfig(it) } },
        onUpdateJobTypeConfigStatus = { id, active ->
            scope.launch {
                jobTypeConfigs.find { it.id == id }?.let { viewModel.updateJobTypeConfig(it.copy(isActive = active)) }
            }
        },
        onBack = onBack
    )
}

@Composable
private fun DesktopVenueManagement(viewModel: EventManagerViewModel, onBack: () -> Unit) {
    val venues by viewModel.venues.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.syncVenuesWithTargetedUpdates() }
    VenueManagementScreen(
        venues = venues,
        onAddVenue = { scope.launch { viewModel.addVenue(it) } },
        onUpdateVenue = { scope.launch { viewModel.updateVenue(it) } },
        onDeleteVenue = { scope.launch { viewModel.deleteVenue(it) } },
        onUpdateVenueStatus = { id, active ->
            scope.launch {
                venues.find { it.id == id }?.let { viewModel.updateVenue(it.copy(isActive = active)) }
            }
        },
        onBack = onBack
    )
}

@Composable
private fun DesktopSalesSheetManagement(viewModel: EventManagerViewModel, onBack: () -> Unit) {
    val items by viewModel.salesSheetItems.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.syncSalesSheetItemsWithTargetedUpdates() }
    SalesSheetItemManagementScreen(
        items = items,
        onAddItem = { scope.launch { viewModel.addSalesSheetItem(it) } },
        onUpdateItem = { scope.launch { viewModel.updateSalesSheetItem(it) } },
        onDeleteItem = { scope.launch { viewModel.deleteSalesSheetItem(it) } },
        onUpdateItemStatus = { id, active -> viewModel.updateSalesSheetItemStatus(id, active) },
        onBack = onBack
    )
}
