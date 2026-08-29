package com.eventmanager.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.hardware.DesktopQrScanResult
import com.eventmanager.app.platform.hardware.DesktopQrScanner
import com.eventmanager.app.platform.hardware.DesktopWebcamQrScanView
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.cancel
import com.eventmanager.app.resources.firebase_join_scan
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun RawPayloadQrScannerDialog(
    platformContext: PlatformContext,
    onDismiss: () -> Unit,
    onPayload: (String) -> Unit,
) {
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val qrScanner = remember { DesktopQrScanner() }

    fun handleFileResult(result: DesktopQrScanResult) {
        when (result) {
            is DesktopQrScanResult.Success -> {
                onPayload(result.payload)
                onDismiss()
            }
            is DesktopQrScanResult.Error -> status = result.message
            DesktopQrScanResult.NotFound -> status = "No QR code found"
            DesktopQrScanResult.Cancelled -> Unit
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.firebase_join_scan)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DesktopWebcamQrScanView(
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                        onQrDetected = { payload ->
                            onPayload(payload)
                            onDismiss()
                        },
                        onError = { status = it },
                        onCancel = onDismiss,
                    )
                    TextButton(
                        onClick = {
                            scope.launch { handleFileResult(qrScanner.scanFromImageFile()) }
                        },
                    ) {
                        Text("Scan from image…")
                    }
                    status?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}
