package com.eventmanager.app.data.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * Manages application icon aliases for dynamic icon switching
 * Supports dynamic icon switching via activity aliases and system theme adaptation
 */
class AppIconManager(private val context: Context) {
    
    companion object {
        const val ICON_LIGHT = "light"
        const val ICON_DARK = "dark"
        const val ICON_DEEP_BLUE = "deep_blue"
        const val ICON_BLUE_OCEAN = "blue_ocean"
        const val ICON_BRAUN = "braun"
        const val ICON_PURPLE = "purple"
        const val ICON_VIOLET = "violet"
        const val DEFAULT_ICON = ICON_LIGHT
        
        // All available icon styles
        val ALL_ICON_STYLES = listOf(
            ICON_LIGHT,
            ICON_DARK,
            ICON_DEEP_BLUE,
            ICON_BLUE_OCEAN,
            ICON_BRAUN,
            ICON_PURPLE,
            ICON_VIOLET
        )
        
        // Activity aliases that need to be enabled/disabled
        // These correspond to the alias activities in AndroidManifest.xml
        private const val LIGHT_ICON_SUFFIX = "LauncherIconLight"
        private const val DARK_ICON_SUFFIX = "LauncherIconDark"
        private const val DEEP_BLUE_ICON_SUFFIX = "LauncherIconDeepBlue"
        private const val BLUE_OCEAN_ICON_SUFFIX = "LauncherIconBlueOcean"
        private const val BRAUN_ICON_SUFFIX = "LauncherIconBraun"
        private const val PURPLE_ICON_SUFFIX = "LauncherIconPurple"
        private const val VIOLET_ICON_SUFFIX = "LauncherIconViolet"
        
        // Map icon types to their component suffixes
        private val ICON_SUFFIX_MAP = mapOf(
            ICON_LIGHT to LIGHT_ICON_SUFFIX,
            ICON_DARK to DARK_ICON_SUFFIX,
            ICON_DEEP_BLUE to DEEP_BLUE_ICON_SUFFIX,
            ICON_BLUE_OCEAN to BLUE_OCEAN_ICON_SUFFIX,
            ICON_BRAUN to BRAUN_ICON_SUFFIX,
            ICON_PURPLE to PURPLE_ICON_SUFFIX,
            ICON_VIOLET to VIOLET_ICON_SUFFIX
        )
    }
    
    /**
     * Sets the app icon by enabling/disabling activity aliases
     * Based on Android best practices: disable all others, then enable the selected one
     * @param iconType One of the available icon types (light, dark, deep_blue, blue_ocean, braun, purple, violet)
     */
    fun setAppIcon(iconType: String) {
        val packageManager = context.packageManager
        val packageName = context.packageName
        
        try {
            // Validate icon type
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
            
            // Disable all icon components first
            println("   Disabling all other icon components...")
            ALL_ICON_STYLES.forEach { style ->
                if (style != iconType) {
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
            
            // Small delay to ensure state is committed
            Thread.sleep(100)
            
            // Enable the target icon component
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
            
            // Verify the change took effect
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
    
    /**
     * Determines if the system is in dark mode
     */
    fun isSystemDarkMode(): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
    
    /**
     * Gets the appropriate icon style based on system theme
     */
    fun getSystemAdaptedIcon(): String {
        return if (isSystemDarkMode()) ICON_DARK else ICON_LIGHT
    }
    
    /**
     * Gets the currently enabled icon alias
     */
    fun getCurrentEnabledIcon(): String {
        val packageManager = context.packageManager
        val packageName = context.packageName
        
        return try {
            // Check all icon components to find which one is enabled
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
            
            // If no icon is enabled, enable default as fallback
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

