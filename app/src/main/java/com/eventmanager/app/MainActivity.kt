package com.eventmanager.app

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import java.util.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import com.eventmanager.app.ui.components.QRScannerDialog
import com.eventmanager.app.ui.components.ScannerMatch
import com.eventmanager.app.ui.components.VolunteerBenefitsPanel
import com.eventmanager.app.ui.components.GuestDetailPanel
import com.eventmanager.app.ui.components.PeopleCounter
import com.eventmanager.app.ui.scaling.ResolutionScaler
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.data.models.Job
import com.eventmanager.app.data.models.JobTypeConfig
import com.eventmanager.app.data.models.VenueEntity
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import kotlin.random.Random
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eventmanager.app.data.database.EventManagerDatabase
import com.eventmanager.app.data.repository.EventManagerRepository
import com.eventmanager.app.data.sync.GoogleSheetsService
import com.eventmanager.app.ui.screens.BenefitsScreen
import com.eventmanager.app.ui.screens.GuestListScreen
import com.eventmanager.app.ui.screens.JobTrackingScreen
import com.eventmanager.app.ui.screens.JobTypeManagementScreen
import com.eventmanager.app.ui.screens.SettingsScreen
import com.eventmanager.app.ui.screens.SetupWizardScreen
import com.eventmanager.app.ui.screens.AdminAuthScreen
import com.eventmanager.app.ui.screens.BilleterieHomeScreen
import com.eventmanager.app.ui.screens.BilleterieScannerScreen
import com.eventmanager.app.ui.screens.BilleterieSettingsScreen
import com.eventmanager.app.ui.screens.VenueManagementScreen
import com.eventmanager.app.ui.screens.VolunteerScreen
import com.eventmanager.app.ui.theme.EventManagerTheme
import com.eventmanager.app.ui.theme.ThemeMode
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.ui.utils.*
import com.eventmanager.app.ui.components.AnimatedBackground
import com.eventmanager.app.ui.components.DashboardClockCard
import com.eventmanager.app.ui.components.SnowAnimation
import com.eventmanager.app.ui.components.FireworksAnimation
import com.eventmanager.app.ui.components.ValentineAnimation
import com.eventmanager.app.ui.components.WorkersDayAnimation
import com.eventmanager.app.ui.components.PrideAnimation
import com.eventmanager.app.ui.components.BeerAnimation
import com.eventmanager.app.R
import androidx.compose.ui.text.style.TextAlign
import com.eventmanager.app.ui.components.StatsGraphsPanel
import java.util.Calendar
import com.eventmanager.app.ui.components.SyncErrorDialog
import android.content.Intent
import android.provider.Settings
import com.eventmanager.app.ui.components.DeviceTimeErrorDialog
import com.eventmanager.app.ui.components.SyncStatusDialog
import com.eventmanager.app.utils.ImageUtils
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.graphics.ImageBitmap
import com.eventmanager.app.data.update.UpdateCheckResult
import com.eventmanager.app.data.update.DownloadState
import androidx.compose.runtime.LaunchedEffect
import android.net.Uri
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.SystemClock
import android.view.MotionEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Admin main UI only (not billeterie, not welcome, not NFC gate). */
private const val ADMIN_SESSION_IDLE_TIMEOUT_MS = 5 * 60 * 1000L

class MainActivity : ComponentActivity() {

    /**
     * Tracks touches while the admin surface is visible and records [ACTION_SCREEN_OFF] for
     * auto-logout on wake (see [dispatchTouchEvent] and [onResume]).
     */
    class AdminSessionWatchdog {
        @Volatile
        var monitoring: Boolean = false

        val lastInteractionElapsedMs = AtomicLong(SystemClock.elapsedRealtime())
        private val logoutAfterSleepPending = AtomicBoolean(false)

        fun onUserInput() {
            if (monitoring) lastInteractionElapsedMs.set(SystemClock.elapsedRealtime())
        }

        fun onDisplayTurnedOff() {
            if (monitoring) logoutAfterSleepPending.set(true)
        }

        fun consumeLogoutAfterSleepIfPending(): Boolean {
            if (!monitoring) {
                logoutAfterSleepPending.set(false)
                return false
            }
            return logoutAfterSleepPending.compareAndSet(true, false)
        }

        fun stopMonitoring() {
            monitoring = false
            logoutAfterSleepPending.set(false)
        }
    }

    val adminSessionWatchdog = AdminSessionWatchdog()
    /** Set from [EventManagerApp] when admin UI is active; cleared on dispose. Same module only. */
    internal var adminSessionAutoLogout: (() -> Unit)? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                adminSessionWatchdog.onDisplayTurnedOff()
            }
        }
    }

    private var screenOffReceiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply language setting
        applyLanguageSettings()
        
        // Apply resolution scaling
        applyResolutionScaling()
        
        // Initialize app icon settings
        applyAppIconSettings()
        
        // Initialize debug file logger
        val settingsManager = SettingsManager(this)
        com.eventmanager.app.data.sync.AppLogger.init(this, settingsManager)
        
        // Log app startup
        com.eventmanager.app.data.sync.AppLogger.i("MainActivity", "App started - Debug mode: ${settingsManager.getDebugMode()}")

        if (!screenOffReceiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(screenOffReceiver, filter)
            }
            screenOffReceiverRegistered = true
        }

        setContent {
            var themeModeString by remember { mutableStateOf(settingsManager.getThemeMode()) }
            EventManagerTheme(themeMode = ThemeMode.fromString(themeModeString)) {
                EventManagerApp(
                    onThemeModeChanged = { themeModeString = it }
                )
            }
        }
    }

    override fun onDestroy() {
        if (screenOffReceiverRegistered) {
            unregisterReceiver(screenOffReceiver)
            screenOffReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (adminSessionWatchdog.consumeLogoutAfterSleepIfPending()) {
            adminSessionAutoLogout?.invoke()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        val result = super.dispatchTouchEvent(ev)
        if (ev != null && adminSessionWatchdog.monitoring) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_POINTER_DOWN -> adminSessionWatchdog.onUserInput()
            }
        }
        return result
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(applyLanguageToContext(applyResolutionScalingToContext(newBase)))
    }
    
    private fun applyLanguageSettings() {
        val settingsManager = SettingsManager(this)
        val language = settingsManager.getLanguage()
        
        val locale = createLocaleFromLanguageCode(language)
        Locale.setDefault(locale)
        
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        
        resources.updateConfiguration(config, resources.displayMetrics)
    }
    
    private fun createLocaleFromLanguageCode(languageCode: String): Locale {
        return when {
            // Ensure English uses a Monday-first calendar (en-GB) instead of the default
            // Sunday-first locale. This affects components like the Material 3 DatePicker,
            // making Monday the first day of the week while keeping English text.
            languageCode.equals("en", ignoreCase = true) -> Locale("en", "GB")
            languageCode.contains("-") -> {
                val parts = languageCode.split("-")
                Locale(parts[0], parts[1])
            }
            languageCode.contains("_") -> {
                val parts = languageCode.split("_")
                Locale(parts[0], parts[1])
            }
            else -> Locale(languageCode)
        }
    }
    
    private fun applyResolutionScaling() {
        val settingsManager = SettingsManager(this)
        val resolutionScale = settingsManager.getResolutionScale()
        
        // Always apply resolution scaling, even if it's 1.0f (to reset any previous scaling)
        val originalMetrics = resources.displayMetrics
        val originalDensity = originalMetrics.density
        val originalScaledDensity = originalMetrics.scaledDensity
        
        // Modify density to achieve resolution scaling effect
        originalMetrics.density = originalDensity / resolutionScale
        originalMetrics.scaledDensity = originalScaledDensity / resolutionScale
        originalMetrics.densityDpi = (originalMetrics.density * 160).toInt()
    }
    
    private fun applyLanguageToContext(context: Context?): Context? {
        if (context == null) return null
        
        val settingsManager = SettingsManager(context)
        val language = settingsManager.getLanguage()
        
        val locale = createLocaleFromLanguageCode(language)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        
        return context.createConfigurationContext(config)
    }
    
    private fun applyResolutionScalingToContext(context: Context?): Context? {
        if (context == null) return null
        
        val settingsManager = SettingsManager(context)
        val resolutionScale = settingsManager.getResolutionScale()
        
        // Always apply resolution scaling, even if it's 1.0f (to reset any previous scaling)
        return ResolutionScaler.applyResolutionScaling(context, resolutionScale)
    }
    
    private fun applyAppIconSettings() {
        val settingsManager = SettingsManager(this)
        val appIconManager = com.eventmanager.app.data.utils.AppIconManager(this)
        
        println("\n═══════════════════════════════════════════════════")
        println("🔍 applyAppIconSettings() called on app startup")
        println("═══════════════════════════════════════════════════")
        
        // Get the currently enabled icon
        val currentlyEnabled = appIconManager.getCurrentEnabledIcon()
        println("🔍 Currently enabled icon alias: $currentlyEnabled")
        
        // Determine what icon SHOULD be enabled based on settings
        val targetIcon: String = if (settingsManager.isAppIconAutoAdapt()) {
            // Use system theme to determine icon
            val adaptedIcon = appIconManager.getSystemAdaptedIcon()
            println("🔍 Auto-adapt is ON, system icon: $adaptedIcon")
            // Save the adapted icon as the current style
            settingsManager.saveAppIconStyle(adaptedIcon)
            adaptedIcon
        } else {
            // Use the user's selected icon style
            val iconStyle = settingsManager.getAppIconStyle()
            println("🔍 Auto-adapt is OFF, using saved icon: $iconStyle")
            iconStyle
        }
        
        println("🔍 Target icon should be: $targetIcon")
        
        // Only apply if the current icon doesn't match the target
        if (currentlyEnabled != targetIcon) {
            println("⚠️ Icon mismatch detected! Current: $currentlyEnabled, Target: $targetIcon")
            println("🔄 Applying icon change to match settings...")
            appIconManager.setAppIcon(targetIcon)
            
            // Verify the change
            val afterChange = appIconManager.getCurrentEnabledIcon()
            println("🔍 After applying, enabled icon alias is: $afterChange")
            
            if (afterChange != targetIcon) {
                println("❌ WARNING: Icon change may not have taken effect!")
                println("   Expected: $targetIcon, Got: $afterChange")
            } else {
                println("✅ Icon successfully set to match settings")
            }
        } else {
            println("✅ Icon already matches settings ($targetIcon), no change needed")
        }
        
        println("═══════════════════════════════════════════════════\n")
    }
}

/**
 * Very subtle haptic feedback for page navigation
 * Uses minimal vibration duration and low amplitude for discrete feedback
 */
private fun performSubtleHaptic(vibrator: Vibrator?) {
    try {
        if (vibrator == null) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use VibrationEffect for Android 8.0+ with very short duration and low amplitude
            // Amplitude range: 1-255, using 50 for very subtle feedback (DEFAULT_AMPLITUDE is ~200)
            val effect = VibrationEffect.createOneShot(1, 90)
            vibrator.vibrate(effect)
        } else {
            // Fallback for older Android versions - very short duration
            @Suppress("DEPRECATION")
            vibrator.vibrate(1)
        }
    } catch (e: SecurityException) {
        // Vibrate permission not granted, silently ignore
    } catch (e: Exception) {
        // Any other vibration error, silently ignore
    }
}

/**
 * Strong haptic feedback for important actions like starting the app
 * Uses longer duration and higher amplitude for noticeable feedback
 */
private fun performStrongHaptic(vibrator: Vibrator?) {
    try {
        if (vibrator == null) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use VibrationEffect for Android 8.0+ with longer duration and higher amplitude
            // Duration: 15ms, Amplitude: 200 (similar to people counter feedback)
            val effect = VibrationEffect.createOneShot(15, 200)
            vibrator.vibrate(effect)
        } else {
            // Fallback for older Android versions - longer duration
            @Suppress("DEPRECATION")
            vibrator.vibrate(15)
        }
    } catch (e: SecurityException) {
        // Vibrate permission not granted, silently ignore
    } catch (e: Exception) {
        // Any other vibration error, silently ignore
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun EventManagerApp(
    onThemeModeChanged: (String) -> Unit = {}
) {
    val appContext = LocalContext.current
    val settingsManager = remember { SettingsManager(appContext) }

    // Use rememberSaveable to persist state across configuration changes
    // When Google Sheets is not configured, setup runs first; then the welcome screen on every launch.
    var showWelcome by rememberSaveable { mutableStateOf(true) }
    var showSetupWizard by rememberSaveable { mutableStateOf(settingsManager.shouldShowSetupWizard()) }
    var showAdminAuth by rememberSaveable { mutableStateOf(false) }
    var showTicketCheck by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var previousTab by rememberSaveable { mutableStateOf(0) }
    val pageAnimationsEnabled = settingsManager.isPageAnimationsEnabled()
    var showJobTypeManagement by rememberSaveable { mutableStateOf(false) }
    var showVenueManagement by rememberSaveable { mutableStateOf(false) }
    var showQRScanner by rememberSaveable { mutableStateOf(false) }
    var showVolunteerBenefits: Volunteer? by remember { mutableStateOf(null) }
    var showScannedGuestDetail: Guest? by remember { mutableStateOf(null) }
    
    // Haptic feedback for page navigation - very subtle vibration
    val vibrator = remember { appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }

    // Update check state - only show dialog if update is available
    var showUpdateDialog by remember { mutableStateOf(false) }
    var hasCheckedUpdate by remember { mutableStateOf(false) }
    
    if (showSetupWizard) {
        SetupWizardScreen(
            onSetupComplete = {
                showSetupWizard = false
                // Resolution scale is applied in attachBaseContext; recreate so layout size matches prefs.
                (appContext as? Activity)?.recreate()
            },
            onThemeModeChanged = onThemeModeChanged
        )
    } else if (showWelcome) {
        WelcomeScreen(
            onAdminSelected = {
                showWelcome = false
                showAdminAuth = true
            },
            onTicketCheckSelected = {
                showWelcome = false
                showTicketCheck = true
            }
        )
    } else {
        // Initialize database and repository
        val database = EventManagerDatabase.getDatabase(LocalContext.current)
        val repository = EventManagerRepository(
            database.guestDao(),
            database.volunteerDao(),
            database.jobDao(),
            database.jobTypeConfigDao(),
            database.venueDao(),
            database.counterDao()
        )
        val context = LocalContext.current
        val googleSheetsService = GoogleSheetsService(context)
        val viewModel: EventManagerViewModel = viewModel {
            EventManagerViewModel(repository, googleSheetsService, context)
        }
        
        // Properly collect StateFlow values to avoid null pointer exceptions on Android 7
        val guests by viewModel.guests.collectAsState()
        val volunteers by viewModel.volunteers.collectAsState()
        val jobs by viewModel.jobs.collectAsState()
        val jobTypeConfigs by viewModel.jobTypeConfigs.collectAsState()
        val venues by viewModel.venues.collectAsState()
        
        // Collect sync error state
        val syncError by viewModel.syncError.collectAsState()
        val showSyncErrorDialog by viewModel.showSyncErrorDialog.collectAsState()
        val isSyncing by viewModel.isSyncing.collectAsState()
        
        // Collect sync status state
        val syncStatusMessage by viewModel.syncStatusMessage.collectAsState()
        val showSyncStatusDialog by viewModel.showSyncStatusDialog.collectAsState()
        
        // Collect update check state
        val updateCheckResult by viewModel.updateCheckState.collectAsState()
        val updateDownloadState by viewModel.updateDownloadState.collectAsState()

        // Admin session: auto-return to welcome after idle timeout or after screen was turned off (sleep).
        val adminSurfaceActive = !showWelcome && !showAdminAuth && !showTicketCheck
        val endAdminSession by rememberUpdatedState {
            showWelcome = true
            showAdminAuth = false
            showTicketCheck = false
            selectedTab = 0
            showJobTypeManagement = false
            showVenueManagement = false
        }
        val mainActivity = context as? MainActivity
        DisposableEffect(adminSurfaceActive, mainActivity) {
            val act = mainActivity ?: return@DisposableEffect onDispose { }
            if (adminSurfaceActive) {
                act.adminSessionWatchdog.monitoring = true
                act.adminSessionWatchdog.lastInteractionElapsedMs.set(SystemClock.elapsedRealtime())
                act.adminSessionAutoLogout = { endAdminSession() }
            } else {
                act.adminSessionWatchdog.stopMonitoring()
                act.adminSessionAutoLogout = null
            }
            onDispose {
                act.adminSessionWatchdog.stopMonitoring()
                act.adminSessionAutoLogout = null
            }
        }
        LaunchedEffect(adminSurfaceActive, mainActivity) {
            if (!adminSurfaceActive || mainActivity == null) return@LaunchedEffect
            val act = mainActivity
            while (true) {
                delay(15_000L)
                val idleMs = SystemClock.elapsedRealtime() - act.adminSessionWatchdog.lastInteractionElapsedMs.get()
                if (idleMs >= ADMIN_SESSION_IDLE_TIMEOUT_MS) {
                    endAdminSession()
                    break
                }
            }
        }
        
        // State for device time error
        val showDeviceTimeErrorDialog = remember { mutableStateOf(false) }
        
        // State to track if device was sleeping when sync error occurred
        val wasDeviceSleeping = remember { mutableStateOf(false) }
        val wasInBackground = remember { mutableStateOf(false) }
        
        // Detect app lifecycle changes to track when app resumes from background/sleep
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        wasInBackground.value = true
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        // App just resumed from background/sleep
                        if (wasInBackground.value) {
                            println("📱 App resumed from background/sleep")
                            // If there's a sync error when resuming, mark that device was sleeping
                            if (syncError != null) {
                                wasDeviceSleeping.value = true
                            }
                        }
                        wasInBackground.value = false
                    }
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
        
        // Reset wasDeviceSleeping when sync error is cleared
        LaunchedEffect(syncError) {
            if (syncError == null) {
                wasDeviceSleeping.value = false
            }
        }
        
        // Trigger update check in background when app starts (after welcome screen is dismissed)
        LaunchedEffect(Unit) {
            if (!hasCheckedUpdate) {
                hasCheckedUpdate = true
                // Check for updates in background
                viewModel.checkForAppUpdates()
            }
        }
        
        // Show update dialog only if update is available
        LaunchedEffect(updateCheckResult) {
            if (updateCheckResult is UpdateCheckResult.UpdateAvailable) {
                showUpdateDialog = true
            }
        }
        
        // Detect device time errors
        LaunchedEffect(syncError) {
            if (syncError != null && com.eventmanager.app.ui.components.isDeviceTimeError(syncError)) {
                showDeviceTimeErrorDialog.value = true
            }
        }
        
        // On app launch: defer sync to allow UI to render first, preventing ANR
        // Use Dispatchers.IO to ensure sync runs on background thread
        LaunchedEffect(Unit) {
            // Delay initial sync to allow UI to render first
            kotlinx.coroutines.delay(500) // Small delay to let UI initialize
            
            // Run sync on IO dispatcher to prevent blocking main thread
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                println("App started - triggering initial download-only full sync...")
                try {
                    viewModel.performFullSync()
                } catch (e: Exception) {
                    println("❌ Sync error: ${e.message}")
                    e.printStackTrace()
                    // Error is handled by viewModel's syncError state
                }
            }
        }

        // Repair sheet headers (e.g. Admin column) and re-sync before NFC/QR admin gate
        LaunchedEffect(showAdminAuth) {
            if (showAdminAuth) {
                viewModel.prepareForAdminAuthentication()
            }
        }
        
        if (showAdminAuth) {
            AdminAuthScreen(
                volunteers = volunteers,
                guests = guests,
                isSyncing = isSyncing,
                onAuthSuccess = { showAdminAuth = false },
                onBack = {
                    showAdminAuth = false
                    showWelcome = true
                }
            )
        } else {
        if (showTicketCheck) {
            var billeterieSection by rememberSaveable { mutableStateOf("home") }
            var showBilleterieSettings by rememberSaveable { mutableStateOf(false) }
            val billeterieDashboardScrollState = rememberScrollState(0)
            val billeterieListContext = LocalContext.current

            LaunchedEffect(showTicketCheck) {
                if (showTicketCheck) {
                    kotlinx.coroutines.delay(250)
                    viewModel.syncGuestsWithTargetedUpdates()
                }
            }

            LaunchedEffect(billeterieSection) {
                if (billeterieSection != "home") {
                    showBilleterieSettings = false
                }
            }

            BackHandler {
                when {
                    showBilleterieSettings -> showBilleterieSettings = false
                    billeterieSection == "guests" || billeterieSection == "scanner" -> billeterieSection = "home"
                    else -> {
                        showTicketCheck = false
                        showWelcome = true
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                AnimatedBackground(
                    enabled = settingsManager.isAnimatedBackgroundEnabled()
                )

                when (billeterieSection) {
                    "home" -> {
                        if (showBilleterieSettings) {
                            BilleterieSettingsScreen(
                                viewModel = viewModel,
                                onBack = { showBilleterieSettings = false }
                            )
                        } else {
                            BilleterieHomeScreen(
                                guests = guests,
                                repository = viewModel.repository,
                                dashboardScrollState = billeterieDashboardScrollState,
                                onBack = {
                                    showTicketCheck = false
                                    showWelcome = true
                                },
                                onOpenGuestList = { billeterieSection = "guests" },
                                onOpenScanner = { billeterieSection = "scanner" },
                                onOpenSettings = { showBilleterieSettings = true }
                            )
                        }
                    }
                    "scanner" -> {
                        BilleterieScannerScreen(
                            volunteers = volunteers,
                            guests = guests,
                            jobs = jobs,
                            jobTypeConfigs = jobTypeConfigs,
                            onBack = { billeterieSection = "home" },
                            onConfirmEntry = { job, selectedInvites ->
                                viewModel.markBenefitAsUsed(job, selectedInvites)
                            }
                        )
                    }
                    else -> {
                        Scaffold(
                            containerColor = if (settingsManager.isAnimatedBackgroundEnabled()) {
                                Color.Transparent
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            topBar = {
                                CenterAlignedTopAppBar(
                                    title = {
                                        Text(
                                            text = billeterieListContext.getString(R.string.nav_guests),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
                                    navigationIcon = {
                                        IconButton(onClick = { billeterieSection = "home" }) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowBack,
                                                contentDescription = billeterieListContext.getString(R.string.setup_back)
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                        containerColor = Color.Transparent,
                                        scrolledContainerColor = Color.Transparent,
                                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                GuestListScreenWithViewModel(viewModel, readOnly = true)
                            }
                        }
                    }
                }

                SyncStatusWidget(
                    viewModel = viewModel,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 8.dp, end = 8.dp)
                )
            }
        } else {
        // Defer sync operations on tab switch to allow instant UI response
        // OPTIMIZED: Syncs are triggered 250ms after tab change to ensure animation completes first
        LaunchedEffect(selectedTab) {
            // Skip sync on initial load (handled by initial sync above)
            if (selectedTab == previousTab) return@LaunchedEffect
            
            // Delay sync until after animation completes (180ms animation + buffer)
            // This prevents sync-related state updates from causing jank during transition
            kotlinx.coroutines.delay(250)
            
            println("Tab changed from $previousTab to $selectedTab - triggering deferred targeted sync")
            when (selectedTab) {
                0 -> viewModel.syncGuestsWithTargetedUpdates() // Enter Dashboard
                1 -> viewModel.syncGuestsWithTargetedUpdates() // Enter Guest List
                2 -> viewModel.syncVolunteersWithTargetedUpdates() // Enter Volunteers
                3 -> viewModel.syncJobsWithTargetedUpdates() // Enter Jobs
                4 -> {
                    // Benefits screen: stagger syncs slightly to reduce concurrent load
                    viewModel.syncJobsWithTargetedUpdates()
                    kotlinx.coroutines.delay(50)
                    viewModel.syncVolunteersWithTargetedUpdates()
                    kotlinx.coroutines.delay(50)
                    viewModel.syncJobTypesWithTargetedUpdates()
                } // Enter Benefits
            }
        }

        Scaffold(
                bottomBar = {
                    // Modern bottom navigation bar with horizontal scrolling on phones
                    if (!isTablet()) {
                        // Horizontal scrolling navigation for phones
                        val scrollState = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollState)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val context = LocalContext.current
                            val tabs = listOf(
                                context.getString(R.string.nav_dashboard) to Icons.Default.Home,
                                context.getString(R.string.nav_guests) to Icons.Default.Person,
                                context.getString(R.string.nav_volunteers) to Icons.Default.Group,
                                context.getString(R.string.nav_shifts) to Icons.Default.Build,
                                context.getString(R.string.nav_benefits) to Icons.Default.Star,
                                context.getString(R.string.nav_settings) to Icons.Default.Settings
                            )
                            
                            tabs.forEachIndexed { index, (title, icon) ->
                                Card(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .clickable {
                                            if (selectedTab != index) {
                                                // Close any open settings dialogs when switching tabs
                                                showJobTypeManagement = false
                                                showVenueManagement = false
                                                // Very subtle haptic feedback for page change
                                                performSubtleHaptic(vibrator)
                                            }
                                            previousTab = selectedTab
                                            selectedTab = index
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedTab == index)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    elevation = CardDefaults.cardElevation(
                                        defaultElevation = if (selectedTab == index) 4.dp else 1.dp
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        val scale by animateFloatAsState(
                                            targetValue = if (selectedTab == index && pageAnimationsEnabled) 1.1f else 1.0f,
                                            animationSpec = if (pageAnimationsEnabled) spring(stiffness = Spring.StiffnessMedium) else spring(stiffness = Spring.StiffnessHigh),
                                            label = "bottom_icon_scale"
                                        )
                                        Icon(
                                            icon,
                                            contentDescription = title,
                                            modifier = Modifier.size(20.dp).graphicsLayer(scaleX = scale, scaleY = scale),
                                            tint = if (selectedTab == index) 
                                                MaterialTheme.colorScheme.onPrimaryContainer 
                                            else 
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = if (selectedTab == index) 
                                                MaterialTheme.colorScheme.onPrimaryContainer 
                                            else 
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Regular navigation bar for tablets
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .height(62.dp)
                        ) {
                            val context = LocalContext.current
                            val tabs = listOf(
                                context.getString(R.string.nav_dashboard) to Icons.Default.Home,
                                context.getString(R.string.nav_guests) to Icons.Default.Person,
                                context.getString(R.string.nav_volunteers) to Icons.Default.Group,
                                context.getString(R.string.nav_shifts) to Icons.Default.Build,
                                context.getString(R.string.nav_benefits) to Icons.Default.Star,
                                context.getString(R.string.nav_settings) to Icons.Default.Settings
                            )
                            
                            tabs.forEachIndexed { index, (title, icon) ->
                                NavigationBarItem(
                                    selected = selectedTab == index,
                                    onClick = {
                                        if (selectedTab != index) {
                                            // Close any open settings dialogs when switching tabs
                                            showJobTypeManagement = false
                                            showVenueManagement = false
                                            // Very subtle haptic feedback for page change
                                            performSubtleHaptic(vibrator)
                                        }
                                        previousTab = selectedTab
                                        selectedTab = index
                                    },
                                    icon = {
                                        val scale by animateFloatAsState(
                                            targetValue = if (selectedTab == index && pageAnimationsEnabled) 1.1f else 1.0f,
                                            animationSpec = if (pageAnimationsEnabled) spring(stiffness = Spring.StiffnessMedium) else spring(stiffness = Spring.StiffnessHigh),
                                            label = "bottom_icon_scale_tablet"
                                        )
                                        Icon(
                                            icon,
                                            contentDescription = title,
                                            modifier = Modifier.size(20.dp).graphicsLayer(scaleX = scale, scaleY = scale)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                // Main content with modern padding, sync widget, and swipe gestures
                val isPhone = !isTablet()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(
                            horizontal = if (isPhone) 8.dp else 16.dp, 
                            vertical = if (isPhone) 4.dp else 8.dp
                        )
                ) {
                    // Root admin: system back would otherwise finish the activity; return to welcome instead
                    // (same as dashboard logout). Job/venue management screens register their own BackHandler
                    // after this and take precedence; open dialogs compose later and dismiss first.
                    BackHandler {
                        showWelcome = true
                        showAdminAuth = false
                        showTicketCheck = false
                        selectedTab = 0
                        showJobTypeManagement = false
                        showVenueManagement = false
                    }
                    // Animated background
                    AnimatedBackground(
                        enabled = settingsManager.isAnimatedBackgroundEnabled()
                    )
                    
                    // Snow Animation (December 22-25)
                    SnowAnimation(
                        enabled = settingsManager.isSeasonalFunEnabled()
                    )
                    
                    // Fireworks Animation (December 31 - January 1)
                    FireworksAnimation(
                        enabled = settingsManager.isSeasonalFunEnabled()
                    )
                    
                    // Valentine's Day Animation (February 14)
                    ValentineAnimation(
                        enabled = settingsManager.isSeasonalFunEnabled()
                    )
                    
                    // Workers' Day Animation (May 1)
                    WorkersDayAnimation(
                        enabled = settingsManager.isSeasonalFunEnabled()
                    )
                    
                    // Pride Animation (June 27)
                    PrideAnimation(
                        enabled = settingsManager.isSeasonalFunEnabled()
                    )
                    
                    // Pride Day Themed Square Overlay (June 28)
                    run {
                        val calendar = java.util.Calendar.getInstance()
                        val month = calendar.get(java.util.Calendar.MONTH)
                        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
                        val isPrideDay = month == java.util.Calendar.JUNE && day == 28 && settingsManager.isSeasonalFunEnabled()
                        
                        if (isPrideDay) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(3.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            )
                        }
                    }
                    
                    // Main content
                    // Capture vibrator for use in suspend context
                    val capturedVibrator = vibrator
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                        var startX = 0f
                        var hasSwiped = false
                        detectDragGestures(
                            onDragStart = { offset ->
                                startX = offset.x
                                hasSwiped = false
                            },
                            onDragEnd = { 
                                // Reset startX after gesture completes
                                startX = 0f
                                hasSwiped = false
                            },
                            onDrag = { change, _ ->
                                val deltaX = change.position.x - startX
                                val threshold = 100f
                                
                                if (!hasSwiped) {
                                    when {
                                        deltaX > threshold -> {
                                        // Swipe right - go to previous tab
                                        if (selectedTab > 0) {
                                                // Close any open settings dialogs when swiping to a different tab
                                                showJobTypeManagement = false
                                                showVenueManagement = false
                                                // Very subtle haptic feedback for page change
                                                performSubtleHaptic(capturedVibrator)
                                                previousTab = selectedTab
                                                selectedTab = selectedTab - 1
                                                hasSwiped = true
                                        }
                                        }
                                        deltaX < -threshold -> {
                                        // Swipe left - go to next tab
                                        if (selectedTab < 5) {
                                                // Close any open settings dialogs when swiping to a different tab
                                                showJobTypeManagement = false
                                                showVenueManagement = false
                                                // Very subtle haptic feedback for page change
                                                performSubtleHaptic(capturedVibrator)
                                                previousTab = selectedTab
                                                selectedTab = selectedTab + 1
                                                hasSwiped = true
                                        }
                                        }
                                    }
                                }
                            }
                        )
                    }
            ) {
                // Tab Content with optimized switching
                // Use key() to maintain screen identity and prevent unnecessary recreation
                val goingLeft = selectedTab > previousTab
                
                // Create a composite screen state for animations
                // Format: "tab:{tabIndex}" for main tabs, "management:jobtype" or "management:venue" for management screens
                val currentScreenState = when {
                    showJobTypeManagement -> "management:jobtype"
                    showVenueManagement -> "management:venue"
                    else -> "tab:$selectedTab"
                }
                
if (pageAnimationsEnabled) {
                    AnimatedContent(
                        targetState = currentScreenState,
                        transitionSpec = {
                            // OPTIMIZED: Shorter duration (180ms) feels snappier while remaining smooth
                            // Fade starts faster (120ms) so content is visible sooner
                            // Exit offset reduced to minimize visual complexity
                            val slideDuration = 180
                            val fadeDuration = 120
                            when {
                                // Opening management screen - slide in from right
                                targetState.startsWith("management:") && initialState.startsWith("tab:") -> {
                                    val enter = slideInHorizontally(
                                        animationSpec = tween(durationMillis = slideDuration, easing = FastOutSlowInEasing),
                                        initialOffsetX = { fullWidth -> fullWidth }
                                    ) + fadeIn(animationSpec = tween(durationMillis = fadeDuration))
                                    val exit = slideOutHorizontally(
                                        animationSpec = tween(durationMillis = slideDuration, easing = FastOutSlowInEasing),
                                        targetOffsetX = { fullWidth -> -fullWidth / 4 }
                                    ) + fadeOut(animationSpec = tween(durationMillis = fadeDuration))
                                    (enter togetherWith exit).using(SizeTransform(clip = false))
                                }
                                // Closing management screen - slide out to right
                                targetState.startsWith("tab:") && initialState.startsWith("management:") -> {
                                    val enter = slideInHorizontally(
                                        animationSpec = tween(durationMillis = slideDuration, easing = FastOutSlowInEasing),
                                        initialOffsetX = { fullWidth -> -fullWidth / 4 }
                                    ) + fadeIn(animationSpec = tween(durationMillis = fadeDuration))
                                    val exit = slideOutHorizontally(
                                        animationSpec = tween(durationMillis = slideDuration, easing = FastOutSlowInEasing),
                                        targetOffsetX = { fullWidth -> fullWidth }
                                    ) + fadeOut(animationSpec = tween(durationMillis = fadeDuration))
                                    (enter togetherWith exit).using(SizeTransform(clip = false))
                                }
                                // Regular tab transitions - optimized for smoothness
                                else -> {
                                    val enter = slideInHorizontally(
                                        animationSpec = tween(durationMillis = slideDuration, easing = FastOutSlowInEasing),
                                        initialOffsetX = { fullWidth -> if (goingLeft) fullWidth else -fullWidth }
                                    ) + fadeIn(animationSpec = tween(durationMillis = fadeDuration))
                                    val exit = slideOutHorizontally(
                                        animationSpec = tween(durationMillis = slideDuration, easing = FastOutSlowInEasing),
                                        targetOffsetX = { fullWidth -> if (goingLeft) -fullWidth / 4 else fullWidth / 4 }
                                    ) + fadeOut(animationSpec = tween(durationMillis = fadeDuration))
                                    (enter togetherWith exit).using(SizeTransform(clip = false))
                                }
                            }
                        },
                        label = "page_transition"
                    ) { screenState: String ->
                        // Use key() to maintain screen identity across recompositions
                        when {
                            screenState == "management:jobtype" -> key("job_type_management") {
                                JobTypeManagementScreenWithViewModel(viewModel) {
                                    println("Exiting Job Type Management - triggering job types sync")
                                    viewModel.syncJobTypesOnly()
                                    showJobTypeManagement = false
                                }
                            }
                            screenState == "management:venue" -> key("venue_management") {
                                VenueManagementScreenWithViewModel(viewModel) {
                                    println("Exiting Venue Management")
                                    showVenueManagement = false
                                }
                            }
                            screenState == "tab:0" -> key("dashboard") {
                                DashboardScreenWithViewModel(
                                    viewModel = viewModel,
                                    onLogout = {
                                        showWelcome = true
                                        showAdminAuth = false
                                        showTicketCheck = false
                                        selectedTab = 0
                                    }
                                )
                            }
                            screenState == "tab:1" -> key("guest_list") { GuestListScreenWithViewModel(viewModel) }
                            screenState == "tab:2" -> key("volunteers") { VolunteerScreenWithViewModel(viewModel) }
                            screenState == "tab:3" -> key("jobs") { JobTrackingScreenWithViewModel(viewModel) }
                            screenState == "tab:4" -> key("benefits") { BenefitsScreenWithViewModel(viewModel) }
                            screenState == "tab:5" -> key("settings") {
                                SettingsScreen(
                                    viewModel = viewModel,
                                    onNavigateToJobTypeManagement = { 
                                        println("Navigating to Job Type Management - triggering job types sync")
                                        viewModel.syncJobTypesOnly()
                                        showJobTypeManagement = true 
                                    },
                                    onNavigateToVenueManagement = { 
                                        println("Navigating to Venue Management")
                                        showVenueManagement = true 
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Without animations, use key() for state preservation
                    when {
                        showJobTypeManagement -> key("job_type_management") {
                            JobTypeManagementScreenWithViewModel(viewModel) {
                                println("Exiting Job Type Management - triggering job types sync")
                                viewModel.syncJobTypesOnly()
                                showJobTypeManagement = false
                            }
                        }
                        showVenueManagement -> key("venue_management") {
                            VenueManagementScreenWithViewModel(viewModel) {
                                println("Exiting Venue Management")
                                showVenueManagement = false
                            }
                        }
                        selectedTab == 0 -> key("dashboard") {
                            DashboardScreenWithViewModel(
                                viewModel = viewModel,
                                onLogout = {
                                    showWelcome = true
                                    showAdminAuth = false
                                    showTicketCheck = false
                                    selectedTab = 0
                                }
                            )
                        }
                        selectedTab == 1 -> key("guest_list") { GuestListScreenWithViewModel(viewModel) }
                        selectedTab == 2 -> key("volunteers") { VolunteerScreenWithViewModel(viewModel) }
                        selectedTab == 3 -> key("jobs") { JobTrackingScreenWithViewModel(viewModel) }
                        selectedTab == 4 -> key("benefits") { BenefitsScreenWithViewModel(viewModel) }
                        selectedTab == 5 -> key("settings") {
                            SettingsScreen(
                                viewModel = viewModel,
                                onNavigateToJobTypeManagement = { 
                                    println("Navigating to Job Type Management - triggering job types sync")
                                    viewModel.syncJobTypesOnly()
                                    showJobTypeManagement = true 
                                },
                                onNavigateToVenueManagement = { 
                                    println("Navigating to Venue Management")
                                    showVenueManagement = true 
                                }
                            )
                        }
                    }
                }
                
                    // QR Scanner button in bottom left corner above navigation bar
                    QRScannerButton(
                        onClick = { showQRScanner = true },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 8.dp, start = 8.dp)
                    )
                    
                    // Sync status widget in bottom right corner above navigation bar
                    SyncStatusWidget(
                        viewModel = viewModel,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 8.dp, end = 8.dp)
                    )
                }
            }
        }
        }
            
        // QR Scanner Dialog
        if (showQRScanner) {
            QRScannerDialog(
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
        
        // Volunteer Benefits Panel
        showVolunteerBenefits?.let { volunteer ->
            // Memoize to prevent unnecessary recompositions
            val settingsManager = remember { SettingsManager(appContext) }
            val offsetHours = remember { settingsManager.getDateChangeOffsetHours() }
            val memoizedBenefitStatus = remember(volunteer.id, jobs, jobTypeConfigs, offsetHours) {
                com.eventmanager.app.data.models.BenefitCalculator.calculateVolunteerBenefitStatus(
                    volunteer = volunteer,
                    jobs = jobs,
                    jobTypeConfigs = jobTypeConfigs,
                    offsetHours = offsetHours
                )
            }
            val memoizedVolunteerJobs = remember(volunteer.id, jobs) {
                jobs.filter { it.volunteerId == volunteer.id }
            }

            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showVolunteerBenefits = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                VolunteerBenefitsPanel(
                    volunteer = volunteer,
                    volunteerBenefitStatus = memoizedBenefitStatus,
                    volunteerJobs = memoizedVolunteerJobs,
                    venues = venues,
                    jobTypeConfigs = jobTypeConfigs,
                    onClose = { showVolunteerBenefits = null },
                    onConfirmEntry = { job, selectedInvites -> viewModel.markBenefitAsUsed(job, selectedInvites) },
                    onAssignNfcUid = { updatedVolunteer, uid ->
                        viewModel.updateVolunteer(
                            updatedVolunteer.copy(
                                nfcCardUid = uid,
                                lastModified = System.currentTimeMillis()
                            )
                        )
                        showVolunteerBenefits = updatedVolunteer.copy(nfcCardUid = uid)
                    }
                )
            }
        }

        showScannedGuestDetail?.let { guest ->
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showScannedGuestDetail = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                GuestDetailPanel(
                    guest = guest,
                    venues = venues,
                    onEdit = { updated ->
                        viewModel.updateGuest(updated)
                        showScannedGuestDetail = updated
                    },
                    onAssignNfcUid = { updatedGuest, uid ->
                        val withUid = updatedGuest.copy(
                            nfcCardUid = uid,
                            lastModified = System.currentTimeMillis()
                        )
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
        
        // Sync Error Dialog
        SyncErrorDialog(
            isVisible = showSyncErrorDialog && !showDeviceTimeErrorDialog.value,
            onDismiss = { viewModel.dismissSyncErrorDialog() },
            onRetry = { viewModel.performFullSync() },
            errorMessage = syncError ?: "",
            onDontTellTodayChanged = { suppress ->
                if (suppress) {
                    viewModel.setSyncErrorSuppressedToday()
                }
                viewModel.dismissSyncErrorDialog()
            },
            isSyncing = isSyncing,
            animationsEnabled = pageAnimationsEnabled,
            wasDeviceSleeping = wasDeviceSleeping.value
        )
        
        // Device Time Error Dialog
        DeviceTimeErrorDialog(
            isVisible = showDeviceTimeErrorDialog.value,
            onDismiss = {
                showDeviceTimeErrorDialog.value = false
                viewModel.dismissSyncErrorDialog()
            },
            onOpenSettings = {
                // Open system settings for date & time
                val intent = Intent(Settings.ACTION_DATE_SETTINGS)
                try {
                    appContext.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to general settings if date settings not available
                    val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
                    try {
                        appContext.startActivity(fallbackIntent)
                    } catch (ex: Exception) {
                        println("Could not open settings: ${ex.message}")
                    }
                }
            },
            onDontTellTodayChanged = { suppress ->
                if (suppress) {
                    viewModel.setSyncErrorSuppressedToday()
                }
            }
        )
        
        // Update dialog - only shown when update is available
        if (showUpdateDialog && updateCheckResult is UpdateCheckResult.UpdateAvailable) {
            val manifest = (updateCheckResult as UpdateCheckResult.UpdateAvailable).manifest
            val isRequired = (updateCheckResult as UpdateCheckResult.UpdateAvailable).isRequired
            val currentDownloadState = updateDownloadState
            
            when (currentDownloadState) {
                is DownloadState.Downloading -> {
                    AlertDialog(
                        onDismissRequest = { },
                        title = {
                            Text(text = context.getString(R.string.downloading_update))
                        },
                        text = {
                            Column {
                                LinearProgressIndicator(
                                    progress = { currentDownloadState.progress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = context.getString(R.string.download_progress, currentDownloadState.progress),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        },
                        confirmButton = {}
                    )
                }
                is DownloadState.Downloaded -> {
                    AlertDialog(
                        onDismissRequest = if (isRequired) { {} } else { { showUpdateDialog = false } },
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
                                viewModel.installUpdate(currentDownloadState.file)
                                showUpdateDialog = false
                            }) {
                                Text(context.getString(R.string.install_update))
                            }
                        },
                        dismissButton = if (!isRequired) {
                            {
                                TextButton(onClick = { showUpdateDialog = false }) {
                                    Text(context.getString(R.string.later))
                                }
                            }
                        } else null
                    )
                }
                is DownloadState.Error -> {
                    AlertDialog(
                        onDismissRequest = if (isRequired) { {} } else { { showUpdateDialog = false } },
                        title = {
                            Text(text = context.getString(R.string.download_error_title))
                        },
                        text = {
                            Text(text = context.getString(R.string.download_error_message, currentDownloadState.message))
                        },
                        confirmButton = {
                            TextButton(onClick = { showUpdateDialog = false }) {
                                Text(context.getString(R.string.ok))
                            }
                        }
                    )
                }
                else -> {
                    // Show update available dialog
                    AlertDialog(
                        onDismissRequest = if (isRequired) { {} } else { { showUpdateDialog = false } },
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
                                    showUpdateDialog = false
                                }
                            }) {
                                Text(context.getString(R.string.update_now))
                            }
                        },
                        dismissButton = {
                            if (!isRequired) {
                                TextButton(onClick = { showUpdateDialog = false }) {
                                    Text(context.getString(R.string.later))
                                }
                            }
                        }
                    )
                }
            }
        }
        
        // Sync Status Dialog
        SyncStatusDialog(
            isVisible = showSyncStatusDialog,
            onDismiss = { viewModel.dismissSyncStatusDialog() },
            statusMessage = syncStatusMessage
        )
        } // end of showAdminAuth else (main app content)
    }
}

// Sync Status Widget
@Composable
fun SyncStatusWidget(
viewModel: EventManagerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    
    // Interaction source for press feedback
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Animate scale on press
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
                onClick = {
                    // Trigger differential sync for efficient targeted updates
                    // Only reloads changed items instead of the entire list
                    viewModel.performDifferentialFullSync()
                }
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSyncing) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
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
                    text = context.getString(R.string.syncing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Icon(
                    imageVector = if (lastSyncTime > 0) Icons.Default.Refresh else Icons.Default.Sync,
                    contentDescription = "Tap to sync",
                    modifier = Modifier.size(16.dp),
                    tint = if (lastSyncTime > 0) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (lastSyncTime > 0) {
                        val timeAgo = System.currentTimeMillis() - lastSyncTime
                        when {
                            timeAgo < 60000 -> context.getString(R.string.synced_now)
                            timeAgo < 3600000 -> context.getString(R.string.synced_minutes_ago, timeAgo / 60000)
                            timeAgo < 86400000 -> context.getString(R.string.synced_hours_ago, timeAgo / 3600000)
                            else -> context.getString(R.string.synced_days_ago, timeAgo / 86400000)
                        }
                    } else {
                        context.getString(R.string.never_synced)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (lastSyncTime > 0) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// QR Scanner Button Widget
@Composable
fun QRScannerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Interaction source for press feedback
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Animate scale on press
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(100),
        label = "qr_button_scale"
    )
    
    Card(
        modifier = modifier
            .padding(4.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isPressed) 4.dp else 2.dp
        )
    ) {
        Box(
            modifier = Modifier
                .padding(12.dp)
                .scale(scale),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.qrscan_icon),
                contentDescription = "Scan QR Code",
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

// Dashboard Screen
@Composable
fun DashboardScreenWithViewModel(
    viewModel: EventManagerViewModel,
    onLogout: () -> Unit = {}
) {
    val guests by viewModel.guests.collectAsState()
    val volunteers by viewModel.volunteers.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val jobTypeConfigs by viewModel.jobTypeConfigs.collectAsState()
    val venues by viewModel.venues.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    
    // OPTIMIZATION: Removed redundant sync triggers
    // Initial sync is handled by LaunchedEffect(Unit) in EventManagerApp
    // Tab-change sync is handled by LaunchedEffect(selectedTab) in EventManagerApp
    // This reduces duplicate network calls and improves navigation smoothness

    DashboardScreen(
        guests = guests,
        volunteers = volunteers,
        jobs = jobs,
        venues = venues,
        jobTypeConfigs = jobTypeConfigs,
        isSyncing = isSyncing,
        lastSyncTime = settingsManager.getLastSyncTime(),
        repository = viewModel.repository,
        onLogout = onLogout
    )
}

@Composable
fun DashboardScreen(
    guests: List<Guest>,
    volunteers: List<Volunteer>,
    jobs: List<Job>,
    venues: List<VenueEntity> = emptyList(),
    jobTypeConfigs: List<JobTypeConfig> = emptyList(),
    isSyncing: Boolean = false,
    lastSyncTime: Long = 0L,
    repository: com.eventmanager.app.data.repository.EventManagerRepository? = null,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val isCompact = isCompactScreen()
    val isPhone = !isTablet()
    val responsivePadding = if (isPhone) getPhonePortraitPadding() else getResponsivePadding()
    val responsiveSpacing = if (isPhone) getPhonePortraitSpacing() else getResponsiveSpacing()
    val settingsManager = remember { SettingsManager(context) }
    
    // State for beer animation
    var showBeerAnimation by remember { mutableStateOf(false) }
    
    // Memoize seasonal fun setting to avoid repeated reads
    val seasonalFunEnabled = remember { settingsManager.isSeasonalFunEnabled() }
    
    // Auto-hide beer animation after short duration
    LaunchedEffect(showBeerAnimation) {
        if (showBeerAnimation) {
            kotlinx.coroutines.delay(1500) // Show for 1.5 seconds
            showBeerAnimation = false
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(responsivePadding)
        ) {
        // Visibility settings
        val isPeopleCounterVisible = remember { settingsManager.isPeopleCounterVisible() }
        val isStatisticsVisible = remember { settingsManager.isStatisticsVisible() }
        val dateChangeOffsetHours = remember { settingsManager.getDateChangeOffsetHours() }

        DashboardClockCard(
            settingsManager = settingsManager,
            isPhone = isPhone
        )
        
        Spacer(modifier = Modifier.height(if (isPhone) 16.dp else 24.dp))

        // Calculate statistics - memoized and grouped by data source for efficient single-pass computation
        // Guest stats: single pass through guests list
        val (allGuests, totalInvites) = remember(guests) {
            var permanentCount = 0
            var invitesSum = 0
            guests.forEach { guest ->
                if (!guest.isVolunteerBenefit) permanentCount++
                invitesSum += guest.invitations
            }
            permanentCount to invitesSum
        }
        
        // Volunteer stats: single pass through volunteers list
        val (totalVolunteers, activeVolunteersCount, inactiveVolunteersCount) = remember(volunteers) {
            var active = 0
            var inactive = 0
            volunteers.forEach { volunteer ->
                if (volunteer.isActive) active++ else inactive++
            }
            Triple(volunteers.size, active, inactive)
        }
        
        // Derived calculation - no remember needed since operands are already memoized
        val totalPeople = allGuests + totalInvites + activeVolunteersCount
        // Move expensive calculation to background if needed
        val totalFreeDrinks = remember(volunteers, jobs, jobTypeConfigs, dateChangeOffsetHours) {
            com.eventmanager.app.data.models.BenefitCalculator.calculateTotalFreeDrinks(
                volunteers = volunteers,
                jobs = jobs,
                jobTypeConfigs = jobTypeConfigs,
                offsetHours = dateChangeOffsetHours
            )
        }
        
        // Statistics Grid - 2 columns (always visible)
        Column(
            verticalArrangement = Arrangement.spacedBy(if (isPhone) 12.dp else 16.dp)
        ) {
            // Row 1: Permanent Guests, Total Volunteers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isPhone) 12.dp else 16.dp)
            ) {
                StatCardV2(
                    title = context.getString(R.string.permanent_guests),
                    value = allGuests.toString(),
                    icon = Icons.Default.Person,
                    modifier = Modifier.weight(1f),
                    isPhone = isPhone
                )
                
                StatCardV2(
                    title = context.getString(R.string.volunteers_total),
                    value = totalVolunteers.toString(),
                    icon = Icons.Default.Group,
                    modifier = Modifier.weight(1f),
                    isPhone = isPhone
                )
            }
            
            // Row 2: +1 Invites, Total People
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isPhone) 12.dp else 16.dp)
            ) {
                StatCardV2(
                    title = context.getString(R.string.plus_one_invites),
                    value = totalInvites.toString(),
                    icon = Icons.Default.PlayArrow,
                    modifier = Modifier.weight(1f),
                    isPhone = isPhone
                )
                
                StatCardV2(
                    title = context.getString(R.string.total_people),
                    value = totalPeople.toString(),
                    icon = Icons.Default.Star,
                    modifier = Modifier.weight(1f),
                    isPhone = isPhone
                )
            }
            
            // Row 3: Active Volunteers and Inactive Volunteers in same box
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isPhone) 12.dp else 16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isPhone) 140.dp else 160.dp),
                    shape = RoundedCornerShape(if (isPhone) 12.dp else 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 6.dp
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(if (isPhone) 14.dp else 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Active Volunteers (Left)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (isPhone) 36.dp else 44.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(if (isPhone) 8.dp else 12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(if (isPhone) 18.dp else 22.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 10.dp))
                            
                            Text(
                                text = activeVolunteersCount.toString(),
                                style = if (isPhone) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(if (isPhone) 4.dp else 6.dp))
                            
                            Text(
                                text = context.getString(R.string.active_volunteers),
                                style = if (isPhone) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        // Divider
                        VerticalDivider(
                            modifier = Modifier
                                .height(if (isPhone) 80.dp else 100.dp)
                                .width(1.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        
                        // Inactive Volunteers (Right)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (isPhone) 36.dp else 44.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(if (isPhone) 8.dp else 12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(if (isPhone) 18.dp else 22.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 10.dp))
                            
                            Text(
                                text = inactiveVolunteersCount.toString(),
                                style = if (isPhone) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(if (isPhone) 4.dp else 6.dp))
                            
                            Text(
                                text = context.getString(R.string.inactive_volunteers),
                                style = if (isPhone) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            
            // Row 4: Free Drinks Today
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isPhone) 12.dp else 16.dp)
            ) {
                StatCardV2(
                    title = context.getString(R.string.free_drinks_today),
                    value = totalFreeDrinks.toString(),
                    icon = Icons.Default.LocalBar,
                    modifier = Modifier.weight(1f),
                    isPhone = isPhone,
                    onTripleTap = { 
                        // Only show animation if seasonal fun is enabled
                        if (seasonalFunEnabled) {
                            showBeerAnimation = true
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(if (isPhone) 16.dp else 24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(if (isPhone) 14.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (isPhone) 10.dp else 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (isPhone) 22.dp else 24.dp)
                    )
                    Text(
                        text = context.getString(R.string.settings_logout),
                        style = if (isPhone) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = context.getString(R.string.settings_logout_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(
                        Icons.Default.ExitToApp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(context.getString(R.string.settings_logout))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(if (isPhone) 16.dp else 24.dp))
        
        // People Counter Component - only show if enabled
        if (isPeopleCounterVisible) {
            Spacer(modifier = Modifier.height(if (isPhone) 16.dp else 24.dp))
            PeopleCounter(isPhone = isPhone, repository = repository)
        }
        
        // Stats Graphs Panel - only show if statistics are enabled
        if (isStatisticsVisible) {
            Spacer(modifier = Modifier.height(if (isPhone) 16.dp else 24.dp))
            StatsGraphsPanel(
                guests = guests,
                volunteers = volunteers,
                jobs = jobs,
                venues = venues,
                jobTypeConfigs = jobTypeConfigs,
                isPhone = isPhone
            )
        }
        
        // Bottom padding to ensure content is not cut off by navigation bar or sync widget
        Spacer(modifier = Modifier.height(if (isPhone) 80.dp else 100.dp))
        }
        
        // Beer Animation Overlay (only if seasonal fun is enabled)
        if (showBeerAnimation && seasonalFunEnabled) {
            BeerAnimation(
                enabled = true,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun StatCardV2(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    emoji: String? = null,
    modifier: Modifier = Modifier,
    isPhone: Boolean = !isTablet(),
    onTripleTap: (() -> Unit)? = null
) {
    // Triple tap detection
    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    
    // Reset tap count after timeout
    LaunchedEffect(tapCount, lastTapTime) {
        if (tapCount > 0 && tapCount < 3) {
            kotlinx.coroutines.delay(500)
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime >= 500) {
                tapCount = 0
            }
        }
    }
    
    Card(
        modifier = modifier
            .height(if (isPhone) 140.dp else 160.dp)
            .then(
                if (onTripleTap != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { _ ->
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastTapTime < 500) { // Within 500ms of last tap
                                tapCount++
                                if (tapCount >= 3) {
                                    onTripleTap()
                                    tapCount = 0
                                }
                            } else {
                                tapCount = 1
                            }
                            lastTapTime = currentTime
                        }
                    }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(if (isPhone) 12.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isPhone) 14.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon or Emoji with background
            Box(
                modifier = Modifier
                    .size(if (isPhone) 36.dp else 44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(if (isPhone) 8.dp else 12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (emoji != null) {
                    Text(
                        text = emoji,
                        style = if (isPhone) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontSize = if (isPhone) 18.sp else 22.sp
                    )
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(if (isPhone) 18.dp else 22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(if (isPhone) 8.dp else 10.dp))
            
            // Large value text
            Text(
                text = value,
                style = if (isPhone) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(if (isPhone) 4.dp else 6.dp))
            
            // Title text - wrapped to handle long titles
            Text(
                text = title,
                style = if (isPhone) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Wrapper composables that connect screens to ViewModel
@Composable
fun GuestListScreenWithViewModel(
    viewModel: EventManagerViewModel,
    readOnly: Boolean = false
) {
    val guests by viewModel.guests.collectAsState()
    val volunteers by viewModel.volunteers.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val jobTypeConfigs by viewModel.jobTypeConfigs.collectAsState()
    val venues by viewModel.venues.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scrollBehavior = remember { settingsManager.getScrollBehavior() }
    
    // OPTIMIZATION: Sync is now handled by the centralized LaunchedEffect(selectedTab) in EventManagerApp
    // This prevents duplicate syncs and improves navigation smoothness
    
    GuestListScreen(
        guests = guests,
        volunteers = volunteers,
        jobs = jobs,
        jobTypeConfigs = jobTypeConfigs,
        venues = venues,
        isSyncing = isSyncing,
        lastSyncTime = settingsManager.getLastSyncTime(),
        scrollBehavior = scrollBehavior,
        readOnly = readOnly,
        onAddGuest = { 
            coroutineScope.launch { 
                try {
                    viewModel.addGuest(it)
                } catch (e: Exception) {
                    // Exception is already handled in ViewModel and shown in syncError
                    println("Guest addition failed: ${e.message}")
                }
            } 
        },
        onAddTemporaryGuests = { batch ->
            coroutineScope.launch {
                try {
                    viewModel.addTemporaryGuestBatch(batch)
                } catch (e: Exception) {
                    println("Temporary guest addition failed: ${e.message}")
                }
            }
        },
        onUpdateGuest = { 
            coroutineScope.launch { 
                try {
                    viewModel.updateGuest(it)
                } catch (e: Exception) {
                    println("Guest update failed: ${e.message}")
                }
            } 
        },
        onUpdateVolunteer = {
            coroutineScope.launch {
                try {
                    viewModel.updateVolunteer(it)
                } catch (e: Exception) {
                    println("Volunteer update from guest list failed: ${e.message}")
                }
            }
        },
        onDeleteGuest = { 
            coroutineScope.launch { 
                try {
                    viewModel.deleteGuest(it)
                } catch (e: Exception) {
                    println("Guest deletion failed: ${e.message}")
                }
            } 
        },
        onRefreshTemporaryGuests = {
            viewModel.refreshTemporaryGuests()
        },
        onConfirmEntry = { job, selectedInvites ->
            coroutineScope.launch {
                try {
                    viewModel.markBenefitAsUsed(job, selectedInvites)
                } catch (e: Exception) {
                    println("Benefit confirmation failed: ${e.message}")
                }
            }
        }
    )
}

@Composable
fun VolunteerScreenWithViewModel(viewModel: EventManagerViewModel) {
    val volunteers by viewModel.volunteers.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val venues by viewModel.venues.collectAsState()
    val jobTypeConfigs by viewModel.jobTypeConfigs.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scrollBehavior = remember { settingsManager.getScrollBehavior() }
    
    // OPTIMIZATION: Sync is handled by the centralized LaunchedEffect(selectedTab) in EventManagerApp
    // Removing duplicate sync trigger improves navigation smoothness
    
    VolunteerScreen(
        volunteers = volunteers,
        volunteerJobs = jobs,
        venues = venues,
        jobTypeConfigs = jobTypeConfigs,
        onConfirmFutureEntry = { job, selectedInvites -> viewModel.markBenefitAsUsed(job, selectedInvites) },
        scrollBehavior = scrollBehavior,
        onAddVolunteer = { 
            coroutineScope.launch { 
                try {
                    viewModel.addVolunteer(it)
                } catch (e: Exception) {
                    println("Volunteer addition failed: ${e.message}")
                }
            } 
        },
        onUpdateVolunteer = { 
            coroutineScope.launch { 
                try {
                    viewModel.updateVolunteer(it)
                } catch (e: Exception) {
                    println("Volunteer update failed: ${e.message}")
                }
            } 
        },
        onDeleteVolunteer = { volunteer, deleteShifts ->
            coroutineScope.launch { 
                try {
                    viewModel.deleteVolunteer(volunteer, deleteShifts)
                } catch (e: Exception) {
                    println("Volunteer deletion failed: ${e.message}")
                }
            } 
        }
    )
}

@Composable
fun JobTrackingScreenWithViewModel(viewModel: EventManagerViewModel) {
    val jobs by viewModel.jobs.collectAsState()
    val volunteers by viewModel.volunteers.collectAsState()
    val jobTypeConfigs by viewModel.jobTypeConfigs.collectAsState()
    val venues by viewModel.venues.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scrollBehavior = remember { settingsManager.getScrollBehavior() }
    
    // OPTIMIZATION: Sync is handled by the centralized LaunchedEffect(selectedTab) in EventManagerApp
    // Removing duplicate sync trigger improves navigation smoothness

    JobTrackingScreen(
        jobs = jobs,
        volunteers = volunteers,
        jobTypeConfigs = jobTypeConfigs,
        venues = venues,
        scrollBehavior = scrollBehavior,
        onAddJob = { 
            coroutineScope.launch { 
                try {
                    viewModel.addJob(it)
                } catch (e: Exception) {
                    println("Job addition failed: ${e.message}")
                }
            }
        },
        onUpdateJob = { 
            coroutineScope.launch { 
                try {
                    viewModel.updateJob(it)
                } catch (e: Exception) {
                    println("Job update failed: ${e.message}")
                }
            }
        },
        onDeleteJob = { 
            coroutineScope.launch { 
                try {
                    viewModel.deleteJob(it)
                } catch (e: Exception) {
                    println("Job deletion failed: ${e.message}")
                }
            }
        }
    )
}

@Composable
fun BenefitsScreenWithViewModel(viewModel: EventManagerViewModel) {
    val volunteers by viewModel.volunteers.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val jobTypeConfigs by viewModel.jobTypeConfigs.collectAsState()
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scrollBehavior = remember { settingsManager.getScrollBehavior() }
    
    // OPTIMIZATION: Sync is handled by the centralized LaunchedEffect(selectedTab) in EventManagerApp
    // Removing duplicate sync trigger improves navigation smoothness
    
    BenefitsScreen(
        volunteers = volunteers,
        jobs = jobs,
        jobTypeConfigs = jobTypeConfigs,
        scrollBehavior = scrollBehavior
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(onAdminSelected: () -> Unit, onTicketCheckSelected: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val colorScheme = MaterialTheme.colorScheme

    BackHandler {
        (context as? Activity)?.finish()
    }
    
    // Haptic feedback for start button
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    
    // Determine if dark theme is active
    val themeMode = ThemeMode.fromString(settingsManager.getThemeMode())
    val systemInDarkTheme = isSystemInDarkTheme()
    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.DEFAULT -> systemInDarkTheme
    }
    
    // Check if it's Pride Day to customize the UI
    val calendar = Calendar.getInstance()
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val isPrideDay = month == Calendar.JUNE && day == 28 && settingsManager.isSeasonalFunEnabled()
    
    Scaffold { innerPadding ->
        // Full-bleed launch screen with custom background and bottom CTA
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (isPrideDay) Color.White else colorScheme.background)
        ) {
            // Center content: Logo based on theme (drawn first, behind animations)
            val logoResId = remember(isDarkTheme) {
                val logoName = if (isDarkTheme) "launch_logo_dark" else "launch_logo_light"
                context.resources.getIdentifier(logoName, "drawable", context.packageName)
            }
            val isLandscape = maxWidth > maxHeight

            // Center logo block without ColumnScope.weight (avoids NoSuchMethodError if Compose layout JARs diverge on device).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Happy Pride text on Pride Day
                if (isPrideDay) {
                    Text(
                        text = "Happy Pride! 🏳️‍🌈",
                        style = if (isLandscape) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
                
                if (logoResId != 0) {
                    // Glass box container for the logo with glassmorphism effect (no logo inside)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(if (isLandscape) 0.5f else 0.85f)
                            .aspectRatio(1f)
                            .padding(if (isLandscape) 16.dp else 24.dp),
                        shape = RoundedCornerShape(if (isLandscape) 28.dp else 36.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colorScheme.surface.copy(alpha = 0.95f)
                        ),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = 12.dp
                        ),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color = colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            colorScheme.primaryContainer.copy(alpha = 0.25f),
                                            colorScheme.surfaceVariant.copy(alpha = 0.1f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(if (isLandscape) 28.dp else 36.dp)
                                )
                        )
                    }
                } else {
                    Text(
                        text = "CNL",
                        style = if (isLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.onBackground
                    )
                }
                }
            }

            // Show Pride animation on Pride Day instead of ArchedCirclesBackground (drawn on top)
            if (isPrideDay) {
                PrideAnimation(enabled = true)
            } else {
                // Background arches - use theme colors (drawn on top)
                ArchedCirclesBackground()
            }

            // Logo image with hollow effect (drawn on top of animations)
            if (logoResId != 0) {
                val density = androidx.compose.ui.platform.LocalDensity.current
                // Calculate max size based on screen dimensions to prevent "too large bitmap" error
                // Use a reasonable maximum (1200dp) to prevent loading huge images even on large screens
                val maxLogoSizeDp = with(density) {
                    val maxScreenSize = max(maxWidth.value, maxHeight.value) * 1.2f // 20% larger than screen for quality
                    val maxAllowed = 1200f // Cap at 1200dp to prevent memory issues
                    min(maxScreenSize, maxAllowed).dp
                }
                
                // Load scaled bitmap to prevent memory issues on older devices
                val scaledLogoBitmap = remember(logoResId, maxLogoSizeDp) {
                    ImageUtils.loadScaledImageBitmap(
                        context = context,
                        resId = logoResId,
                        maxWidthDp = maxLogoSizeDp,
                        maxHeightDp = maxLogoSizeDp
                    )
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Happy Pride text on Pride Day - same spacing as glass column
                    if (isPrideDay) {
                        Text(
                            text = "Happy Pride! 🏳️‍🌈",
                            style = if (isLandscape) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.Transparent, // Invisible but maintains spacing
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                    }
                    
                    // Hollow effect: Create outline using multiple offset layers
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (isLandscape) 0.5f else 0.85f)
                            .aspectRatio(1f)
                            .padding(if (isLandscape) 16.dp else 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val logoPadding = if (isLandscape) 6.dp else 8.dp
                        val outlineWidth = if (isLandscape) 6.dp else 8.dp
                        
                        // Only render if bitmap was loaded successfully
                        scaledLogoBitmap?.let { bitmap ->
                            // Create hollow outline effect using multiple offset layers
                            // Draw outline layers in all directions
                            for (dx in listOf(-outlineWidth, 0.dp, outlineWidth)) {
                                for (dy in listOf(-outlineWidth, 0.dp, outlineWidth)) {
                                    if (dx != 0.dp || dy != 0.dp) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(logoPadding)
                                                .graphicsLayer {
                                                    translationX = with(density) { dx.toPx() }
                                                    translationY = with(density) { dy.toPx() }
                                                    alpha = 0.4f
                                                },
                                            colorFilter = ColorFilter.tint(
                                                colorScheme.primaryContainer.copy(alpha = 0.8f)
                                            )
                                        )
                                    }
                                }
                            }
                            
                            // Main logo on top
                            Image(
                                bitmap = bitmap,
                                contentDescription = "App Logo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(logoPadding)
                            )
                        }
                    }
                    }
                }
            }

            // Bottom buttons — ticketing (primary) first, admin (outlined) second
            val buttonColor = if (isPrideDay) {
                Color(0xFFE40303)
            } else {
                colorScheme.primary
            }
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .fillMaxWidth(if (isLandscape) 0.6f else 1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        performStrongHaptic(vibrator)
                        onTicketCheckSelected()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 44.dp else 56.dp),
                    shape = RoundedCornerShape(if (isLandscape) 22.dp else 28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        Icons.Default.ConfirmationNumber,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = context.getString(R.string.ticket_check_mode),
                        style = if (isLandscape) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        performStrongHaptic(vibrator)
                        onAdminSelected()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 44.dp else 56.dp),
                    shape = RoundedCornerShape(if (isLandscape) 22.dp else 28.dp),
                    border = BorderStroke(1.5.dp, buttonColor.copy(alpha = 0.6f))
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = buttonColor
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = context.getString(R.string.admin_mode),
                        style = if (isLandscape) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = buttonColor
                    )
                }
            }
        }
    }
}

// Data class outside composable to avoid LiveEdit conflicts
private data class ArchCircleParam(val fx: Float, val fy: Float, val theta: Float, val color: Color)

@Composable
private fun ArchedCirclesBackground() {
    val colorScheme = MaterialTheme.colorScheme
    
    // Prepare randomized circle layout once per app run
    // Use theme colors for the circles - lighter variants of surfaceVariant
    val circles = remember(colorScheme) {
        val rnd = Random(System.nanoTime())
        val count = 50 // a lot more circles
        
        // Create two subtle color variants based on theme
        val baseColor = colorScheme.surfaceVariant
        val variantColor = colorScheme.surfaceVariant.copy(alpha = 0.6f)
        
        List(count) { i ->
            val fx = rnd.nextFloat() // 0..1 normalized
            val fy = rnd.nextFloat()
            val theta = rnd.nextFloat() * 2f * PI.toFloat()
            val color = if (i % 2 == 0) baseColor else variantColor
            ArchCircleParam(fx, fy, theta, color)
        }
    }

    // Subtle perpetual drift animation
    val infinite = rememberInfiniteTransition(label = "arches")
    val phase1 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 25000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase1"
    )
    val phase2 by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "phase2"
    )

    // Draw very large circles so only sweeping arches are visible, adapting to phone/tablet/orientation
    Canvas(modifier = Modifier.fillMaxSize()) {
        val maxDim = maxOf(size.width, size.height)
        val stroke = (maxDim * 0.012f).coerceIn(6f, 18f)

        // Same radius for all, centers distributed and animated
        val circleRadius = maxDim * 1.2f
        val amp = maxDim * 0.025f // subtle drift amplitude

        // Allow centers anywhere across and slightly beyond the whole screen
        val startX = -0.5f * size.width
        val spanX = size.width * 2.0f
        val startY = -0.5f * size.height
        val spanY = size.height * 2.0f

        circles.forEachIndexed { index, p ->
            val driftX = amp * cos(phase1 + p.theta)
            val driftY = amp * sin(phase2 + p.theta)
            val cx = startX + p.fx * spanX + driftX
            val cy = startY + p.fy * spanY + driftY
            drawCircle(
                color = p.color,
                radius = circleRadius,
                center = Offset(cx, cy),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

// Job Type Management Screen
@Composable
fun JobTypeManagementScreenWithViewModel(
    viewModel: EventManagerViewModel,
    _onBack: () -> Unit
) {
    val jobTypeConfigs by viewModel.jobTypeConfigs.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    // Track if screen has been initialized to prevent re-syncing on every recomposition
    var isInitialized by remember { mutableStateOf(false) }
    
    // Trigger TARGETED sync when screen loads - only changed job types are updated
    // Use isInitialized flag to prevent re-execution on recomposition
    LaunchedEffect(isInitialized) {
        if (!isInitialized) {
            println("Job Type Management screen loaded - triggering TARGETED job type sync")
            viewModel.syncJobTypesWithTargetedUpdates()
            isInitialized = true
        }
    }
    
    BackHandler {
        _onBack()
    }

    JobTypeManagementScreen(
        jobTypeConfigs = jobTypeConfigs,
        onAddJobTypeConfig = { config ->
            coroutineScope.launch {
                try {
                    viewModel.addJobTypeConfig(config)
                } catch (e: Exception) {
                    println("Job type config addition failed: ${e.message}")
                }
            }
        },
        onUpdateJobTypeConfig = { config ->
            coroutineScope.launch {
                try {
                    viewModel.updateJobTypeConfig(config)
                } catch (e: Exception) {
                    println("Job type config update failed: ${e.message}")
                }
            }
        },
        onDeleteJobTypeConfig = { config ->
            coroutineScope.launch {
                try {
                    viewModel.deleteJobTypeConfig(config)
                } catch (e: Exception) {
                    println("Job type config deletion failed: ${e.message}")
                }
            }
        },
        onUpdateJobTypeConfigStatus = { id, isActive ->
            coroutineScope.launch {
                try {
                    val config = jobTypeConfigs.find { it.id == id }
                    if (config != null) {
                        viewModel.updateJobTypeConfig(config.copy(isActive = isActive))
                    }
                } catch (e: Exception) {
                    println("Job type config status update failed: ${e.message}")
                }
            }
        },
        onBack = _onBack
    )
}

@Composable
fun VenueManagementScreenWithViewModel(
    viewModel: EventManagerViewModel,
    _onBack: () -> Unit
) {
    val venues by viewModel.venues.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    // Track if screen has been initialized to prevent re-syncing on every recomposition
    var isInitialized by remember { mutableStateOf(false) }
    
    // Trigger TARGETED sync when screen loads - only changed venues are updated
    // Use isInitialized flag to prevent re-execution on recomposition
    LaunchedEffect(isInitialized) {
        if (!isInitialized) {
            println("Venue Management screen loaded - triggering TARGETED venue sync")
            viewModel.syncVenuesWithTargetedUpdates()
            isInitialized = true
        }
    }
    
    BackHandler {
        _onBack()
    }
    
    VenueManagementScreen(
        venues = venues,
        onAddVenue = { venue ->
            coroutineScope.launch {
                try {
                    viewModel.addVenue(venue)
                } catch (e: Exception) {
                    println("Venue addition failed: ${e.message}")
                }
            }
        },
        onUpdateVenue = { venue ->
            coroutineScope.launch {
                try {
                    viewModel.updateVenue(venue)
                } catch (e: Exception) {
                    println("Venue update failed: ${e.message}")
                }
            }
        },
        onDeleteVenue = { venue ->
            coroutineScope.launch {
                try {
                    viewModel.deleteVenue(venue)
                } catch (e: Exception) {
                    println("Venue deletion failed: ${e.message}")
                }
            }
        },
        onUpdateVenueStatus = { id, isActive ->
            coroutineScope.launch {
                try {
                    viewModel.updateVenueStatus(id, isActive)
                } catch (e: Exception) {
                    println("Venue status update failed: ${e.message}")
                }
            }
        },
        onBack = _onBack
    )
}
