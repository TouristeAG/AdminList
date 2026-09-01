package com.eventmanager.app.utils

import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.appDataDir
import com.eventmanager.app.ui.components.BarExportItem
import com.eventmanager.app.ui.components.GraphSeriesExport
import com.eventmanager.app.ui.components.TimePeriod
import com.eventmanager.app.ui.components.buildGraphExportTable
import com.eventmanager.app.ui.components.sanitizeExportSheetName
import org.apache.poi.ss.usermodel.ClientAnchor
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

internal actual object GraphExportUtils {
    actual fun exportToXLSX(
        platformContext: PlatformContext,
        title: String,
        series: List<GraphSeriesExport>,
        timePeriod: TimePeriod,
        graphImagePath: String?
    ): File {
        val out = File(platformContext.appDataDir, "export_${System.currentTimeMillis()}.xlsx")
        val table = buildGraphExportTable(series)
        val includeTrend = series.size == 1 && series.first().trendPoints.isNotEmpty()
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet(sanitizeExportSheetName(title))
            var rowIdx = 0
            sheet.createRow(rowIdx++).createCell(0).setCellValue(title)
            sheet.createRow(rowIdx++).createCell(0).setCellValue("Period: ${timePeriod.displayName}")
            val header = sheet.createRow(rowIdx++)
            header.createCell(0).setCellValue("Label")
            table.seriesNames.forEachIndexed { index, name ->
                header.createCell(1 + index).setCellValue(name)
            }
            if (includeTrend) {
                header.createCell(1 + table.seriesNames.size).setCellValue("Trend")
            }
            val trendByTimestamp = series.firstOrNull()?.trendPoints?.associateBy { it.timestamp }.orEmpty()
            table.rows.forEach { row ->
                val excelRow = sheet.createRow(rowIdx++)
                excelRow.createCell(0).setCellValue(row.label)
                row.values.forEachIndexed { index, value ->
                    if (value != null) {
                        excelRow.createCell(1 + index).setCellValue(value.toDouble())
                    }
                }
                if (includeTrend) {
                    trendByTimestamp[row.timestamp]?.let { trend ->
                        excelRow.createCell(1 + table.seriesNames.size).setCellValue(trend.value.toDouble())
                    }
                }
            }

            val image = renderLineGraphImage(title, series, timePeriod)
            val pngBytes = ByteArrayOutputStream().use { stream ->
                ImageIO.write(image, "png", stream)
                stream.toByteArray()
            }
            val pictureIdx = wb.addPicture(pngBytes, Workbook.PICTURE_TYPE_PNG)
            val drawing = sheet.createDrawingPatriarch()
            val startCol = max(2, table.seriesNames.size + 2)
            val anchor = XSSFClientAnchor(
                0, 0, 0, 0,
                startCol, 1,
                startCol + 10, 22,
            )
            anchor.anchorType = ClientAnchor.AnchorType.MOVE_DONT_RESIZE
            drawing.createPicture(anchor, pictureIdx)

            FileOutputStream(out).use { stream -> wb.write(stream) }
        }
        return out
    }

    actual fun exportToJPG(
        platformContext: PlatformContext,
        title: String,
        series: List<GraphSeriesExport>,
        timePeriod: TimePeriod
    ): File {
        val out = File(platformContext.appDataDir, "graph_${System.currentTimeMillis()}.jpg")
        ImageIO.write(renderLineGraphImage(title, series, timePeriod), "jpg", out)
        return out
    }

    actual fun exportBarChartToJPG(
        platformContext: PlatformContext,
        title: String,
        bars: List<BarExportItem>,
    ): File {
        val out = File(platformContext.appDataDir, "graph_${System.currentTimeMillis()}.jpg")
        ImageIO.write(renderBarChartImage(title, bars), "jpg", out)
        return out
    }
}

private fun renderLineGraphImage(
    title: String,
    series: List<GraphSeriesExport>,
    timePeriod: TimePeriod,
    width: Int = 1600,
    height: Int = 1000,
): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = Color(0xFA, 0xFA, 0xFA)
    g.fillRect(0, 0, width, height)

    g.color = Color(0x1C, 0x1B, 0x1F)
    g.font = Font("SansSerif", Font.BOLD, 36)
    g.drawString(title, 80, 56)
    g.color = Color(0x49, 0x45, 0x4F)
    g.font = Font("SansSerif", Font.PLAIN, 22)
    g.drawString(timePeriod.displayName, 80, 88)

    val legendRows = if (series.size > 1) ((series.size + 3) / 4) else 0
    val legendHeight = if (legendRows > 0) 28 + legendRows * 28 else 0
    val left = 110
    val right = width - 60
    val top = 130
    val bottom = height - 80 - legendHeight
    val chartWidth = (right - left).toFloat()
    val chartHeight = (bottom - top).toFloat()

    val allPoints = series.flatMap { it.dataPoints }
    val maxValue = (allPoints.maxOfOrNull { it.value } ?: 1f).let { if (it <= 0f) 1f else it * 1.15f }
    val minValue = 0f
    val valueRange = (maxValue - minValue).coerceAtLeast(0.0001f)

    g.color = Color(0x79, 0x74, 0x7E, 28)
    g.stroke = BasicStroke(1f)
    for (i in 1..2) {
        val y = top + (chartHeight * i / 3f)
        g.drawLine(left, y.roundToInt(), right, y.roundToInt())
    }

    g.color = Color(0x49, 0x45, 0x4F)
    g.font = Font("SansSerif", Font.PLAIN, 18)
    val fm = g.fontMetrics
    listOf(maxValue, (maxValue + minValue) / 2f, minValue).forEachIndexed { index, value ->
        val y = top + (chartHeight * index / 2f)
        val text = value.roundToInt().toString()
        g.drawString(text, left - 16 - fm.stringWidth(text), y.roundToInt() + 6)
    }

    val firstLabel = series.firstOrNull()?.dataPoints?.firstOrNull()?.label.orEmpty()
    val lastLabel = series.firstOrNull()?.dataPoints?.lastOrNull()?.label.orEmpty()
    g.drawString(firstLabel, left, bottom + 28)
    g.drawString(lastLabel, right - fm.stringWidth(lastLabel), bottom + 28)

    series.forEach { item ->
        drawSeries(g, item, left, top, chartWidth, chartHeight, minValue, valueRange)
    }

    if (series.size > 1) {
        var legendX = left
        var legendY = bottom + 58
        g.font = Font("SansSerif", Font.PLAIN, 16)
        series.forEachIndexed { index, item ->
            if (index > 0 && index % 4 == 0) {
                legendX = left
                legendY += 28
            }
            g.color = argb(item.colorArgb)
            g.fillRoundRect(legendX, legendY - 12, 16, 16, 4, 4)
            g.color = Color(0x49, 0x45, 0x4F)
            g.drawString(item.name, legendX + 22, legendY + 2)
            legendX += 280
        }
    }

    g.dispose()
    return image
}

private fun drawSeries(
    g: Graphics2D,
    series: GraphSeriesExport,
    left: Int,
    top: Int,
    chartWidth: Float,
    chartHeight: Float,
    minValue: Float,
    valueRange: Float,
) {
    val points = series.dataPoints
    if (points.size < 2) return
    val color = argb(series.colorArgb)
    val trend = series.trendPoints
    fun xFor(index: Int, size: Int) = left + (index.toFloat() / (size - 1)) * chartWidth
    fun yFor(value: Float) = top + chartHeight - ((value - minValue) / valueRange) * chartHeight

    if (trend.size >= 2) {
        g.color = Color(color.red, color.green, color.blue, 128)
        g.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, floatArrayOf(8f, 4f), 0f)
        for (i in 0 until trend.size - 1) {
            g.drawLine(
                xFor(i, trend.size).roundToInt(),
                yFor(trend[i].value).roundToInt(),
                xFor(i + 1, trend.size).roundToInt(),
                yFor(trend[i + 1].value).roundToInt(),
            )
        }
    }

    g.color = color
    g.stroke = BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    for (i in 0 until points.size - 1) {
        g.drawLine(
            xFor(i, points.size).roundToInt(),
            yFor(points[i].value).roundToInt(),
            xFor(i + 1, points.size).roundToInt(),
            yFor(points[i + 1].value).roundToInt(),
        )
    }
    points.forEachIndexed { index, point ->
        val x = xFor(index, points.size).roundToInt()
        val y = yFor(point.value).roundToInt()
        g.fillOval(x - 4, y - 4, 8, 8)
    }
}

private fun renderBarChartImage(
    title: String,
    bars: List<BarExportItem>,
    width: Int = 1600,
    height: Int = 1000,
): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g = image.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.color = Color(0xFA, 0xFA, 0xFA)
    g.fillRect(0, 0, width, height)
    g.color = Color(0x1C, 0x1B, 0x1F)
    g.font = Font("SansSerif", Font.BOLD, 36)
    g.drawString(title, 80, 60)

    val left = 80
    val right = width - 80
    val top = 120
    val bottom = height - 140
    val maxValue = bars.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
    val slotWidth = if (bars.isEmpty()) 0f else (right - left).toFloat() / bars.size
    val barWidth = (slotWidth * 0.62f).coerceAtLeast(8f)

    bars.forEachIndexed { index, bar ->
        val barHeight = ((bar.value / maxValue) * (bottom - top)).roundToInt().coerceAtLeast(if (bar.value > 0f) 4 else 0)
        val x = (left + index * slotWidth + (slotWidth - barWidth) / 2f).roundToInt()
        val y = bottom - barHeight
        g.color = argb(bar.colorArgb)
        g.fillRoundRect(x, y, barWidth.roundToInt(), barHeight, 12, 12)

        g.color = Color(0x1C, 0x1B, 0x1F)
        g.font = Font("SansSerif", Font.BOLD, 20)
        val count = bar.count.toString()
        val countWidth = g.fontMetrics.stringWidth(count)
        g.drawString(count, x + (barWidth.roundToInt() - countWidth) / 2, y - 10)

        g.color = Color(0x49, 0x45, 0x4F)
        g.font = Font("SansSerif", Font.PLAIN, 16)
        val label = bar.label.replace('\n', ' ')
        val wrapped = wrapLabel(g, label, slotWidth.roundToInt() - 8)
        wrapped.forEachIndexed { lineIndex, line ->
            val lineWidth = g.fontMetrics.stringWidth(line)
            g.drawString(
                line,
                x + (barWidth.roundToInt() - lineWidth) / 2,
                bottom + 24 + lineIndex * 18,
            )
        }
    }
    g.dispose()
    return image
}

private fun wrapLabel(g: Graphics2D, text: String, maxWidth: Int): List<String> {
    if (maxWidth <= 0) return listOf(text)
    val words = text.split(' ')
    val lines = mutableListOf<String>()
    var current = ""
    words.forEach { word ->
        val candidate = if (current.isBlank()) word else "$current $word"
        if (g.fontMetrics.stringWidth(candidate) <= maxWidth || current.isBlank()) {
            current = candidate
        } else {
            lines += current
            current = word
        }
    }
    if (current.isNotBlank()) lines += current
    return lines.take(2)
}

private fun argb(value: Int): Color = Color(value, true)
