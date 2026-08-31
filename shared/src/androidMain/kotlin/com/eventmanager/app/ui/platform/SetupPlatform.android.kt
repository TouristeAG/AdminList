package com.eventmanager.app.ui.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.eventmanager.app.data.sync.FileManager
import com.eventmanager.app.platform.AndroidFragmentActivityProvider
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.recreateActivity
import com.eventmanager.app.ui.platform.AppAppearanceState
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import com.eventmanager.app.ui.components.ResolutionScaleSlider
import com.eventmanager.app.ui.components.isNvidiaShieldTablet
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun NfcUidListenerEffect(
    platformContext: PlatformContext,
    enabled: Boolean,
    onUidRead: (String) -> Unit,
    onScanStatus: (String?) -> Unit,
) {
    val composeContext = LocalContext.current
    val cardReader = remember(platformContext) {
        com.eventmanager.app.platform.createCardReaderService(platformContext)
    }
    ExternalCardReaderUidEffect(
        platformContext = platformContext,
        enabled = enabled,
        onUidRead = onUidRead,
        onScanStatus = onScanStatus,
    )

    val activity = remember(composeContext) {
        composeContext.findActivity() ?: AndroidFragmentActivityProvider.current
    }
    val nfcAdapter = remember(composeContext) { NfcAdapter.getDefaultAdapter(composeContext) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, activity, nfcAdapter, enabled, cardReader) {
        if (!enabled || activity == null || nfcAdapter == null || cardReader.shouldSuppressBuiltInNfc()) {
            onDispose { }
        } else if (!nfcAdapter.isEnabled) {
            onDispose { }
        } else {
            val callback = NfcAdapter.ReaderCallback { tag ->
                val uid = tag.id?.joinToString(separator = "") { "%02X".format(it) }.orEmpty()
                mainHandler.post { if (uid.isNotBlank()) onUidRead(uid) }
            }

            fun enablePhoneNfcReader() {
                try {
                    nfcAdapter.enableReaderMode(
                        activity,
                        callback,
                        NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                        null,
                    )
                } catch (_: Exception) {
                    // Reader mode can fail transiently; ON_RESUME will retry.
                }
            }

            fun disablePhoneNfcReader() {
                try {
                    nfcAdapter.disableReaderMode(activity)
                } catch (_: Exception) {
                }
            }

            enablePhoneNfcReader()
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> enablePhoneNfcReader()
                    Lifecycle.Event.ON_PAUSE -> disablePhoneNfcReader()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                disablePhoneNfcReader()
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

actual fun supportsResolutionScaleStep(): Boolean = true

actual fun applyLocaleOrThemeChange(platformContext: PlatformContext) {
    applyLocaleChange(platformContext)
}

actual fun applyLocaleChange(platformContext: PlatformContext) {
    val settingsManager = com.eventmanager.app.data.sync.settingsManagerFor(platformContext)
    AppAppearanceState.notifyLocaleChanged(settingsManager.getLanguage())
}

actual fun applyThemeAppearanceChange(platformContext: PlatformContext) {
    val settingsManager = com.eventmanager.app.data.sync.settingsManagerFor(platformContext)
    AppAppearanceState.notifyThemeAppearanceChanged(settingsManager.getThemeMode())
    recreateActivity(platformContext)
}

@Composable
actual fun ServiceAccountKeyUploadButton(
    platformContext: PlatformContext,
    onStatusUpdate: (String) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val fileManager = remember { FileManager(context) }
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri = uri
    }

    LaunchedEffect(selectedUri) {
        selectedUri?.let { uri ->
            onStatusUpdate(context.getString(com.eventmanager.app.R.string.validating_file))
            fileManager.validateJsonKeyFile(uri)
                .onSuccess {
                    onStatusUpdate(context.getString(com.eventmanager.app.R.string.file_validated_uploading))
                    fileManager.copyFileToAssets(uri, "service_account_key.json")
                        .onSuccess { onStatusUpdate(context.getString(com.eventmanager.app.R.string.file_uploaded_successfully)) }
                        .onFailure { e -> onStatusUpdate(context.getString(com.eventmanager.app.R.string.upload_failed, e.message ?: "")) }
                }
                .onFailure { e -> onStatusUpdate(context.getString(com.eventmanager.app.R.string.validation_failed, e.message ?: "")) }
        }
    }

    Button(onClick = { launcher.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = modifier) {
        Icon(Icons.Default.Upload, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(Res.string.upload_key_file))
    }
}

@Composable
actual fun SetupLayoutScalePage(
    resolutionScale: Float,
    onSave: (Float) -> Unit,
    onUseRecommended: () -> Unit,
    modifier: Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ResolutionScaleSlider(
            value = resolutionScale,
            onValueChange = onSave,
            modifier = Modifier.fillMaxWidth()
        )
        if (isNvidiaShieldTablet()) {
            Text(
                text = stringResource(Res.string.setup_layout_nvidia_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onUseRecommended, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.setup_layout_use_recommended))
            }
        }
    }
}
