package com.eventmanager.app.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.UidReadResult
import com.eventmanager.app.platform.createCardReaderService
import com.eventmanager.app.resources.Res
import com.eventmanager.app.resources.usb_reader_waiting_card
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.stringResource

/** Polls USB / BLE external NFC readers via [createCardReaderService]. */
@Composable
fun ExternalCardReaderUidEffect(
    platformContext: PlatformContext,
    enabled: Boolean,
    onUidRead: (String) -> Unit,
    onScanStatus: (String?) -> Unit = {},
) {
    val cardReader = remember(platformContext) { createCardReaderService(platformContext) }
    val waitingCardMsg = stringResource(Res.string.usb_reader_waiting_card)

    LaunchedEffect(enabled, cardReader) {
        if (!enabled) {
            onScanStatus(null)
            return@LaunchedEffect
        }
        var lastDispatchedUid: String? = null
        var lastDispatchAtMs = 0L
        while (isActive) {
            if (!cardReader.isReaderConnected()) {
                onScanStatus(null)
                delay(800)
                continue
            }
            onScanStatus(waitingCardMsg)
            when (val result = cardReader.readUid()) {
                is UidReadResult.Success -> {
                    val normalized = result.uid.uppercase()
                    val now = System.currentTimeMillis()
                    if (normalized != lastDispatchedUid || now - lastDispatchAtMs >= UID_REPLAY_GAP_MS) {
                        lastDispatchedUid = normalized
                        lastDispatchAtMs = now
                        onUidRead(result.uid)
                    }
                    delay(280)
                }
                is UidReadResult.Retryable -> delay(300)
                is UidReadResult.Fatal -> {
                    onScanStatus(result.error)
                    delay(800)
                }
                UidReadResult.NoReader -> {
                    onScanStatus(null)
                    delay(800)
                }
            }
        }
    }
}

private const val UID_REPLAY_GAP_MS = 850L
