package com.eventmanager.app.data.remote

import com.eventmanager.app.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import java.io.File

/**
 * Loads the deployable Firestore rules for in-app clipboard copy.
 * Source of truth: [composeResources/files/firestore.rules] (keep in sync with
 * repo-root [firebase/firestore.rules]).
 */
object FirestoreRulesClipboardContent {
    /**
     * Exact UTF-8 text of firestore.rules, trimmed, without BOM.
     * Safe to paste into Firebase Console → Firestore → Rules (not Realtime Database).
     */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun load(): String {
        val raw = Res.readBytes("files/firestore.rules").decodeToString()
        val sanitized = sanitizeForFirebaseConsole(raw)
        // #region agent log
        debugAgentLog(
            hypothesisId = "D",
            location = "FirestoreRulesClipboardContent.load",
            message = "rules_loaded",
            data = mapOf(
                "rawLen" to raw.length,
                "sanitizedLen" to sanitized.length,
                "firstLine" to (sanitized.lineSequence().firstOrNull() ?: ""),
                "firstCodepoints" to (sanitized.lineSequence().firstOrNull()?.map { it.code }?.take(24) ?: emptyList()),
                "startsOk" to sanitized.startsWith("rules_version = '2';"),
                "hasService" to sanitized.contains("service cloud.firestore"),
            ),
        )
        // #endregion
        return sanitized
    }

    fun sanitizeForFirebaseConsole(raw: String): String {
        var text = raw
        if (text.startsWith("\uFEFF")) text = text.removePrefix("\uFEFF")
        text = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        // Strip markdown fences if user/app somehow wrapped the file
        if (text.startsWith("```")) {
            text = text.removePrefix("```").removePrefix("rules").trimStart('\n')
            val fence = text.lastIndexOf("```")
            if (fence >= 0) text = text.substring(0, fence).trim()
        }
        // Strip editor/chat line-number prefixes: "   12|rules_version..."
        text = text.lineSequence().joinToString("\n") { line ->
            val stripped = LINE_NUMBER_PREFIX.matchEntire(line)?.groupValues?.getOrNull(1)
            stripped ?: line
        }.trim()
        // Guard against accidentally copying Kotlin source escapes
        text = text.replace("\${'\$'}", "$").replace("\\$", "$")
        return text
    }

    private val LINE_NUMBER_PREFIX = Regex("""^\s*\d+\|(.*)$""")

    // #region agent log
    fun debugAgentLog(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?>,
        runId: String = "pre-fix",
    ) {
        try {
            val payload = buildString {
                append("{")
                append("\"sessionId\":\"14fef1\",")
                append("\"timestamp\":").append(System.currentTimeMillis()).append(",")
                append("\"hypothesisId\":\"").append(hypothesisId).append("\",")
                append("\"location\":\"").append(location).append("\",")
                append("\"message\":\"").append(message).append("\",")
                append("\"runId\":\"").append(runId).append("\",")
                append("\"data\":{")
                data.entries.forEachIndexed { i, (k, v) ->
                    if (i > 0) append(",")
                    append("\"").append(k).append("\":")
                    when (v) {
                        null -> append("null")
                        is Number, is Boolean -> append(v.toString())
                        is List<*> -> append(v.joinToString(prefix = "[", postfix = "]") { it.toString() })
                        else -> append("\"").append(v.toString().replace("\"", "'").take(200)).append("\"")
                    }
                }
                append("}}")
            }
            File("/Users/leonardomondada/Documents/DEV/NoctuList PC Edition/.cursor/debug-14fef1.log")
                .appendText(payload + "\n")
        } catch (_: Exception) {
        }
    }
    // #endregion
}
