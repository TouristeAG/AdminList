package com.eventmanager.app.ui.components

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.eventmanager.app.platform.PlatformContext

private const val LOW_RAM_BYTES = 3L * 1024L * 1024L * 1024L // 3 GiB

@Volatile
private var cachedResult: Boolean? = null

/**
 * Heuristics for staff tablets and other weak Android devices:
 * - Known weak models (NVIDIA Shield tablet)
 * - System low-RAM flag
 * - Under ~3 GiB total RAM
 * - Android 12 and below (topographic falls back to an expensive CPU path)
 */
actual fun isLowPerformanceDeviceForBackgroundAnimation(platformContext: PlatformContext): Boolean {
    cachedResult?.let { return it }

    val result = evaluate(platformContext.androidContext)
    cachedResult = result
    return result
}

private fun evaluate(context: Context): Boolean {
    if (isNvidiaShieldTablet()) return true

    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        ?: return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU

    if (activityManager.isLowRamDevice) return true

    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)
    if (memInfo.totalMem in 1 until LOW_RAM_BYTES) return true

    // Pre-AGSL: topographic uses CPU noise + Canvas points — too heavy for settings previews.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true

    return false
}
