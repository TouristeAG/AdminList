package com.eventmanager.app.data.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.eventmanager.app.BuildConfig

/**
 * Manages application icon aliases for dynamic icon switching
 * Supports dynamic icon switching via activity aliases and system theme adaptation
 */
class AppIconManager(private val context: Context) {

    companion object {
        const val ICON_WHITE = "white"
        const val ICON_BLACK = "black"
        const val ICON_DARK_BLUE = "dark_blue"
        const val ICON_DARK_TURQUOISE = "dark_turquoise"
        const val ICON_BROWN = "brown"
        const val ICON_CREME_BLACK = "creme_black"
        const val ICON_DARK_VIOLET = "dark_violet"
        const val ICON_DARK_LEGACY = "dark_legacy"
        const val ICON_LIGHT_LEGACY = "light_legacy"
        const val ICON_LIGHT_BLUE = "light_blue"
        const val ICON_LIGHT_VIOLET = "light_violet"
        const val ICON_ORANGE = "orange"
        const val ICON_PINK = "pink"
        const val DEFAULT_ICON = ICON_WHITE

        /** Preference / UI keys for the 13 launcher icon styles (see project ICON asset folders). */
        val ALL_ICON_STYLES = listOf(
            ICON_WHITE,
            ICON_BLACK,
            ICON_DARK_BLUE,
            ICON_DARK_TURQUOISE,
            ICON_BROWN,
            ICON_CREME_BLACK,
            ICON_DARK_VIOLET,
            ICON_DARK_LEGACY,
            ICON_LIGHT_LEGACY,
            ICON_LIGHT_BLUE,
            ICON_LIGHT_VIOLET,
            ICON_ORANGE,
            ICON_PINK
        )

        // Activity-alias android:name values in AndroidManifest.xml (first seven names are legacy for upgrades)
        private const val WHITE_ICON_SUFFIX = "LauncherIconLight"
        private const val BLACK_ICON_SUFFIX = "LauncherIconDark"
        private const val DARK_BLUE_ICON_SUFFIX = "LauncherIconDeepBlue"
        private const val DARK_TURQUOISE_ICON_SUFFIX = "LauncherIconBlueOcean"
        private const val BROWN_ICON_SUFFIX = "LauncherIconBraun"
        private const val CREME_BLACK_ICON_SUFFIX = "LauncherIconPurple"
        private const val DARK_VIOLET_ICON_SUFFIX = "LauncherIconViolet"
        private const val DARK_LEGACY_ICON_SUFFIX = "LauncherIconDarkLegacy"
        private const val LIGHT_LEGACY_ICON_SUFFIX = "LauncherIconLightLegacy"
        private const val LIGHT_BLUE_ICON_SUFFIX = "LauncherIconLightBlue"
        private const val LIGHT_VIOLET_ICON_SUFFIX = "LauncherIconLightViolet"
        private const val ORANGE_ICON_SUFFIX = "LauncherIconOrange"
        private const val PINK_ICON_SUFFIX = "LauncherIconPink"

        private val ICON_SUFFIX_MAP = mapOf(
            ICON_WHITE to WHITE_ICON_SUFFIX,
            ICON_BLACK to BLACK_ICON_SUFFIX,
            ICON_DARK_BLUE to DARK_BLUE_ICON_SUFFIX,
            ICON_DARK_TURQUOISE to DARK_TURQUOISE_ICON_SUFFIX,
            ICON_BROWN to BROWN_ICON_SUFFIX,
            ICON_CREME_BLACK to CREME_BLACK_ICON_SUFFIX,
            ICON_DARK_VIOLET to DARK_VIOLET_ICON_SUFFIX,
            ICON_DARK_LEGACY to DARK_LEGACY_ICON_SUFFIX,
            ICON_LIGHT_LEGACY to LIGHT_LEGACY_ICON_SUFFIX,
            ICON_LIGHT_BLUE to LIGHT_BLUE_ICON_SUFFIX,
            ICON_LIGHT_VIOLET to LIGHT_VIOLET_ICON_SUFFIX,
            ICON_ORANGE to ORANGE_ICON_SUFFIX,
            ICON_PINK to PINK_ICON_SUFFIX
        )
    }

    /**
     * Sets the app icon by enabling/disabling activity aliases
     * Based on Android best practices: disable all others, then enable the selected one
     */
    fun setAppIcon(iconType: String) {
        val packageManager = context.packageManager
        val packageName = context.packageName

        try {
            if (iconType !in ICON_SUFFIX_MAP) {
                println("⚠️ Unknown icon type: $iconType, using default: $DEFAULT_ICON")
                setAppIcon(DEFAULT_ICON)
                return
            }

            val targetSuffix = ICON_SUFFIX_MAP[iconType]!!
            val targetComponent = ComponentName(packageName, "$packageName.$targetSuffix")

            println("🔄 Setting icon to: $iconType")
            println("   Package: $packageName")
            println("   Target component: $targetComponent")

            println("   Disabling all other icon components...")
            ALL_ICON_STYLES.forEach { style ->
                if (style != iconType) {
                    // Keep the default white launcher alias enabled in debug builds.
                    // Android Studio can cache this alias as launch target and fail with
                    // "Activity class ... LauncherIconLight does not exist" if it gets disabled.
                    if (BuildConfig.DEBUG && style == ICON_WHITE) {
                        return@forEach
                    }
                    val suffix = ICON_SUFFIX_MAP[style]
                    if (suffix != null) {
                        val component = ComponentName(packageName, "$packageName.$suffix")
                        try {
                            packageManager.setComponentEnabledSetting(
                                component,
                                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                PackageManager.DONT_KILL_APP
                            )
                        } catch (e: Exception) {
                            println("   ⚠️ Warning disabling $style: ${e.message}")
                        }
                    }
                }
            }

            Thread.sleep(100)

            try {
                packageManager.setComponentEnabledSetting(
                    targetComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                println("   ✓ $iconType icon enabled")
            } catch (e: Exception) {
                println("   ❌ Error enabling $iconType: ${e.message}")
                throw e
            }

            val targetState = packageManager.getComponentEnabledSetting(targetComponent)
            val targetEnabled = targetState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

            println("✓ Verification:")
            println("   Target state: $targetState (enabled=$targetEnabled)")

            if (!targetEnabled) {
                println("❌ WARNING: $iconType icon should be enabled but isn't!")
            } else {
                println("✅ Icon change applied successfully!")
                println("   Note: Launcher may need to refresh. Restart app to see change immediately.")
            }
        } catch (e: SecurityException) {
            println("❌ SecurityException: ${e.message}")
            println("   This may indicate the app doesn't have permission to change components")
            e.printStackTrace()
        } catch (e: Exception) {
            println("❌ Error setting icon to $iconType: ${e.message}")
            e.printStackTrace()
        }
    }

    fun isSystemDarkMode(): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun getSystemAdaptedIcon(): String {
        return if (isSystemDarkMode()) ICON_BLACK else ICON_WHITE
    }

    fun getCurrentEnabledIcon(): String {
        val packageManager = context.packageManager
        val packageName = context.packageName

        return try {
            var enabledIcon: String? = null

            ALL_ICON_STYLES.forEach { style ->
                val suffix = ICON_SUFFIX_MAP[style]
                if (suffix != null) {
                    val component = ComponentName(packageName, "$packageName.$suffix")
                    val state = packageManager.getComponentEnabledSetting(component)
                    val enabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

                    if (enabled && enabledIcon == null) {
                        enabledIcon = style
                    }
                }
            }

            val result = enabledIcon
            if (result != null) {
                return result
            }

            println("⚠️ WARNING: No icon alias is enabled! Defaulting to $DEFAULT_ICON.")
            val defaultSuffix = ICON_SUFFIX_MAP[DEFAULT_ICON]!!
            val defaultComponent = ComponentName(packageName, "$packageName.$defaultSuffix")
            try {
                packageManager.setComponentEnabledSetting(
                    defaultComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                println("   ✓ Enabled $DEFAULT_ICON icon as fallback")
            } catch (e: Exception) {
                println("   ❌ Failed to enable fallback: ${e.message}")
            }
            DEFAULT_ICON
        } catch (e: Exception) {
            println("❌ Error checking current icon: ${e.message}")
            e.printStackTrace()
            DEFAULT_ICON
        }
    }
}
