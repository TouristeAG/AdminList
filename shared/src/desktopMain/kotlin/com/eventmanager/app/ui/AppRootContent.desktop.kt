package com.eventmanager.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.data.repository.EventManagerRepository
import com.eventmanager.app.data.sync.GoogleSheetsService
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.createAppStorage
import com.eventmanager.app.platform.createDatabase
import com.eventmanager.app.platform.hardware.DesktopPcscCardReader
import com.eventmanager.app.ui.components.QRScannerDialog
import com.eventmanager.app.ui.components.SearchBar
import com.eventmanager.app.ui.components.StatsGraphsPanel
import com.eventmanager.app.ui.desktop.DesktopNavigationHooks
import com.eventmanager.app.ui.navigation.AdminTab
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun AppRootContent(
    platformContext: PlatformContext,
    onThemeModeChanged: (String) -> Unit
) {
    val settingsManager = remember(platformContext) { SettingsManager(createAppStorage(platformContext)) }
    var showWelcome by rememberSaveable { mutableStateOf(true) }
    var showSetupWizard by rememberSaveable { mutableStateOf(settingsManager.shouldShowSetupWizard()) }
    var showAdmin by rememberSaveable { mutableStateOf(false) }
    var showBilleterie by rememberSaveable { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(AdminTab.Dashboard.index) }
    var databaseReady by remember { mutableStateOf(false) }
    var guestSearch by rememberSaveable { mutableStateOf("") }
    var volunteerSearch by rememberSaveable { mutableStateOf("") }
    var lastAdminInteraction by remember { mutableStateOf(System.currentTimeMillis()) }
    var searchFocusTick by remember { mutableIntStateOf(0) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    fun touchAdminSession() {
        lastAdminInteraction = System.currentTimeMillis()
    }

    LaunchedEffect(platformContext) {
        withContext(Dispatchers.IO) { createDatabase(platformContext) }
        databaseReady = true
    }

    LaunchedEffect(showAdmin, lastAdminInteraction) {
        if (!showAdmin) return@LaunchedEffect
        while (showAdmin) {
            delay(30_000)
            if (System.currentTimeMillis() - lastAdminInteraction > ADMIN_SESSION_IDLE_TIMEOUT_MS) {
                showAdmin = false
                showWelcome = true
            }
        }
    }

    if (!databaseReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
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

    val guests by viewModel.guests.collectAsState()
    val volunteers by viewModel.volunteers.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val jobTypeConfigs by viewModel.jobTypeConfigs.collectAsState()
    val venues by viewModel.venues.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncStatusMessage by viewModel.syncStatusMessage.collectAsState()
    val showSyncStatusDialog by viewModel.showSyncStatusDialog.collectAsState()
    val pcscReader = remember { DesktopPcscCardReader() }

    DisposableEffect(showAdmin, selectedTab, showQrScanner, showSyncStatusDialog) {
        DesktopNavigationHooks.openSettingsTab = if (showAdmin) {
            { selectedTab = AdminTab.Settings.index; touchAdminSession() }
        } else null
        DesktopNavigationHooks.focusListSearch = if (showAdmin && selectedTab in listOf(AdminTab.Guests.index, AdminTab.Volunteers.index)) {
            { searchFocusTick++ }
        } else null
        DesktopNavigationHooks.dismissOverlay = {
            if (showQrScanner) showQrScanner = false
            if (showSyncStatusDialog) viewModel.dismissSyncStatusDialog()
        }
        onDispose {
            DesktopNavigationHooks.openSettingsTab = null
            DesktopNavigationHooks.focusListSearch = null
            DesktopNavigationHooks.dismissOverlay = null
        }
    }

    LaunchedEffect(showAdmin) {
        if (!showAdmin) return@LaunchedEffect
        val checker = com.eventmanager.app.data.update.UpdateChecker(platformContext)
        when (val result = checker.checkForUpdates()) {
            is com.eventmanager.app.data.update.UpdateCheckResult.UpdateAvailable -> {
                updateMessage = "Update ${result.manifest.latestVersionName} available"
                showUpdateDialog = true
            }
            else -> Unit
        }
    }

    when {
        showSetupWizard -> {
            DesktopSetupWizard(
                settingsManager = settingsManager,
                onComplete = {
                    settingsManager.setSetupWizardCompleted(true)
                    showSetupWizard = false
                    showWelcome = true
                }
            )
        }
        showWelcome -> {
            DesktopWelcomeScreen(
                onAdmin = { showWelcome = false; showAdmin = true; touchAdminSession() },
                onBilleterie = { showWelcome = false; showBilleterie = true }
            )
        }
        showBilleterie -> {
            DesktopBilleterieScreen(
                guests = guests,
                volunteers = volunteers,
                pcscAvailable = pcscReader.isReaderAvailable(),
                readerName = pcscReader.readerName(),
                onBack = { showBilleterie = false; showWelcome = true },
                onOpenScanner = { showQrScanner = true }
            )
        }
        showAdmin -> {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = { Text("NoctuList Admin") },
                        navigationIcon = {
                            IconButton(onClick = { showAdmin = false; showWelcome = true }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (isSyncing) {
                                CircularProgressIndicator(Modifier.size(24.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                            }
                            IconButton(onClick = { touchAdminSession(); viewModel.performFullSync() }) {
                                Icon(Icons.Default.Sync, contentDescription = "Sync")
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar {
                        AdminTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab.index,
                                onClick = { touchAdminSession(); selectedTab = tab.index },
                                icon = {
                                    Icon(
                                        when (tab) {
                                            AdminTab.Dashboard -> Icons.Default.Home
                                            AdminTab.Guests -> Icons.Default.Group
                                            AdminTab.Volunteers -> Icons.Default.Person
                                            AdminTab.Shifts -> Icons.Default.Event
                                            AdminTab.Benefits -> Icons.Default.Star
                                            AdminTab.Settings -> Icons.Default.Settings
                                        },
                                        contentDescription = tab.name
                                    )
                                },
                                label = { Text(tab.name) }
                            )
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
                    if (showSyncStatusDialog && syncStatusMessage != null) {
                        Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudSync, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(syncStatusMessage!!, Modifier.weight(1f))
                                TextButton(onClick = { viewModel.dismissSyncStatusDialog() }) { Text("OK") }
                            }
                        }
                    }
                    when (AdminTab.fromIndex(selectedTab)) {
                        AdminTab.Dashboard -> StatsGraphsPanel(
                            platformContext = platformContext,
                            guests = guests,
                            volunteers = volunteers,
                            jobs = jobs,
                            venues = venues,
                            jobTypeConfigs = jobTypeConfigs,
                            isPhone = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                        AdminTab.Guests -> DesktopGuestsTab(guests, guestSearch, searchFocusTick) { guestSearch = it }
                        AdminTab.Volunteers -> DesktopVolunteersTab(volunteers, volunteerSearch, searchFocusTick) { volunteerSearch = it }
                        AdminTab.Shifts -> Text("Shifts: ${jobs.size} jobs — sync from Google Sheets.")
                        AdminTab.Benefits -> Text("Benefits are calculated from jobs in the shared ViewModel.")
                        AdminTab.Settings -> DesktopSettingsTab(settingsManager, onThemeModeChanged)
                    }
                }
            }
        }
    }

    if (showQrScanner) {
        QRScannerDialog(
            platformContext = platformContext,
            onDismiss = { showQrScanner = false },
            onMatchFound = { showQrScanner = false },
            volunteers = volunteers,
            guests = guests
        )
    }

    if (showUpdateDialog && updateMessage != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("Update available") },
            text = { Text(updateMessage!!) },
            confirmButton = {
                TextButton(onClick = { showUpdateDialog = false }) { Text("OK") }
            }
        )
    }
}

@Composable
private fun DesktopWelcomeScreen(onAdmin: () -> Unit, onBilleterie: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("NoctuList", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onAdmin, modifier = Modifier.fillMaxWidth(0.4f)) {
            Icon(Icons.Default.Lock, null)
            Spacer(Modifier.width(8.dp))
            Text("Admin")
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onBilleterie, modifier = Modifier.fillMaxWidth(0.4f)) {
            Icon(Icons.Default.ConfirmationNumber, null)
            Spacer(Modifier.width(8.dp))
            Text("Billeterie")
        }
    }
}

@Composable
private fun DesktopSetupWizard(settingsManager: SettingsManager, onComplete: () -> Unit) {
    var spreadsheetId by remember { mutableStateOf(settingsManager.getSpreadsheetId()) }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
        Text("Setup Wizard", style = MaterialTheme.typography.headlineMedium)
        Text("Enter your Google Sheets spreadsheet ID to get started.", Modifier.padding(vertical = 16.dp))
        OutlinedTextField(
            value = spreadsheetId,
            onValueChange = { spreadsheetId = it },
            label = { Text("Spreadsheet ID") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            settingsManager.saveSpreadsheetId(spreadsheetId)
            onComplete()
        }) { Text("Continue") }
    }
}

@Composable
private fun DesktopBilleterieScreen(
    guests: List<Guest>,
    volunteers: List<Volunteer>,
    pcscAvailable: Boolean,
    readerName: String,
    onBack: () -> Unit,
    onOpenScanner: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text("Billeterie", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(16.dp))
        Text("Guests: ${guests.size} · Volunteers: ${volunteers.size}")
        Text("PC/SC reader: ${if (pcscAvailable) readerName else "Not connected"}")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenScanner) {
            Icon(Icons.Default.QrCodeScanner, null)
            Spacer(Modifier.width(8.dp))
            Text("Scan QR (webcam)")
        }
    }
}

@Composable
private fun DesktopGuestsTab(
    guests: List<Guest>,
    search: String,
    searchFocusTick: Int,
    onSearchChange: (String) -> Unit
) {
    val filtered = remember(guests, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) guests else guests.filter {
            it.name.lowercase().contains(q) || it.email.lowercase().contains(q)
        }
    }
    SearchBar(search, onSearchChange, placeholder = "Search guests...", requestFocusTrigger = searchFocusTick)
    Spacer(Modifier.height(8.dp))
    Text("Guests (${filtered.size})", style = MaterialTheme.typography.headlineSmall)
    filtered.take(100).forEach { g ->
        Text("• ${g.name} — ${g.venueName} (${g.invitations} inv.)")
    }
}

@Composable
private fun DesktopVolunteersTab(
    volunteers: List<Volunteer>,
    search: String,
    searchFocusTick: Int,
    onSearchChange: (String) -> Unit
) {
    val filtered = remember(volunteers, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) volunteers else volunteers.filter {
            it.name.lowercase().contains(q) || it.email.lowercase().contains(q)
        }
    }
    SearchBar(search, onSearchChange, placeholder = "Search volunteers...", requestFocusTrigger = searchFocusTick)
    Spacer(Modifier.height(8.dp))
    Text("Volunteers (${filtered.size})", style = MaterialTheme.typography.headlineSmall)
    filtered.take(100).forEach { v ->
        Text("• ${v.name} ${v.lastNameAbbreviation}")
    }
}

@Composable
private fun DesktopSettingsTab(settings: SettingsManager, onThemeChanged: (String) -> Unit) {
    Text("Settings", style = MaterialTheme.typography.headlineSmall)
    Text("Spreadsheet: ${settings.getSpreadsheetId()}")
    Text("Language: ${settings.getLanguage()}")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Theme: ")
        TextButton(onClick = { settings.saveThemeMode("light"); onThemeChanged("light") }) { Text("Light") }
        TextButton(onClick = { settings.saveThemeMode("dark"); onThemeChanged("dark") }) { Text("Dark") }
        TextButton(onClick = { settings.saveThemeMode("default"); onThemeChanged("default") }) { Text("System") }
    }
}
