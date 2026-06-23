package com.eventmanager.app.utils

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import com.eventmanager.app.platform.PlatformContext
import kotlin.math.min

actual object ImageUtils {
    actual fun loadScaledImageBitmap(
        platformContext: PlatformContext,
        resourceName: String,
        maxWidthDp: Dp,
        maxHeightDp: Dp
    ): ImageBitmap? {
        return try {
            val ctx = platformContext.androidContext
            val resId = ctx.resources.getIdentifier(resourceName, "drawable", ctx.packageName)
            if (resId == 0) return null
            val density = ctx.resources.displayMetrics.density
            val maxWidthPx = (maxWidthDp.value * density).toInt()
            val maxHeightPx = (maxHeightDp.value * density).toInt()
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(ctx.resources, resId, options)
            options.inSampleSize = calculateInSampleSize(options, maxWidthPx, maxHeightPx)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeResource(ctx.resources, resId, options)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
