package com.eventmanager.app.data.sync

import com.eventmanager.app.platform.PlatformContext
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

class DesktopGmailSendService(private val context: PlatformContext) {
    suspend fun sendMimeEmail(
        requestInitializer: HttpRequestInitializer,
        userId: String,
        to: String,
        mimeMessage: String,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val gmail = Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                requestInitializer
            ).setApplicationName("NoctuList Desktop").build()
            val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(mimeMessage.toByteArray(Charsets.UTF_8))
            gmail.users().messages().send(userId, Message().setRaw(encoded)).execute()
            true
        }.getOrDefault(false)
    }
}
