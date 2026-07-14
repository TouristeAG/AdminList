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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
        var webcam: Webcam? = null
        try {
            withContext(Dispatchers.IO) {
                DesktopWebcamSupport.ensureInitialized()
                webcam = Webcam.getWebcams().firstOrNull()
                val cam = webcam
                if (cam == null) {
                    withContext(Dispatchers.Main.immediate) {
                        onError(noWebcamMsg)
                    }
                    return@withContext
                }
                cam.viewSize = Dimension(640, 480)
                if (!cam.isOpen) cam.open()
                withContext(Dispatchers.Main.immediate) { status = pointMsg }

                val reader = MultiFormatReader()
                var framesWithoutImage = 0
                while (isActive) {
                    ensureActive()
                    val image = cam.image
                    if (image == null) {
                        framesWithoutImage++
                        if (framesWithoutImage > 100) {
                            withContext(Dispatchers.Main.immediate) {
                                onError(noFramesMsg)
                            }
                            break
                        }
                        delay(50)
                        continue
                    }
                    framesWithoutImage = 0
                    val decoded = decodeBufferedImage(image, reader)
                    if (decoded != null) {
                        withContext(Dispatchers.Main.immediate) { onQrDetected(decoded) }
                        break
                    }
                    val frame = image.toComposeImageBitmapFast()
                    withContext(Dispatchers.Main.immediate) { preview = frame }
                    delay(80)
                }
            }
        } catch (e: CancellationException) {
            // Closing the scanner / leaving the screen — normal, do not report as error.
            throw e
        } catch (e: Exception) {
            withContext(Dispatchers.Main.immediate) {
                onError(e.message?.takeIf { it.isNotBlank() } ?: "Webcam scan failed.")
            }
        } finally {
            // Always release the camera even when the LaunchedEffect is cancelled.
            withContext(NonCancellable + Dispatchers.IO) {
                runCatching {
                    webcam?.takeIf { it.isOpen }?.close()
                }
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
    } catch (_: Exception) {
        // Corrupt / mid-teardown frames while the camera is closing.
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
