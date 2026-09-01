package com.eventmanager.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GraphExportTableTest {
    @Test
    fun alignsMultipleSeriesByTimestamp() {
        val table = buildGraphExportTable(
            listOf(
                GraphSeriesExport(
                    name = "Bar",
                    dataPoints = listOf(
                        DataPoint("Mon", 2f, 1_000L),
                        DataPoint("Tue", 4f, 2_000L),
                    ),
                ),
                GraphSeriesExport(
                    name = "Total",
                    dataPoints = listOf(
                        DataPoint("Mon", 5f, 1_000L),
                        DataPoint("Tue", 9f, 2_000L),
                    ),
                ),
            ),
        )
        assertEquals(listOf("Bar", "Total"), table.seriesNames)
        assertEquals(2, table.rows.size)
        assertEquals("Mon", table.rows[0].label)
        assertEquals(listOf(2f, 5f), table.rows[0].values)
        assertEquals(listOf(4f, 9f), table.rows[1].values)
    }

    @Test
    fun keepsMissingPointsAsNullInsteadOfDroppingASeries() {
        val table = buildGraphExportTable(
            listOf(
                GraphSeriesExport(
                    name = "Coat check",
                    dataPoints = listOf(DataPoint("Mon", 1f, 1_000L)),
                ),
                GraphSeriesExport(
                    name = "Bar",
                    dataPoints = listOf(
                        DataPoint("Mon", 2f, 1_000L),
                        DataPoint("Tue", 3f, 2_000L),
                    ),
                ),
            ),
        )
        assertEquals(2, table.rows.size)
        assertEquals(listOf(1f, 2f), table.rows[0].values)
        assertNull(table.rows[1].values[0])
        assertEquals(3f, table.rows[1].values[1])
    }
}
