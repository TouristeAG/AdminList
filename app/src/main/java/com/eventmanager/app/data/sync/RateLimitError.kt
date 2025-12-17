package com.eventmanager.app.data.sync

/**
 * Represents a rate limit error with detailed information
 */
data class RateLimitError(
    val isRateLimit: Boolean,
    val briefMessage: String,
    val detailedMessage: String,
    val retryAfterSeconds: Int? = null
) {
    companion object {
        /**
         * Create a RateLimitError from an exception
         */
        fun createFromException(e: Exception): RateLimitError {
            val message = e.message?.lowercase() ?: ""
            val isRateLimit = message.contains("429") || 
                             message.contains("rate limit") || 
                             message.contains("quota") ||
                             message.contains("too many requests")
            
            return if (isRateLimit) {
                RateLimitError(
                    isRateLimit = true,
                    briefMessage = "Rate limit exceeded. Please wait and try again.",
                    detailedMessage = "Google Sheets API rate limit exceeded. Please wait 1-2 minutes before trying again.",
                    retryAfterSeconds = 60
                )
            } else {
                RateLimitError(
                    isRateLimit = false,
                    briefMessage = e.message ?: "Unknown error occurred",
                    detailedMessage = e.message ?: "An unknown error occurred during sync"
                )
            }
        }
    }
}