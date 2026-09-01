package com.eventmanager.app.data.remote

import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * GitLive JVM Storage is a stub ([Data] has no payload). Upload via Firebase Storage REST
 * using a refreshed Firebase Auth id token.
 */
internal actual suspend fun firebaseStoragePutJpeg(
    bucket: String,
    path: String,
    jpegBytes: ByteArray,
    platformContext: PlatformContext?,
): String? = withContext(Dispatchers.IO) {
    val token = desktopFirebaseIdToken(platformContext)
        ?: error("Not signed in to Firebase (missing id token)")
    val encodedName = URLEncoder.encode(path, Charsets.UTF_8).replace("+", "%20")
    val uploadUrl = URL(
        "https://firebasestorage.googleapis.com/v0/b/$bucket/o?uploadType=media&name=$encodedName",
    )
    val uploadConn = (uploadUrl.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doOutput = true
        setFixedLengthStreamingMode(jpegBytes.size)
        setRequestProperty("Authorization", "Bearer $token")
        setRequestProperty("Content-Type", "image/jpeg")
        connectTimeout = 30_000
        readTimeout = 60_000
    }
    try {
        uploadConn.outputStream.use { it.write(jpegBytes) }
        val code = uploadConn.responseCode
        val body = storageHttpBody(uploadConn)
        if (code !in 200..299) {
            error("Storage upload HTTP $code: ${body.take(300)}")
        }
        desktopStorageDownloadUrlFromUploadBody(bucket, path, body)
            ?: desktopStorageDownloadUrl(bucket, path, token)
            ?: error("Storage upload succeeded but no download URL was returned")
    } finally {
        uploadConn.disconnect()
    }
}

internal actual suspend fun firebaseStorageDeleteObject(
    bucket: String,
    path: String,
    platformContext: PlatformContext?,
): Boolean = withContext(Dispatchers.IO) {
    val token = desktopFirebaseIdToken(platformContext) ?: return@withContext false
    val encodedPath = URLEncoder.encode(path, Charsets.UTF_8).replace("+", "%20")
    val url = URL("https://firebasestorage.googleapis.com/v0/b/$bucket/o/$encodedPath")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "DELETE"
        setRequestProperty("Authorization", "Bearer $token")
        connectTimeout = 20_000
        readTimeout = 20_000
    }
    try {
        val code = conn.responseCode
        code in 200..299 || code == 404
    } finally {
        conn.disconnect()
    }
}

private fun desktopFirebaseIdToken(platformContext: PlatformContext?): String? {
    val apiKey = platformContext?.let { SettingsManager(it).getFirebaseApiKey() }.orEmpty()
    return DesktopFirebaseGoogleRestSignIn.idTokenForApi(apiKey)
}

private fun storageHttpBody(conn: HttpURLConnection): String {
    val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
    return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
}

private fun desktopStorageDownloadUrlFromUploadBody(bucket: String, path: String, body: String): String? {
    if (body.isBlank()) return null
    val downloadToken = runCatching {
        Json { ignoreUnknownKeys = true }
            .parseToJsonElement(body)
            .jsonObject["downloadTokens"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: return null
    val encodedPath = URLEncoder.encode(path, Charsets.UTF_8).replace("+", "%20")
    return "https://firebasestorage.googleapis.com/v0/b/$bucket/o/$encodedPath?alt=media&token=$downloadToken"
}

private fun desktopStorageDownloadUrl(bucket: String, path: String, token: String): String? {
    val encodedPath = URLEncoder.encode(path, Charsets.UTF_8).replace("+", "%20")
    val url = URL("https://firebasestorage.googleapis.com/v0/b/$bucket/o/$encodedPath")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        setRequestProperty("Authorization", "Bearer $token")
        connectTimeout = 20_000
        readTimeout = 20_000
    }
    return try {
        if (conn.responseCode !in 200..299) return null
        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        desktopStorageDownloadUrlFromUploadBody(bucket, path, body)
    } finally {
        conn.disconnect()
    }
}

internal actual suspend fun firebaseStorageGetBytes(
    bucket: String,
    path: String,
    platformContext: PlatformContext?,
): ByteArray? = withContext(Dispatchers.IO) {
    if (path.isBlank() || bucket.isBlank()) return@withContext null
    val token = desktopFirebaseIdToken(platformContext) ?: return@withContext null
    firebaseStorageDownloadJpegRest(bucket, path, token)
}
