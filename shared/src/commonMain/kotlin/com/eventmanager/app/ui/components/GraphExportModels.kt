package com.eventmanager.app.ui.components

internal const val DEFAULT_GRAPH_LINE_COLOR: Int = 0xFF5B8FF9.toInt()

internal data class GraphSeriesExport(
    val name: String,
    val dataPoints: List<DataPoint>,
    val trendPoints: List<DataPoint> = emptyList(),
    val colorArgb: Int = DEFAULT_GRAPH_LINE_COLOR,
)

internal data class BarExportItem(
    val label: String,
    val value: Float,
    val count: Int,
    val colorArgb: Int,
)

internal data class GraphExportTableRow(
    val label: String,
    val timestamp: Long,
    val values: List<Float?>,
)

internal data class GraphExportTable(
    val seriesNames: List<String>,
    val rows: List<GraphExportTableRow>,
)

internal fun singleSeriesGraphExport(
    name: String,
    dataPoints: List<DataPoint>,
    trendPoints: List<DataPoint>,
    colorArgb: Int = DEFAULT_GRAPH_LINE_COLOR,
): List<GraphSeriesExport> = listOf(
    GraphSeriesExport(
        name = name,
        dataPoints = dataPoints,
        trendPoints = trendPoints,
        colorArgb = colorArgb,
    ),
)

internal fun buildGraphExportTable(series: List<GraphSeriesExport>): GraphExportTable {
    val names = series.mapIndexed { index, item ->
        item.name.ifBlank { "Series ${index + 1}" }
    }
    val timestamps = series
        .flatMap { item -> item.dataPoints.map { it.timestamp } }
        .distinct()
        .sorted()
    if (timestamps.isEmpty()) {
        return GraphExportTable(names, emptyList())
    }
    val lookups = series.map { item -> item.dataPoints.associateBy { it.timestamp } }
    val rows = timestamps.map { timestamp ->
        val label = lookups.firstNotNullOfOrNull { points -> points[timestamp]?.label }.orEmpty()
        GraphExportTableRow(
            label = label,
            timestamp = timestamp,
            values = lookups.map { points -> points[timestamp]?.value },
        )
    }
    return GraphExportTable(names, rows)
}

internal fun sanitizeExportSheetName(title: String): String {
    return title
        .replace(Regex("[\\\\/*?:\\[\\]]"), "_")
        .trim()
        .take(31)
        .ifBlank { "Graph" }
}
