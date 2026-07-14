package com.eventmanager.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eventmanager.app.data.sync.settingsManagerFor
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.hardware.DesktopBleReaderScanner
import com.eventmanager.app.platform.hardware.DesktopExternalNfcReader
import com.eventmanager.app.platform.hardware.DesktopPcscCardReader
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun BleReaderPickerDialog(
    platformContext: PlatformContext,
    onDismiss: () -> Unit,
    onPicked: (mac: String, name: String) -> Unit
) {
    var devices by remember { mutableStateOf<List<DesktopBleReaderScanner.DiscoveredReader>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val btUnavailableMsg = stringResource(Res.string.ble_reader_bluetooth_unavailable)
    val lowLevelErrorMsg = stringResource(Res.string.ble_reader_low_level_error)
    val unnamedLabel = stringResource(Res.string.ble_reader_unnamed)
    val pcscReadyLabel = stringResource(Res.string.desktop_ble_reader_pcsc_ready)
    val notPcscReadyLabel = stringResource(Res.string.desktop_ble_reader_not_pcsc_ready)
    val pairedNotPcscToolHint = stringResource(Res.string.desktop_ble_paired_not_pcsc_tool_hint)

    LaunchedEffect(Unit) {
        DesktopBleReaderScanner.scan(preferAcrOnly = false).collectLatest { state ->
            when (state) {
                is DesktopBleReaderScanner.ScanState.Idle -> scanning = false
                is DesktopBleReaderScanner.ScanState.Scanning -> {
                    scanning = true
                    devices = state.devices
                }
                is DesktopBleReaderScanner.ScanState.Failed -> {
                    scanning = false
                    errorMessage = when (state.reason) {
                        DesktopBleReaderScanner.ScanState.Reason.BluetoothUnavailable -> btUnavailableMsg
                        DesktopBleReaderScanner.ScanState.Reason.LowLevelError -> lowLevelErrorMsg
                    }
                }
            }
        }
    }

    val pickerItems = remember(devices, unnamedLabel, pcscReadyLabel, notPcscReadyLabel, pairedNotPcscToolHint) {
        devices.map { device ->
            val displayName = device.name?.takeIf { it.isNotBlank() } ?: unnamedLabel
            BleReaderPickerItem(
                pickId = if (device.pcscReady && device.name != null) {
                    DesktopPcscCardReader.terminalId(device.name)
                } else {
                    device.mac
                },
                displayName = displayName,
                mac = device.mac,
                rssi = device.rssi,
                bonded = device.bonded,
                isAcrReader = device.matchesAcr1255,
                pcscReady = device.pcscReady,
                statusHint = when {
                    device.pcscReady -> pcscReadyLabel
                    device.bonded || device.matchesAcr1255 ->
                        "$notPcscReadyLabel — $pairedNotPcscToolHint"
                    else -> null
                },
            )
        }
    }
    val acrReaders = remember(pickerItems) { pickerItems.filter { it.isAcrReader || it.pcscReady } }
    val otherReaders = remember(pickerItems) { pickerItems.filter { !it.isAcrReader && !it.pcscReady } }

    BleReaderPickerSheet(
        scanning = scanning && errorMessage == null,
        errorContent = errorMessage?.let { message ->
            {
                Text(message, color = MaterialTheme.colorScheme.error)
                if (!DesktopBleReaderScanner.isBlePcscSupportedOnPlatform()) {
                    val os = System.getProperty("os.name").orEmpty().lowercase()
                    Text(
                        text = stringResource(
                            if (os.contains("mac") || os.contains("darwin")) {
                                Res.string.desktop_ble_unsupported_macos
                            } else {
                                Res.string.desktop_ble_unsupported_linux
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        acrReaders = acrReaders,
        otherReaders = otherReaders,
        onPick = { item -> onPicked(item.pickId, item.displayName) },
        onDismiss = onDismiss,
        topContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.desktop_ble_reader_picker_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (DesktopBleReaderScanner.isBlePcscSupportedOnPlatform()) {
                    Text(
                        text = stringResource(Res.string.desktop_acs_prefer_usb),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
    )
}

@Composable
actual fun BleReaderScannerStatusFooter(
    platformContext: PlatformContext,
    isExternalReaderBusy: Boolean,
    labelColor: Color,
    activeColor: Color,
    idleWarnColor: Color,
    modifier: Modifier
) {
    val settings = remember(platformContext) { settingsManagerFor(platformContext) }
    val configured = remember(settings) { DesktopExternalNfcReader.isBleConfigured(settings) }
    if (!configured) return

    val name = settings.getExternalBleReaderName().ifBlank {
        stringResource(Res.string.ble_reader_unnamed)
    }
    var linkState by remember {
        mutableStateOf(DesktopExternalNfcReader.BleLinkState.Offline)
    }
    LaunchedEffect(settings) {
        while (isActive) {
            val status = withContext(Dispatchers.IO) {
                DesktopExternalNfcReader.refreshStatus(settings)
            }
            linkState = status.bleLinkState
            delay(1_500)
        }
    }

    val statusText = when {
        isExternalReaderBusy -> stringResource(Res.string.scanner_ble_reader_footer_reading)
        linkState == DesktopExternalNfcReader.BleLinkState.Ready ->
            stringResource(Res.string.scanner_ble_reader_footer_connected)
        linkState == DesktopExternalNfcReader.BleLinkState.Connecting ->
            stringResource(Res.string.scanner_ble_reader_footer_connecting)
        else -> stringResource(Res.string.scanner_ble_reader_footer_disconnected)
    }
    val color = when {
        isExternalReaderBusy -> activeColor
        linkState == DesktopExternalNfcReader.BleLinkState.Ready -> activeColor
        linkState == DesktopExternalNfcReader.BleLinkState.Connecting -> labelColor
        else -> idleWarnColor
    }

    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(
            text = "${stringResource(Res.string.scanner_ble_reader_footer_label)}: $name — $statusText",
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
