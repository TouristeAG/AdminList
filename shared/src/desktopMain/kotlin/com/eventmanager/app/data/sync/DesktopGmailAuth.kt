package com.eventmanager.app.data.sync

import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.appDataDir
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import java.io.File
import java.io.FileReader

class DesktopGmailAuth(private val context: PlatformContext) {
    private val accountFile = File(context.appDataDir, "gmail_account.properties")
    private val tokenDir = File(context.appDataDir, "gmail_tokens").also { it.mkdirs() }
    private val clientSecretsFile = File(context.appDataDir, "gmail_oauth_client.json")

    fun isSignedIn(): Boolean = accountFile.exists() && loadCredential() != null

    fun getSignedInAccountEmail(): String? =
        if (!accountFile.exists()) null else accountFile.readLines().firstOrNull { it.startsWith("email=") }?.substringAfter("=")

    suspend fun signIn(): Boolean {
        val credential = loadCredential() ?: run {
            if (!clientSecretsFile.exists()) return false
            val flow = buildFlow() ?: return false
            val receiver = LocalServerReceiver.Builder().setPort(8888).build()
            AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
        } ?: return false
        val gmail = Gmail.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("NoctuList Desktop").build()
        val email = gmail.users().getProfile("me").execute().emailAddress
        accountFile.writeText("email=$email")
        return true
    }

    fun signOut() {
        accountFile.delete()
        tokenDir.deleteRecursively()
    }

    private fun buildFlow(): AuthorizationCodeFlow? = runCatching {
        val secrets = GoogleClientSecrets.load(GsonFactory.getDefaultInstance(), FileReader(clientSecretsFile))
        GoogleAuthorizationCodeFlow.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            secrets,
            listOf(GmailScopes.GMAIL_SEND)
        ).setDataStoreFactory(FileDataStoreFactory(tokenDir)).setAccessType("offline").build()
    }.getOrNull()

    private fun loadCredential(): Credential? = runCatching {
        val flow = buildFlow() ?: return null
        flow.loadCredential("user")
    }.getOrNull()
}
