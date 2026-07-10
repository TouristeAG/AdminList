package com.eventmanager.app.platform

import androidx.fragment.app.FragmentActivity

/**
 * Holds the foreground [FragmentActivity] for APIs (e.g. [BiometricPrompt]) that require it.
 * [PlatformContext] uses [android.content.Context.getApplicationContext] to avoid leaks.
 */
object AndroidFragmentActivityProvider {
    @Volatile
    var current: FragmentActivity? = null
}
