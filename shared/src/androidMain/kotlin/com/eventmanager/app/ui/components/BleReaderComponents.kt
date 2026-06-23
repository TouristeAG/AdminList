package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eventmanager.app.R
import com.eventmanager.app.data.sync.settingsManagerFor
import com.eventmanager.app.hardware.BleReaderScanner
import com.eventmanager.app.platform.PlatformContext
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun BleReaderPickerDialog(
    platformContext: PlatformContext,
    onDismiss: () -> Unit,
    onPicked: (mac: String, name: String) -> Unit
) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf<List<BleReaderScanner.DiscoveredReader>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!BleReaderScanner.hasScanPermission(context)) {
            scanning = false
            errorMessage = context.getString(R.string.ble_reader_scan_permission_denied)
            return@LaunchedEffect
        }
        BleReaderScanner.scan(context).collectLatest { state ->
            when (state) {
                is BleReaderScanner.ScanState.Idle -> scanning = false
                is BleReaderScanner.ScanState.Scanning -> {
                    scanning = true
                    devices = state.devices
                }
                is BleReaderScanner.ScanState.Failed -> {
                    scanning = false
                    errorMessage = state.reason.name
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(context.getString(R.string.external_reader_pick_button)) },
        text = {
            when {
                scanning -> CircularProgressIndicator()
                errorMessage != null -> Text(errorMessage!!)
                devices.isEmpty() -> Text(context.getString(R.string.external_reader_description))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(devices, key = { it.mac }) { device ->
                        OutlinedButton(
                            onClick = { onPicked(device.mac, device.name ?: device.mac) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(device.name ?: device.mac)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.cancel))
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
    val context = platformContext.androidContext
    val settings = remember { settingsManagerFor(context) }
    val name = settings.getExternalBleReaderName()
    if (name.isNotBlank()) {
        Text(
            text = if (isExternalReaderBusy) "BLE: $name (busy)" else "BLE: $name",
            style = MaterialTheme.typography.labelSmall,
            color = if (isExternalReaderBusy) activeColor else idleWarnColor,
            modifier = modifier.padding(8.dp)
        )
    }
}
