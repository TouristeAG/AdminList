package com.eventmanager.app.ui.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.appDataDir
import com.eventmanager.app.platform.openUrl
import java.awt.Desktop
import java.net.URI
import javax.swing.JOptionPane

actual fun showToast(platformContext: PlatformContext, message: String) {
    JOptionPane.showMessageDialog(null, message, "NoctuList", JOptionPane.INFORMATION_MESSAGE)
}

actual fun openEmailClient(platformContext: PlatformContext, to: String, subject: String, body: String) {
    if (Desktop.isDesktopSupported()) {
        val uri = URI("mailto:$to?subject=${UriEncoder.encode(subject)}&body=${UriEncoder.encode(body)}")
        Desktop.getDesktop().mail(uri)
    }
}

actual fun openAppSettings(platformContext: PlatformContext) {
    openUrl(platformContext.appDataDir.absolutePath)
}

@Composable
actual fun PlatformWebView(url: String, modifier: Modifier) {
    Box(modifier) {
        Text("Open in browser: $url")
    }
}

private object UriEncoder {
    fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8)
}
