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
 * Full team join secret: Firebase web config + Web OAuth client + invitation (bootstrap) code.
 * Treat as a physical deployment secret; do not post publicly. One scan should configure a device.
 *
 * [webClientSecret] is the institution Firebase **Web** OAuth client secret (Desktop code
 * exchange). It is unrelated to the developer-owned Gmail Desktop OAuth JSON.
 *
 * [bootstrapCode] is the short org invitation code required to create the Firestore member doc.
 */
@Serializable
data class FirebaseJoinPayload(
    val orgId: String,
    val projectId: String,
    val applicationId: String,
    val apiKey: String,
    val webClientId: String = "",
    val webClientSecret: String = "",
    /** Org invitation code — embedded in v1 QR so one scan is enough. */
    val bootstrapCode: String = "",
) {
    fun isComplete(): Boolean =
        orgId.isNotBlank() &&
            projectId.isNotBlank() &&
            applicationId.isNotBlank() &&
            apiKey.isNotBlank()

    fun hasJoinSecrets(): Boolean =
        isComplete() &&
            webClientId.isNotBlank() &&
            webClientSecret.isNotBlank() &&
            bootstrapCode.isNotBlank()

    /** Legacy v2 public slice (no OAuth secret / invite) — decode-only compatibility. */
    fun toPublicPayload(): FirebaseJoinPublicPayload = FirebaseJoinPublicPayload(
        orgId = orgId,
        projectId = projectId,
        applicationId = applicationId,
        apiKey = apiKey,
        webClientId = webClientId,
    )
}

/** Legacy version-2 join payload (no client secret / invite). Still decoded for old QRs. */
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
    /** Full join: project + OAuth secret + invitation code. */
    const val VERSION = 1
    /** Legacy public QR without secrets — still accepted on decode. */
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

    /**
     * Legacy encoder kept for tests / old tooling. Prefer [encode] for team devices.
     */
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
