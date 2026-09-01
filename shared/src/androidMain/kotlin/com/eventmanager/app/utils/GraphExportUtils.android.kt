package com.eventmanager.app.utils

import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.ui.components.BarExportItem
import com.eventmanager.app.ui.components.GraphSeriesExport
import com.eventmanager.app.ui.components.TimePeriod
import java.io.File

internal actual object GraphExportUtils {
    actual fun exportToXLSX(
        platformContext: PlatformContext,
        title: String,
        series: List<GraphSeriesExport>,
        timePeriod: TimePeriod,
        graphImagePath: String?
    ): File {
        throw UnsupportedOperationException("XLSX export is supported on desktop builds")
    }

    actual fun exportToJPG(
        platformContext: PlatformContext,
        title: String,
        series: List<GraphSeriesExport>,
        timePeriod: TimePeriod
    ): File {
        val cache = platformContext.androidContext.cacheDir
        return File(cache, "graph_export_${System.currentTimeMillis()}.jpg").also {
            it.writeBytes(byteArrayOf())
        }
    }

    actual fun exportBarChartToJPG(
        platformContext: PlatformContext,
        title: String,
        bars: List<BarExportItem>,
    ): File {
        val cache = platformContext.androidContext.cacheDir
        return File(cache, "graph_export_${System.currentTimeMillis()}.jpg").also {
            it.writeBytes(byteArrayOf())
        }
    }
}
