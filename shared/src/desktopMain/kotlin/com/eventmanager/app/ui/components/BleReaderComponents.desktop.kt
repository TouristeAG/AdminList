package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun BleReaderPickerDialog(
    platformContext: PlatformContext,
    onDismiss: () -> Unit,
    onPicked: (mac: String, name: String) -> Unit
) {
    var devices by remember { mutableStateOf<List<DesktopBleReaderScanner.DiscoveredReader>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAllDevices by remember { mutableStateOf(false) }
    val btUnavailableMsg = stringResource(Res.string.ble_reader_bluetooth_unavailable)
    val lowLevelErrorMsg = stringResource(Res.string.ble_reader_low_level_error)

    LaunchedEffect(showAllDevices) {
        scanning = true
        errorMessage = null
        DesktopBleReaderScanner.scan(preferAcrOnly = !showAllDevices).collectLatest { state ->
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.ble_reader_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(Res.string.desktop_ble_reader_picker_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { showAllDevices = !showAllDevices }) {
                    Text(
                        if (showAllDevices) {
                            stringResource(Res.string.ble_reader_show_acr_only)
                        } else {
                            stringResource(Res.string.ble_reader_show_all)
                        }
                    )
                }
                when {
                    scanning && devices.isEmpty() -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(Res.string.ble_reader_scanning))
                        }
                        Text(
                            stringResource(Res.string.ble_reader_scanning_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    errorMessage != null -> Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                    devices.isEmpty() -> Text(stringResource(Res.string.desktop_ble_reader_not_found))
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(devices, key = { it.mac }) { device ->
                            val displayName = device.name ?: stringResource(Res.string.ble_reader_unnamed)
                            val subtitle = if (device.pcscReady) {
                                stringResource(Res.string.desktop_ble_reader_pcsc_ready, device.mac)
                            } else if (device.bonded) {
                                stringResource(Res.string.ble_reader_row_subtitle_bonded, device.mac, displayName)
                            } else {
                                stringResource(Res.string.ble_reader_row_subtitle, device.mac, displayName)
                            }
                            OutlinedButton(
                                onClick = {
                                    val id = if (device.pcscReady && device.name != null) {
                                        DesktopPcscCardReader.terminalId(device.name)
                                    } else {
                                        device.mac
                                    }
                                    onPicked(id, displayName)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (device.pcscReady) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(displayName, fontWeight = FontWeight.SemiBold)
                                    }
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        }
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
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(
            text = "${stringResource(Res.string.scanner_ble_reader_footer_label)}: $name — $statusText",
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
