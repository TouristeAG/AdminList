package com.eventmanager.app.platform

import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

actual fun playAnnouncementReceivedFeedback(context: PlatformContext) {
    playAnnouncementVibration(context)
    playAnnouncementSound(context)
}

private fun playAnnouncementVibration(context: PlatformContext) {
    val ctx = context.androidContext
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        ctx.getSystemService(Vibrator::class.java)
    } ?: return

    // bzz — pause — bzz
    val pattern = longArrayOf(0L, 110L, 75L, 110L)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val amplitudes = intArrayOf(0, 200, 0, 200)
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(pattern, -1)
    }
}

private fun playAnnouncementSound(context: PlatformContext) {
    runCatching {
        val ctx = context.androidContext
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        val ringtone = RingtoneManager.getRingtone(ctx, uri) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        }
        ringtone.play()
    }
}
