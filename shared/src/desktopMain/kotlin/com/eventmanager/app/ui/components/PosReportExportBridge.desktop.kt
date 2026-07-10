package com.eventmanager.app.ui.components

import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.PlatformFileManager
import java.awt.Desktop
import java.io.File

internal actual object PosReportExportBridge {
    actual fun shareReport(platformContext: PlatformContext, file: File, title: String) {
        openReport(platformContext, file)
    }

    actual fun openReport(platformContext: PlatformContext, file: File) {
        runCatching {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file)
            }
        }
    }

    actual suspend fun saveReportToUserLocation(
        platformContext: PlatformContext,
        file: File,
        suggestedName: String,
    ): Boolean {
        val manager = PlatformFileManager(platformContext)
        return manager.saveFileToUserLocation(file, suggestedName, MIME_PDF)
    }

    private const val MIME_PDF = "application/pdf"
}
