package com.eventmanager.app.utils

import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.ui.components.DataPoint
import com.eventmanager.app.ui.components.TimePeriod
import java.io.File

expect object GraphExportUtils {
    fun exportToXLSX(
        platformContext: PlatformContext,
        title: String,
        dataPoints: List<DataPoint>,
        trendPoints: List<DataPoint>,
        timePeriod: TimePeriod,
        graphImagePath: String?
    ): File

    fun exportToJPG(
        platformContext: PlatformContext,
        title: String,
        dataPoints: List<DataPoint>,
        trendPoints: List<DataPoint>,
        timePeriod: TimePeriod
    ): File
}
