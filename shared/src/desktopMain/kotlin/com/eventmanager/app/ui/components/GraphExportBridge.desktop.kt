package com.eventmanager.app.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.PlatformFileManager
import com.eventmanager.app.utils.GraphExportUtils
import org.jetbrains.skia.Image
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Desktop
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.imageio.ImageIO

internal actual object GraphExportBridge {
    actual suspend fun exportDistributionXlsx(
        platformContext: PlatformContext,
        fileNamePrefix: String,
        sheetName: String,
        title: String,
        firstColumnHeader: String,
        rows: List<DistributionExportRow>
    ): File {
        val cache = PlatformFileManager(platformContext).getCacheDirectory()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(cache, "${fileNamePrefix}_${timestamp}.xlsx")
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet(sheetName.take(31))
            sheet.createRow(0).createCell(0).setCellValue(title)
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
            FileOutputStream(file).use { workbook.write(it) }
        }
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
        return when (exportType) {
            GraphExportType.XLSX -> GraphExportUtils.exportToXLSX(
                platformContext, title, dataPoints, trendPoints, timePeriod, null
            )
            GraphExportType.JPG -> GraphExportUtils.exportToJPG(
                platformContext, title, dataPoints, trendPoints, timePeriod
            )
        }
    }

    actual suspend fun exportPieChartJpg(
        platformContext: PlatformContext,
        title: String,
        segments: List<Pair<String, Pair<Float, Int>>>
    ): File {
        val cache = PlatformFileManager(platformContext).getCacheDirectory()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val sanitized = title.lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9]+"), "_")
            .replace(Regex("^_+|_+$"), "")
            .take(50)
        val out = File(cache, "${sanitized}_${timestamp}.jpg")
        val width = 1600
        val height = 1000
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(250, 250, 250)
        g.fillRect(0, 0, width, height)
        g.color = Color(28, 27, 31)
        g.font = Font("SansSerif", Font.BOLD, 48)
        val fm = g.fontMetrics
        g.drawString(title, (width - fm.stringWidth(title)) / 2, 70)

        val chartSize = (width * 0.4f).coerceAtMost(height * 0.6f)
        val cx = width * 0.25f
        val cy = height * 0.5f
        val radius = chartSize / 2f * 0.85f
        var startAngle = -90.0
        segments.forEach { (label, data) ->
            val (percentage, colorInt) = data
            val sweep = (percentage / 100f) * 360.0
            g.color = Color(colorInt)
            g.fillArc(
                (cx - radius).toInt(), (cy - radius).toInt(),
                (radius * 2).toInt(), (radius * 2).toInt(),
                startAngle.toInt(), sweep.toInt()
            )
            startAngle += sweep
        }

        g.font = Font("SansSerif", Font.PLAIN, 28)
        val legendX = (width * 0.6f).toInt()
        var legendY = (height * 0.3f).toInt()
        segments.forEach { (label, data) ->
            val (percentage, colorInt) = data
            g.color = Color(colorInt)
            g.fillRect(legendX, legendY - 14, 24, 24)
            g.color = Color(73, 69, 79)
            g.drawString("$label (${String.format("%.1f", percentage)}%)", legendX + 36, legendY + 6)
            legendY += 40
        }
        g.dispose()
        ImageIO.write(image, "jpg", out)
        return out
    }

    actual fun shareExportedFile(
        platformContext: PlatformContext,
        file: File,
        exportType: GraphExportType,
        title: String
    ) {
        openExportedFile(platformContext, file, exportType)
    }

    actual fun openExportedFile(
        platformContext: PlatformContext,
        file: File,
        exportType: GraphExportType
    ) {
        if (!Desktop.isDesktopSupported()) return
        runCatching {
            if (Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file)
            } else if (Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(file.parentFile?.toURI())
            }
        }
    }
}

internal actual fun decodeJpgPreview(file: File): ImageBitmap? {
    return runCatching {
        val buffered = ImageIO.read(file) ?: return null
        val bytes = java.io.ByteArrayOutputStream().use { baos ->
            ImageIO.write(buffered, "png", baos)
            baos.toByteArray()
        }
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
}
