package com.eventmanager.app.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * GitLive's JVM Auth does not implement Google credential sign-in ([NotImplementedError]).
 * Exchange a Google OpenID id_token via Identity Toolkit REST, then inject the session into
 * [FirebasePlatform] storage (`com.google.firebase.auth.FIREBASE_USER`) so Firestore attaches auth.
 */
internal object DesktopFirebaseGoogleRestSignIn {
    private const val FIREBASE_USER_KEY = "com.google.firebase.auth.FIREBASE_USER"
    private val json = Json { ignoreUnknownKeys = true }

    fun signInWithGoogleIdToken(
        apiKey: String,
        googleIdToken: String,
        projectId: String = "",
    ): FirebaseAuthResult {
        val rawLen = apiKey.trim().length
        val key = sanitizeApiKey(apiKey)
        if (key.isBlank()) {
            return FirebaseAuthResult.Error("Firebase API key is required for Desktop Sign-In")
        }
        if (!looksLikeGoogleApiKey(key) || rawLen > 60) {
            return FirebaseAuthResult.Error(
                "Firebase API key field is invalid (${apiKeyFingerprint(key)}; pasted length=$rawLen). " +
                    "Paste ONLY the key (starts with AIzaSy, about 39 characters) — " +
                    "not the whole firebaseConfig JSON. " +
                    "Copy apiKey alone from Firebase → Project settings → Your apps → Web config.",
            )
        }
        if (googleIdToken.isBlank()) {
            return FirebaseAuthResult.Error("Missing Google id_token")
        }
        return try {
            // Fail fast with a clear fingerprint before the long Google browser flow results
            // are thrown away on a bad key.
            preflightApiKey(key)?.let { return it }

            val response = postSignInWithIdp(key, googleIdToken, projectId)
            val root = json.parseToJsonElement(response).jsonObject
            val error = root["error"]?.jsonObject
            if (error != null) {
                val message = error["message"]?.jsonPrimitive?.contentOrNull
                    ?: error.toString()
                return FirebaseAuthResult.Error(mapIdentityToolkitError(message, key))
            }
            val idToken = root.string("idToken")
                ?: return FirebaseAuthResult.Error("Firebase Auth returned no idToken")
            val refreshToken = root.string("refreshToken").orEmpty()
            val uid = root.string("localId")
                ?: return FirebaseAuthResult.Error("Firebase Auth returned no localId")
            val email = root.string("email")
            val expiresIn = root.string("expiresIn")?.toLongOrNull() ?: 3600L

            // Shape expected by firebase-java-sdk FirebaseUserImpl serializer.
            val userJson = buildJsonObject {
                put("uid", uid)
                put("isAnonymous", false)
                put("idToken", idToken)
                put("refreshToken", refreshToken)
                put("expiresIn", expiresIn)
                put("createdAt", System.currentTimeMillis())
                if (!email.isNullOrBlank()) put("email", email)
            }.toString()

            val platform = FirebaseBootstrap.platformOrNull()
                ?: return FirebaseAuthResult.Error(
                    "FirebasePlatform not initialized — fill Project ID / Application ID / API key first",
                )
            platform.store(FIREBASE_USER_KEY, userJson)

            FirebaseAuthResult.Success(uid = uid, email = email)
        } catch (e: Exception) {
            FirebaseAuthResult.Error(e.message ?: "Desktop Firebase REST sign-in failed")
        }
    }

    fun clearStoredUser() {
        FirebaseBootstrap.platformOrNull()?.clear(FIREBASE_USER_KEY)
    }

    fun hydrateSessionFromStore() {
        val raw = FirebaseBootstrap.platformOrNull()?.retrieve(FIREBASE_USER_KEY) ?: return
        runCatching {
            val root = json.parseToJsonElement(raw).jsonObject
            val uid = root.string("uid") ?: return
            val email = root.string("email")
            DesktopFirebaseSession.uid = uid
            DesktopFirebaseSession.email = email
        }
    }

    /**
     * Returns a usable Firebase Auth id token, refreshing via the stored refresh token when
     * the session is missing or near expiry. Storage REST uploads need this — GitLive JVM
     * Auth does not refresh automatically.
     */
    fun idTokenForApi(apiKey: String): String? {
        val platform = FirebaseBootstrap.platformOrNull() ?: return null
        val raw = platform.retrieve(FIREBASE_USER_KEY) ?: return null
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val current = root.string("idToken")?.takeIf { it.isNotBlank() }
        val refreshToken = root.string("refreshToken")?.takeIf { it.isNotBlank() }
        val createdAt = root.longish("createdAt") ?: 0L
        val expiresInSec = root.longish("expiresIn") ?: 3600L
        val stillValid = current != null &&
            createdAt > 0L &&
            System.currentTimeMillis() < createdAt + (expiresInSec - 120L).coerceAtLeast(60L) * 1000L
        if (stillValid) return current
        if (refreshToken.isNullOrBlank() || apiKey.isBlank()) return current
        return refreshAndStore(apiKey, refreshToken, root) ?: current
    }

    private fun refreshAndStore(apiKey: String, refreshToken: String, previous: JsonObject): String? {
        val key = sanitizeApiKey(apiKey)
        if (key.isBlank()) return null
        val url = URL("https://securetoken.googleapis.com/v1/token?key=${URLEncoder.encode(key, Charsets.UTF_8)}")
        val body = "grant_type=refresh_token&refresh_token=${URLEncoder.encode(refreshToken, Charsets.UTF_8)}"
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connectTimeout = 20_000
            readTimeout = 20_000
        }
        return try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (conn.responseCode !in 200..299) {
                println("Firebase token refresh failed (${conn.responseCode}): ${text.take(200)}")
                return null
            }
            val refreshed = json.parseToJsonElement(text).jsonObject
            val idToken = refreshed.string("id_token") ?: refreshed.string("idToken") ?: return null
            val nextRefresh = refreshed.string("refresh_token") ?: refreshed.string("refreshToken") ?: refreshToken
            val expiresIn = refreshed.string("expires_in")?.toLongOrNull()
                ?: refreshed.longish("expires_in")
                ?: 3600L
            val userJson = buildJsonObject {
                previous.forEach { (k, v) -> put(k, v) }
                put("idToken", idToken)
                put("refreshToken", nextRefresh)
                put("expiresIn", expiresIn)
                put("createdAt", System.currentTimeMillis())
            }.toString()
            FirebaseBootstrap.platformOrNull()?.store(FIREBASE_USER_KEY, userJson)
            idToken
        } catch (e: Exception) {
            println("Firebase token refresh failed: ${e.message}")
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Strip paste artifacts that make Google return API_KEY_INVALID. */
    internal fun sanitizeApiKey(raw: String): String {
        var s = raw.trim().replace("\uFEFF", "")
        // Whole Firebase web config pasted into the API key field by mistake.
        if (s.contains("apiKey") && (s.contains('{') || s.contains(':'))) {
            val fromJson = Regex(
                """(?i)["']?apiKey["']?\s*[:=]\s*["'](AIza[^"'\s]+)["']""",
            ).find(s)?.groupValues?.getOrNull(1)
            if (!fromJson.isNullOrBlank()) return fromJson
        }
        // Key + trailing JSON / quotes: AIzaSy…","appId":…
        val embedded = Regex("""AIza[0-9A-Za-z_-]{20,}""").find(s)?.value
        if (!embedded.isNullOrBlank()) {
            s = embedded
        }
        s = s.trim().trim('"', '\'', ',', '}', ']', ' ')
        return s.replace(Regex("\\s+"), "")
    }

    internal fun apiKeyFingerprint(apiKey: String): String {
        val k = sanitizeApiKey(apiKey)
        if (k.length < 12) return "(key too short, len=${k.length})"
        return "${k.take(8)}…${k.takeLast(4)} (len=${k.length})"
    }

    /** True when the value looks like a real Google browser/API key. */
    internal fun looksLikeGoogleApiKey(apiKey: String): Boolean {
        val k = sanitizeApiKey(apiKey)
        // Current Google API keys are typically 39 chars: AIzaSy + 33.
        return k.matches(Regex("""AIza[0-9A-Za-z_-]{30,}""")) && k.length in 35..45
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.longish(key: String): Long? {
        val p = this[key]?.jsonPrimitive ?: return null
        return p.longOrNull ?: p.contentOrNull?.toLongOrNull()
    }

    /**
     * Probe Identity Toolkit with the key alone (invalid token). Distinguishes key rejection
     * from id_token / OAuth problems. Returns an error only when the key itself is rejected.
     */
    fun probeApiKey(apiKey: String): FirebaseAuthResult.Error? {
        val rawLen = apiKey.trim().length
        val key = sanitizeApiKey(apiKey)
        if (key.isBlank()) {
            return FirebaseAuthResult.Error("Firebase API key is required for Desktop Sign-In")
        }
        if (!looksLikeGoogleApiKey(key) || rawLen > 60) {
            return FirebaseAuthResult.Error(
                "Firebase API key field is invalid (${apiKeyFingerprint(key)}; pasted length=$rawLen). " +
                    "Paste ONLY the key (starts with AIzaSy, about 39 characters) — " +
                    "not the whole firebaseConfig JSON.",
            )
        }
        return preflightApiKey(key)
    }

    /**
     * Probe Identity Toolkit with the key alone (invalid token). Distinguishes key rejection
     * from id_token / OAuth problems.
     */
    private fun preflightApiKey(apiKey: String): FirebaseAuthResult.Error? {
        val body = postSignInWithIdp(apiKey, googleIdToken = "preflight-invalid", projectId = "")
        val errMsg = runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull().orEmpty()
        if (errMsg.contains("API key not valid", ignoreCase = true) ||
            errMsg.contains("API_KEY_INVALID", ignoreCase = true)
        ) {
            return FirebaseAuthResult.Error(mapIdentityToolkitError(errMsg, apiKey))
        }
        // Any other error (INVALID_IDP_RESPONSE, etc.) means the key was accepted.
        return null
    }

    private fun mapIdentityToolkitError(message: String, apiKey: String): String {
        val m = message.trim()
        val fp = apiKeyFingerprint(apiKey)
        if (m.contains("API key not valid", ignoreCase = true) ||
            m.contains("API_KEY_INVALID", ignoreCase = true)
        ) {
            return "Firebase Auth: API key rejected ($fp). " +
                "Cloud Console checklist: (1) paste THIS new key into NoctuList and save, " +
                "(2) Application restrictions = None, (3) API restrictions = Don’t restrict key " +
                "(temporarily) OR include Identity Toolkit API + Token Service API, " +
                "(4) wait up to 5 minutes after Save, (5) key must belong to the same project " +
                "as Project ID. Then retry Sign-In."
        }
        return "Firebase Auth: $m ($fp)"
    }

    private fun postSignInWithIdp(apiKey: String, googleIdToken: String, projectId: String): String {
        val url = URL(
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=" +
                URLEncoder.encode(apiKey, Charsets.UTF_8),
        )
        val postBody = "id_token=${URLEncoder.encode(googleIdToken, Charsets.UTF_8)}" +
            "&providerId=google.com"
        val requestUri = if (projectId.isNotBlank()) {
            "https://$projectId.firebaseapp.com"
        } else {
            "http://localhost"
        }
        val bodyJson = buildJsonObject {
            put("postBody", postBody)
            put("requestUri", requestUri)
            put("returnIdpCredential", true)
            put("returnSecureToken", true)
        }.toString()

        // null = no Referer (correct for Application restrictions = None).
        // Extra referers help Browser keys that only allow firebaseapp.com / localhost.
        val referers: List<String?> = buildList {
            add(null)
            if (projectId.isNotBlank()) {
                add("https://$projectId.firebaseapp.com/")
                add("https://$projectId.web.app/")
            }
            add("http://localhost/")
            add("http://localhost")
        }

        var lastBody: String? = null
        for (referer in referers) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                if (referer != null) {
                    setRequestProperty("Referer", referer)
                    setRequestProperty("Origin", referer.trimEnd('/'))
                }
                connectTimeout = 30_000
                readTimeout = 30_000
            }
            conn.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) {
                conn.inputStream
            } else {
                conn.errorStream ?: continue
            }
            val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            lastBody = body
            val errMsg = runCatching {
                json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
                    ?.get("message")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            val keyRejected = errMsg?.contains("API key not valid", ignoreCase = true) == true ||
                errMsg?.contains("API_KEY_INVALID", ignoreCase = true) == true
            if (!keyRejected) return body
        }
        return lastBody ?: """{"error":{"message":"API key not valid. Please pass a valid API key."}}"""
    }
}
