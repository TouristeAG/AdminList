package com.eventmanager.app.ui.components

import com.eventmanager.app.platform.PlatformContext
import java.io.File

internal expect object PosReportExportBridge {
    fun shareReport(platformContext: PlatformContext, file: File, title: String)
    fun openReport(platformContext: PlatformContext, file: File)
    suspend fun saveReportToUserLocation(platformContext: PlatformContext, file: File, suggestedName: String): Boolean
}
