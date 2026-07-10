package com.eventmanager.app.data.sync

data class ServiceAccountKeyInfo(
    val clientEmail: String,
    val projectId: String,
)

object ServiceAccountJsonParser {
    private val clientEmailRegex = "\"client_email\"\\s*:\\s*\"([^\"]+)\"".toRegex()
    private val projectIdRegex = "\"project_id\"\\s*:\\s*\"([^\"]+)\"".toRegex()

    fun parse(json: String): ServiceAccountKeyInfo? {
        val normalized = json.trim()
        if (normalized.isEmpty() || !normalized.startsWith("{") || !normalized.endsWith("}")) {
            return null
        }
        if (!normalized.contains("\"type\"") || !normalized.contains("service_account")) {
            return null
        }
        val clientEmail = clientEmailRegex.find(normalized)?.groupValues?.get(1)?.trim().orEmpty()
        val projectId = projectIdRegex.find(normalized)?.groupValues?.get(1)?.trim().orEmpty()
        if (clientEmail.isEmpty() && projectId.isEmpty()) return null
        return ServiceAccountKeyInfo(
            clientEmail = clientEmail.ifEmpty { "—" },
            projectId = projectId.ifEmpty { "—" },
        )
    }
}
