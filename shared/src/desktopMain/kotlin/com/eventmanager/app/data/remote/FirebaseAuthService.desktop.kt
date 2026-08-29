package com.eventmanager.app.data.remote

import com.eventmanager.app.data.sync.SettingsManager
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.appDataDir
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.GenericUrl
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.io.StringReader
import java.net.URI

/**
 * Desktop Google OAuth → GitLive Firebase Auth.
 * Always runs an interactive OAuth code exchange so an OpenID [id_token] is present
 * (refresh-only credentials never include id_token, which Firebase Auth requires).
 */
class DesktopFirebaseAuthService(
    private val platformContext: PlatformContext,
) : FirebaseAuthService {
    private val settings by lazy { SettingsManager(platformContext) }
    private val accountFile = File(platformContext.appDataDir, "firebase_auth_account.properties")
    private val tokenDir = File(platformContext.appDataDir, "firebase_auth_tokens").also { it.mkdirs() }
    private val idTokenFile = File(platformContext.appDataDir, "firebase_auth_id_token.txt")

    override suspend fun signInWithGoogle(): FirebaseAuthResult = withContext(Dispatchers.IO) {
        try {
            val options = FirebaseOptionsReader.fromSettings(settings)
            if (options == null) {
                return@withContext FirebaseAuthResult.Error(
                    "Firebase project options incomplete. Fill Project ID, Application ID and API key " +
                        "(Web app from the same Google Cloud project as Sheets), then try Sign-In again.",
                )
            }
            val initialized = FirebaseBootstrap.ensureInitialized(platformContext, options)
            if (!initialized) {
                val detail = FirebaseBootstrap.lastFailureMessage().orEmpty()
                return@withContext FirebaseAuthResult.Error(
                    if (detail.isNotBlank()) {
                        "Firebase failed to initialize: $detail"
                    } else {
                        "Firebase failed to initialize. Check Project ID, Application ID and API key, then retry."
                    },
                )
            }
            val webClientId = settings.getFirebaseWebClientId().trim()
            val webClientSecret = settings.getFirebaseWebClientSecret().trim()
            if (webClientId.isBlank() || webClientSecret.isBlank()) {
                return@withContext FirebaseAuthResult.Error(
                    "Desktop Firebase Sign-In uses the institution Web client ID + Client secret " +
                        "(not the developer Gmail OAuth JSON). " +
                        "In the institution Firebase/Google Cloud project → APIs & Services → Credentials → " +
                        "your OAuth 2.0 Web client: copy Client ID and Client secret into Firebase settings. " +
                        "Authorized redirect URIs must include the exact localhost callbacks with ports " +
                        "(see ? guide) — e.g. http://localhost:8889/Callback",
                )
            }
            // Reject a bad API key before opening the browser (OAuth success is useless otherwise).
            DesktopFirebaseGoogleRestSignIn.probeApiKey(settings.getFirebaseApiKey())
                ?.let { return@withContext it }
            val secrets = GoogleClientSecrets.load(
                GsonFactory.getDefaultInstance(),
                StringReader(buildInstitutionWebClientSecretsJson(webClientId, webClientSecret)),
            )
            val flow = GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                secrets,
                listOf(
                    "openid",
                    "email",
                    "profile",
                    "https://www.googleapis.com/auth/userinfo.email",
                    "https://www.googleapis.com/auth/userinfo.profile",
                ),
            )
                .setDataStoreFactory(FileDataStoreFactory(tokenDir))
                .setAccessType("offline")
                .build()

            // Always interactive for Firebase — stored refresh tokens do not yield id_token.
            runCatching { flow.credentialDataStore?.delete("firebase-user") }

            val (receiver, redirectUri) = openLocalOAuthReceiver()
                ?: return@withContext FirebaseAuthResult.Error(
                    "Address already in use: could not start the local OAuth callback server. " +
                        "Close any other NoctuList / Google Sign-In window still waiting, " +
                        "or quit leftover Java processes using ports 8888–9090, then try again.",
                )
            val idToken: String?
            val credential: Credential
            try {
                val authUrl = flow.newAuthorizationUrl()
                    .setRedirectUri(redirectUri)
                    .set("prompt", "consent")
                    .build()
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI(authUrl))
                }
                val code = receiver.waitForCode()
                val tokenResponse: GoogleTokenResponse = flow.newTokenRequest(code)
                    .setRedirectUri(redirectUri)
                    .execute()
                idToken = tokenResponse.idToken
                credential = flow.createAndStoreCredential(tokenResponse, "firebase-user")
                if (!idToken.isNullOrBlank()) {
                    writeOwnerOnlyText(idTokenFile, idToken)
                }
            } catch (e: Exception) {
                runCatching { receiver.stop() }
                val msg = e.message.orEmpty()
                if (msg.contains("redirect_uri_mismatch", ignoreCase = true) ||
                    msg.contains("redirect_uri", ignoreCase = true)
                ) {
                    return@withContext FirebaseAuthResult.Error(
                        "redirect_uri_mismatch: Google rejected callback $redirectUri. " +
                            "In Cloud Console → APIs & Services → Credentials → your Web OAuth client → " +
                            "Authorized redirect URIs, add exactly: $redirectUri " +
                            "(also add http://localhost:8888/Callback, http://localhost:8765/Callback, " +
                            "http://localhost:9090/Callback). Save, wait ~1 minute, retry Sign-In.",
                    )
                }
                return@withContext FirebaseAuthResult.Error(msg.ifBlank { "Desktop Google Sign-In failed" })
            } finally {
                runCatching { receiver.stop() }
            }

            val accessToken = credential.accessToken
            val resolvedEmail = fetchOAuthUserEmail(credential)
                ?: return@withContext FirebaseAuthResult.Error("Could not resolve Google account email")

            val resolvedIdToken = idToken?.takeIf { it.isNotBlank() }
                ?: idTokenFile.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotBlank() }

            if (resolvedIdToken.isNullOrBlank()) {
                return@withContext FirebaseAuthResult.Error(
                    "Google returned no OpenID id_token after browser login. " +
                        "Use a Desktop OAuth client from the same Cloud project as Firebase " +
                        "(APIs & Services → Credentials), with openid scope. " +
                        "Browser success alone is not enough.",
                )
            }

            val firebaseResult = FirebaseAuthBridge.signInWithGoogleTokens(
                idToken = resolvedIdToken,
                accessToken = accessToken,
                platformContext = platformContext,
            )
            when (firebaseResult) {
                is FirebaseAuthResult.Success -> {
                    val gated = FirebaseAuthAccessGate.enforceEmailDomain(
                        result = firebaseResult,
                        settings = settings,
                        signOut = {
                            FirebaseAuthBridge.signOut()
                            accountFile.delete()
                            idTokenFile.delete()
                        },
                    )
                    if (gated is FirebaseAuthResult.Success) {
                        writeOwnerOnlyText(
                            accountFile,
                            "email=${gated.email ?: resolvedEmail}\nuid=${gated.uid}\n",
                        )
                        settings.setFirebaseAuthEmail(gated.email ?: resolvedEmail)
                    }
                    gated
                }
                is FirebaseAuthResult.Error -> FirebaseAuthResult.Error(
                    firebaseResult.message,
                )
            }
        } catch (e: Exception) {
            FirebaseAuthResult.Error(e.message ?: "Desktop Google Sign-In failed")
        }
    }

    private fun fetchOAuthUserEmail(credential: Credential): String? = runCatching {
        val transport = GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        val response = transport.createRequestFactory(credential)
            .buildGetRequest(GenericUrl("https://www.googleapis.com/oauth2/v2/userinfo"))
            .execute()
        @Suppress("UNCHECKED_CAST")
        val payload = jsonFactory.fromString(response.parseAsString(), Map::class.java) as Map<String, Any>
        payload["email"] as? String
    }.getOrNull()

    /**
     * Prefer fixed localhost ports (same as Gmail OAuth), then ephemeral `0`.
     * Avoids BindException when a previous Sign-In left 8889 occupied.
     */
    private fun openLocalOAuthReceiver(): Pair<LocalServerReceiver, String>? {
        for (port in listOf(8889, 8888, 8765, 9090, 0)) {
            val opened = runCatching {
                val receiver = LocalServerReceiver.Builder().setPort(port).build()
                receiver to receiver.redirectUri
            }.getOrNull()
            if (opened != null) return opened
        }
        return null
    }

    /**
     * Institution Web OAuth client (not developer Gmail JSON).
     * Desktop and Android code exchange require client_secret.
     */
    private fun buildInstitutionWebClientSecretsJson(clientId: String, clientSecret: String): String =
        """
        {
          "web": {
            "client_id": ${jsonQuote(clientId)},
            "client_secret": ${jsonQuote(clientSecret)},
            "redirect_uris": [
            "http://localhost:8889/Callback",
            "http://localhost:8888/Callback",
            "http://localhost:8765/Callback",
            "http://localhost:9090/Callback"
          ]
          }
        }
        """.trimIndent()

    private fun jsonQuote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    override suspend fun signOut() {
        withContext(Dispatchers.IO) {
            FirebaseAuthBridge.signOut()
            accountFile.delete()
            idTokenFile.delete()
            tokenDir.deleteRecursively()
            tokenDir.mkdirs()
            settings.setFirebaseAuthEmail("")
        }
    }

    override fun currentUserEmail(): String? =
        FirebaseAuthBridge.currentUserEmail()
            ?: if (!accountFile.exists()) null
            else accountFile.readLines().firstOrNull { it.startsWith("email=") }?.substringAfter("=")

    override fun currentUserId(): String? =
        FirebaseAuthBridge.currentUserId()
            ?: if (!accountFile.exists()) null
            else accountFile.readLines().firstOrNull { it.startsWith("uid=") }?.substringAfter("=")

    override suspend fun restoreSession(): FirebaseAuthResult? = withContext(Dispatchers.IO) {
        FirebaseBootstrap.ensureInitialized(
            platformContext,
            FirebaseOptionsReader.fromSettings(settings),
        )
        DesktopFirebaseGoogleRestSignIn.hydrateSessionFromStore()
        if (FirebaseAuthBridge.isSignedIn()) {
            val candidate = FirebaseAuthResult.Success(
                uid = FirebaseAuthBridge.currentUserId().orEmpty(),
                email = FirebaseAuthBridge.currentUserEmail(),
            )
            return@withContext when (
                val gated = FirebaseAuthAccessGate.enforceEmailDomain(
                    result = candidate,
                    settings = settings,
                    signOut = {
                        FirebaseAuthBridge.signOut()
                        accountFile.delete()
                        idTokenFile.delete()
                    },
                )
            ) {
                is FirebaseAuthResult.Success -> gated
                is FirebaseAuthResult.Error -> null
            }
        }
        try {
            val stored = idTokenFile.takeIf { it.exists() }?.readText()?.trim().orEmpty()
            if (stored.isBlank()) return@withContext null
            when (val result = FirebaseAuthBridge.signInWithGoogleTokens(stored, null, platformContext)) {
                is FirebaseAuthResult.Success -> {
                    val gated = FirebaseAuthAccessGate.enforceEmailDomain(
                        result = result,
                        settings = settings,
                        signOut = {
                            FirebaseAuthBridge.signOut()
                            accountFile.delete()
                            idTokenFile.delete()
                        },
                    )
                    if (gated is FirebaseAuthResult.Success) {
                        writeOwnerOnlyText(
                            accountFile,
                            "email=${gated.email.orEmpty()}\nuid=${gated.uid}\n",
                        )
                        settings.setFirebaseAuthEmail(gated.email.orEmpty())
                        gated
                    } else {
                        null
                    }
                }
                is FirebaseAuthResult.Error -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun isSignedIn(): Boolean = FirebaseAuthBridge.isSignedIn()

    /** Persist auth material with owner-only permissions when the OS supports it. */
    private fun writeOwnerOnlyText(file: File, text: String) {
        file.writeText(text)
        runCatching {
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setExecutable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)
        }
    }
}

actual fun createFirebaseAuthService(platformContext: PlatformContext?): FirebaseAuthService {
    val ctx = platformContext ?: return NoOpFirebaseAuthService()
    return DesktopFirebaseAuthService(ctx)
}
