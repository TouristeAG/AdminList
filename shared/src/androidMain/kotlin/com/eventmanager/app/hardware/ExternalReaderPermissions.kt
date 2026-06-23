package com.eventmanager.app.hardware

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Runtime permission helpers for USB host and Bluetooth external readers.
 * When the user chooses "Don't ask again" / equivalent for Bluetooth, [shouldOpenAppSettingsForBluetoothConnect]
 * becomes true and the app should offer opening system app details so they can grant access.
 */
object ExternalReaderPermissions {

    const val BLUETOOTH_CONNECT_DENIED = "Bluetooth permission denied"

    fun hasBluetoothConnect(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * `true` when the user has permanently denied [Manifest.permission.BLUETOOTH_CONNECT] from the
     * system dialog (no rationale, still not granted). Offer "App settings" in that case.
     */
    fun shouldOpenAppSettingsForBluetoothConnect(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        if (hasBluetoothConnect(activity)) return false
        return !ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }

    fun launchAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
    }
}
