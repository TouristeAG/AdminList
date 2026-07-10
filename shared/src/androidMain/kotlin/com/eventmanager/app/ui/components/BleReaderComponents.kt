package com.eventmanager.app.ui.components

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eventmanager.app.R
import com.eventmanager.app.data.sync.settingsManagerFor
import com.eventmanager.app.hardware.Acr1255uj1BleNfcReader
import com.eventmanager.app.hardware.BleReaderScanner
import com.eventmanager.app.hardware.ExternalReaderPermissions
import com.eventmanager.app.platform.PlatformContext
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
    val context = LocalContext.current
    val activity = context as? Activity
    var devices by remember { mutableStateOf<List<BleReaderScanner.DiscoveredReader>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var permissionDeniedReason by remember { mutableStateOf<BleReaderScanner.ScanState.Reason?>(null) }
    var permissionsReady by remember { mutableStateOf(false) }
    var connectPermissionAsked by remember { mutableStateOf(false) }

    val unnamedLabel = stringResource(Res.string.ble_reader_unnamed)

    fun hasRequiredPermissions(): Boolean {
        if (!BleReaderScanner.hasScanPermission(context)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !Acr1255uj1BleNfcReader.hasBluetoothConnectPermission(context)
        ) {
            return false
        }
        return true
    }

    fun reasonToMessage(reason: BleReaderScanner.ScanState.Reason): String = when (reason) {
        BleReaderScanner.ScanState.Reason.BluetoothOff ->
            context.getString(R.string.ble_reader_bluetooth_off)
        BleReaderScanner.ScanState.Reason.BluetoothUnavailable ->
            context.getString(R.string.ble_reader_bluetooth_unavailable)
        BleReaderScanner.ScanState.Reason.ScanPermissionDenied ->
            context.getString(R.string.ble_reader_scan_permission_denied)
        BleReaderScanner.ScanState.Reason.ConnectPermissionDenied ->
            context.getString(R.string.ble_reader_connect_permission_denied)
        BleReaderScanner.ScanState.Reason.LocationPermissionDenied ->
            context.getString(R.string.ble_reader_location_permission_denied)
        BleReaderScanner.ScanState.Reason.ScannerUnavailable ->
            context.getString(R.string.ble_reader_scanner_unavailable)
        BleReaderScanner.ScanState.Reason.LowLevelError ->
            context.getString(R.string.ble_reader_low_level_error)
    }

    val connectPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        connectPermissionAsked = true
        permissionsReady = hasRequiredPermissions()
        if (!permissionsReady) {
            scanning = false
            errorMessage = context.getString(R.string.ble_reader_connect_permission_denied)
            permissionDeniedReason = BleReaderScanner.ScanState.Reason.ConnectPermissionDenied
        }
    }

    val scanPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (it && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !Acr1255uj1BleNfcReader.hasBluetoothConnectPermission(context)
        ) {
            connectPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissionsReady = hasRequiredPermissions()
            if (!permissionsReady) {
                scanning = false
                errorMessage = if (!BleReaderScanner.hasScanPermission(context)) {
                    context.getString(R.string.ble_reader_scan_permission_denied)
                } else {
                    context.getString(R.string.ble_reader_connect_permission_denied)
                }
                permissionDeniedReason = BleReaderScanner.ScanState.Reason.ScanPermissionDenied
            }
        }
    }

    LaunchedEffect(Unit) {
        when {
            !BleReaderScanner.hasScanPermission(context) -> {
                scanPermissionLauncher.launch(BleReaderScanner.scanPermission())
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !Acr1255uj1BleNfcReader.hasBluetoothConnectPermission(context) -> {
                connectPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
            else -> permissionsReady = true
        }
    }

    LaunchedEffect(permissionsReady) {
        if (!permissionsReady) return@LaunchedEffect
        errorMessage = null
        permissionDeniedReason = null
        BleReaderScanner.scan(context, preferAcrOnly = false).collectLatest { state ->
            when (state) {
                is BleReaderScanner.ScanState.Idle -> scanning = false
                is BleReaderScanner.ScanState.Scanning -> {
                    scanning = true
                    devices = state.devices
                    errorMessage = null
                }
                is BleReaderScanner.ScanState.Failed -> {
                    scanning = false
                    errorMessage = reasonToMessage(state.reason)
                    permissionDeniedReason = state.reason
                }
            }
        }
    }

    val showAppSettings = permissionDeniedReason == BleReaderScanner.ScanState.Reason.ConnectPermissionDenied &&
        activity != null &&
        connectPermissionAsked &&
        ExternalReaderPermissions.shouldOpenAppSettingsForBluetoothConnect(activity)

    val pickerItems = remember(devices, unnamedLabel) {
        devices.map { device ->
            BleReaderPickerItem(
                pickId = device.mac,
                displayName = device.name?.takeIf { it.isNotBlank() } ?: unnamedLabel,
                mac = device.mac,
                rssi = device.rssi,
                bonded = device.bonded,
                isAcrReader = device.matchesAcr1255,
            )
        }
    }
    val acrReaders = remember(pickerItems) { pickerItems.filter { it.isAcrReader } }
    val otherReaders = remember(pickerItems) { pickerItems.filter { !it.isAcrReader } }

    BleReaderPickerSheet(
        scanning = scanning && permissionsReady && errorMessage == null,
        errorContent = when {
            !permissionsReady && errorMessage == null -> {
                { Text(stringResource(Res.string.ble_reader_scanning)) }
            }
            errorMessage != null -> {
                {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                    if (permissionDeniedReason == BleReaderScanner.ScanState.Reason.ScanPermissionDenied ||
                        permissionDeniedReason == BleReaderScanner.ScanState.Reason.LocationPermissionDenied
                    ) {
                        TextButton(
                            onClick = { scanPermissionLauncher.launch(BleReaderScanner.scanPermission()) }
                        ) {
                            Text(stringResource(Res.string.ble_reader_enable_bluetooth))
                        }
                    }
                    if (permissionDeniedReason == BleReaderScanner.ScanState.Reason.ConnectPermissionDenied) {
                        if (showAppSettings) {
                            TextButton(
                                onClick = { ExternalReaderPermissions.launchAppDetailsSettings(context) }
                            ) {
                                Text(stringResource(Res.string.external_reader_bt_blocked_hint))
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    connectPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                }
                            ) {
                                Text(stringResource(Res.string.ble_reader_connect_permission_denied))
                            }
                        }
                    }
                }
            }
            else -> null
        },
        acrReaders = acrReaders,
        otherReaders = otherReaders,
        onPick = { item -> onPicked(item.pickId, item.displayName) },
        onDismiss = onDismiss,
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
