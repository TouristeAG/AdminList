package com.eventmanager.app.ui.desktop

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
import com.eventmanager.app.data.repository.EventManagerRepository
import com.eventmanager.app.data.sync.GoogleSheetsService
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.createAppStorage
import com.eventmanager.app.platform.createDatabase
import com.eventmanager.app.ui.theme.EventManagerTheme
import com.eventmanager.app.ui.theme.ThemeMode
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Desktop application root — shared data/sync/viewmodel with platform-native shell.
 * Full Compose screens live in androidMain; desktop uses the same VM + navigation model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopAppRootContent(
    platformContext: PlatformContext,
    onThemeModeChanged: (String) -> Unit = {}
) {
    val settingsManager = remember(platformContext) { SettingsManager(createAppStorage(platformContext)) }
    var showWelcome by rememberSaveable { mutableStateOf(true) }
    var showSetupWizard by rememberSaveable { mutableStateOf(settingsManager.shouldShowSetupWizard()) }
    var showAdmin by rememberSaveable { mutableStateOf(false) }
    var showBilleterie by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var databaseReady by remember { mutableStateOf(false) }

  LaunchedEffect(platformContext) {
        withContext(Dispatchers.IO) { createDatabase(platformContext) }
        databaseReady = true
    }

    if (!databaseReady) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
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

    when {
        showSetupWizard -> {
            SetupWizardPlaceholder(
                onComplete = {
                    settingsManager.setSetupWizardCompleted(true)
                    showSetupWizard = false
                    showWelcome = true
                }
            )
        }
        showWelcome -> {
            WelcomeScreen(
                onAdmin = { showWelcome = false; showAdmin = true },
                onBilleterie = { showWelcome = false; showBilleterie = true }
            )
        }
        showBilleterie -> {
            BilleteriePlaceholder(
                guestCount = guests.size,
                volunteerCount = volunteers.size,
                onBack = { showBilleterie = false; showWelcome = true },
                onOpenScanner = { /* QRScannerDialog via platform */ }
            )
        }
        showAdmin -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("NoctuList Admin") },
                        navigationIcon = {
                            IconButton(onClick = { showAdmin = false; showWelcome = true }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.performFullSync() }) {
                                Icon(Icons.Default.Sync, contentDescription = "Sync")
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar {
                        val tabs = listOf("Dashboard", "Guests", "Volunteers", "Shifts", "Benefits", "Settings")
                        tabs.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = {
                                    Icon(
                                        when (index) {
                                            0 -> Icons.Default.Home
                                            1 -> Icons.Default.Group
                                            2 -> Icons.Default.Person
                                            3 -> Icons.Default.Event
                                            4 -> Icons.Default.Star
                                            else -> Icons.Default.Settings
                                        },
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
                    when (selectedTab) {
                        0 -> DashboardTab(guests.size, volunteers.size, jobs.size)
                        1 -> GuestsTab(guests)
                        2 -> VolunteersTab(volunteers)
                        3 -> Text("Shifts: ${jobs.size} jobs — open Android app for full shift editor or sync from Sheets.")
                        4 -> Text("Benefits — calculated live from jobs; use Android for full benefits UI.")
                        else -> SettingsTab(settingsManager, onThemeModeChanged)
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen(onAdmin: () -> Unit, onBilleterie: () -> Unit) {
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
private fun SetupWizardPlaceholder(onComplete: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
        Text("Setup Wizard", style = MaterialTheme.typography.headlineMedium)
        Text("Configure Google Sheets in Settings → Sync, then mark setup complete.", Modifier.padding(vertical = 16.dp))
        Button(onClick = onComplete) { Text("Continue") }
    }
}

@Composable
private fun BilleteriePlaceholder(
    guestCount: Int,
    volunteerCount: Int,
    onBack: () -> Unit,
    onOpenScanner: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text("Billeterie", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(16.dp))
        Text("Guests tonight: $guestCount · Volunteers: $volunteerCount")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenScanner) {
            Icon(Icons.Default.QrCodeScanner, null)
            Spacer(Modifier.width(8.dp))
            Text("Open scanner (webcam / NFC reader)")
        }
    }
}

@Composable
private fun DashboardTab(guests: Int, volunteers: Int, jobs: Int) {
    Text("Dashboard", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text("Guests: $guests · Volunteers: $volunteers · Jobs: $jobs")
}

@Composable
private fun GuestsTab(guests: List<com.eventmanager.app.data.models.Guest>) {
    Text("Guests (${guests.size})", style = MaterialTheme.typography.headlineSmall)
    guests.take(50).forEach { g ->
        Text("• ${g.name} — ${g.venueName} (${g.invitations} inv.)")
    }
}

@Composable
private fun VolunteersTab(volunteers: List<com.eventmanager.app.data.models.Volunteer>) {
    Text("Volunteers (${volunteers.size})", style = MaterialTheme.typography.headlineSmall)
    volunteers.take(50).forEach { v ->
        Text("• ${v.name} ${v.lastNameAbbreviation}")
    }
}

@Composable
private fun SettingsTab(settings: SettingsManager, onThemeChanged: (String) -> Unit) {
    Text("Settings", style = MaterialTheme.typography.headlineSmall)
    Text("Spreadsheet: ${settings.getSpreadsheetId()}")
    Text("Language: ${settings.getLanguage()}")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Theme: ")
        TextButton(onClick = { settings.saveThemeMode("light"); onThemeChanged("light") }) { Text("Light") }
        TextButton(onClick = { settings.saveThemeMode("dark"); onThemeChanged("dark") }) { Text("Dark") }
    }
}
