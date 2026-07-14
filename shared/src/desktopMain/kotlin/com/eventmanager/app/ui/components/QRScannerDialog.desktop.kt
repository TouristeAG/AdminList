package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventmanager.app.data.models.Guest
import com.eventmanager.app.data.models.Volunteer
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.data.sync.settingsManagerFor
import com.eventmanager.app.platform.UidReadResult
import com.eventmanager.app.platform.createCardReaderService
import com.eventmanager.app.platform.hardware.DesktopExternalNfcReader
import com.eventmanager.app.platform.hardware.DesktopQrScanResult
import com.eventmanager.app.platform.hardware.DesktopQrScanner
import com.eventmanager.app.platform.hardware.DesktopWebcamQrScanView
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

private enum class QrDialogMode { Choose, Webcam, ImageLoading }

@Composable
actual fun QRScannerDialog(
    platformContext: PlatformContext,
    onDismiss: () -> Unit,
    onMatchFound: (ScannerMatch) -> Unit,
    volunteers: List<Volunteer>,
    guests: List<Guest>
) {
    var mode by remember { mutableStateOf(QrDialogMode.Choose) }
    var status by remember { mutableStateOf<String?>(null) }
    var duplicateMatches by remember { mutableStateOf<List<NfcUidMatchOption>>(emptyList()) }
    var isExternalReaderBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val settingsManager = remember(platformContext) { settingsManagerFor(platformContext) }
    val cardReader = remember(platformContext) { createCardReaderService(platformContext) }
    var hasExternalReader by remember { mutableStateOf(DesktopExternalNfcReader.isConnected(settingsManager)) }
    val qrScanner = remember { DesktopQrScanner() }

    LaunchedEffect(Unit) {
        while (isActive) {
            hasExternalReader = withContext(Dispatchers.IO) {
                DesktopExternalNfcReader.refreshStatus(settingsManager).let {
                    it.usbConnected || it.bleAvailable
                }
            }
            delay(1500)
        }
    }

    val multipleMatchesTitle = stringResource(Res.string.nfc_uid_multiple_matches_title)
    val readFailedMsg = stringResource(Res.string.nfc_uid_read_failed)
    val readyMsg = stringResource(Res.string.billeterie_scanner_ready)
    val cancelLabel = stringResource(Res.string.cancel)

    val scanWithReaderLabel = stringResource(Res.string.scan_with_usb_reader)
    val waitingCardMsg = stringResource(Res.string.usb_reader_waiting_card_short)

    fun handlePayload(raw: String) {
        val (match, duplicates) = resolveDesktopScannerPayload(raw, volunteers, guests)
        when {
            match != null -> onMatchFound(match)
            duplicates.isNotEmpty() -> {
                duplicateMatches = duplicates
                status = multipleMatchesTitle
                mode = QrDialogMode.Choose
            }
            else -> {
                scope.launch {
                    status = getString(Res.string.nfc_uid_not_found, raw.trim())
                }
                mode = QrDialogMode.Choose
            }
        }
    }

    LaunchedEffect(hasExternalReader, mode) {
        if (!hasExternalReader || mode != QrDialogMode.Choose) {
            isExternalReaderBusy = false
            return@LaunchedEffect
        }
        try {
            var lastConnectionRefreshAtMs = 0L
            while (isActive && mode == QrDialogMode.Choose && duplicateMatches.isEmpty()) {
                val nowMs = System.currentTimeMillis()
                val connected = withContext(Dispatchers.IO) {
                    if (nowMs - lastConnectionRefreshAtMs >= 2_500L) {
                        cardReader.refreshConnectionState()
                        lastConnectionRefreshAtMs = nowMs
                    }
                    cardReader.isReaderConnected()
                }
                if (!connected) {
                    delay(800)
                    continue
                }
                isExternalReaderBusy = true
                when (val result = cardReader.readUid()) {
                    is UidReadResult.Success -> handlePayload(result.uid)
                    is UidReadResult.Fatal -> {
                        status = result.error
                        delay(500)
                    }
                    is UidReadResult.Retryable -> delay(20)
                    UidReadResult.NoReader -> delay(800)
                }
            }
        } finally {
            isExternalReaderBusy = false
        }
    }

    fun handleScanResult(result: DesktopQrScanResult) {
        when (result) {
            is DesktopQrScanResult.Success -> handlePayload(result.payload)
            is DesktopQrScanResult.Error -> {
                status = result.message
                mode = QrDialogMode.Choose
            }
            DesktopQrScanResult.NotFound -> {
                status = readFailedMsg
                mode = QrDialogMode.Choose
            }
            DesktopQrScanResult.Cancelled -> mode = QrDialogMode.Choose
        }
    }

    if (duplicateMatches.isNotEmpty()) {
        Dialog(onDismissRequest = {
            duplicateMatches = emptyList()
            onDismiss()
        }) {
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.nfc_uid_multiple_matches_title), style = MaterialTheme.typography.titleLarge)
                    duplicateMatches.forEach { option ->
                        Button(
                            onClick = {
                                duplicateMatches = emptyList()
                                onMatchFound(option.match)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(option.title)
                                Text(option.subtitle, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            duplicateMatches = emptyList()
                            onDismiss()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text(cancelLabel) }
                }
            }
        }
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(min = 320.dp, max = 520.dp)
        ) {
            Column(
                Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(Res.string.billeterie_button_scanner),
                    style = MaterialTheme.typography.titleLarge
                )

                when (mode) {
                    QrDialogMode.Choose -> {
                        Text(
                            text = when {
                                status != null -> status!!
                                isExternalReaderBusy -> waitingCardMsg
                                hasExternalReader -> scanWithReaderLabel
                                else -> readyMsg
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (status != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (DesktopExternalNfcReader.isBleConfigured(settingsManager)) {
                            BleReaderScannerStatusFooter(
                                platformContext = platformContext,
                                isExternalReaderBusy = isExternalReaderBusy,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                activeColor = MaterialTheme.colorScheme.primary,
                                idleWarnColor = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Button(
                            onClick = {
                                status = null
                                mode = QrDialogMode.Webcam
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(Res.string.desktop_qr_start_webcam))
                        }
                        OutlinedButton(
                            onClick = {
                                mode = QrDialogMode.ImageLoading
                                scope.launch {
                                    handleScanResult(qrScanner.scanFromImageFile())
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(Res.string.desktop_qr_open_image))
                        }
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.End)
                        ) { Text(cancelLabel) }
                    }

                    QrDialogMode.Webcam -> {
                        DesktopWebcamQrScanView(
                            onQrDetected = { handlePayload(it) },
                            onError = { msg ->
                                status = msg
                                mode = QrDialogMode.Choose
                            },
                            onCancel = { mode = QrDialogMode.Choose }
                        )
                    }

                    QrDialogMode.ImageLoading -> {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        Text(
                            stringResource(Res.string.desktop_qr_opening_file_picker),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
