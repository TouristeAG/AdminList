package com.eventmanager.app.platform

import com.eventmanager.app.data.sync.DesktopGmailAuth
import com.eventmanager.app.data.sync.DesktopGmailSendService
import com.eventmanager.app.email.MimeEmailBuilder

actual fun createGmailAuth(context: PlatformContext): GmailAuth =
    DesktopGmailAuthImpl(context)

private class DesktopGmailAuthImpl(private val context: PlatformContext) : GmailAuth {
    private val auth = DesktopGmailAuth(context)
    private val send = DesktopGmailSendService(context)

    override val isSignedIn: Boolean get() = auth.isSignedIn()
    override val accountEmail: String? get() = auth.getSignedInAccountEmail()
    override val lastSignInError: String? get() = auth.lastSignInError

    override suspend fun signIn(): Boolean = auth.signIn()
    override suspend fun signOut() { auth.signOut() }

    override suspend fun sendEmail(
        to: String,
        subject: String,
        htmlBody: String,
        attachments: List<EmailAttachment>
    ): Boolean {
        val requestInitializer = auth.loadRequestInitializer() ?: return false
        val mime = MimeEmailBuilder.buildMultipartRelatedEmail(
            to = to,
            subject = subject,
            plainText = htmlBody,
            htmlContent = htmlBody,
            attachments = emptyList(),
        )
        return send.sendMimeEmail(
            requestInitializer = requestInitializer,
            userId = auth.getSendUserId(),
            to = to,
            mimeMessage = mime,
        )
    }
}
