package com.eventmanager.app.platform

import android.content.pm.PackageManager
import android.os.Build

/** Installed APK version from the package manager (authoritative for update checks). */
fun PlatformContext.installedVersionCode(): Int {
    val packageManager = androidContext.packageManager
    val packageName = androidContext.packageName
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode.toInt()
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode
    }
}

fun PlatformContext.installedVersionName(): String {
    val packageManager = androidContext.packageManager
    val packageName = androidContext.packageName
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    return packageInfo.versionName.orEmpty()
}
