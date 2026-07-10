package com.eventmanager.app.ui.platform

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.eventmanager.app.platform.PlatformContext

actual fun showToast(platformContext: PlatformContext, message: String) {
    Toast.makeText(platformContext.androidContext, message, Toast.LENGTH_SHORT).show()
}

actual fun openEmailClient(platformContext: PlatformContext, to: String, subject: String, body: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        platformContext.androidContext.startActivity(intent)
    } catch (_: Exception) { }
}

actual fun openAppSettings(platformContext: PlatformContext) {
    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", platformContext.androidContext.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    platformContext.androidContext.startActivity(intent)
}

@Composable
actual fun PlatformWebView(url: String, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        }
    )
}
