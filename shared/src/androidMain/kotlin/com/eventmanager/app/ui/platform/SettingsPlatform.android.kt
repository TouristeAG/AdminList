package com.eventmanager.app.ui.platform

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import com.eventmanager.app.data.sync.FileManager
import com.eventmanager.app.platform.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

actual fun isAppIconChangeSupported(): Boolean = true

@Composable
actual fun rememberServiceAccountJsonPicker(
    platformContext: PlatformContext,
    onPicked: (Result<String>) -> Unit
): () -> Unit {
    val context = platformContext.androidContext
    val fileManager = androidx.compose.runtime.remember { FileManager(context) }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            onPicked(Result.failure(Exception("Cancelled")))
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            withContext(Dispatchers.IO) {
                fileManager.validateJsonKeyFile(uri)
                    .onSuccess {
                        fileManager.copyFileToAssets(uri, "service_account_key.json")
                            .onSuccess { onPicked(Result.success(it)) }
                            .onFailure { onPicked(Result.failure(it)) }
                    }
                    .onFailure { onPicked(Result.failure(it)) }
            }
        }
    }
    return { launcher.launch(arrayOf("application/json", "text/plain", "*/*")) }
}

@Composable
actual fun SettingsWebView(url: String, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        }
    )
}

actual fun showPlatformToast(platformContext: PlatformContext, message: String) {
    Toast.makeText(platformContext.androidContext, message, Toast.LENGTH_SHORT).show()
}
