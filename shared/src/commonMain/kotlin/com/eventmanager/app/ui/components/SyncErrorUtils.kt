package com.eventmanager.app.ui.components

fun shouldShowSyncError(errorMessage: String?): Boolean {
    if (errorMessage == null) return false
    val ignoredPatterns = listOf(
        "already exists",
        "duplicate",
        "integrity constraint",
        "does not exist",
    )
    return !ignoredPatterns.any { pattern ->
        errorMessage.contains(pattern, ignoreCase = true)
    }
}
