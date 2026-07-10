package com.eventmanager.app.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Density
import androidx.core.content.FileProvider
import com.eventmanager.app.R
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.utils.GraphExportAndroid
import java.io.File
import java.io.FileOutputStream

internal actual object GraphExportBridge {
    actual suspend fun exportDistributionXlsx(
        platformContext: PlatformContext,
        fileNamePrefix: String,
        sheetName: String,
        title: String,
        firstColumnHeader: String,
        rows: List<DistributionExportRow>
    ): File {
        val context = platformContext.androidContext
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val file = File(context.cacheDir, "${fileNamePrefix}_${timestamp}.xlsx")
        val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook()
        val sheet = workbook.createSheet(sheetName.take(31))
        val titleRow = sheet.createRow(0)
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue(title)
        val titleStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.bold = true
            font.fontHeightInPoints = 16
            setFont(font)
        }
        titleCell.cellStyle = titleStyle
        val headerRow = sheet.createRow(2)
        headerRow.createCell(0).setCellValue(firstColumnHeader)
        headerRow.createCell(1).setCellValue("Count")
        headerRow.createCell(2).setCellValue("Percentage")
        rows.forEachIndexed { index, row ->
            val dataRow = sheet.createRow(3 + index)
            dataRow.createCell(0).setCellValue(row.label)
            dataRow.createCell(1).setCellValue(row.count.toDouble())
            dataRow.createCell(2).setCellValue("${String.format("%.1f", row.percentage)}%")
        }
        sheet.setColumnWidth(0, 5000)
        sheet.setColumnWidth(1, 3000)
        sheet.setColumnWidth(2, 3000)
        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()
        return file
    }

    actual suspend fun exportLineGraph(
        platformContext: PlatformContext,
        title: String,
        dataPoints: List<DataPoint>,
        trendPoints: List<DataPoint>,
        timePeriod: TimePeriod,
        density: Density,
        exportType: GraphExportType
    ): File {
        val context = platformContext.androidContext
        val graphBitmap = GraphExportAndroid.renderGraphAsBitmap(
            dataPoints = dataPoints,
            trendPoints = trendPoints,
            title = title,
            timePeriod = timePeriod,
            density = density
        )
        return when (exportType) {
            GraphExportType.XLSX -> GraphExportAndroid.exportToXLSX(
                context = context,
                title = title,
                dataPoints = dataPoints,
                trendPoints = trendPoints,
                timePeriod = timePeriod,
                graphBitmap = graphBitmap
            )
            GraphExportType.JPG -> GraphExportAndroid.exportToJPG(context, graphBitmap, title)
        }
    }

    actual suspend fun exportPieChartJpg(
        platformContext: PlatformContext,
        title: String,
        segments: List<Pair<String, Pair<Float, Int>>>
    ): File {
        val context = platformContext.androidContext
        val bitmap = GraphExportAndroid.renderPieChartAsBitmap(segments, title)
        return GraphExportAndroid.exportToJPG(context, bitmap, title)
    }

    actual fun shareExportedFile(
        platformContext: PlatformContext,
        file: File,
        exportType: GraphExportType,
        title: String
    ) {
        shareFile(platformContext.androidContext, file, exportType, title)
    }

    actual fun openExportedFile(
        platformContext: PlatformContext,
        file: File,
        exportType: GraphExportType
    ) {
        openFile(platformContext.androidContext, file, exportType)
    }
}

internal actual fun decodeJpgPreview(file: File): ImageBitmap? {
    return BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
}

private fun shareFile(context: Context, file: File, exportType: GraphExportType, title: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = when (exportType) {
            GraphExportType.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            GraphExportType.JPG -> "image/jpeg"
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, context.getString(R.string.share_graph)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }
}

private fun openFile(context: Context, file: File, exportType: GraphExportType) {
    runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = when (exportType) {
            GraphExportType.XLSX -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            GraphExportType.JPG -> "image/jpeg"
        }
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserIntent = Intent.createChooser(viewIntent, context.getString(R.string.open_with)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooserIntent)
    }
}
