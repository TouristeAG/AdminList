package com.eventmanager.app.utils

import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.ui.components.DataPoint
import com.eventmanager.app.ui.components.TimePeriod
import java.io.File

actual object GraphExportUtils {
    actual fun exportToXLSX(
        platformContext: PlatformContext,
        title: String,
        dataPoints: List<DataPoint>,
        trendPoints: List<DataPoint>,
        timePeriod: TimePeriod,
        graphImagePath: String?
    ): File {
        throw UnsupportedOperationException("XLSX export is supported on desktop builds")
    }

    actual fun exportToJPG(
        platformContext: PlatformContext,
        title: String,
        dataPoints: List<DataPoint>,
        trendPoints: List<DataPoint>,
        timePeriod: TimePeriod
    ): File {
        val cache = platformContext.androidContext.cacheDir
        return File(cache, "graph_export_${System.currentTimeMillis()}.jpg").also {
            it.writeBytes(byteArrayOf())
        }
    }
}
