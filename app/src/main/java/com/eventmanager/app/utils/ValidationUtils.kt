package com.eventmanager.app.utils

import java.text.SimpleDateFormat
import java.util.*

object ValidationUtils {
    
    /**
     * Validates if a string is a valid email address
     */
    fun isValidEmail(email: String): Boolean {
        if (email.isBlank()) return false
        
        val emailRegex = "^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$".toRegex()
        return emailRegex.matches(email)
    }
    
    /**
     * Validates if a string is a valid date in dd.mm.yyyy format
     */
    fun isValidDate(dateString: String): Boolean {
        if (dateString.isBlank()) return false
        
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        dateFormat.isLenient = false // Strict parsing
        
        return try {
            val date = dateFormat.parse(dateString)
            date != null && date.before(Date()) // Date should be in the past
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Converts date from dd.mm.yyyy format to yyyy-MM-dd format for storage
     */
    fun convertDateToStorageFormat(dateString: String): String? {
        if (!isValidDate(dateString)) return null
        
        return try {
            val inputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            date?.let { outputFormat.format(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Converts date from yyyy-MM-dd format to dd.mm.yyyy format for display
     */
    fun convertDateToDisplayFormat(dateString: String): String {
        if (dateString.isBlank()) return ""
        
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val date = inputFormat.parse(dateString)
            date?.let { outputFormat.format(it) } ?: dateString
        } catch (e: Exception) {
            dateString
        }
    }
    
    /**
     * Formats date input to ensure proper dd.mm.yyyy format as user types
     */
    fun formatDateInput(input: String): String {
        // Remove all non-digit characters
        val digitsOnly = input.filter { it.isDigit() }
        
        return when {
            digitsOnly.length <= 2 -> digitsOnly
            digitsOnly.length <= 4 -> "${digitsOnly.substring(0, 2)}.${digitsOnly.substring(2)}"
            else -> "${digitsOnly.substring(0, 2)}.${digitsOnly.substring(2, 4)}.${digitsOnly.substring(4, minOf(8, digitsOnly.length))}"
        }
    }
    
    /**
     * Gets error message for invalid email
     */
    fun getEmailErrorMessage(email: String): String? {
        return when {
            email.isBlank() -> "Email is required"
            !isValidEmail(email) -> "Please enter a valid email address (e.g., name@example.com)"
            else -> null
        }
    }
    
    /**
     * Gets error message for invalid date
     */
    fun getDateErrorMessage(dateString: String): String? {
        return when {
            dateString.isBlank() -> "Date of birth is required"
            !isValidDate(dateString) -> "Please enter a valid date in dd.mm.yyyy format"
            else -> null
        }
    }
}
