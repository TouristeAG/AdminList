package com.eventmanager.app.ui.screens

import android.app.Activity
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.eventmanager.app.R
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.data.sync.settingsManagerFor
import com.eventmanager.app.platform.createPlatformContext
import com.eventmanager.app.ui.components.QRScannerDialog
import com.eventmanager.app.ui.components.ScannerMatch
import com.eventmanager.app.ui.viewmodel.EventManagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class AuthState {
    data object Syncing : AuthState()
    data object Ready : AuthState()
    data class AccessGranted(val name: String) : AuthState()
    data class AccessDenied(val name: String) : AuthState()
    data class NotFound(val uid: String = "") : AuthState()
    data class Error(val message: String) : AuthState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminAuthScreen(
    viewModel: EventManagerViewModel,
    volunteers: List<Volunteer>,
    guests: List<Guest>,
    isSyncing: Boolean,
    onAuthSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { settingsManagerFor(context) }
    val platformContext = remember(context) { createPlatformContext(context) }
    val scope = rememberCoroutineScope()

    var authState by remember { mutableStateOf<AuthState>(if (isSyncing) AuthState.Syncing else AuthState.Ready) }
    var showQRScanner by remember { mutableStateOf(false) }

    val permanentGuests = remember(guests) { guests.filter { !it.isVolunteerBenefit && !it.isTemporaryGuest } }
    val volunteersByNfcUid = remember(volunteers) {
        volunteers.filter { it.nfcCardUid.isNotBlank() }
            .groupBy { it.nfcCardUid.trim().replace(" ", "").replace(":", "").uppercase() }
    }
    val guestsByNfcUid = remember(permanentGuests) {
        permanentGuests.filter { it.nfcCardUid.isNotBlank() }
            .groupBy { it.nfcCardUid.trim().replace(" ", "").replace(":", "").uppercase() }
    }

    fun applyVerifiedAdminFromCandidates(candidates: List<ScannerMatch>) {
        if (candidates.isEmpty()) {
            authState = AuthState.NotFound()
            return
        }
        scope.launch {
            var grantedName: String? = null
            var deniedName: String? = null
            for (match in candidates) {
                val fresh = try {
                    viewModel.resolveFreshAdminScanMatch(match)
                } catch (_: Exception) {
                    match
                }
                when (fresh) {
                    is ScannerMatch.VolunteerMatch -> {
                        if (fresh.volunteer.isAdmin) {
                            grantedName = fresh.volunteer.name
                            break
                        } else {
                            deniedName = fresh.volunteer.name
                        }
                    }
                    is ScannerMatch.GuestMatch -> {
                        if (fresh.guest.isAdmin) {
                            grantedName = fresh.guest.name
                            break
                        } else {
                            deniedName = fresh.guest.name
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                authState = when {
                    grantedName != null -> AuthState.AccessGranted(grantedName)
                    deniedName != null -> AuthState.AccessDenied(deniedName)
                    else -> AuthState.NotFound()
                }
            }
        }
    }

    fun resolveUidMatch(rawUid: String) {
        val uid = rawUid.trim().replace(" ", "").replace(":", "").uppercase()
        if (uid.isBlank()) return
        val allMatches = volunteersByNfcUid[uid].orEmpty().map { ScannerMatch.VolunteerMatch(it) } +
            guestsByNfcUid[uid].orEmpty().map { ScannerMatch.GuestMatch(it) }
        applyVerifiedAdminFromCandidates(allMatches)
    }

    LaunchedEffect(isSyncing) {
        authState = if (isSyncing) AuthState.Syncing else {
            when (val current = authState) {
                is AuthState.AccessGranted, is AuthState.AccessDenied, is AuthState.NotFound, is AuthState.Error -> current
                else -> AuthState.Ready
            }
        }
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.AccessGranted) {
            delay(800)
            onAuthSuccess()
        }
    }

    val activity = remember(context) {
        var ctx: android.content.Context = context
        while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
        ctx as? Activity
    }
    val nfcAdapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(activity, nfcAdapter, authState) {
        val canScan = authState is AuthState.Ready || authState is AuthState.AccessDenied ||
            authState is AuthState.NotFound || authState is AuthState.Error
        if (activity == null || nfcAdapter == null || !nfcAdapter.isEnabled || !canScan) {
            onDispose { }
        } else {
            val callback = NfcAdapter.ReaderCallback { tag ->
                val uid = tag.id?.joinToString(separator = "") { "%02X".format(it) }.orEmpty()
                mainHandler.post { resolveUidMatch(uid) }
            }
            try {
                nfcAdapter.enableReaderMode(
                    activity, callback,
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                        NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                    null
                )
            } catch (_: Exception) { }
            onDispose {
                try { nfcAdapter.disableReaderMode(activity) } catch (_: Exception) { }
            }
        }
    }

    val biometricManager = remember { BiometricManager.from(context) }
    val canAuthenticate = remember {
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    }
    val biometricAvailable = canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS
    val showBiometric = settingsManager.isBiometricAdminLoginEnabled() && biometricAvailable

    fun launchBiometricPrompt() {
        val fragmentActivity = context as? FragmentActivity ?: return
        val executor = ContextCompat.getMainExecutor(context)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.admin_auth_biometric_prompt_title))
            .setSubtitle(context.getString(R.string.admin_auth_biometric_prompt_subtitle))
            .setNegativeButtonText(context.getString(R.string.admin_auth_biometric_cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        BiometricPrompt(
            fragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authState = AuthState.AccessGranted("")
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        authState = AuthState.Error(
                            context.getString(R.string.admin_auth_biometric_error, errString)
                        )
                    }
                }
            }
        ).authenticate(promptInfo)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.admin_auth_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = context.getString(R.string.setup_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = context.getString(R.string.admin_auth_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            AnimatedContent(
                targetState = authState,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "adminAuthState"
            ) { state ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (state) {
                        AuthState.Syncing -> {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = context.getString(R.string.admin_auth_syncing),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                        AuthState.Ready -> {
                            Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = context.getString(R.string.admin_auth_ready),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        is AuthState.AccessGranted -> {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (state.name.isNotBlank()) {
                                    context.getString(R.string.admin_auth_granted, state.name)
                                } else {
                                    context.getString(R.string.admin_auth_granted, context.getString(R.string.admin_auth_title))
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        is AuthState.AccessDenied -> {
                            Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = context.getString(R.string.admin_auth_denied, state.name),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            TextButton(onClick = { authState = AuthState.Ready }) {
                                Text(context.getString(R.string.admin_auth_try_again))
                            }
                        }
                        is AuthState.NotFound -> {
                            Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = context.getString(R.string.admin_auth_not_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            TextButton(onClick = { authState = AuthState.Ready }) {
                                Text(context.getString(R.string.admin_auth_try_again))
                            }
                        }
                        is AuthState.Error -> {
                            Icon(Icons.Default.Error, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                            Text(text = state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                            TextButton(onClick = { authState = AuthState.Ready }) {
                                Text(context.getString(R.string.admin_auth_try_again))
                            }
                        }
                    }
                }
            }

            if (!isSyncing && authState !is AuthState.AccessGranted) {
                Spacer(modifier = Modifier.weight(1f))

                if (showBiometric) {
                    OutlinedButton(
                        onClick = { launchBiometricPrompt() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        enabled = authState is AuthState.Ready || authState is AuthState.AccessDenied ||
                            authState is AuthState.NotFound || authState is AuthState.Error
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(context.getString(R.string.admin_auth_use_fingerprint), fontWeight = FontWeight.SemiBold)
                            Text(
                                context.getString(R.string.admin_auth_fingerprint_description),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedButton(
                    onClick = { showQRScanner = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    enabled = authState is AuthState.Ready || authState is AuthState.AccessDenied ||
                        authState is AuthState.NotFound || authState is AuthState.Error
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(context.getString(R.string.admin_auth_scan_qr), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showQRScanner) {
        QRScannerDialog(
            platformContext = platformContext,
            onDismiss = { showQRScanner = false },
            onMatchFound = { match ->
                showQRScanner = false
                applyVerifiedAdminFromCandidates(listOf(match))
            },
            volunteers = volunteers,
            guests = guests
        )
    }
}
