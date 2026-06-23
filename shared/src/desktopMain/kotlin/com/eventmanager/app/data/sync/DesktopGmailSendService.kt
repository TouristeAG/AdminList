package com.eventmanager.app.data.sync

import com.eventmanager.app.platform.EmailAttachment
import com.eventmanager.app.platform.PlatformContext
import com.eventmanager.app.platform.appDataDir
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Message
import java.io.File
import java.io.FileReader
import java.util.Base64

class DesktopGmailSendService(private val context: PlatformContext) {
    suspend fun sendHtmlEmail(
        to: String,
        subject: String,
        htmlBody: String,
        attachments: List<EmailAttachment> = emptyList()
    ): Boolean {
        val auth = DesktopGmailAuth(context)
        if (!auth.isSignedIn()) return false
        val clientSecretsFile = File(context.appDataDir, "gmail_oauth_client.json")
        if (!clientSecretsFile.exists()) return false
        val credential = runCatching {
            val flow = GoogleAuthorizationCodeFlow.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                GoogleClientSecrets.load(GsonFactory.getDefaultInstance(), FileReader(clientSecretsFile)),
                listOf(GmailScopes.GMAIL_SEND)
            ).setDataStoreFactory(FileDataStoreFactory(File(context.appDataDir, "gmail_tokens"))).build()
            flow.loadCredential("user")
        }.getOrNull() ?: return false
        val gmail = Gmail.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("NoctuList Desktop").build()
        val raw = buildRawMime(to, subject, htmlBody)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
        gmail.users().messages().send("me", Message().setRaw(encoded)).execute()
        return true
    }

    private fun buildRawMime(to: String, subject: String, html: String): String =
        "To: $to\r\nSubject: $subject\r\nMIME-Version: 1.0\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n$html"
}
