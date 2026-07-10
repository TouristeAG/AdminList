package com.eventmanager.app.ui.components

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.eventmanager.app.R
import com.eventmanager.app.platform.PlatformContext
import java.io.File

internal actual object PosReportExportBridge {
    actual fun shareReport(platformContext: PlatformContext, file: File, title: String) {
        val context = platformContext.androidContext
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = MIME_PDF
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startChooser(context, intent, context.getString(R.string.pos_report_share))
        }
    }

    actual fun openReport(platformContext: PlatformContext, file: File) {
        val context = platformContext.androidContext
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, MIME_PDF)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startChooser(context, intent, context.getString(R.string.pos_report_open))
        }
    }

    actual suspend fun saveReportToUserLocation(
        platformContext: PlatformContext,
        file: File,
        suggestedName: String,
    ): Boolean = false

    private fun startChooser(context: Context, intent: Intent, title: String) {
        val chooser = Intent.createChooser(intent, title).apply {
            // PlatformContext holds applicationContext — required for startActivity.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }

    private const val MIME_PDF = "application/pdf"
}
