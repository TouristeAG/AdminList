package com.eventmanager.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * When true, continuous background motion (topo / arches) should hold the current frame
 * instead of advancing. Used during Desktop space entrances so a parent [androidx.compose.ui.graphics.graphicsLayer]
 * can reuse its offscreen buffer while only scale / fade / slide change.
 */
val LocalPauseBackgroundMotion = staticCompositionLocalOf { false }

/**
 * Redraw budget for the continuous backgrounds (~30 fps). Both the topographic noise and the
 * arches drift by well under a pixel per display frame (the topo noise coordinate advances
 * ~1/1500th of a pattern per frame at 60 Hz, the arches by ~0.06 px), so sampling their motion
 * at 30 fps is visually identical while halving — or quartering on 120 Hz panels — the number of
 * full-screen draw passes.
 */
internal const val BackgroundFrameIntervalNanos = 33_000_000L
