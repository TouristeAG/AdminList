package com.eventmanager.app.data.sync

import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Handles API rate limiting for Google Sheets operations
 */
object ApiRateLimitHandler {
    private const val MAX_RETRIES = 3
    private const val BASE_DELAY_MS = 1000L
    private const val MAX_DELAY_MS = 10000L
    
    /**
     * Execute an operation with automatic retry on rate limit errors
     */
    suspend fun <T> executeWithRetry(
        operation: suspend () -> T,
        operationName: String = "API operation"
    ): T {
        var lastException: Exception? = null
        
        repeat(MAX_RETRIES) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                
                if (isRateLimitError(e) && attempt < MAX_RETRIES - 1) {
                    val delayMs = calculateDelay(attempt)
                    println("⚠️ Rate limit hit for $operationName (attempt ${attempt + 1}/$MAX_RETRIES). Retrying in ${delayMs}ms...")
                    delay(delayMs)
                } else {
                    // Exit the loop
                    return@repeat
                }
            }
        }
        
        throw lastException ?: IOException("Operation failed after $MAX_RETRIES attempts")
    }
    
    /**
     * Check if an exception is a rate limit error
     */
    private fun isRateLimitError(e: Exception): Boolean {
        val message = e.message?.lowercase() ?: ""
        return message.contains("429") || 
               message.contains("rate limit") || 
               message.contains("quota") ||
               message.contains("too many requests")
    }
    
    /**
     * Calculate exponential backoff delay
     */
    private fun calculateDelay(attempt: Int): Long {
        val delay = BASE_DELAY_MS * (1L shl attempt) // 1s, 2s, 4s, 8s...
        return minOf(delay, MAX_DELAY_MS)
    }
    
    /**
     * Get a brief rate limit error message for UI display
     */
    fun getBriefRateLimitMessage(): String {
        return "Rate limit exceeded. Please wait a moment and try again."
    }
    
    /**
     * Get detailed rate limit error message
     */
    fun getRateLimitErrorMessage(): String {
        return "Google Sheets API rate limit exceeded. The app will automatically retry with backoff. Please wait 1-2 minutes before trying again."
    }
}