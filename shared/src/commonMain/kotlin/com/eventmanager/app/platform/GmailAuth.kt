package com.eventmanager.app.platform

/**
 * Gmail OAuth and send abstraction.
 */
interface GmailAuth {
    val isSignedIn: Boolean
    val accountEmail: String?
    val lastSignInError: String?
    suspend fun signIn(): Boolean
    suspend fun signOut()
    suspend fun sendEmail(
        to: String,
        subject: String,
        htmlBody: String,
        attachments: List<EmailAttachment> = emptyList()
    ): Boolean
}

data class EmailAttachment(
    val fileName: String,
    val mimeType: String,
    val data: ByteArray
)

expect fun createGmailAuth(context: PlatformContext): GmailAuth
