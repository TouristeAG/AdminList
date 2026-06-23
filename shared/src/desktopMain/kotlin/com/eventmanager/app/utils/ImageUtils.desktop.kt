package com.eventmanager.app.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import com.eventmanager.app.platform.PlatformContext

actual object ImageUtils {
    actual fun loadScaledImageBitmap(
        platformContext: PlatformContext,
        resourceName: String,
        maxWidthDp: Dp,
        maxHeightDp: Dp
    ): ImageBitmap? = null
}
