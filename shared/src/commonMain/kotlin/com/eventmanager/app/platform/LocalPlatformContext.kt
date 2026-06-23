package com.eventmanager.app.platform

import androidx.compose.runtime.compositionLocalOf

val LocalPlatformContext = compositionLocalOf<PlatformContext> {
    error("PlatformContext not provided")
}
