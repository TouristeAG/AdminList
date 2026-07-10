package com.eventmanager.app.platform.hardware

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.*
import com.github.sarxos.webcam.Webcam
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.client.j2se.BufferedImageLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skia.Image as SkiaImage
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

@Composable
fun DesktopWebcamQrScanView(
    onQrDetected: (String) -> Unit,
    onError: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val openingMsg = stringResource(Res.string.desktop_qr_opening_camera)
    val pointMsg = stringResource(Res.string.desktop_qr_point_at_code)
    val cancelLabel = stringResource(Res.string.cancel)
    val noWebcamMsg = desktopWebcamUnavailableMessage()
    val noFramesMsg = stringResource(Res.string.desktop_qr_no_frames)

    LaunchedEffect(Unit) {
        status = openingMsg
        withContext(Dispatchers.IO) {
            var webcam: Webcam? = null
            try {
                DesktopWebcamSupport.ensureInitialized()
                webcam = Webcam.getWebcams().firstOrNull()
                if (webcam == null) {
                    withContext(Dispatchers.Main) {
                        onError(noWebcamMsg)
                    }
                    return@withContext
                }
                webcam.viewSize = Dimension(640, 480)
                if (!webcam.isOpen) webcam.open()
                withContext(Dispatchers.Main) { status = pointMsg }

                val reader = MultiFormatReader()
                var framesWithoutImage = 0
                var done = false
                while (isActive && !done) {
                    val image = webcam.image
                    if (image == null) {
                        framesWithoutImage++
                        if (framesWithoutImage > 100) {
                            withContext(Dispatchers.Main) {
                                onError(noFramesMsg)
                            }
                            done = true
                        } else {
                            Thread.sleep(50)
                        }
                        continue
                    }
                    framesWithoutImage = 0
                    val decoded = decodeBufferedImage(image, reader)
                    if (decoded != null) {
                        withContext(Dispatchers.Main) { onQrDetected(decoded) }
                        done = true
                    } else {
                        val frame = image.toComposeImageBitmapFast()
                        withContext(Dispatchers.Main) { preview = frame }
                        Thread.sleep(80)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message?.takeIf { it.isNotBlank() } ?: "Webcam scan failed.")
                }
            } finally {
                webcam?.takeIf { it.isOpen }?.close()
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 360.dp)
                .aspectRatio(4f / 3f),
            contentAlignment = Alignment.Center
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                CircularProgressIndicator()
            }
        }
        Text(
            text = status ?: openingMsg,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onCancel) {
            Text(cancelLabel)
        }
    }
}

@Composable
private fun desktopWebcamUnavailableMessage(): String {
    val os = System.getProperty("os.name").orEmpty()
    return when {
        os.contains("linux", ignoreCase = true) ->
            stringResource(Res.string.desktop_qr_no_webcam_linux)
        os.startsWith("Mac", ignoreCase = true) ->
            stringResource(Res.string.desktop_qr_no_webcam_mac)
        else -> stringResource(Res.string.desktop_qr_no_webcam_generic)
    }
}

private fun decodeBufferedImage(image: BufferedImage, reader: MultiFormatReader): String? {
    val source = BufferedImageLuminanceSource(image)
    val bitmap = BinaryBitmap(HybridBinarizer(source))
    return try {
        reader.decode(bitmap).text
    } catch (_: NotFoundException) {
        null
    }
}

private fun BufferedImage.toComposeImageBitmapFast(): ImageBitmap {
    val bytes = ByteArrayOutputStream().use { baos ->
        ImageIO.write(this, "png", baos)
        baos.toByteArray()
    }
    return SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
}
