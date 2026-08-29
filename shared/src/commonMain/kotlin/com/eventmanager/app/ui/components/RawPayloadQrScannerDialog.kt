package com.eventmanager.app.ui.components

import androidx.compose.runtime.Composable
import com.eventmanager.app.platform.PlatformContext

/** Camera / image QR scan that returns the raw payload string (for Firebase join codes). */
@Composable
expect fun RawPayloadQrScannerDialog(
    platformContext: PlatformContext,
    onDismiss: () -> Unit,
    onPayload: (String) -> Unit,
)
