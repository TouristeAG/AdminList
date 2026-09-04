package com.eventmanager.app.ui.screens

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PosItemGridSpecTest {

    /** Regression: a long catalogue used to collapse into one very wide column per row. */
    @Test
    fun longCatalogue_fillsDesktopPaneHorizontally() {
        val spec = resolvePosItemGridSpec(
            availableWidth = 626.dp,
            availableHeight = 475.dp,
            cellCount = 21,
        )
        assertEquals(4, spec.columns)
        assertTrue(spec.scrollEnabled)
    }

    @Test
    fun longCatalogue_scalesWithWiderPanes() {
        val wide = resolvePosItemGridSpec(1400.dp, 800.dp, cellCount = 60)
        val medium = resolvePosItemGridSpec(900.dp, 800.dp, cellCount = 60)
        assertTrue(wide.columns > medium.columns, "wide=${wide.columns} medium=${medium.columns}")
        assertTrue(wide.columns in 7..9)
    }

    @Test
    fun phonePane_staysAtTwoColumns() {
        val spec = resolvePosItemGridSpec(340.dp, 520.dp, cellCount = 21)
        assertEquals(2, spec.columns)
    }

    @Test
    fun shortCatalogue_fitsWithoutScrolling() {
        val spec = resolvePosItemGridSpec(626.dp, 475.dp, cellCount = 6)
        assertFalse(spec.scrollEnabled)
        assertTrue(spec.tileHeight >= 104.dp)
        assertTrue(spec.tileHeight <= 210.dp)
    }

    /** A handful of products should grow into big tiles rather than sit in one thin strip. */
    @Test
    fun fewProducts_useLargeTiles() {
        val spec = resolvePosItemGridSpec(900.dp, 600.dp, cellCount = 4)
        assertTrue(spec.largeTiles)
        assertFalse(spec.scrollEnabled)
    }

    @Test
    fun singleProduct_neverExceedsOneColumn() {
        val spec = resolvePosItemGridSpec(1400.dp, 800.dp, cellCount = 1)
        assertEquals(1, spec.columns)
    }

    @Test
    fun degenerateConstraints_stayValid() {
        val spec = resolvePosItemGridSpec(0.dp, 0.dp, cellCount = 0)
        assertEquals(1, spec.columns)
        assertTrue(spec.tileHeight > 0.dp)
    }
}
