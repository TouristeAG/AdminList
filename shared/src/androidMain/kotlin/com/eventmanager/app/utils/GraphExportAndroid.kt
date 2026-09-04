package com.eventmanager.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.eventmanager.app.ui.components.BarExportItem
import com.eventmanager.app.ui.components.GraphSeriesExport
import com.eventmanager.app.ui.components.TimePeriod
import com.eventmanager.app.ui.components.buildGraphExportTable
import com.eventmanager.app.ui.components.sanitizeExportSheetName
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFDrawing
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFPicture
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object GraphExportAndroid { 
    /**
     * Exports graph data to XLSX format with data table and Excel chart
     */
    fun exportToXLSX(
        context: Context,
        title: String,
        series: List<GraphSeriesExport>,
        timePeriod: TimePeriod,
        graphBitmap: Bitmap?
    ): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        // Sanitize title for filename (remove special characters, spaces, etc.)
        val sanitizedTitle = title
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9]+"), "_")
            .replace(Regex("^_+|_+$"), "") // Remove leading/trailing underscores
            .take(50) // Limit length
        val fileName = "${sanitizedTitle}_${timestamp}.xlsx"
        val file = File(context.cacheDir, fileName)
        
        // Determine column header based on time period
        val dateColumnHeader = when (timePeriod) {
            TimePeriod.ONE_WEEK, TimePeriod.TWO_WEEKS, TimePeriod.ONE_MONTH -> "Date"
            TimePeriod.SIX_MONTHS -> "Week"
            TimePeriod.ONE_YEAR, TimePeriod.MAX -> "Month"
        }
        
        // Format export date
        val exportDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val workbook: Workbook = createXssfWorkbook()
        val table = buildGraphExportTable(series)
        val includeTrend = series.size == 1 && series.first().trendPoints.isNotEmpty()
        val lastDataColumn = table.seriesNames.size + if (includeTrend) 1 else 0
        val sheet: Sheet = workbook.createSheet(sanitizeExportSheetName(title))
        
        // Create styles (using POI 3.15 constants instead of enums)
        val titleStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.bold = true
            font.fontHeightInPoints = 18
            setFont(font)
            alignment = CellStyle.ALIGN_CENTER
            verticalAlignment = CellStyle.VERTICAL_CENTER
        }
        
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = CellStyle.SOLID_FOREGROUND
            val font = workbook.createFont()
            font.bold = true
            font.fontHeightInPoints = 12
            setFont(font)
            alignment = CellStyle.ALIGN_CENTER
            verticalAlignment = CellStyle.VERTICAL_CENTER
            borderBottom = CellStyle.BORDER_THIN
            borderTop = CellStyle.BORDER_THIN
            borderLeft = CellStyle.BORDER_THIN
            borderRight = CellStyle.BORDER_THIN
        }
        
        val dataStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.fontHeightInPoints = 11
            setFont(font)
            alignment = CellStyle.ALIGN_CENTER
            verticalAlignment = CellStyle.VERTICAL_CENTER
            borderBottom = CellStyle.BORDER_THIN
            borderTop = CellStyle.BORDER_THIN
            borderLeft = CellStyle.BORDER_THIN
            borderRight = CellStyle.BORDER_THIN
        }
        
        val metadataStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.fontHeightInPoints = 10
            setFont(font)
            alignment = CellStyle.ALIGN_LEFT
        }
        
        var currentRow = 0
        
        // Title row
        val titleRow = sheet.createRow(currentRow++)
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue(title)
        titleCell.cellStyle = titleStyle
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, lastDataColumn.coerceAtLeast(1)))
        
        // Empty row
        currentRow++
        
        // Metadata rows
        val metadataRow1 = sheet.createRow(currentRow++)
        metadataRow1.createCell(0).apply {
            setCellValue("Time Period: ${timePeriod.displayName}")
            cellStyle = metadataStyle
        }
        
        val metadataRow2 = sheet.createRow(currentRow++)
        metadataRow2.createCell(0).apply {
            setCellValue("Export Date: $exportDate")
            cellStyle = metadataStyle
        }
        
        val metadataRow3 = sheet.createRow(currentRow++)
        metadataRow3.createCell(0).apply {
            setCellValue("Total Data Points: ${table.rows.size}")
            cellStyle = metadataStyle
        }
        
        // Empty row
        currentRow++
        
        // Data table header
        val headerRow = sheet.createRow(currentRow++)
        val dateHeader = headerRow.createCell(0)
        dateHeader.setCellValue(dateColumnHeader)
        dateHeader.cellStyle = headerStyle
        table.seriesNames.forEachIndexed { index, name ->
            headerRow.createCell(1 + index).apply {
                setCellValue(name)
                cellStyle = headerStyle
            }
        }
        if (includeTrend) {
            headerRow.createCell(1 + table.seriesNames.size).apply {
                setCellValue("Trend")
                cellStyle = headerStyle
            }
        }
        
        val trendByTimestamp = series.firstOrNull()?.trendPoints?.associateBy { it.timestamp }.orEmpty()
        table.rows.forEach { tableRow ->
            val row = sheet.createRow(currentRow++)
            val labelCell = row.createCell(0)
            labelCell.setCellValue(tableRow.label)
            labelCell.cellStyle = dataStyle
            tableRow.values.forEachIndexed { index, value ->
                if (value != null) {
                    row.createCell(1 + index).apply {
                        setCellValue(value.toDouble())
                        cellStyle = dataStyle
                    }
                }
            }
            if (includeTrend) {
                trendByTimestamp[tableRow.timestamp]?.let { trend ->
                    row.createCell(1 + table.seriesNames.size).apply {
                        setCellValue(trend.value.toDouble())
                        cellStyle = dataStyle
                    }
                }
            }
        }
        
        // Empty row
        currentRow++
        
        val allValues = table.rows.flatMap { row -> row.values.filterNotNull() }
        if (allValues.isNotEmpty()) {
            val summaryTitleRow = sheet.createRow(currentRow++)
            val summaryTitleCell = summaryTitleRow.createCell(0)
            summaryTitleCell.setCellValue("Summary Statistics")
            val summaryStyle = workbook.createCellStyle()
            val summaryFont = workbook.createFont()
            summaryFont.bold = true
            summaryFont.fontHeightInPoints = 12
            summaryStyle.setFont(summaryFont)
            summaryTitleCell.cellStyle = summaryStyle
            sheet.addMergedRegion(CellRangeAddress(currentRow - 1, currentRow - 1, 0, lastDataColumn.coerceAtLeast(1)))
            
            val maxValue = allValues.maxOrNull() ?: 0f
            val minValue = allValues.minOrNull() ?: 0f
            val avgValue = allValues.map { it.toDouble() }.average()
            
            val maxRow = sheet.createRow(currentRow++)
            maxRow.createCell(0).setCellValue("Maximum: ${maxValue.toInt()}")
            
            val minRow = sheet.createRow(currentRow++)
            minRow.createCell(0).setCellValue("Minimum: ${minValue.toInt()}")
            
            val avgRow = sheet.createRow(currentRow++)
            avgRow.createCell(0).setCellValue("Average: ${String.format("%.2f", avgValue)}")
        }
        
        sheet.setColumnWidth(0, 5000)
        table.seriesNames.indices.forEach { index ->
            sheet.setColumnWidth(1 + index, 5000)
        }
        if (includeTrend) {
            sheet.setColumnWidth(1 + table.seriesNames.size, 5000)
        }
        
        if (table.rows.isNotEmpty() && graphBitmap != null && workbook is XSSFWorkbook) {
            try {
                val drawing = sheet.createDrawingPatriarch() as XSSFDrawing
                
                // Convert bitmap to PNG byte array
                val bitmapBytes = ByteArrayOutputStream().use { outputStream ->
                    graphBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.toByteArray()
                }
                
                // Add picture to workbook
                val pictureIdx = workbook.addPicture(bitmapBytes, Workbook.PICTURE_TYPE_PNG)
                
                // Calculate size in EMU (English Metric Units) - 1 pixel = 9525 EMU
                // Limit max size to fit within reasonable Excel cell bounds
                val maxWidthEmu = 6 * 9525 * 100  // ~6 columns worth
                val maxHeightEmu = 20 * 9525 * 20  // ~20 rows worth
                val widthEmu = (graphBitmap.width * 9525L).coerceAtMost(maxWidthEmu.toLong())
                val heightEmu = (graphBitmap.height * 9525L).coerceAtMost(maxHeightEmu.toLong())
                
                // Create anchor for image position (after summary statistics)
                // dx1, dy1 are offsets from top-left corner of start cell
                // dx2, dy2 are offsets from top-left corner of end cell
                val anchor = XSSFClientAnchor(
                    0, 0, widthEmu.toInt(), heightEmu.toInt(),
                    4, currentRow + 2,  // Start after summary, column E (0-indexed: 4)
                    10, currentRow + 25  // End 25 rows later, column K (0-indexed: 10)
                )
                
                // Create and position the picture
                val picture: XSSFPicture = drawing.createPicture(anchor, pictureIdx)
                
            } catch (e: Exception) {
                // If image embedding fails, at least the data is exported correctly
                e.printStackTrace()
            }
        }
        
        // Write to file
        FileOutputStream(file).use { outputStream ->
            workbook.write(outputStream)
        }
        workbook.close()
        
        return file
    }
    
    /**
     * Renders the graph as a bitmap for export - matches app styling
     */
    fun renderGraphAsBitmap(
        series: List<GraphSeriesExport>,
        title: String,
        timePeriod: TimePeriod,
        width: Int = 1600,
        height: Int = 1000,
        density: Density
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Background - use light gray like Material Design surface
        canvas.drawColor(Color.parseColor("#FAFAFA"))
        
        // Better padding for modern look
        val leftPadding = 100f
        val rightPadding = 80f
        val topPadding = 130f
        val legendRows = if (series.size > 1) (series.size + 3) / 4 else 0
        val bottomPadding = 120f + legendRows * 36f
        val graphWidth = width - leftPadding - rightPadding
        val graphHeight = height - topPadding - bottomPadding
        
        val paint = Paint().apply {
            isAntiAlias = true
            isDither = true
        }
        
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#1C1B1F")
            textSize = 48f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        
        val labelPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#49454F")
            textSize = 28f
        }
        
        val yAxisLabelPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#49454F")
            textSize = 26f
            textAlign = Paint.Align.RIGHT
        }
        
        canvas.drawText(title, width / 2f, 58f, titlePaint)
        labelPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(timePeriod.displayName, width / 2f, 98f, labelPaint)
        
        val allPoints = series.flatMap { it.dataPoints }
        val maxValue = (allPoints.maxOfOrNull { it.value } ?: 1f).let { if (it <= 0f) 1f else it * 1.15f }
        val minValue = 0f
        val valueRange = (maxValue - minValue).coerceAtLeast(0.0001f)
        
        val gridColor = Color.parseColor("#79747E")
        paint.color = Color.argb((255 * 0.1f).toInt(), Color.red(gridColor), Color.green(gridColor), Color.blue(gridColor))
        paint.strokeWidth = 0.5f
        paint.pathEffect = null
        for (i in 1..3) {
            val y = topPadding + (graphHeight * i / 3)
            canvas.drawLine(leftPadding, y, leftPadding + graphWidth, y, paint)
        }
        
        for (i in 0..2) {
            val value = maxValue - (valueRange * i / 2)
            val y = topPadding + (graphHeight * i / 2)
            canvas.drawText(value.toInt().toString(), leftPadding - 20f, y + 10f, yAxisLabelPaint)
        }
        
        labelPaint.textAlign = Paint.Align.CENTER
        val axisPoints = series.firstOrNull()?.dataPoints.orEmpty()
        if (axisPoints.isNotEmpty()) {
            canvas.drawText(axisPoints.first().label, leftPadding, height - bottomPadding + 44f, labelPaint)
            canvas.drawText(axisPoints.last().label, leftPadding + graphWidth, height - bottomPadding + 44f, labelPaint)
        }
        
        series.forEach { item ->
            drawExportedSeries(
                canvas = canvas,
                paint = paint,
                series = item,
                leftPadding = leftPadding,
                topPadding = topPadding,
                graphWidth = graphWidth,
                graphHeight = graphHeight,
                minValue = minValue,
                valueRange = valueRange,
                width = width,
            )
        }

        if (series.size > 1) {
            val legendPaint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#49454F")
                textSize = 24f
                textAlign = Paint.Align.LEFT
            }
            series.forEachIndexed { index, item ->
                val col = index % 4
                val row = index / 4
                val x = leftPadding + col * (graphWidth / 4f)
                val y = height - bottomPadding + 78f + row * 32f
                paint.color = item.colorArgb
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(x, y - 16f, x + 20f, y + 4f, 4f, 4f, paint)
                canvas.drawText(item.name, x + 28f, y, legendPaint)
            }
        }
        
        return bitmap
    }

    private fun drawExportedSeries(
        canvas: Canvas,
        paint: Paint,
        series: GraphSeriesExport,
        leftPadding: Float,
        topPadding: Float,
        graphWidth: Float,
        graphHeight: Float,
        minValue: Float,
        valueRange: Float,
        width: Int,
    ) {
        val dataPoints = series.dataPoints
        val trendPoints = series.trendPoints
        val color = series.colorArgb
        if (trendPoints.size >= 2) {
            paint.color = Color.argb((255 * 0.5f).toInt(), Color.red(color), Color.green(color), Color.blue(color))
            paint.strokeWidth = 2f * (width / 1200f)
            paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 3f), 0f)
            paint.style = Paint.Style.STROKE
            val path = Path()
            for (i in trendPoints.indices) {
                val x = leftPadding + (i.toFloat() / (trendPoints.size - 1)) * graphWidth
                val y = topPadding + graphHeight - ((trendPoints[i].value - minValue) / valueRange) * graphHeight
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, paint)
        }
        if (dataPoints.size >= 2) {
            paint.color = color
            paint.strokeWidth = 2.5f * (width / 1200f)
            paint.pathEffect = null
            paint.style = Paint.Style.STROKE
            val path = Path()
            for (i in dataPoints.indices) {
                val x = leftPadding + (i.toFloat() / (dataPoints.size - 1)) * graphWidth
                val y = topPadding + graphHeight - ((dataPoints[i].value - minValue) / valueRange) * graphHeight
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, paint)
            paint.style = Paint.Style.FILL
            val pointRadius = 3.5f * (width / 1200f)
            for (i in dataPoints.indices) {
                val x = leftPadding + (i.toFloat() / (dataPoints.size - 1)) * graphWidth
                val y = topPadding + graphHeight - ((dataPoints[i].value - minValue) / valueRange) * graphHeight
                canvas.drawCircle(x, y, pointRadius, paint)
            }
        }
    }

    fun renderBarChartAsBitmap(
        bars: List<BarExportItem>,
        title: String,
        width: Int = 1600,
        height: Int = 1000,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#FAFAFA"))
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#1C1B1F")
            textSize = 48f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        canvas.drawText(title, width / 2f, 70f, titlePaint)
        val paint = Paint().apply { isAntiAlias = true }
        val labelPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#49454F")
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }
        val countPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#1C1B1F")
            textSize = 24f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val left = 80f
        val right = width - 80f
        val top = 140f
        val bottom = height - 160f
        val maxValue = bars.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f
        val slotWidth = if (bars.isEmpty()) 0f else (right - left) / bars.size
        val barWidth = (slotWidth * 0.62f).coerceAtLeast(12f)
        bars.forEachIndexed { index, bar ->
            val barHeight = ((bar.value / maxValue) * (bottom - top)).coerceAtLeast(if (bar.value > 0f) 6f else 0f)
            val x = left + index * slotWidth + (slotWidth - barWidth) / 2f
            val y = bottom - barHeight
            paint.color = bar.colorArgb
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(x, y, x + barWidth, bottom, 12f, 12f, paint)
            canvas.drawText(bar.count.toString(), x + barWidth / 2f, y - 12f, countPaint)
            val label = bar.label.replace('\n', ' ')
            canvas.drawText(label.take(18), x + barWidth / 2f, bottom + 36f, labelPaint)
        }
        return bitmap
    }
    
    /**
     * Renders a pie chart as a bitmap for export
     */
    fun renderPieChartAsBitmap(
        segments: List<Pair<String, Pair<Float, Int>>>, // List of (label, (percentage, color))
        title: String,
        width: Int = 1600,
        height: Int = 1000
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Background
        canvas.drawColor(Color.parseColor("#FAFAFA"))
        
        val paint = Paint().apply {
            isAntiAlias = true
            isDither = true
        }
        
        // Title styling
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#1C1B1F")
            textSize = 48f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        
        // Label styling
        val labelPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#49454F")
            textSize = 28f
        }
        
        // Draw title
        canvas.drawText(title, width / 2f, 70f, titlePaint)
        
        // Calculate pie chart area (centered, with space for legend)
        val chartSize = (width * 0.4f).coerceAtMost(height * 0.6f)
        val chartX = width * 0.25f
        val chartY = height * 0.5f
        val center = android.graphics.PointF(chartX, chartY)
        val radius = chartSize / 2f * 0.85f
        
        // Draw pie chart
        var startAngle = -90f // Start from top
        val rectF = RectF(
            center.x - radius,
            center.y - radius,
            center.x + radius,
            center.y + radius
        )
        
        segments.forEach { (label, data) ->
            val (percentage, colorInt) = data
            val sweepAngle = (percentage / 100f) * 360f
            
            paint.color = colorInt
            paint.style = Paint.Style.FILL
            canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
            
            startAngle += sweepAngle
        }
        
        // Draw legend on the right side
        val legendX = width * 0.6f
        val legendY = height * 0.3f
        val legendSpacing = 40f
        
        segments.forEachIndexed { index, (label, data) ->
            val (percentage, colorInt) = data
            val y = legendY + (index * legendSpacing)
            
            // Color box
            paint.color = colorInt
            paint.style = Paint.Style.FILL
            canvas.drawRect(legendX, y - 12f, legendX + 24f, y + 12f, paint)
            
            // Label text
            labelPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(label, legendX + 32f, y + 8f, labelPaint)
            
            // Percentage text
            val percentageText = "${String.format("%.1f", percentage)}%"
            labelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(percentageText, width - 100f, y + 8f, labelPaint)
        }
        
        return bitmap
    }
    
    /**
     * Exports graph as JPG image with high quality
     */
    fun exportToJPG(
        context: Context,
        bitmap: Bitmap,
        title: String
    ): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        // Sanitize title for filename (remove special characters, spaces, etc.)
        val sanitizedTitle = title
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9]+"), "_")
            .replace(Regex("^_+|_+$"), "") // Remove leading/trailing underscores
            .take(50) // Limit length
        val fileName = "${sanitizedTitle}_${timestamp}.jpg"
        val file = File(context.cacheDir, fileName)
        
        FileOutputStream(file).use { outputStream ->
            // Use maximum quality (100) for best results
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        }
        
        return file
    }

    /**
     * POI may throw [java.lang.Error] (e.g. FactoryConfigurationError) when StAX is misconfigured.
     * Wrap as Exception so coroutine export catch blocks can recover without killing the process.
     */
    internal fun createXssfWorkbook(): XSSFWorkbook {
        PoiAndroidInit.ensureStaxFactories()
        return try {
            XSSFWorkbook()
        } catch (t: Throwable) {
            throw IllegalStateException("Failed to create XLSX workbook: ${t.message}", t)
        }
    }
}

