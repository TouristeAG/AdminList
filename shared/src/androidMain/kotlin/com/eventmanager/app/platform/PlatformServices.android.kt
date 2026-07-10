package com.eventmanager.app.platform

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.room.Room
import com.eventmanager.app.data.database.EventManagerDatabase
import com.eventmanager.app.data.database.EventManagerDatabaseAndroid
import java.util.Locale

actual fun createDatabase(context: PlatformContext): EventManagerDatabase =
    EventManagerDatabaseAndroid.getDatabase(context.androidContext)

actual fun openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // Best-effort; caller should have a valid context in Android shell
}

actual fun vibrateShort(context: PlatformContext) {
    val ctx = context.androidContext
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = ctx.getSystemService(VibratorManager::class.java)
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        ctx.getSystemService(Vibrator::class.java)
    } ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(50)
    }
}

actual fun getSystemLocaleTag(): String = Locale.getDefault().toLanguageTag()
