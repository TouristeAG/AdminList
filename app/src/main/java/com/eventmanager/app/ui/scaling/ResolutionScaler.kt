package com.eventmanager.app.ui.scaling

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.R

/**
 * Resolution-based UI scaling system that modifies display density
 * to achieve UI scaling effects similar to changing render resolution
 */
object ResolutionScaler {
    
    /**
     * Get available resolution scale options (80% to 120%)
     */
    @Composable
    fun getResolutionOptions(): List<ResolutionOption> {
        val context = LocalContext.current
        return listOf(
            ResolutionOption(0.8f, context.getString(R.string.resolution_small), "80%"),
            ResolutionOption(0.9f, context.getString(R.string.resolution_medium_small), "90%"),
            ResolutionOption(1.0f, context.getString(R.string.resolution_normal), "100%"),
            ResolutionOption(1.1f, context.getString(R.string.resolution_medium_large), "110%"),
            ResolutionOption(1.2f, context.getString(R.string.resolution_large), "120%")
        )
    }
    
    /**
     * Get the current resolution scale factor from settings
     */
    @Composable
    fun getCurrentResolutionScale(): Float {
        val context = LocalContext.current
        val settingsManager = remember { SettingsManager(context) }
        return settingsManager.getResolutionScale()
    }
    
    /**
     * Save resolution scale factor to settings
     */
    @Composable
    fun saveResolutionScale(scale: Float) {
        val context = LocalContext.current
        val settingsManager = remember { SettingsManager(context) }
        settingsManager.saveResolutionScale(scale)
    }
    
    /**
     * Get resolution option by scale value
     */
    @Composable
    fun getResolutionOptionByScale(scale: Float): ResolutionOption {
        return getResolutionOptions().find { it.scale == scale } ?: getResolutionOptions()[2] // Default to Normal
    }
    
    /**
     * Apply resolution scaling to a context by modifying display density
     * This creates a new context with modified density that makes UI elements appear smaller/larger
     */
    fun applyResolutionScaling(context: Context, scale: Float): Context {
        val originalMetrics = context.resources.displayMetrics
        val originalDensity = originalMetrics.density
        val originalScaledDensity = originalMetrics.scaledDensity
        
        // Create new metrics with modified density
        val newMetrics = DisplayMetrics().apply {
            setTo(originalMetrics)
            // Increase density to make UI smaller, decrease to make UI larger
            // This is counterintuitive but works like changing render resolution
            density = originalDensity / scale
            scaledDensity = originalScaledDensity / scale
        }
        
        // Create new configuration with modified metrics
        val newConfig = Configuration(context.resources.configuration).apply {
            // Update density DPI
            densityDpi = (newMetrics.density * 160).toInt()
        }
        
        // Create new context with modified configuration
        return context.createConfigurationContext(newConfig)
    }
    
    /**
     * Get the effective density multiplier for the current resolution scale
     */
    @Composable
    fun getDensityMultiplier(): Float {
        val scale = getCurrentResolutionScale()
        // Return inverse of scale because higher scale should make UI smaller
        return 1.0f / scale
    }
}

/**
 * Data class representing a resolution scale option
 */
data class ResolutionOption(
    val scale: Float,
    val displayName: String,
    val percentage: String
)
