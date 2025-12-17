package com.eventmanager.app.ui.theme

/**
 * Enum representing the available theme modes for the app
 */
enum class ThemeMode(val value: String) {
    LIGHT("light"),
    DARK("dark"),
    DEFAULT("default");
    
    companion object {
        fun fromString(value: String): ThemeMode {
            return when (value) {
                "light" -> LIGHT
                "dark" -> DARK
                "default" -> DEFAULT
                else -> DEFAULT
            }
        }
    }
}
