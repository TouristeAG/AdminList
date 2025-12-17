package com.eventmanager.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

object ImageUtils {
    /**
     * Loads a drawable resource and scales it to fit within the maximum dimensions.
     * This prevents "too large bitmap" errors on older devices.
     * 
     * @param context The context to load resources
     * @param resId The drawable resource ID
     * @param maxWidthDp Maximum width in dp (default: 800dp)
     * @param maxHeightDp Maximum height in dp (default: 800dp)
     * @return Scaled ImageBitmap or null if loading fails
     */
    fun loadScaledImageBitmap(
        context: Context,
        resId: Int,
        maxWidthDp: Dp = 800.dp,
        maxHeightDp: Dp = 800.dp
    ): ImageBitmap? {
        return try {
            val density = context.resources.displayMetrics.density
            val maxWidthPx = (maxWidthDp.value * density).toInt()
            val maxHeightPx = (maxHeightDp.value * density).toInt()
            
            // First, decode just the dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeResource(context.resources, resId, options)
            
            // Calculate sample size to reduce memory usage
            options.inSampleSize = calculateInSampleSize(
                options.outWidth,
                options.outHeight,
                maxWidthPx,
                maxHeightPx
            )
            
            // Now decode the actual bitmap with the sample size
            options.inJustDecodeBounds = false
            options.inScaled = false // We'll handle scaling ourselves
            // Use ARGB_8888 to support transparency (needed for logos with alpha)
            // The inSampleSize will still reduce memory usage significantly
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            
            var bitmap = BitmapFactory.decodeResource(context.resources, resId, options)
            
            if (bitmap != null) {
                // Further scale if needed to ensure it fits within max dimensions
                val scale = min(
                    maxWidthPx.toFloat() / bitmap.width,
                    maxHeightPx.toFloat() / bitmap.height
                ).coerceAtMost(1.0f) // Don't upscale
                
                if (scale < 1.0f) {
                    val scaledWidth = (bitmap.width * scale).toInt()
                    val scaledHeight = (bitmap.height * scale).toInt()
                    bitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                }
            }
            
            bitmap?.asImageBitmap()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Calculates the appropriate inSampleSize for BitmapFactory.Options
     * to reduce memory usage when loading large images.
     */
    private fun calculateInSampleSize(
        imageWidth: Int,
        imageHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        
        if (imageHeight > reqHeight || imageWidth > reqWidth) {
            val halfHeight = imageHeight / 2
            val halfWidth = imageWidth / 2
            
            // Calculate the largest inSampleSize value that is a power of 2
            // and keeps both height and width larger than the requested height and width
            while ((halfHeight / inSampleSize) >= reqHeight &&
                   (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
}

