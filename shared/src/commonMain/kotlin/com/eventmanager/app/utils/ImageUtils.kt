package com.eventmanager.app.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eventmanager.app.platform.PlatformContext

expect object ImageUtils {
    fun loadScaledImageBitmap(
        platformContext: PlatformContext,
        resourceName: String,
        maxWidthDp: Dp = 800.dp,
        maxHeightDp: Dp = 800.dp
    ): ImageBitmap?
}
