package com.eventmanager.app.ui.platform

import androidx.compose.runtime.Composable
import com.eventmanager.app.platform.PlatformContext

@Composable
actual fun NfcUidListenerEffect(
    platformContext: PlatformContext,
    enabled: Boolean,
    onUidRead: (String) -> Unit,
    onScanStatus: (String?) -> Unit,
) {
    ExternalCardReaderUidEffect(
        platformContext = platformContext,
        enabled = enabled,
        onUidRead = onUidRead,
        onScanStatus = onScanStatus,
    )
}
