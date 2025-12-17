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
import com.eventmanager.app.ui.components.DataPoint
import com.eventmanager.app.ui.components.TimePeriod
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFDrawing
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFPicture
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object GraphExportUtils {
    
    /**
     * Converts Java timestamp to Excel date serial number
     * Excel stores dates as days since January 1, 1900
     */
    private fun timestampToExcelDate(timestamp: Long): Double {
        // Excel epoch is January 1, 1900, but Excel incorrectly treats 1900 as a leap year
        // So we need to account for that
        val excelEpoch = Calendar.getInstance().apply {
            set(1900, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        // Calculate days difference
        val daysDiff = (timestamp - excelEpoch).toDouble() / (24 * 60 * 60 * 1000)
        
        // Excel date serial starts at 1 (not 0), and we need to add 1 day for the 1900 leap year bug
        return daysDiff + 1.0
    }
    
    /**
     * Exports graph data to XLSX format with data table and Excel chart
     */
    fun exportToXLSX(
        context: Context,
        title: String,
        dataPoints: List<DataPoint>,
        trendPoints: List<DataPoint>,
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
        
        val workbook: Workbook = XSSFWorkbook()
        val sheet: Sheet = workbook.createSheet("Active Volunteers")
        
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
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 2))
        
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
            setCellValue("Total Data Points: ${dataPoints.size}")
            cellStyle = metadataStyle
        }
        
        // Empty row
        currentRow++
        
        // Data table header
        val headerRow = sheet.createRow(currentRow++)
        val dateHeader = headerRow.createCell(0)
        dateHeader.setCellValue(dateColumnHeader)
        dateHeader.cellStyle = headerStyle
        
        val valueHeader = headerRow.createCell(1)
        valueHeader.setCellValue("Active Volunteers")
        valueHeader.cellStyle = headerStyle
        
        val trendHeader = headerRow.createCell(2)
        trendHeader.setCellValue("Trend")
        trendHeader.cellStyle = headerStyle
        
        // Store data row start for chart reference
        val dataStartRow = currentRow
        
        // Create date style for Excel date format
        val dateCellStyle = workbook.createCellStyle().apply {
            val font = workbook.createFont()
            font.fontHeightInPoints = 11
            setFont(font)
            alignment = CellStyle.ALIGN_CENTER
            verticalAlignment = CellStyle.VERTICAL_CENTER
            borderBottom = CellStyle.BORDER_THIN
            borderTop = CellStyle.BORDER_THIN
            borderLeft = CellStyle.BORDER_THIN
            borderRight = CellStyle.BORDER_THIN
            // Set date format based on time period
            val dateFormat = when (timePeriod) {
                TimePeriod.ONE_WEEK, TimePeriod.TWO_WEEKS, TimePeriod.ONE_MONTH -> "dd/mm/yyyy"
                TimePeriod.SIX_MONTHS -> "dd/mm/yyyy"
                TimePeriod.ONE_YEAR, TimePeriod.MAX -> "mm/yyyy"
            }
            dataFormat = workbook.creationHelper.createDataFormat().getFormat(dateFormat)
        }
        
        // Data rows with Excel date format
        dataPoints.forEachIndexed { index, dataPoint ->
            val row = sheet.createRow(currentRow++)
            
            val dateCell = row.createCell(0)
            // Convert timestamp to Excel date format
            val excelDate = timestampToExcelDate(dataPoint.timestamp)
            dateCell.setCellValue(excelDate)
            dateCell.cellStyle = dateCellStyle
            
            val valueCell = row.createCell(1)
            valueCell.setCellValue(dataPoint.value.toDouble())
            valueCell.cellStyle = dataStyle
            
            if (index < trendPoints.size) {
                val trendCell = row.createCell(2)
                trendCell.setCellValue(trendPoints[index].value.toDouble())
                trendCell.cellStyle = dataStyle
            }
        }
        
        val dataEndRow = currentRow - 1
        
        // Empty row
        currentRow++
        
        // Summary statistics
        if (dataPoints.isNotEmpty()) {
            val summaryTitleRow = sheet.createRow(currentRow++)
            val summaryTitleCell = summaryTitleRow.createCell(0)
            summaryTitleCell.setCellValue("Summary Statistics")
            val summaryStyle = workbook.createCellStyle()
            val summaryFont = workbook.createFont()
            summaryFont.bold = true
            summaryFont.fontHeightInPoints = 12
            summaryStyle.setFont(summaryFont)
            summaryTitleCell.cellStyle = summaryStyle
            sheet.addMergedRegion(CellRangeAddress(currentRow - 1, currentRow - 1, 0, 2))
            
            val maxValue = dataPoints.maxOfOrNull { it.value } ?: 0f
            val minValue = dataPoints.minOfOrNull { it.value } ?: 0f
            val avgValue = dataPoints.map { it.value }.average()
            
            val maxRow = sheet.createRow(currentRow++)
            maxRow.createCell(0).setCellValue("Maximum: ${maxValue.toInt()}")
            
            val minRow = sheet.createRow(currentRow++)
            minRow.createCell(0).setCellValue("Minimum: ${minValue.toInt()}")
            
            val avgRow = sheet.createRow(currentRow++)
            avgRow.createCell(0).setCellValue("Average: ${String.format("%.2f", avgValue)}")
        }
        
        // Auto-size columns
        sheet.setColumnWidth(0, 5000)
        sheet.setColumnWidth(1, 5000)
        sheet.setColumnWidth(2, 5000)
        
        // Embed graph as image instead of native chart (more reliable on Android)
        if (dataPoints.isNotEmpty() && graphBitmap != null && workbook is XSSFWorkbook) {
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
        dataPoints: List<DataPoint>,
        trendPoints: List<DataPoint>,
        title: String,
        timePeriod: TimePeriod,
        width: Int = 1600,  // Increased for better quality
        height: Int = 1000, // Increased for better quality
        density: Density
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // Background - use light gray like Material Design surface
        canvas.drawColor(Color.parseColor("#FAFAFA"))
        
        // Better padding for modern look
        val leftPadding = 100f
        val rightPadding = 80f
        val topPadding = 120f
        val bottomPadding = 120f
        val graphWidth = width - leftPadding - rightPadding
        val graphHeight = height - topPadding - bottomPadding
        
        val paint = Paint().apply {
            isAntiAlias = true
            isDither = true
        }
        
        // Title styling - modern typography
        val titlePaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#1C1B1F") // Material Design onSurface
            textSize = 48f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        
        // Label styling
        val labelPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#49454F") // Material Design onSurfaceVariant
            textSize = 28f
        }
        
        val yAxisLabelPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#49454F")
            textSize = 26f
            textAlign = Paint.Align.RIGHT
        }
        
        // Draw title
        canvas.drawText(title, width / 2f, 70f, titlePaint)
        
        // Calculate value range
        val maxValue = dataPoints.maxOfOrNull { it.value } ?: 1f
        val minValue = 0f
        val valueRange = maxValue - minValue
        
        // Draw subtle grid lines (matching app - alpha 0.1)
        val gridColor = Color.parseColor("#79747E") // onSurfaceVariant
        paint.color = Color.argb((255 * 0.1f).toInt(), Color.red(gridColor), Color.green(gridColor), Color.blue(gridColor))
        paint.strokeWidth = 0.5f
        paint.pathEffect = null
        
        // Draw 3 horizontal grid lines (at 1/3, 2/3, and bottom)
        for (i in 1..3) {
            val y = topPadding + (graphHeight * i / 3)
            canvas.drawLine(leftPadding, y, leftPadding + graphWidth, y, paint)
        }
        
        // Draw Y-axis labels (matching app style)
        for (i in 0..3) {
            val value = maxValue - (valueRange * i / 3)
            val y = topPadding + (graphHeight * i / 3)
            canvas.drawText(value.toInt().toString(), leftPadding - 20f, y + 10f, yAxisLabelPaint)
        }
        
        // Draw X-axis labels (first and last, matching app)
        labelPaint.textAlign = Paint.Align.CENTER
        if (dataPoints.isNotEmpty()) {
            val firstLabel = dataPoints.first().label
            val lastLabel = dataPoints.last().label
            canvas.drawText(firstLabel, leftPadding, height - bottomPadding + 50f, labelPaint)
            canvas.drawText(lastLabel, leftPadding + graphWidth, height - bottomPadding + 50f, labelPaint)
        }
        
        // Draw trend line first (behind main line) - matching app styling
        if (trendPoints.size >= 2) {
            val trendColor = Color.parseColor("#FF6B9D") // Tertiary color
            paint.color = Color.argb((255 * 0.7f).toInt(), Color.red(trendColor), Color.green(trendColor), Color.blue(trendColor))
            paint.strokeWidth = 2f * (width / 1200f) // Scale with resolution
            paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 3f), 0f)
            paint.style = Paint.Style.STROKE
            
            val path = Path()
            for (i in trendPoints.indices) {
                val x = leftPadding + (i.toFloat() / (trendPoints.size - 1)) * graphWidth
                val y = topPadding + graphHeight - ((trendPoints[i].value - minValue) / valueRange) * graphHeight
                
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            canvas.drawPath(path, paint)
        }
        
        // Draw main value line (matching app - strokeWidth 2.5f)
        if (dataPoints.size >= 2) {
            paint.color = Color.parseColor("#6200EE") // Primary color
            paint.strokeWidth = 2.5f * (width / 1200f) // Scale with resolution
            paint.pathEffect = null
            paint.style = Paint.Style.STROKE
            
            val path = Path()
            for (i in dataPoints.indices) {
                val x = leftPadding + (i.toFloat() / (dataPoints.size - 1)) * graphWidth
                val y = topPadding + graphHeight - ((dataPoints[i].value - minValue) / valueRange) * graphHeight
                
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            canvas.drawPath(path, paint)
        }
        
        // Draw data points (matching app - radius 3.5f)
        paint.color = Color.parseColor("#6200EE")
        paint.style = Paint.Style.FILL
        val pointRadius = 3.5f * (width / 1200f) // Scale with resolution
        for (i in dataPoints.indices) {
            val x = leftPadding + (i.toFloat() / (dataPoints.size - 1)) * graphWidth
            val y = topPadding + graphHeight - ((dataPoints[i].value - minValue) / valueRange) * graphHeight
            canvas.drawCircle(x, y, pointRadius, paint)
        }
        
        // No visible axes (matching app - only grid lines)
        
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
}

