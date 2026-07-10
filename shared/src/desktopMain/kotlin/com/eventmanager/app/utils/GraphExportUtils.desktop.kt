package com.eventmanager.app.utils

import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.appDataDir
import com.eventmanager.app.ui.components.DataPoint
import com.eventmanager.app.ui.components.TimePeriod
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import javax.imageio.ImageIO

actual object GraphExportUtils {
    actual fun exportToXLSX(
        platformContext: PlatformContext,
        title: String,
        dataPoints: List<DataPoint>,
        trendPoints: List<DataPoint>,
        timePeriod: TimePeriod,
        graphImagePath: String?
    ): File {
        val out = File(platformContext.appDataDir, "export_${System.currentTimeMillis()}.xlsx")
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet(title.take(31))
            var rowIdx = 0
            sheet.createRow(rowIdx++).createCell(0).setCellValue(title)
            sheet.createRow(rowIdx++).createCell(0).setCellValue("Period: ${timePeriod.displayName}")
            sheet.createRow(rowIdx++).createCell(0).setCellValue("Label")
            sheet.getRow(rowIdx - 1).createCell(1).setCellValue("Value")
            dataPoints.forEach { dp ->
                val row = sheet.createRow(rowIdx++)
                row.createCell(0).setCellValue(dp.label)
                row.createCell(1).setCellValue(dp.value.toDouble())
            }
            FileOutputStream(out).use { stream -> wb.write(stream) }
        }
        return out
    }

    actual fun exportToJPG(
        platformContext: PlatformContext,
        title: String,
        dataPoints: List<DataPoint>,
        trendPoints: List<DataPoint>,
        timePeriod: TimePeriod
    ): File {
        val out = File(platformContext.appDataDir, "graph_${System.currentTimeMillis()}.jpg")
        val width = 1200
        val height = 700
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        g.color = Color(45, 45, 48)
        g.font = Font("SansSerif", Font.BOLD, 28)
        g.drawString(title, 48, 56)
        g.font = Font("SansSerif", Font.PLAIN, 18)
        g.drawString(timePeriod.displayName, 48, 88)

        val chartLeft = 80
        val chartRight = width - 48
        val chartTop = 120
        val chartBottom = height - 80
        val maxValue = (dataPoints.maxOfOrNull { it.value } ?: 1f).coerceAtLeast(1f)
        g.color = Color(220, 220, 220)
        g.drawRect(chartLeft, chartTop, chartRight - chartLeft, chartBottom - chartTop)

        if (dataPoints.isNotEmpty()) {
            val barWidth = ((chartRight - chartLeft).toFloat() / dataPoints.size) * 0.7f
            val gap = ((chartRight - chartLeft).toFloat() / dataPoints.size) * 0.3f
            dataPoints.forEachIndexed { index, dp ->
                val barHeight = ((dp.value / maxValue) * (chartBottom - chartTop)).toInt()
                val x = (chartLeft + index * (barWidth + gap)).toInt()
                val y = chartBottom - barHeight
                g.color = Color(66, 133, 244)
                g.fillRect(x, y, barWidth.toInt().coerceAtLeast(2), barHeight.coerceAtLeast(1))
                g.color = Color.DARK_GRAY
                g.font = Font("SansSerif", Font.PLAIN, 11)
                val label = if (dp.label.length > 10) dp.label.take(9) + "…" else dp.label
                g.drawString(label, x, chartBottom + 16)
            }
        } else {
            g.drawString("No data points", chartLeft + 16, chartTop + 32)
        }

        g.dispose()
        ImageIO.write(image, "jpg", out)
        return out
    }
}
