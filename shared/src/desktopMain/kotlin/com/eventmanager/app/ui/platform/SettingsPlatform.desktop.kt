package com.eventmanager.app.ui.platform

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.PlatformFileManager
import com.eventmanager.app.ui.platform.PlatformWebView
import com.eventmanager.app.ui.platform.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

actual fun isAppIconChangeSupported(): Boolean = false

@Composable
actual fun rememberServiceAccountJsonPicker(
    platformContext: PlatformContext,
    onPicked: (Result<String>) -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val fileManager = androidx.compose.runtime.remember(platformContext) { PlatformFileManager(platformContext) }
    return {
        scope.launch {
            val json = withContext(Dispatchers.IO) { fileManager.pickServiceAccountJsonFile() }
            if (json == null) onPicked(Result.failure(Exception("Cancelled")))
            else {
                val saved = withContext(Dispatchers.IO) { fileManager.saveServiceAccountJson(json) }
                if (saved) onPicked(Result.success("saved")) else onPicked(Result.failure(Exception("Save failed")))
            }
        }
    }
}

@Composable
actual fun SettingsWebView(url: String, modifier: Modifier) {
    PlatformWebView(url = url, modifier = modifier.fillMaxSize())
}

actual fun showPlatformToast(platformContext: PlatformContext, message: String) {
    showToast(platformContext, message)
}
