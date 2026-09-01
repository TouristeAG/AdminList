package com.eventmanager.app.data.remote

import com.eventmanager.app.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Loads deployable Storage rules for in-app clipboard copy.
 * Keep [composeResources/files/storage.rules] in sync with repo-root [firebase/storage.rules].
 */
object StorageRulesClipboardContent {
    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(): String {
        val raw = Res.readBytes("files/storage.rules").decodeToString()
        val sanitized = FirestoreRulesClipboardContent.sanitizeForFirebaseConsole(raw)
        require(sanitized.contains("service firebase.storage")) {
            "files/storage.rules is not Storage rules"
        }
        return sanitized
    }
}
