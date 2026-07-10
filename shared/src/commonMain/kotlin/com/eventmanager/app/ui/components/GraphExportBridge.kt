package com.eventmanager.app.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import com.eventmanager.app.platform.PlatformContext
import java.io.File

internal enum class GraphExportType {
    XLSX, JPG
}

internal data class DistributionExportRow(
    val label: String,
    val count: Int,
    val percentage: Float
)

internal expect object GraphExportBridge {
    suspend fun exportDistributionXlsx(
        platformContext: PlatformContext,
        fileNamePrefix: String,
        sheetName: String,
        title: String,
        firstColumnHeader: String,
        rows: List<DistributionExportRow>
    ): File

    suspend fun exportLineGraph(
        platformContext: PlatformContext,
        title: String,
        dataPoints: List<DataPoint>,
        trendPoints: List<DataPoint>,
        timePeriod: TimePeriod,
        density: Density,
        exportType: GraphExportType
    ): File

    suspend fun exportPieChartJpg(
        platformContext: PlatformContext,
        title: String,
        segments: List<Pair<String, Pair<Float, Int>>>
    ): File

    fun shareExportedFile(
        platformContext: PlatformContext,
        file: File,
        exportType: GraphExportType,
        title: String
    )

    fun openExportedFile(
        platformContext: PlatformContext,
        file: File,
        exportType: GraphExportType
    )
}

internal expect fun decodeJpgPreview(file: File): ImageBitmap?
