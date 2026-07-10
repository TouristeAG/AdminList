package com.eventmanager.app.ui

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.vibrateShort

fun performSubtleHaptic(vibrator: Vibrator?) {
    vibrator ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(20)
    }
}

fun performStrongHaptic(vibrator: Vibrator?) {
    vibrator ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(80)
    }
}

fun performSubtleHaptic(platformContext: PlatformContext) = vibrateShort(platformContext)
fun performStrongHaptic(platformContext: PlatformContext) = vibrateShort(platformContext)
