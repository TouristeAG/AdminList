package com.eventmanager.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates a lightweight .pkpass package using the same QR payload used for entry.
 *
 * Note: Apple Wallet requires signed passes for full compatibility.
 * This package is intentionally lightweight so compatible wallet apps can still import it.
 */
object DigitalWalletPassGenerator {

    fun createPassFile(
        context: Context,
        serialNumber: String,
        holderName: String,
        qrPayload: String,
        logoUriString: String? = null,
        associationName: String = "Collectif Nocturne"
    ): File? {
        return try {
            val passFile = File(context.cacheDir, "digital_wallet_pass_${serialNumber}.pkpass")
            ZipOutputStream(FileOutputStream(passFile)).use { zip ->
                val passJson = buildPassJson(
                    serialNumber = serialNumber,
                    holderName = holderName,
                    qrPayload = qrPayload,
                    associationName = associationName
                )
                zip.putNextEntry(ZipEntry("pass.json"))
                zip.write(passJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // Use the configured association logo when available.
                val configuredLogo = loadConfiguredLogo(context, logoUriString)
                val icon = if (configuredLogo != null) {
                    fitCenterBitmap(configuredLogo, 120, 120, Color.parseColor("#111827"))
                } else {
                    createGradientBitmap(120, 120, Color.parseColor("#4F46E5"), Color.parseColor("#7C3AED"), holderName)
                }
                val logo = if (configuredLogo != null) {
                    fitCenterBitmap(configuredLogo, 320, 100, Color.parseColor("#0F172A"))
                } else {
                    createGradientBitmap(320, 100, Color.parseColor("#0F172A"), Color.parseColor("#1E293B"), associationName)
                }
                val strip = createGradientBitmap(624, 246, Color.parseColor("#312E81"), Color.parseColor("#6D28D9"), "Digital Wallet Pass")

                writeBitmapEntry(zip, "icon.png", icon)
                writeBitmapEntry(zip, "icon@2x.png", Bitmap.createScaledBitmap(icon, 240, 240, true))
                writeBitmapEntry(zip, "logo.png", logo)
                writeBitmapEntry(zip, "logo@2x.png", Bitmap.createScaledBitmap(logo, 640, 200, true))
                writeBitmapEntry(zip, "strip.png", strip)
                writeBitmapEntry(zip, "strip@2x.png", Bitmap.createScaledBitmap(strip, 1248, 492, true))

                icon.recycle()
                logo.recycle()
                strip.recycle()
                configuredLogo?.recycle()
            }
            passFile
        } catch (_: Exception) {
            null
        }
    }

    private fun buildPassJson(
        serialNumber: String,
        holderName: String,
        qrPayload: String,
        associationName: String
    ): String {
        val safeName = holderName.replace("\"", "\\\"")
        val safePayload = qrPayload.replace("\"", "\\\"")
        val safeSerial = serialNumber.replace("\"", "\\\"")
        val safeAssociationName = associationName.replace("\"", "\\\"").ifBlank { "Collectif Nocturne" }
        return """
            {
              "formatVersion": 1,
              "passTypeIdentifier": "pass.com.eventmanager.app.entry",
              "serialNumber": "$safeSerial",
              "teamIdentifier": "EVENTMGR",
              "organizationName": "$safeAssociationName",
              "description": "Digital Wallet Pass",
              "logoText": "$safeAssociationName",
              "foregroundColor": "rgb(255, 255, 255)",
              "backgroundColor": "rgb(38, 38, 38)",
              "labelColor": "rgb(203, 213, 225)",
              "barcodes": [
                {
                  "format": "PKBarcodeFormatQR",
                  "message": "$safePayload",
                  "messageEncoding": "iso-8859-1",
                  "altText": "$safeName"
                }
              ],
              "barcode": {
                "format": "PKBarcodeFormatQR",
                "message": "$safePayload",
                "messageEncoding": "iso-8859-1",
                "altText": "$safeName"
              },
              "eventTicket": {
                "primaryFields": [
                  {
                    "key": "holder",
                    "label": "PASS HOLDER",
                    "value": "$safeName"
                  }
                ],
                "secondaryFields": [
                  {
                    "key": "entry",
                    "label": "ENTRY",
                    "value": "Use QR at check-in"
                  }
                ],
                "auxiliaryFields": [
                  {
                    "key": "reference",
                    "label": "REFERENCE",
                    "value": "$safeSerial"
                  }
                ],
                "backFields": [
                  {
                    "key": "compatibility",
                    "label": "Digital Wallet Pass",
                    "value": ".pkpass works with Apple Wallet and compatible Android wallet apps."
                  }
                ]
              }
            }
        """.trimIndent()
    }

    private fun writeBitmapEntry(zip: ZipOutputStream, name: String, bitmap: Bitmap) {
        zip.putNextEntry(ZipEntry(name))
        val buffer = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)
        zip.write(buffer.toByteArray())
        zip.closeEntry()
    }

    private fun createGradientBitmap(
        width: Int,
        height: Int,
        startColor: Int,
        endColor: Int,
        text: String
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                startColor,
                endColor,
                Shader.TileMode.CLAMP
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

    private fun loadConfiguredLogo(context: Context, logoUriString: String?): Bitmap? {
        if (logoUriString.isNullOrBlank()) return null
        return try {
            context.contentResolver.openInputStream(Uri.parse(logoUriString)).use { stream ->
                if (stream != null) BitmapFactory.decodeStream(stream) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fitCenterBitmap(source: Bitmap, width: Int, height: Int, backgroundColor: Int): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(backgroundColor)

        val scale = minOf(width.toFloat() / source.width.toFloat(), height.toFloat() / source.height.toFloat())
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
