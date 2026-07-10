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
import kotlinx.coroutines.flow.collectLatest
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

    val pickerItems = remember(devices, unnamedLabel) {
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
            Text(
                text = stringResource(Res.string.desktop_ble_reader_picker_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    val linked = remember(settings) { DesktopExternalNfcReader.isBleLinkActive(settings) }
    val statusText = when {
        isExternalReaderBusy -> stringResource(Res.string.scanner_ble_reader_footer_reading)
        linked -> stringResource(Res.string.scanner_ble_reader_footer_connected)
        else -> stringResource(Res.string.scanner_ble_reader_footer_disconnected)
    }
    val color = when {
        isExternalReaderBusy -> activeColor
        linked -> activeColor
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
