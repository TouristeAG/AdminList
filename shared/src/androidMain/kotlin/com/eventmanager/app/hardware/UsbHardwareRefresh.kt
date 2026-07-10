package com.eventmanager.app.hardware

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Bumps a generation counter when USB devices attach/detach or Bluetooth state / ACL link
 * changes so Compose can re-read [ExternalAcsUidReader.isConnected] and restart polling.
 *
 * **Do not** listen for [Acr122uUsbNfcReader.USB_PERMISSION_ACTION] here: that broadcast runs while
 * [Acr122uUsbNfcReader.readUid] is suspended in the permission dialog; incrementing generation
 * restarts [androidx.compose.runtime.LaunchedEffect] and **cancels** the coroutine that owns the
 * permission [BroadcastReceiver], so the grant is missed and the UI shows "USB permission denied"
 * until the sheet is reopened.
 */
@Composable
fun rememberUsbHardwareGeneration(): Int {
    val context = LocalContext.current
    var generation by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val appContext = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED,
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> generation++
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(receiver, filter)
        }

        var btReceiver: BroadcastReceiver? = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            Acr1255uj1BleNfcReader.hasBluetoothConnectPermission(context)
        ) {
            val bt = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    when (intent?.action) {
                        BluetoothAdapter.ACTION_STATE_CHANGED,
                        BluetoothDevice.ACTION_ACL_CONNECTED,
                        BluetoothDevice.ACTION_ACL_DISCONNECTED,
                        BluetoothDevice.ACTION_BOND_STATE_CHANGED -> generation++
                    }
                }
            }
            btReceiver = bt
            val btFilter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(bt, btFilter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    appContext.registerReceiver(bt, btFilter)
                }
            } catch (_: SecurityException) {
                btReceiver = null
            }
        }

        onDispose {
            try {
                appContext.unregisterReceiver(receiver)
            } catch (_: Exception) {
                // Ignore if already unregistered.
            }
            btReceiver?.let { br ->
                try {
                    appContext.unregisterReceiver(br)
                } catch (_: Exception) {
                    // Ignore if already unregistered.
                }
            }
        }
    }
    return generation
}
