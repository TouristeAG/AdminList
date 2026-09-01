package com.eventmanager.app.data.remote

import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Firestore reads/writes via HTTPS REST (HttpURLConnection).
 *
 * GitLive Firestore on JVM uses gRPC inside jpackage-trimmed runtimes (all desktop OSes) and can hang
 * indefinitely on [get] / listeners while `./gradlew :desktopApp:run` works with a full JDK.
 */
internal class DesktopFirestoreRestClient(
    private val platformContext: PlatformContext,
    private val settingsManager: SettingsManager,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun isReady(): Boolean {
        if (!FirebaseBootstrap.isInitialized()) return false
        val projectId = settingsManager.getFirebaseProjectId().trim()
        return projectId.isNotBlank()
    }

    fun listCollection(
        orgId: String,
        collection: String,
        fieldPaths: List<String>? = null,
    ): List<Pair<String, Map<String, Any?>>> {
        val projectId = settingsManager.getFirebaseProjectId().trim()
        require(projectId.isNotBlank()) { "Firebase project ID missing" }
        val results = mutableListOf<Pair<String, Map<String, Any?>>>()
        var pageToken: String? = null
        do {
            val encodedPath = encodePath("orgs/$orgId/$collection")
            val url = buildString {
                append(baseUrl(projectId))
                append("/$encodedPath")
                var hasQuery = false
                if (!pageToken.isNullOrBlank()) {
                    append("?pageToken=")
                    append(URLEncoder.encode(pageToken, Charsets.UTF_8.name()))
                    hasQuery = true
                }
                fieldPaths.orEmpty().forEach { field ->
                    append(if (hasQuery) "&" else "?")
                    append("mask.fieldPaths=")
                    append(URLEncoder.encode(field, Charsets.UTF_8.name()))
                    hasQuery = true
                }
            }
            val body = authorizedGet(url)
            val root = json.parseToJsonElement(body).jsonObject
            root["documents"]?.jsonArray?.forEach { docEl ->
                val doc = docEl.jsonObject
                val name = doc["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val docId = name.substringAfterLast('/')
                if (docId.isBlank()) return@forEach
                val fields = doc["fields"]?.jsonObject
                results += docId to DesktopFirestoreRestCodec.decodeDocumentFields(fields)
            }
            pageToken = root["nextPageToken"]?.jsonPrimitive?.contentOrNull
        } while (!pageToken.isNullOrBlank())
        return results
    }

    fun getDocument(orgId: String, collection: String, docId: String): Map<String, Any?>? {
        val projectId = settingsManager.getFirebaseProjectId().trim()
        require(projectId.isNotBlank()) { "Firebase project ID missing" }
        val encodedPath = encodePath("orgs/$orgId/$collection/$docId")
        val url = "${baseUrl(projectId)}/$encodedPath"
        return try {
            val body = authorizedGet(url)
            val doc = json.parseToJsonElement(body).jsonObject
            DesktopFirestoreRestCodec.decodeDocumentFields(doc["fields"]?.jsonObject)
        } catch (e: IOException) {
            if (e.message?.contains("HTTP 404") == true) null else throw e
        }
    }

    fun upsertDocument(orgId: String, collection: String, docId: String, data: Map<String, Any?>) {
        if (collection == "transfers") {
            val existing = runCatching { getDocument(orgId, collection, docId) }.getOrNull()
            if (existing != null && existing.isNotEmpty()) return
        }
        val projectId = settingsManager.getFirebaseProjectId().trim()
        require(projectId.isNotBlank()) { "Firebase project ID missing" }
        val encodedPath = encodePath("orgs/$orgId/$collection/$docId")
        val fields = DesktopFirestoreRestCodec.encodeDocumentFields(data)
        val mask = fields.keys.joinToString("&") { key ->
            "updateMask.fieldPaths=${URLEncoder.encode(key, Charsets.UTF_8.name())}"
        }
        val url = "${baseUrl(projectId)}/$encodedPath?$mask"
        val payload = buildJsonObject { put("fields", fields) }.toString()
        authorizedPatch(url, payload)
    }

    fun deleteDocument(orgId: String, collection: String, docId: String) {
        val projectId = settingsManager.getFirebaseProjectId().trim()
        require(projectId.isNotBlank()) { "Firebase project ID missing" }
        val encodedPath = encodePath("orgs/$orgId/$collection/$docId")
        val url = "${baseUrl(projectId)}/$encodedPath"
        authorizedDelete(url)
    }

    private fun baseUrl(projectId: String): String =
        "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"

    private fun encodePath(path: String): String =
        path.split('/').joinToString("/") { segment ->
            URLEncoder.encode(segment, Charsets.UTF_8.name())
        }

    private fun idToken(): String {
        val apiKey = settingsManager.getFirebaseApiKey().trim()
        return DesktopFirebaseGoogleRestSignIn.idTokenForApi(apiKey)
            ?: error("Not signed in to Firebase (missing id token)")
    }

    private fun authorizedGet(url: String): String =
        httpRequest(url, "GET", null)

    private fun authorizedPatch(url: String, body: String) {
        httpRequest(url, "PATCH", body)
    }

    private fun authorizedDelete(url: String) {
        httpRequest(url, "DELETE", null)
    }

    private fun httpRequest(url: String, method: String, body: String?): String {
        val token = idToken()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
        }
        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val responseBody = if (code in 200..299) {
                conn.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            } else {
                conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            if (code !in 200..299) {
                throw IOException("Firestore REST HTTP $code: ${responseBody.take(400)}")
            }
            return responseBody
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 60_000
    }
}
