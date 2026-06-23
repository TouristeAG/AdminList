package com.eventmanager.app.ui.components

import android.app.Activity
import android.content.Context
import kotlin.system.exitProcess

/**
 * Relaunches the app process so launcher icon / activity-alias changes take effect.
 * Falls back to [Activity.recreate] if relaunch intent is unavailable.
 */
fun restartApp(context: Context) {
    val activity = context as? Activity ?: return
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Thread {
                Thread.sleep(300)
                exitProcess(0)
            }.start()
            return
        }
    } catch (e: Exception) {
        println("Error relaunching app: ${e.message}")
    }
    try {
        activity.recreate()
    } catch (ex: Exception) {
        println("Error restarting: ${ex.message}")
    }
}
