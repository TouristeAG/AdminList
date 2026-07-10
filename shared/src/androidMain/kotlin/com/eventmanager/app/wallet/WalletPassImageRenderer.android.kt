package com.eventmanager.app.wallet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import java.io.ByteArrayOutputStream

actual object WalletPassImageRenderer {
    actual fun render(request: WalletPassRequest, logoBytes: ByteArray?): WalletPassImages {
        val configuredLogo = logoBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        val icon = if (configuredLogo != null) {
            fitCenterBitmap(configuredLogo, 120, 120, Color.parseColor("#111827"))
        } else {
            createGradientBitmap(120, 120, Color.parseColor("#4F46E5"), Color.parseColor("#7C3AED"), request.holderName)
        }
        val logo = if (configuredLogo != null) {
            fitCenterBitmap(configuredLogo, 320, 100, Color.parseColor("#0F172A"))
        } else {
            createGradientBitmap(
                320, 100,
                Color.parseColor("#0F172A"),
                Color.parseColor("#1E293B"),
                request.associationName,
            )
        }
        val strip = createGradientBitmap(624, 246, Color.parseColor("#312E81"), Color.parseColor("#6D28D9"), "Digital Wallet Pass")

        val files = linkedMapOf(
            "icon.png" to icon.toPngBytes(),
            "icon@2x.png" to Bitmap.createScaledBitmap(icon, 240, 240, true).toPngBytes(),
            "logo.png" to logo.toPngBytes(),
            "logo@2x.png" to Bitmap.createScaledBitmap(logo, 640, 200, true).toPngBytes(),
            "strip.png" to strip.toPngBytes(),
            "strip@2x.png" to Bitmap.createScaledBitmap(strip, 1248, 492, true).toPngBytes(),
        )
        icon.recycle()
        logo.recycle()
        strip.recycle()
        configuredLogo?.recycle()
        return WalletPassImages(files)
    }

    private fun Bitmap.toPngBytes(): ByteArray {
        val buffer = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, buffer)
        return buffer.toByteArray()
    }

    private fun createGradientBitmap(width: Int, height: Int, startColor: Int, endColor: Int, text: String): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                startColor, endColor, Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = (height * 0.18f).coerceAtLeast(18f)
            isFakeBoldText = true
        }
        canvas.drawText(text.take(18), width / 2f, height / 2f + (textPaint.textSize / 3f), textPaint)
        return bitmap
    }

    private fun fitCenterBitmap(source: Bitmap, width: Int, height: Int, backgroundColor: Int): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(backgroundColor)
        val scale = minOf(width.toFloat() / source.width, height.toFloat() / source.height)
        val drawWidth = source.width * scale
        val drawHeight = source.height * scale
        val left = (width - drawWidth) / 2f
        val top = (height - drawHeight) / 2f
        val dest = android.graphics.RectF(left, top, left + drawWidth, top + drawHeight)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, dest, paint)
        return output
    }
}
