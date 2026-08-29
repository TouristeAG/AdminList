package com.eventmanager.app.data.remote

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Compact join payload for QR / clipboard: `noctulist-fb:1:<base64url(json)>`.
 * Contains institution Firebase options (web config + Web OAuth client). Treat as a physical
 * deployment secret; do not post publicly.
 *
 * [webClientSecret] is the institution Firebase **Web** OAuth client secret (Desktop code
 * exchange). It is unrelated to the developer-owned Gmail Desktop OAuth JSON.
 */
@Serializable
data class FirebaseJoinPayload(
    val orgId: String,
    val projectId: String,
    val applicationId: String,
    val apiKey: String,
    val webClientId: String = "",
    val webClientSecret: String = "",
    /** Shown to admin only — required when a device joins the org (not in public QR). */
    val bootstrapCode: String = "",
) {
    fun isComplete(): Boolean =
        orgId.isNotBlank() &&
            projectId.isNotBlank() &&
            applicationId.isNotBlank() &&
            apiKey.isNotBlank()

    /** Public join QR: project config without OAuth secret. */
    fun toPublicPayload(): FirebaseJoinPublicPayload = FirebaseJoinPublicPayload(
        orgId = orgId,
        projectId = projectId,
        applicationId = applicationId,
        apiKey = apiKey,
        webClientId = webClientId,
    )
}

/** Version-2 join payload — safe to print on QR (no client secret). */
@Serializable
data class FirebaseJoinPublicPayload(
    val orgId: String,
    val projectId: String,
    val applicationId: String,
    val apiKey: String,
    val webClientId: String = "",
) {
    fun isComplete(): Boolean =
        orgId.isNotBlank() &&
            projectId.isNotBlank() &&
            applicationId.isNotBlank() &&
            apiKey.isNotBlank()
}

object FirebaseJoinCodec {
    const val PREFIX = "noctulist-fb"
    const val VERSION = 1
    const val PUBLIC_VERSION = 2

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun encode(payload: FirebaseJoinPayload): String {
        require(payload.isComplete()) { "Firebase join payload incomplete" }
        val bytes = json.encodeToString(FirebaseJoinPayload.serializer(), payload)
            .encodeToByteArray()
        val b64 = Base64.UrlSafe.encode(bytes).trimEnd('=')
        return "$PREFIX:$VERSION:$b64"
    }

    /** QR / clipboard for team devices — no OAuth client secret. */
    @OptIn(ExperimentalEncodingApi::class)
    fun encodePublic(payload: FirebaseJoinPublicPayload): String {
        require(payload.isComplete()) { "Firebase join payload incomplete" }
        val bytes = json.encodeToString(FirebaseJoinPublicPayload.serializer(), payload)
            .encodeToByteArray()
        val b64 = Base64.UrlSafe.encode(bytes).trimEnd('=')
        return "$PREFIX:$PUBLIC_VERSION:$b64"
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun decode(raw: String): Result<FirebaseJoinPayload> = runCatching {
        val trimmed = raw.trim()
        val parts = trimmed.split(':', limit = 3)
        if (parts.size != 3 || parts[0] != PREFIX) {
            error("Not a NoctuList Firebase join code")
        }
        val version = parts[1].toIntOrNull()
            ?: error("Invalid join code version")
        var b64 = parts[2]
        val pad = (4 - b64.length % 4) % 4
        if (pad > 0) b64 += "=".repeat(pad)
        val decoded = Base64.UrlSafe.decode(b64).decodeToString()
        when (version) {
            VERSION -> {
                val payload = json.decodeFromString(FirebaseJoinPayload.serializer(), decoded)
                if (!payload.isComplete()) error("Join code missing required fields")
                payload
            }
            PUBLIC_VERSION -> {
                val public = json.decodeFromString(FirebaseJoinPublicPayload.serializer(), decoded)
                if (!public.isComplete()) error("Join code missing required fields")
                FirebaseJoinPayload(
                    orgId = public.orgId,
                    projectId = public.projectId,
                    applicationId = public.applicationId,
                    apiKey = public.apiKey,
                    webClientId = public.webClientId,
                )
            }
            else -> error("Unsupported join code version $version")
        }
    }

    /**
     * Parse Firebase Console web SDK snippet / JSON (`apiKey`, `appId`, `projectId`, …).
     * Accepts strict JSON or JS-object style (`apiKey: "..."`).
     */
    fun parseFirebaseWebConfig(raw: String): Result<FirebaseWebConfigPaste> = runCatching {
        val text = raw.trim()
        if (text.isBlank()) error("Empty config")

        fun fromMap(map: Map<String, String>): FirebaseWebConfigPaste {
            val apiKey = map["apiKey"].orEmpty().ifBlank { map["api_key"].orEmpty() }
            val appId = map["appId"].orEmpty().ifBlank {
                map["applicationId"].orEmpty().ifBlank { map["mobilesdk_app_id"].orEmpty() }
            }
            val projectId = map["projectId"].orEmpty().ifBlank { map["project_id"].orEmpty() }
            val webClientId = map["webClientId"].orEmpty()
                .ifBlank { map["clientId"].orEmpty() }
            val webClientSecret = map["webClientSecret"].orEmpty()
                .ifBlank { map["clientSecret"].orEmpty() }
            if (apiKey.isBlank() || appId.isBlank() || projectId.isBlank()) {
                error("Config must include apiKey, appId, and projectId")
            }
            return FirebaseWebConfigPaste(
                apiKey = apiKey,
                applicationId = appId,
                projectId = projectId,
                webClientId = webClientId,
                webClientSecret = webClientSecret,
                gcmSenderId = map["messagingSenderId"].orEmpty()
                    .ifBlank { map["gcm_sender_id"].orEmpty() },
                storageBucket = map["storageBucket"].orEmpty()
                    .ifBlank { map["storage_bucket"].orEmpty() },
            )
        }

        // Strict JSON object
        runCatching {
            val element = json.parseToJsonElement(text)
            val obj = element.jsonObject
            return@runCatching fromMap(
                obj.mapValues { (_, v) -> v.jsonPrimitive.contentOrNull.orEmpty() },
            )
        }.getOrNull()?.let { return@runCatching it }

        // JS firebaseConfig = { apiKey: "...", ... }
        val extracted = linkedMapOf<String, String>()
        val pattern = Regex(
            """(?i)(?:["']?)(apiKey|appId|applicationId|projectId|messagingSenderId|storageBucket|webClientId|clientId|webClientSecret|clientSecret|api_key|project_id|mobilesdk_app_id)(?:["']?)\s*[:=]\s*["']([^"']+)["']""",
        )
        pattern.findAll(text).forEach { m ->
            extracted[m.groupValues[1]] = m.groupValues[2]
        }
        if (extracted.isEmpty()) error("Could not parse Firebase web config")
        fromMap(extracted)
    }
}

data class FirebaseWebConfigPaste(
    val apiKey: String,
    val applicationId: String,
    val projectId: String,
    val webClientId: String = "",
    val webClientSecret: String = "",
    val gcmSenderId: String = "",
    val storageBucket: String = "",
)

/** Whether join/follow UIs should expose project secret fields. Always false when options already present. */
fun firebaseSecretsVisibleForJoin(projectOptionsAlreadyPresent: Boolean): Boolean =
    !projectOptionsAlreadyPresent
