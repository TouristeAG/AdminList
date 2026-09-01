package com.eventmanager.app.utils

import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.ui.components.BarExportItem
import com.eventmanager.app.ui.components.GraphSeriesExport
import com.eventmanager.app.ui.components.TimePeriod
import java.io.File

internal expect object GraphExportUtils {
    fun exportToXLSX(
        platformContext: PlatformContext,
        title: String,
        series: List<GraphSeriesExport>,
        timePeriod: TimePeriod,
        graphImagePath: String?
    ): File

    fun exportToJPG(
        platformContext: PlatformContext,
        title: String,
        series: List<GraphSeriesExport>,
        timePeriod: TimePeriod
    ): File

    fun exportBarChartToJPG(
        platformContext: PlatformContext,
        title: String,
        bars: List<BarExportItem>,
    ): File
}
