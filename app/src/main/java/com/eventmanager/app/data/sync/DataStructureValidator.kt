package com.eventmanager.app.data.sync

import android.content.Context
import com.eventmanager.app.data.models.*
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Data Structure Validator ensures consistent headers and data structure across all Google Sheets
 * This validates that the sheets have the correct format as specified in the requirements
 */
class DataStructureValidator(
    private val context: Context,
    private val googleSheetsService: GoogleSheetsService
) {
    
    private val settingsManager = SettingsManager(context)
    
    /**
     * Expected headers for each sheet type
     */
    private val expectedHeaders = mapOf(
        "guests" to listOf("Name", "Invitations", "Venue", "Notes", "Volunteer Benefit", "Last Modified"),
        "volunteers" to listOf("ID", "Name", "Abbreviation", "Email", "Phone", "Date of Birth", "Rank", "Active", "Last Modified"),
        "jobs" to listOf("Volunteer ID", "Shift Type", "Venue", "Date", "Shift Time", "Notes", "Last Modified"),
        "job_types" to listOf("Name", "Status", "Shift Type", "Orion Type", "Requires Time", "Description", "Last Modified"),
        "venues" to listOf("Name", "Description", "Active", "Last Modified")
    )
    
    /**
     * Validate all sheets have correct structure
     */
    suspend fun validateAllSheets(): ValidationResult = withContext(Dispatchers.IO) {
        try {
            if (!settingsManager.isConfigured()) {
                return@withContext ValidationResult.Error("Google Sheets not configured")
            }
            
            googleSheetsService.initializeSheetsService()
            
            val results = mutableMapOf<String, SheetValidationResult>()
            
            // Validate each sheet
            results["guests"] = validateSheet("guests", settingsManager.getGuestListSheet())
            results["volunteers"] = validateSheet("volunteers", settingsManager.getVolunteerSheet())
            results["jobs"] = validateSheet("jobs", settingsManager.getJobsSheet())
            results["job_types"] = validateSheet("job_types", "JobTypes")
            results["venues"] = validateSheet("venues", settingsManager.getVenuesSheet())
            
            val allValid = results.values.all { it.isValid }
            
            ValidationResult.Success(
                isValid = allValid,
                sheetResults = results,
                message = if (allValid) "All sheets have correct structure" else "Some sheets have incorrect structure"
            )
            
        } catch (e: Exception) {
            ValidationResult.Error("Validation failed: ${e.message}")
        }
    }
    
    /**
     * Validate a specific sheet
     */
    private suspend fun validateSheet(sheetType: String, sheetName: String): SheetValidationResult = withContext(Dispatchers.IO) {
        try {
            val expectedHeaders = expectedHeaders[sheetType] ?: return@withContext SheetValidationResult.Error(sheetName = sheetName, message = "Unknown sheet type: $sheetType")
            
            val sheetsService = googleSheetsService.getSheetsService()
            if (sheetsService == null) {
                return@withContext SheetValidationResult.Error(sheetName = sheetName, message = "Sheets service not initialized")
            }
            
            val spreadsheetId = settingsManager.getSpreadsheetId()
            val range = "${sheetName}!A1:${getLastColumn(expectedHeaders.size)}1"
            
            val response = sheetsService.spreadsheets().values().get(spreadsheetId, range).execute()
            val actualHeaders = response.getValues()?.firstOrNull()?.map { it.toString() } ?: emptyList()
            
            val headerValidation = validateHeaders(expectedHeaders, actualHeaders)
            
            if (headerValidation.isValid) {
                SheetValidationResult.Success(
                    sheetName = sheetName,
                    expectedHeaders = expectedHeaders,
                    actualHeaders = actualHeaders,
                    message = "Sheet structure is correct"
                )
            } else {
                val errorResult = headerValidation as HeaderValidationResult.Error
                SheetValidationResult.Error(
                    sheetName = sheetName,
                    expectedHeaders = expectedHeaders,
                    actualHeaders = actualHeaders,
                    message = errorResult.message
                )
            }
            
        } catch (e: Exception) {
            SheetValidationResult.Error(
                sheetName = sheetName,
                message = "Failed to validate sheet: ${e.message}"
            )
        }
    }
    
    /**
     * Validate headers match expected format
     */
    private fun validateHeaders(expected: List<String>, actual: List<String>): HeaderValidationResult {
        if (actual.size != expected.size) {
            return HeaderValidationResult.Error(
                "Header count mismatch. Expected ${expected.size}, got ${actual.size}",
                expected,
                actual
            )
        }
        
        val mismatches = mutableListOf<String>()
        expected.forEachIndexed { index, expectedHeader ->
            val actualHeader = actual.getOrNull(index)?.toString() ?: ""
            if (expectedHeader != actualHeader) {
                mismatches.add("Column ${index + 1}: expected '$expectedHeader', got '$actualHeader'")
            }
        }
        
        return if (mismatches.isEmpty()) {
            HeaderValidationResult.Success("Headers match expected format")
        } else {
            HeaderValidationResult.Error(
                "Header mismatches: ${mismatches.joinToString(", ")}",
                expected,
                actual
            )
        }
    }
    
    /**
     * Get the last column letter for a given number of columns
     */
    private fun getLastColumn(columnCount: Int): String {
        return when (columnCount) {
            1 -> "A"
            2 -> "B"
            3 -> "C"
            4 -> "D"
            5 -> "E"
            6 -> "F"
            7 -> "G"
            8 -> "H"
            9 -> "I"
            10 -> "J"
            else -> "Z" // Fallback for more columns
        }
    }
    
    /**
     * Create or fix sheet structure if needed
     */
    suspend fun createOrFixSheetStructure(): ValidationResult = withContext(Dispatchers.IO) {
        try {
            if (!settingsManager.isConfigured()) {
                return@withContext ValidationResult.Error("Google Sheets not configured")
            }
            
            googleSheetsService.initializeSheetsService()
            
            val results = mutableMapOf<String, SheetValidationResult>()
            
            // Create or fix each sheet
            results["guests"] = createOrFixSheet("guests", settingsManager.getGuestListSheet())
            results["volunteers"] = createOrFixSheet("volunteers", settingsManager.getVolunteerSheet())
            results["jobs"] = createOrFixSheet("jobs", settingsManager.getJobsSheet())
            results["job_types"] = createOrFixSheet("job_types", "JobTypes")
            results["venues"] = createOrFixSheet("venues", settingsManager.getVenuesSheet())
            
            val allValid = results.values.all { it.isValid }
            
            ValidationResult.Success(
                isValid = allValid,
                sheetResults = results,
                message = if (allValid) "All sheets created/fixed successfully" else "Some sheets could not be created/fixed"
            )
            
        } catch (e: Exception) {
            ValidationResult.Error("Failed to create/fix sheet structure: ${e.message}")
        }
    }
    
    /**
     * Create or fix a specific sheet
     */
    private suspend fun createOrFixSheet(sheetType: String, sheetName: String): SheetValidationResult = withContext(Dispatchers.IO) {
        try {
            val expectedHeaders = expectedHeaders[sheetType] ?: return@withContext SheetValidationResult.Error(sheetName = sheetName, message = "Unknown sheet type: $sheetType")
            
            val sheetsService = googleSheetsService.getSheetsService()
            if (sheetsService == null) {
                return@withContext SheetValidationResult.Error(sheetName = sheetName, message = "Sheets service not initialized")
            }
            
            val spreadsheetId = settingsManager.getSpreadsheetId()
            
            // First, check if sheet exists and has correct headers
            val validationResult = validateSheet(sheetType, sheetName)
            if (validationResult.isValid) {
                return@withContext validationResult
            }
            
            // Try to fix the headers
            val range = "${sheetName}!A1:${getLastColumn(expectedHeaders.size)}1"
            val valueRange = ValueRange().setValues(listOf(expectedHeaders))
            
            sheetsService.spreadsheets().values().update(
                spreadsheetId,
                range,
                valueRange
            ).setValueInputOption("RAW").execute()
            
            // Validate again after fixing
            val revalidationResult = validateSheet(sheetType, sheetName)
            if (revalidationResult.isValid) {
                val successResult = revalidationResult as SheetValidationResult.Success
                SheetValidationResult.Success(
                    sheetName = sheetName,
                    expectedHeaders = expectedHeaders,
                    actualHeaders = expectedHeaders,
                    message = "Sheet structure fixed successfully"
                )
            } else {
                val errorResult = revalidationResult as SheetValidationResult.Error
                SheetValidationResult.Error(
                    sheetName = sheetName,
                    message = "Failed to fix sheet structure: ${errorResult.message}"
                )
            }
            
        } catch (e: Exception) {
            SheetValidationResult.Error(
                sheetName = sheetName,
                message = "Failed to create/fix sheet: ${e.message}"
            )
        }
    }
    
    /**
     * Get expected headers for a sheet type
     */
    fun getExpectedHeaders(sheetType: String): List<String>? {
        return expectedHeaders[sheetType]
    }
    
    /**
     * Get all expected headers
     */
    fun getAllExpectedHeaders(): Map<String, List<String>> {
        return expectedHeaders
    }
}

/**
 * Validation result for all sheets
 */
sealed class ValidationResult {
    data class Success(
        val isValid: Boolean,
        val sheetResults: Map<String, SheetValidationResult>,
        val message: String
    ) : ValidationResult()
    
    data class Error(val message: String) : ValidationResult()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
}

/**
 * Validation result for a single sheet
 */
sealed class SheetValidationResult {
    data class Success(
        val sheetName: String,
        val expectedHeaders: List<String>,
        val actualHeaders: List<String>,
        val message: String
    ) : SheetValidationResult()
    
    data class Error(
        val sheetName: String,
        val expectedHeaders: List<String>? = null,
        val actualHeaders: List<String>? = null,
        val message: String
    ) : SheetValidationResult()
    
    val isValid: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
}

/**
 * Header validation result
 */
sealed class HeaderValidationResult {
    data class Success(val message: String) : HeaderValidationResult()
    data class Error(
        val message: String,
        val expectedHeaders: List<String>,
        val actualHeaders: List<String>
    ) : HeaderValidationResult()
    
    val isValid: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
}
