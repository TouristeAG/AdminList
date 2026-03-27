    package com.eventmanager.app.data.sync

import android.content.Context
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.*
import com.google.auth.oauth2.GoogleCredentials
import com.eventmanager.app.data.models.*
import com.eventmanager.app.data.models.BenefitSystemType
import com.eventmanager.app.data.models.ManualRewards
import com.eventmanager.app.data.sync.GoogleSheetsConfig
import com.eventmanager.app.data.utils.NanoIdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException
import java.net.UnknownHostException

class GoogleSheetsService(private val context: Context) {
    
    /**
     * Creates a user-friendly error message for network connectivity issues
     */
    private fun createNetworkErrorMessage(operation: String, originalException: Exception): String {
        val errorMessage = originalException.message ?: ""
        val cause = originalException.cause
        
        // Check for network connectivity issues
        val isNetworkError = originalException is UnknownHostException ||
                cause is UnknownHostException ||
                errorMessage.contains("Unable to resolve host", ignoreCase = true) ||
                errorMessage.contains("No address associated with hostname", ignoreCase = true) ||
                errorMessage.contains("Network is unreachable", ignoreCase = true) ||
                errorMessage.contains("Connection refused", ignoreCase = true) ||
                errorMessage.contains("Connection timed out", ignoreCase = true) ||
                errorMessage.contains("No route to host", ignoreCase = true)
        
        return if (isNetworkError) {
            "Your internet connection might not be working correctly. Please check your Wi-Fi or mobile data connection and try again."
        } else {
            "Failed to $operation: ${errorMessage}"
        }
    }
    private var sheetsService: Sheets? = null
    private val settingsManager = SettingsManager(context)
    private val fileManager = FileManager(context)

    suspend fun initializeSheetsService() = withContext(Dispatchers.IO) {
        try {
            val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()
            
            // Use the uploaded service account key file
            val keyFilePath = fileManager.getServiceAccountKeyPath()
            if (keyFilePath == null) {
                throw IOException("Service account key file not found. Please upload it in Settings.")
            }
            
            // Use the modern GoogleCredentials approach
            println("Initializing Google Sheets service with service account...")
            
            // Convert ServiceAccountCredentials to HttpRequestInitializer
            val requestInitializer = com.google.api.client.googleapis.auth.oauth2.GoogleCredential.fromStream(
                java.io.FileInputStream(keyFilePath)
            ).createScoped(listOf(GoogleSheetsConfig.SCOPES))
            
            sheetsService = Sheets.Builder(httpTransport, jsonFactory, requestInitializer)
                .setApplicationName("Event Manager App")
                .build()
            
            println("Google Sheets service initialized successfully")
        } catch (e: GeneralSecurityException) {
            throw IOException("Failed to initialize Google Sheets service: ${e.message}", e)
        } catch (e: Exception) {
            throw IOException(createNetworkErrorMessage("initialize Google Sheets service", e), e)
        }
    }

    // Single Guest Operations (App Priority)
    suspend fun addGuestToSheets(guest: Guest, venues: List<VenueEntity>) = withContext(Dispatchers.IO) {
        try {
            if (guest.isTemporaryGuest) {
                println("Skipping addGuestToSheets for temporary guest: ${guest.name}")
                return@withContext null
            }
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val values = listOf(
                        guest.name,
                        guest.email,
                        guest.phoneNumber,
                        guest.invitations.toString(),
                        guest.venueName,
                        guest.notes,
                        if (guest.isVolunteerBenefit) "Yes" else "No",
                        guest.lastModified.toString(),
                        guest.nfcCardUid,
                        guest.nanoId
                    )
                    
                    val valueRange = ValueRange().setValues(listOf(values))
                    
                    val response = sheetsService?.spreadsheets()?.values()?.append(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getGuestListSheet()}!A:J",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    
                    if (response == null) {
                        throw IOException("Failed to add guest to Google Sheets - no response received")
                    }
                    
                    // Update the guest with the sheets ID (row number)
                    val sheetsId = response.updates?.updatedRange?.let { range ->
                        val match = Regex(".*!A(\\d+):J\\d+").find(range)
                        match?.groupValues?.get(1)?.toIntOrNull()
                    }?.toString() ?: "1"
                    
                    println("Successfully added guest to Google Sheets: ${guest.name} (Row: $sheetsId)")
                    sheetsId
                },
                operationName = "add guest to sheets"
            )
        } catch (e: Exception) {
            println("Failed to add guest to sheets: ${e.message}")
            throw IOException(createNetworkErrorMessage("add guest to Google Sheets", e), e)
        }
    }
    
    /**
     * Maps a venue name from Google Sheets to the appropriate Venue enum
     * @param venueName The venue name from sheets (e.g., "Groove", "Le Terreau", "Both", "All")
     * @return The corresponding Venue enum value
     */
    private fun mapVenueNameToEnum(venueName: String): Venue {
        return when (venueName.trim().uppercase()) {
            "GROOVE" -> Venue.GROOVE
            "LE_TERREAU", "LE TERREAU" -> Venue.LE_TERREAU
            "BOTH", "ALL" -> Venue.BOTH
            else -> {
                // For custom venues, map them to available enums based on position
                val hash = venueName.hashCode()
                val enumValues = listOf(Venue.GROOVE, Venue.LE_TERREAU)
                val index = kotlin.math.abs(hash) % enumValues.size
                println("DEBUG: Mapping custom venue '$venueName' to ${enumValues[index]}")
                enumValues[index]
            }
        }
    }

    /**
     * Maps a Venue enum to the appropriate venue name for Google Sheets
     * @param venue The Venue enum value
     * @param venues List of active venues from database
     * @return The venue name to store in sheets
     */
    private fun mapVenueEnumToName(venue: Venue, venues: List<VenueEntity>): String {
        val activeVenues = venues.filter { it.isActive }
        
        return when (venue) {
            Venue.BOTH -> {
                if (activeVenues.size <= 2) "Both" else "All"
            }
            else -> {
                // Find the venue entity that maps to this enum
                val venueEntity = activeVenues.find { entity ->
                    when (venue) {
                        Venue.GROOVE -> entity.name.uppercase() == "GROOVE"
                        Venue.LE_TERREAU -> entity.name.uppercase() == "LE_TERREAU"
                        Venue.BOTH -> false // Already handled above
                    }
                }
                
                // If no exact match, find venues that map to this enum using the same logic as UI
                val mappedEntity = venueEntity ?: activeVenues.find { entity ->
                    mapVenueNameToEnum(entity.name) == venue
                }
                
                mappedEntity?.name ?: venue.name.replace("_", " ")
            }
        }
    }

    suspend fun updateGuestInSheets(guest: Guest, venues: List<VenueEntity>) = withContext(Dispatchers.IO) {
        try {
            if (guest.isTemporaryGuest) {
                println("Skipping updateGuestInSheets for temporary guest: ${guest.name}")
                return@withContext
            }
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            if (guest.sheetsId == null) {
                throw IOException("Guest has no sheets ID - cannot update")
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val values = listOf(
                        guest.name,
                        guest.email,
                        guest.phoneNumber,
                        guest.invitations.toString(),
                        guest.venueName,
                        guest.notes,
                        if (guest.isVolunteerBenefit) "Yes" else "No",
                        guest.lastModified.toString(),
                        guest.nfcCardUid,
                        guest.nanoId
                    )
                    
                    val valueRange = ValueRange().setValues(listOf(values))
                    val rowNumber = guest.sheetsId.toIntOrNull() ?: throw IOException("Invalid sheets ID")
                    
                    val response = sheetsService?.spreadsheets()?.values()?.update(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getGuestListSheet()}!A$rowNumber:J$rowNumber",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    
                    if (response == null) {
                        throw IOException("Failed to update guest in Google Sheets - no response received")
                    }
                    
                    println("Successfully updated guest in Google Sheets: ${guest.name}")
                },
                operationName = "update guest in sheets"
            )
        } catch (e: Exception) {
            println("Failed to update guest in sheets: ${e.message}")
            throw IOException(createNetworkErrorMessage("update guest in Google Sheets", e), e)
        }
    }

    // Guest List Operations
    suspend fun syncGuestsToSheets(guests: List<Guest>, venues: List<VenueEntity>) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                // First, clear the entire sheet to prevent duplicate last rows
                clearSheetRange("${settingsManager.getGuestListSheet()}!A:Z")
                println("🧹 Cleared entire guests sheet to prevent duplicates")
                
                // Only upload regular guests here; volunteer benefits and temporary guests go to their own sheets
                val values = guests.filter { !it.isVolunteerBenefit && !it.isTemporaryGuest }.map { guest ->
                    listOf(
                        guest.name,
                        guest.email,
                        guest.phoneNumber,
                        guest.invitations.toString(),
                        guest.venueName,
                        guest.notes,
                        "No",
                        guest.lastModified.toString(),
                        guest.nfcCardUid,
                        guest.nanoId
                    )
                }
                
                val valueRange = ValueRange()
                    .setValues(listOf(listOf("Name", "Email", "Phone", "Invitations", "Venue", "Notes", "Volunteer Benefit", "Last Modified", "NFC UID", "ID")) + values)
                
                val response = sheetsService?.spreadsheets()?.values()?.update(
                    settingsManager.getSpreadsheetId(),
                    "${settingsManager.getGuestListSheet()}!A1",
                    valueRange
                )?.setValueInputOption("RAW")?.execute()
                
                
                if (response == null) {
                    throw IOException("Failed to update guests in Google Sheets - no response received")
                }
                
                println("Successfully synced ${values.size} regular guests to Google Sheets")
                },
                operationName = "sync guests to sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync guests to sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw IOException(createNetworkErrorMessage("sync guests to Google Sheets", e), e)
            }
        }
    }

    /**
     * Upload-only sync for the Volunteer Guest List sheet.
     * This writes the computed volunteer benefit entries to a dedicated tab.
     */
    suspend fun syncVolunteerGuestListToSheets(volunteerGuests: List<Guest>, venues: List<VenueEntity>) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    // Clear the entire volunteer guest list sheet before upload
                    clearSheetRange("${settingsManager.getVolunteerGuestListSheet()}!A:Z")
                    println("🧹 Cleared entire volunteer guest list sheet to prevent duplicates")
                    val values = volunteerGuests.map { guest ->
                        listOf(
                            guest.name,
                            guest.lastNameAbbreviation,
                            guest.invitations.toString(),
                            guest.venueName,
                            guest.notes,
                            "Yes",
                            guest.lastModified.toString(),
                            guest.nfcCardUid
                        )
                    }
                    val valueRange = ValueRange()
                        .setValues(listOf(listOf("Name", "Last Name Abbreviation", "Invitations", "Venue", "Notes", "Volunteer Benefit", "Last Modified", "NFC UID")) + values)
                    val response = sheetsService?.spreadsheets()?.values()?.update(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getVolunteerGuestListSheet()}!A1",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    if (response == null) {
                        throw IOException("Failed to update volunteer guest list in Google Sheets - no response received")
                    }
                    println("Successfully synced ${values.size} volunteer guest entries to Google Sheets")
                },
                operationName = "sync volunteer guest list to sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync volunteer guest list to sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw IOException(createNetworkErrorMessage("sync volunteer guest list to Google Sheets", e), e)
            }
        }
    }

    suspend fun syncGuestsFromSheets(): List<Guest> = withContext(Dispatchers.IO) {
        try {
            println("Syncing guests from sheets...")
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                val spreadsheetId = settingsManager.getSpreadsheetId()
                val sheetName = settingsManager.getGuestListSheet()
                val range = "${sheetName}!A2:J"
                
                println("Reading from spreadsheet: $spreadsheetId, range: $range")
                
                val response = sheetsService?.spreadsheets()?.values()?.get(
                    spreadsheetId,
                    range
                )?.execute()
                
                if (response == null) {
                    throw IOException("Failed to retrieve guests from Google Sheets - no response received")
                }
                
                val values = response.getValues() ?: emptyList()
                println("Retrieved ${values.size} guest rows from sheets")
                
                val guests = mutableListOf<Guest>()
                val guestsToFixInSheets = mutableListOf<Pair<Int, String>>() // (rowNumber, newNanoId)

                values.forEachIndexed { index, row ->
                    val rowNumber = index + 2 // +2 because we start from row 2 (after header)
                    if (row.size >= 10) {
                        try {
                            val rawNanoId = row[9].toString()
                            val guestName = row[0].toString()
                            val needsFix = NanoIdGenerator.needsRegeneration(rawNanoId)
                            val validNanoId = NanoIdGenerator.ensureValidNanoId(rawNanoId, guestName)
                            if (needsFix) {
                                guestsToFixInSheets.add(Pair(rowNumber, validNanoId))
                            }
                            guests.add(Guest(
                                sheetsId = rowNumber.toString(),
                                nanoId = validNanoId,
                                name = guestName,
                                email = row[1].toString(),
                                phoneNumber = row[2].toString(),
                                invitations = row[3].toString().toIntOrNull() ?: 1,
                                venueName = row[4].toString(),
                                notes = row[5].toString(),
                                isVolunteerBenefit = row[6].toString().equals("Yes", ignoreCase = true),
                                lastModified = row[7].toString().toLongOrNull() ?: System.currentTimeMillis(),
                                nfcCardUid = row[8].toString()
                            ))
                        } catch (e: Exception) {
                            println("Failed to parse guest row $rowNumber: ${e.message}")
                        }
                    } else if (row.size >= 9) {
                        // Backward compatibility: no ID column yet — assign a new NanoID and queue for fix
                        try {
                            val guestName = row[0].toString()
                            val newNanoId = NanoIdGenerator.generateGuestId()
                            guestsToFixInSheets.add(Pair(rowNumber, newNanoId))
                            guests.add(Guest(
                                sheetsId = rowNumber.toString(),
                                nanoId = newNanoId,
                                name = guestName,
                                email = row[1].toString(),
                                phoneNumber = row[2].toString(),
                                invitations = row[3].toString().toIntOrNull() ?: 1,
                                venueName = row[4].toString(),
                                notes = row[5].toString(),
                                isVolunteerBenefit = row[6].toString().equals("Yes", ignoreCase = true),
                                lastModified = row[7].toString().toLongOrNull() ?: System.currentTimeMillis(),
                                nfcCardUid = row[8].toString()
                            ))
                        } catch (e: Exception) {
                            println("Failed to parse guest row $rowNumber (no ID format): ${e.message}")
                        }
                    } else if (row.size >= 8) {
                        // Backward compatibility: no NFC UID column
                        try {
                            val guestName = row[0].toString()
                            val newNanoId = NanoIdGenerator.generateGuestId()
                            guestsToFixInSheets.add(Pair(rowNumber, newNanoId))
                            guests.add(Guest(
                                sheetsId = rowNumber.toString(),
                                nanoId = newNanoId,
                                name = guestName,
                                email = row[1].toString(),
                                phoneNumber = row[2].toString(),
                                invitations = row[3].toString().toIntOrNull() ?: 1,
                                venueName = row[4].toString(),
                                notes = row[5].toString(),
                                isVolunteerBenefit = row[6].toString().equals("Yes", ignoreCase = true),
                                lastModified = row[7].toString().toLongOrNull() ?: System.currentTimeMillis(),
                                nfcCardUid = ""
                            ))
                        } catch (e: Exception) {
                            println("Failed to parse guest row $rowNumber (no NFC UID format): ${e.message}")
                        }
                    } else if (row.size >= 6) {
                        // Backward compatibility: old format without email and phone
                        try {
                            val guestName = row[0].toString()
                            val newNanoId = NanoIdGenerator.generateGuestId()
                            guestsToFixInSheets.add(Pair(rowNumber, newNanoId))
                            guests.add(Guest(
                                sheetsId = rowNumber.toString(),
                                nanoId = newNanoId,
                                name = guestName,
                                email = "",
                                phoneNumber = "",
                                invitations = row[1].toString().toIntOrNull() ?: 1,
                                venueName = row[2].toString(),
                                notes = row[3].toString(),
                                isVolunteerBenefit = row[4].toString().equals("Yes", ignoreCase = true),
                                lastModified = row[5].toString().toLongOrNull() ?: System.currentTimeMillis(),
                                nfcCardUid = ""
                            ))
                        } catch (e: Exception) {
                            println("Failed to parse guest row $rowNumber (old format): ${e.message}")
                        }
                    } else {
                        println("Skipping guest row $rowNumber - insufficient columns: ${row.size}")
                    }
                }

                // Write back any missing or invalid NanoIDs to Google Sheets (column J)
                if (guestsToFixInSheets.isNotEmpty()) {
                    println("📝 Writing ${guestsToFixInSheets.size} guest NanoID(s) to Google Sheets...")
                    guestsToFixInSheets.forEach { (row, nanoId) ->
                        try {
                            val fixRange = ValueRange().setValues(listOf(listOf(nanoId)))
                            sheetsService?.spreadsheets()?.values()?.update(
                                spreadsheetId,
                                "${sheetName}!J$row:J$row",
                                fixRange
                            )?.setValueInputOption("RAW")?.execute()
                            println("✅ Set NanoID for guest row $row: $nanoId")
                        } catch (e: Exception) {
                            println("⚠️ Failed to write NanoID for guest row $row: ${e.message}")
                        }
                    }
                }
                
                println("Successfully parsed ${guests.size} guests")
                guests
                },
                operationName = "sync guests from sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync guests from sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw IOException(createNetworkErrorMessage("sync guests from Google Sheets", e), e)
            }
        }
    }

    // Single Volunteer Operations (App Priority)
    suspend fun addVolunteerToSheets(volunteer: Volunteer) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val values = listOf(
                        volunteer.id, // NanoID (String) - no conversion needed
                        volunteer.name,
                        volunteer.lastNameAbbreviation,
                        volunteer.email,
                        volunteer.phoneNumber,
                        volunteer.dateOfBirth,
                        volunteer.gender?.let { gender ->
                            when (gender) {
                                Gender.FEMALE -> "Female"
                                Gender.MALE -> "Male"
                                Gender.NON_BINARY -> "Non-binary"
                                Gender.OTHER -> "Other"
                                Gender.PREFER_NOT_TO_DISCLOSE -> "Prefer not to disclose"
                            }
                        } ?: "",
                        volunteer.currentRank?.name ?: "No Rank",
                        if (volunteer.isActive) "Yes" else "No",
                        volunteer.lastModified.toString(),
                        volunteer.nfcCardUid
                    )
                    
                    val valueRange = ValueRange().setValues(listOf(values))
                    
                    val response = sheetsService?.spreadsheets()?.values()?.append(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getVolunteerSheet()}!A:K",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    
                    if (response == null) {
                        throw IOException("Failed to add volunteer to Google Sheets - no response received")
                    }
                    
                    // Update the volunteer with the sheets ID (row number)
                    val sheetsId = response.updates?.updatedRange?.let { range ->
                        val match = Regex(".*!A(\\d+):K\\d+").find(range)
                        match?.groupValues?.get(1)?.toIntOrNull()
                    }?.toString() ?: "1"
                    
                    println("Successfully added volunteer to Google Sheets: ${volunteer.name} (Row: $sheetsId)")
                    sheetsId
                },
                operationName = "add volunteer to sheets"
            )
        } catch (e: Exception) {
            println("Failed to add volunteer to sheets: ${e.message}")
            throw IOException(createNetworkErrorMessage("add volunteer to Google Sheets", e), e)
        }
    }
    
    suspend fun updateVolunteerInSheets(volunteer: Volunteer) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            if (volunteer.sheetsId == null) {
                throw IOException("Volunteer has no sheets ID - cannot update")
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val values = listOf(
                        volunteer.id, // NanoID (String) - no conversion needed
                        volunteer.name,
                        volunteer.lastNameAbbreviation,
                        volunteer.email,
                        volunteer.phoneNumber,
                        volunteer.dateOfBirth,
                        volunteer.gender?.let { gender ->
                            when (gender) {
                                Gender.FEMALE -> "Female"
                                Gender.MALE -> "Male"
                                Gender.NON_BINARY -> "Non-binary"
                                Gender.OTHER -> "Other"
                                Gender.PREFER_NOT_TO_DISCLOSE -> "Prefer not to disclose"
                            }
                        } ?: "",
                        volunteer.currentRank?.name ?: "No Rank",
                        if (volunteer.isActive) "Yes" else "No",
                        volunteer.lastModified.toString(),
                        volunteer.nfcCardUid
                    )
                    
                    val valueRange = ValueRange().setValues(listOf(values))
                    val rowNumber = volunteer.sheetsId.toIntOrNull() ?: throw IOException("Invalid sheets ID")
                    
                    val response = sheetsService?.spreadsheets()?.values()?.update(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getVolunteerSheet()}!A$rowNumber:K$rowNumber",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    
                    if (response == null) {
                        throw IOException("Failed to update volunteer in Google Sheets - no response received")
                    }
                    
                    println("Successfully updated volunteer in Google Sheets: ${volunteer.name}")
                },
                operationName = "update volunteer in sheets"
            )
        } catch (e: Exception) {
            println("Failed to update volunteer in sheets: ${e.message}")
            throw IOException(createNetworkErrorMessage("update volunteer in Google Sheets", e), e)
        }
    }

    // Volunteer Operations
    suspend fun syncVolunteersToSheets(volunteers: List<Volunteer>) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                // First, clear the entire sheet to prevent duplicate last rows
                clearSheetRange("${settingsManager.getVolunteerSheet()}!A:Z")
                println("🧹 Cleared entire volunteers sheet to prevent duplicates")
                
                val values = volunteers.map { volunteer ->
                    listOf(
                        volunteer.id, // NanoID (String) - no conversion needed
                        volunteer.name,
                        volunteer.lastNameAbbreviation,
                        volunteer.email,
                        volunteer.phoneNumber,
                        volunteer.dateOfBirth,
                        volunteer.gender?.let { gender ->
                            when (gender) {
                                Gender.FEMALE -> "Female"
                                Gender.MALE -> "Male"
                                Gender.NON_BINARY -> "Non-binary"
                                Gender.OTHER -> "Other"
                                Gender.PREFER_NOT_TO_DISCLOSE -> "Prefer not to disclose"
                            }
                        } ?: "",
                        volunteer.currentRank?.name ?: "No Rank",
                        if (volunteer.isActive) "Yes" else "No",
                        volunteer.lastModified.toString(),
                        volunteer.nfcCardUid
                    )
                }
                
                val valueRange = ValueRange()
                    .setValues(listOf(listOf("ID", "Name", "Abbreviation", "Email", "Phone", "Date of Birth", "Gender", "Rank", "Active", "Last Modified", "NFC UID")) + values)
                
                val response = sheetsService?.spreadsheets()?.values()?.update(
                    settingsManager.getSpreadsheetId(),
                    "${settingsManager.getVolunteerSheet()}!A1",
                    valueRange
                )?.setValueInputOption("RAW")?.execute()
                
                if (response == null) {
                    throw IOException("Failed to update volunteers in Google Sheets - no response received")
                }
                
                println("Successfully synced ${volunteers.size} volunteers to Google Sheets")
                },
                operationName = "sync volunteers to sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync volunteers to sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw IOException(createNetworkErrorMessage("sync volunteers to Google Sheets", e), e)
            }
        }
    }

    suspend fun syncVolunteersFromSheets(): List<Volunteer> = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                val response = sheetsService?.spreadsheets()?.values()?.get(
                    settingsManager.getSpreadsheetId(),
                    "${settingsManager.getVolunteerSheet()}!A2:K"
                )?.execute()
                
                if (response == null) {
                    throw IOException("Failed to retrieve volunteers from Google Sheets - no response received")
                }
                
                val values = response.getValues() ?: emptyList()
                println("Retrieved ${values.size} volunteer rows from sheets")
                
                val volunteers = mutableListOf<Volunteer>()
                val volunteersToFixInSheets = mutableListOf<Pair<Int, String>>() // (rowNumber, newId)
                
                values.forEachIndexed { index, row ->
                    if (row.size >= 11) {
                        try {
                            val rowNumber = index + 2 // +2 because we start from row 2 (after header)
                            // Column A now contains NanoID (String)
                            // Validate and fix invalid IDs automatically
                            val rawId = row[0].toString()
                            val volunteerName = row[1].toString()
                            val needsFix = NanoIdGenerator.needsRegeneration(rawId)
                            val validId = NanoIdGenerator.ensureValidNanoId(rawId, volunteerName)
                            
                            // If ID was fixed, mark it for update in Google Sheets
                            if (needsFix) {
                                volunteersToFixInSheets.add(Pair(rowNumber, validId))
                            }
                            
                            val volunteer = Volunteer(
                                id = validId, // Validated NanoID (generated if invalid)
                                sheetsId = rowNumber.toString(),
                                name = row[1].toString(),
                                lastNameAbbreviation = row[2].toString(),
                                email = row[3].toString(),
                                phoneNumber = row[4].toString(),
                                dateOfBirth = row[5].toString(),
                                gender = try {
                                    val genderString = row[6].toString()
                                    if (genderString.isBlank()) {
                                        null
                                    } else {
                                        when (genderString) {
                                            "Female" -> Gender.FEMALE
                                            "Male" -> Gender.MALE
                                            "Non-binary" -> Gender.NON_BINARY
                                            "Other" -> Gender.OTHER
                                            "Prefer not to disclose" -> Gender.PREFER_NOT_TO_DISCLOSE
                                            else -> null
                                        }
                                    }
                                } catch (e: Exception) {
                                    println("Failed to parse volunteer gender '${row[6]}' for volunteer '${row[1]}', setting to null")
                                    null
                                },
                                currentRank = try {
                                    val rankString = row[7].toString()
                                    if (rankString == "No Rank" || rankString.isBlank()) {
                                        null
                                    } else {
                                        VolunteerRank.valueOf(rankString)
                                    }
                                } catch (e: Exception) {
                                    println("Failed to parse volunteer rank '${row[7]}' for volunteer '${row[1]}', setting to null")
                                    null
                                },
                                isActive = try {
                                    row[8].toString().equals("Yes", ignoreCase = true)
                                } catch (e: Exception) {
                                    println("Failed to parse volunteer active status for volunteer '${row[1]}', setting to true")
                                    true
                                },
                                lastModified = try {
                                    row[9].toString().toLongOrNull() ?: System.currentTimeMillis()
                                } catch (e: Exception) {
                                    println("Failed to parse volunteer last modified for volunteer '${row[1]}', setting to current time")
                                    System.currentTimeMillis()
                                },
                                nfcCardUid = row[10].toString()
                            )
                            volunteers.add(volunteer)
                        } catch (e: Exception) {
                            println("Failed to parse volunteer row ${index + 2}: ${e.message}")
                            println("Row data: ${row.joinToString(", ")}")
                        }
                    } else if (row.size >= 10) {
                        try {
                            val rowNumber = index + 2
                            val rawId = row[0].toString()
                            val volunteerName = row[1].toString()
                            val needsFix = NanoIdGenerator.needsRegeneration(rawId)
                            val validId = NanoIdGenerator.ensureValidNanoId(rawId, volunteerName)
                            if (needsFix) {
                                volunteersToFixInSheets.add(Pair(rowNumber, validId))
                            }
                            val volunteer = Volunteer(
                                id = validId,
                                sheetsId = rowNumber.toString(),
                                name = row[1].toString(),
                                lastNameAbbreviation = row[2].toString(),
                                email = row[3].toString(),
                                phoneNumber = row[4].toString(),
                                dateOfBirth = row[5].toString(),
                                gender = try {
                                    val genderString = row[6].toString()
                                    if (genderString.isBlank()) null else when (genderString) {
                                        "Female" -> Gender.FEMALE
                                        "Male" -> Gender.MALE
                                        "Non-binary" -> Gender.NON_BINARY
                                        "Other" -> Gender.OTHER
                                        "Prefer not to disclose" -> Gender.PREFER_NOT_TO_DISCLOSE
                                        else -> null
                                    }
                                } catch (_: Exception) { null },
                                currentRank = try {
                                    val rankString = row[7].toString()
                                    if (rankString == "No Rank" || rankString.isBlank()) null else VolunteerRank.valueOf(rankString)
                                } catch (_: Exception) { null },
                                isActive = row[8].toString().equals("Yes", ignoreCase = true),
                                lastModified = row[9].toString().toLongOrNull() ?: System.currentTimeMillis(),
                                nfcCardUid = ""
                            )
                            volunteers.add(volunteer)
                        } catch (e: Exception) {
                            println("Failed to parse volunteer row ${index + 2} (no NFC UID format): ${e.message}")
                        }
                    } else {
                        println("Skipping volunteer row ${index + 2} - insufficient columns: ${row.size} (expected 11)")
                        println("Row data: ${row.joinToString(", ")}")
                    }
                }
                
                // Update Google Sheets with fixed IDs immediately
                if (volunteersToFixInSheets.isNotEmpty()) {
                    println("📝 Updating ${volunteersToFixInSheets.size} volunteer(s) with fixed NanoIDs in Google Sheets...")
                    try {
                        volunteersToFixInSheets.forEach { (rowNumber, newId) ->
                            try {
                                // Update only the ID column (Column A) for the specific row
                                val valueRange = ValueRange().setValues(listOf(listOf(newId)))
                                sheetsService?.spreadsheets()?.values()?.update(
                                    settingsManager.getSpreadsheetId(),
                                    "${settingsManager.getVolunteerSheet()}!A$rowNumber:A$rowNumber",
                                    valueRange
                                )?.setValueInputOption("RAW")?.execute()
                                println("✅ Updated row $rowNumber with new NanoID: $newId")
                            } catch (e: Exception) {
                                println("⚠️ Failed to update row $rowNumber with new NanoID: ${e.message}")
                            }
                        }
                        println("✅ Successfully updated ${volunteersToFixInSheets.size} volunteer ID(s) in Google Sheets")
                    } catch (e: Exception) {
                        println("⚠️ Failed to update some volunteer IDs in Google Sheets: ${e.message}")
                        // Don't throw - we still want to return the volunteers with fixed IDs
                    }
                }
                
                println("Successfully parsed ${volunteers.size} volunteers")
                volunteers
                },
                operationName = "sync volunteers from sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync volunteers from sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw IOException(createNetworkErrorMessage("sync volunteers from Google Sheets", e), e)
            }
        }
    }

    // Single Job Operations (App Priority)
    suspend fun addJobToSheets(job: Job, venues: List<VenueEntity>): String? = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            val sheetsId = ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val values = listOf(
                        job.volunteerId, // NanoID (String) - no conversion needed
                        job.jobTypeName,
                        job.venueName,
                        job.date.toString(),
                        job.shiftTime.name,
                        job.notes,
                        job.lastModified.toString(),
                        when (job.benefitUsed) { true -> "Yes"; false -> "No"; null -> "" }
                    )
                    
                    val valueRange = ValueRange().setValues(listOf(values))
                    
                    val response = sheetsService?.spreadsheets()?.values()?.append(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getJobsSheet()}!A:H",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    
                    if (response == null) {
                        throw IOException("Failed to add job to Google Sheets - no response received")
                    }
                    
                    // Update the job with the sheets ID (row number)
                    val sheetsId = response.updates?.updatedRange?.let { range ->
                        val match = Regex(".*!A(\\d+):[A-Z]+\\d+").find(range)
                        match?.groupValues?.get(1)?.toIntOrNull()
                    }?.toString() ?: "1"
                    
                    println("Successfully added job to Google Sheets: ${job.jobTypeName} (Row: $sheetsId)")
                    sheetsId
                },
                operationName = "add job to sheets"
            )
            
            sheetsId
        } catch (e: Exception) {
            println("Failed to add job to sheets: ${e.message}")
            throw IOException(createNetworkErrorMessage("add job to Google Sheets", e), e)
        }
    }
    
    suspend fun updateJobInSheets(job: Job, venues: List<VenueEntity>) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            if (job.sheetsId == null) {
                throw IOException("Job has no sheets ID - cannot update")
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val values = listOf(
                        job.volunteerId, // NanoID (String) - no conversion needed
                        job.jobTypeName,
                        job.venueName,
                        job.date.toString(),
                        job.shiftTime.name,
                        job.notes,
                        job.lastModified.toString(),
                        when (job.benefitUsed) { true -> "Yes"; false -> "No"; null -> "" }
                    )
                    
                    val valueRange = ValueRange().setValues(listOf(values))
                    val rowNumber = job.sheetsId.toIntOrNull() ?: throw IOException("Invalid sheets ID")
                    
                    val response = sheetsService?.spreadsheets()?.values()?.update(
                        settingsManager.getSpreadsheetId(),
                        "${settingsManager.getJobsSheet()}!A$rowNumber:H$rowNumber",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    
                    if (response == null) {
                        throw IOException("Failed to update job in Google Sheets - no response received")
                    }
                    
                    println("Successfully updated job in Google Sheets: ${job.jobTypeName}")
                },
                operationName = "update job in sheets"
            )
        } catch (e: Exception) {
            println("Failed to update job in sheets: ${e.message}")
            throw IOException(createNetworkErrorMessage("update job in Google Sheets", e), e)
        }
    }

    // Job Operations
    suspend fun syncJobsToSheets(jobs: List<Job>, venues: List<VenueEntity>) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            println("🔄 Syncing ${jobs.size} jobs to Google Sheets (OVERWRITE MODE)...")
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                // First, clear the entire sheet to prevent duplicate last rows
                clearSheetRange("${settingsManager.getJobsSheet()}!A:Z")
                println("🧹 Cleared entire jobs sheet to prevent duplicates")
                
                val values = jobs.map { job ->
                    listOf(
                        job.volunteerId, // NanoID (String) - no conversion needed
                        job.jobTypeName, // Use the personalized job type name
                        job.venueName,
                        job.date.toString(),
                        job.shiftTime.name,
                        job.notes,
                        job.lastModified.toString(),
                        when (job.benefitUsed) { true -> "Yes"; false -> "No"; null -> "" }
                    )
                }
                
                val valueRange = ValueRange()
                    .setValues(listOf(listOf("Volunteer ID", "Job Type", "Venue", "Date", "Shift Time", "Notes", "Last Modified", "Used")) + values)
                
                println("📤 Sending ${values.size + 1} rows (including header) to Google Sheets...")
                
                val response = sheetsService?.spreadsheets()?.values()?.update(
                    settingsManager.getSpreadsheetId(),
                    "${settingsManager.getJobsSheet()}!A1",
                    valueRange
                )?.setValueInputOption("RAW")?.execute()
                
                if (response == null) {
                    throw IOException("Failed to update jobs in Google Sheets - no response received")
                }
                
                println("✅ Successfully synced ${jobs.size} jobs to Google Sheets (overwrote entire sheet)")
                },
                operationName = "sync jobs to sheets"
            )
        } catch (e: Exception) {
            println("❌ Failed to sync jobs to sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw IOException(createNetworkErrorMessage("sync jobs to Google Sheets", e), e)
            }
        }
    }

    suspend fun syncJobsFromSheets(_jobTypeConfigs: List<JobTypeConfig> = emptyList()): List<Job> = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                val response = sheetsService?.spreadsheets()?.values()?.get(
                    settingsManager.getSpreadsheetId(),
                    "${settingsManager.getJobsSheet()}!A2:H"
                )?.execute()
                
                if (response == null) {
                    throw IOException("Failed to retrieve jobs from Google Sheets - no response received")
                }
                
                val values = response.getValues() ?: emptyList()
                println("Retrieved ${values.size} job rows from sheets")
                
                val jobs = values.mapIndexedNotNull { index, row ->
                    if (row.size >= 7) {
                        try {
                            val rowNumber = index + 2 // +2 because we start from row 2 (after header)
                            val jobTypeName = row[1].toString()
                            
                            // Column A now contains volunteer NanoID (String)
                            // Validate and fix invalid IDs automatically
                            val rawVolunteerId = row[0].toString()
                            val validVolunteerId = NanoIdGenerator.ensureValidNanoId(rawVolunteerId, "job_${rowNumber}")
                            
                            // For custom job types, always use OTHER as the enum value
                            // The actual job type name is stored in jobTypeName field
                            val jobType = JobType.OTHER
                            
                            // Parse benefitUsed from column H (index 7) if present
                            val benefitUsedRaw = if (row.size > 7) row[7].toString().trim() else ""
                            val benefitUsed: Boolean? = when (benefitUsedRaw.lowercase()) {
                                "yes" -> true
                                "no" -> false
                                else -> null
                            }
                            
                            Job(
                                sheetsId = rowNumber.toString(),
                                volunteerId = validVolunteerId, // Validated NanoID (generated if invalid)
                                jobType = jobType,
                                jobTypeName = jobTypeName, // Store the actual job type name
                                venueName = row[2].toString(),
                                date = row[3].toString().toLongOrNull() ?: System.currentTimeMillis(),
                                shiftTime = ShiftTime.valueOf(row[4].toString()),
                                benefitUsed = benefitUsed,
                                notes = row[5].toString(),
                                lastModified = row[6].toString().toLongOrNull() ?: System.currentTimeMillis()
                            )
                        } catch (e: Exception) {
                            println("Failed to parse job row ${index + 2}: ${e.message}")
                            null
                        }
                    } else {
                        println("Skipping job row ${index + 2} - insufficient columns: ${row.size}")
                        null
                    }
                }
                
                println("Successfully parsed ${jobs.size} jobs")
                jobs
                },
                operationName = "sync jobs from sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync jobs from sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw IOException(createNetworkErrorMessage("sync jobs from Google Sheets", e), e)
            }
        }
    }


    // Single Job Type Operations (App Priority)
    suspend fun addJobTypeToSheets(config: JobTypeConfig) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val values = listOf(
                        config.name,
                        if (config.isActive) "Active" else "Inactive",
                        if (config.isShiftJob) "Yes" else "No",
                        if (config.isOrionJob) "Yes" else "No",
                        if (config.requiresShiftTime) "Yes" else "No",
                        config.benefitSystemType.name,
                        config.manualRewards?.let { rewards ->
                            "${rewards.durationDays}|${rewards.freeDrinks}|${rewards.barDiscountPercentage}|${rewards.freeEntry}|${rewards.invites}|${rewards.otherNotes}"
                        } ?: "",
                        config.description,
                        config.lastModified.toString()
                    )
                    
                    val valueRange = ValueRange().setValues(listOf(values))
                    
                    val response = sheetsService?.spreadsheets()?.values()?.append(
                        settingsManager.getSpreadsheetId(),
                        "JobTypes!A:I",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    
                    if (response == null) {
                        throw IOException("Failed to add job type to Google Sheets - no response received")
                    }
                    
                    val sheetsId = response.updates?.updatedRange?.let { range ->
                        val match = Regex(".*!A(\\d+):I\\d+").find(range)
                        match?.groupValues?.get(1)?.toIntOrNull()
                    }?.toString() ?: "1"
                    
                    println("Successfully added job type to Google Sheets: ${config.name} (Row: $sheetsId)")
                    sheetsId
                },
                operationName = "add job type to sheets"
            )
        } catch (e: Exception) {
            println("Failed to add job type to sheets: ${e.message}")
            throw IOException(createNetworkErrorMessage("add job type to Google Sheets", e), e)
        }
    }
    
    suspend fun updateJobTypeInSheets(config: JobTypeConfig) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            if (config.sheetsId == null) {
                throw IOException("Job type has no sheets ID - cannot update")
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val values = listOf(
                        config.name,
                        if (config.isActive) "Active" else "Inactive",
                        if (config.isShiftJob) "Yes" else "No",
                        if (config.isOrionJob) "Yes" else "No",
                        if (config.requiresShiftTime) "Yes" else "No",
                        config.benefitSystemType.name,
                        config.manualRewards?.let { rewards ->
                            "${rewards.durationDays}|${rewards.freeDrinks}|${rewards.barDiscountPercentage}|${rewards.freeEntry}|${rewards.invites}|${rewards.otherNotes}"
                        } ?: "",
                        config.description,
                        config.lastModified.toString()
                    )
                    
                    val valueRange = ValueRange().setValues(listOf(values))
                    val rowNumber = config.sheetsId.toIntOrNull() ?: throw IOException("Invalid sheets ID")
                    
                    val response = sheetsService?.spreadsheets()?.values()?.update(
                        settingsManager.getSpreadsheetId(),
                        "JobTypes!A$rowNumber:I$rowNumber",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()
                    
                    if (response == null) {
                        throw IOException("Failed to update job type in Google Sheets - no response received")
                    }
                    
                    println("Successfully updated job type in Google Sheets: ${config.name}")
                },
                operationName = "update job type in sheets"
            )
        } catch (e: Exception) {
            println("Failed to update job type in sheets: ${e.message}")
            throw IOException(createNetworkErrorMessage("update job type in Google Sheets", e), e)
        }
    }

    // Job Type Config Operations
    suspend fun syncJobTypeConfigsToSheets(jobTypeConfigs: List<JobTypeConfig>) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            println("🔄 Syncing ${jobTypeConfigs.size} job types to Google Sheets (OVERWRITE MODE)...")
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                // First, clear the entire sheet to prevent duplicate last rows
                clearSheetRange("JobTypes!A:Z")
                println("🧹 Cleared entire job types sheet to prevent duplicates")
                
                val values = jobTypeConfigs.map { config ->
                    listOf(
                        config.name,
                        if (config.isActive) "Active" else "Inactive",
                        if (config.isShiftJob) "Yes" else "No",
                        if (config.isOrionJob) "Yes" else "No",
                        if (config.requiresShiftTime) "Yes" else "No",
                        config.benefitSystemType.name,
                        config.manualRewards?.let { rewards ->
                            "${rewards.durationDays}|${rewards.freeDrinks}|${rewards.barDiscountPercentage}|${rewards.freeEntry}|${rewards.invites}|${rewards.otherNotes}"
                        } ?: "",
                        config.description,
                        config.lastModified.toString()
                    )
                }
                
                val valueRange = ValueRange()
                    .setValues(listOf(listOf("Name", "Status", "Shift Type", "Orion Type", "Requires Time", "Benefit System", "Manual Rewards", "Description", "Last Modified")) + values)
                
                println("📤 Sending ${values.size + 1} rows (including header) to Google Sheets...")
                
                val response = sheetsService?.spreadsheets()?.values()?.update(
                    settingsManager.getSpreadsheetId(),
                    "JobTypes!A1",
                    valueRange
                )?.setValueInputOption("RAW")?.execute()
                
                if (response == null) {
                    throw IOException("Failed to update job type configs in Google Sheets - no response received")
                }
                
                println("✅ Successfully synced ${jobTypeConfigs.size} job types to Google Sheets (overwrote entire sheet)")
                },
                operationName = "sync job type configs to sheets"
            )
        } catch (e: Exception) {
            println("❌ Failed to sync job type configs to sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw IOException(createNetworkErrorMessage("sync job type configs to Google Sheets", e), e)
            }
        }
    }

    suspend fun syncJobTypeConfigsFromSheets(): List<JobTypeConfig> = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                val response = sheetsService?.spreadsheets()?.values()?.get(
                    settingsManager.getSpreadsheetId(),
                    "JobTypes!A2:I"
                )?.execute()
                
                if (response == null) {
                    throw IOException("Failed to retrieve job type configs from Google Sheets - no response received")
                }
                
                val values = response.getValues() ?: emptyList()
                println("Retrieved ${values.size} job type config rows from sheets")
                
                val configs = values.mapIndexedNotNull { index, row ->
                    if (row.size >= 9) {
                        try {
                            // Parse benefit system type
                            val benefitSystemType = try {
                                BenefitSystemType.valueOf(row[5].toString())
                            } catch (e: Exception) {
                                BenefitSystemType.STELLAR // Default to STELLAR for backward compatibility
                            }
                            
                            // Parse manual rewards
                            val manualRewards = if (row[6].toString().isNotEmpty()) {
                                try {
                                    val parts = row[6].toString().split("|")
                                    if (parts.size == 6) {
                                        ManualRewards(
                                            durationDays = parts[0].toIntOrNull() ?: 1,
                                            freeDrinks = parts[1].toIntOrNull() ?: 0,
                                            barDiscountPercentage = parts[2].toIntOrNull() ?: 0,
                                            freeEntry = parts[3].toBooleanStrictOrNull() ?: false,
                                            invites = parts[4].toIntOrNull() ?: 0,
                                            otherNotes = parts[5]
                                        )
                                    } else null
                                } catch (e: Exception) {
                                    println("Failed to parse manual rewards for row ${index + 2}: ${e.message}")
                                    null
                                }
                            } else null
                            
                            JobTypeConfig(
                                id = 0, // Will be set by database
                                name = row[0].toString(),
                                isActive = row[1].toString().equals("Active", ignoreCase = true),
                                isShiftJob = row[2].toString().equals("Yes", ignoreCase = true),
                                isOrionJob = row[3].toString().equals("Yes", ignoreCase = true),
                                requiresShiftTime = row[4].toString().equals("Yes", ignoreCase = true),
                                benefitSystemType = benefitSystemType,
                                manualRewards = manualRewards,
                                description = row[7].toString(),
                                lastModified = row[8].toString().toLongOrNull() ?: System.currentTimeMillis()
                            )
                        } catch (e: Exception) {
                            println("Failed to parse job type config row ${index + 2}: ${e.message}")
                            null
                        }
                    } else if (row.size >= 7) {
                        // Backward compatibility for old format (7 columns)
                        try {
                            JobTypeConfig(
                                id = 0, // Will be set by database
                                name = row[0].toString(),
                                isActive = row[1].toString().equals("Active", ignoreCase = true),
                                isShiftJob = row[2].toString().equals("Yes", ignoreCase = true),
                                isOrionJob = row[3].toString().equals("Yes", ignoreCase = true),
                                requiresShiftTime = row[4].toString().equals("Yes", ignoreCase = true),
                                benefitSystemType = BenefitSystemType.STELLAR, // Default for old format
                                manualRewards = null, // No manual rewards in old format
                                description = row[5].toString(),
                                lastModified = row[6].toString().toLongOrNull() ?: System.currentTimeMillis()
                            )
                        } catch (e: Exception) {
                            println("Failed to parse job type config row ${index + 2} (old format): ${e.message}")
                            null
                        }
                    } else {
                        println("Skipping job type config row ${index + 2} - insufficient columns: ${row.size}")
                        null
                    }
                }
                
                println("Successfully parsed ${configs.size} job type configs")
                configs
                },
                operationName = "sync job type configs from sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync job type configs from sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw IOException(createNetworkErrorMessage("sync job type configs from Google Sheets", e), e)
            }
        }
    }

    // Venue Operations
    suspend fun syncVenuesToSheets(venues: List<VenueEntity>) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            println("🔄 Syncing ${venues.size} venues to Google Sheets (OVERWRITE MODE)...")
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                // First, clear the entire sheet to prevent duplicate last rows
                clearSheetRange("${settingsManager.getVenuesSheet()}!A:Z")
                println("🧹 Cleared entire venues sheet to prevent duplicates")
                
                val values = venues.map { venue ->
                    listOf(
                        venue.name,
                        venue.description,
                        if (venue.isActive) "Active" else "Inactive",
                        venue.lastModified.toString()
                    )
                }
                
                val valueRange = ValueRange()
                    .setValues(listOf(listOf("Name", "Description", "Status", "Last Modified")) + values)
                
                println("📤 Sending ${values.size + 1} rows (including header) to Google Sheets...")
                
                val response = sheetsService?.spreadsheets()?.values()?.update(
                    settingsManager.getSpreadsheetId(),
                    "${settingsManager.getVenuesSheet()}!A1",
                    valueRange
                )?.setValueInputOption("RAW")?.execute()
                
                if (response == null) {
                    throw IOException("Failed to update venues in Google Sheets - no response received")
                }
                
                println("✅ Successfully synced ${venues.size} venues to Google Sheets (overwrote entire sheet)")
                },
                operationName = "sync venues to sheets"
            )
        } catch (e: Exception) {
            println("❌ Failed to sync venues to sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw IOException(createNetworkErrorMessage("sync venues to Google Sheets", e), e)
            }
        }
    }

    suspend fun syncVenuesFromSheets(): List<VenueEntity> = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                val response = sheetsService?.spreadsheets()?.values()?.get(
                    settingsManager.getSpreadsheetId(),
                    "${settingsManager.getVenuesSheet()}!A2:D"
                )?.execute()
                
                if (response == null) {
                    throw IOException("Failed to retrieve venues from Google Sheets - no response received")
                }
                
                val values = response.getValues() ?: emptyList()
                println("Retrieved ${values.size} venue rows from sheets")
                
                val venues = values.mapIndexedNotNull { index, row ->
                    if (row.size >= 4) {
                        try {
                            val rowNumber = index + 2 // +2 because we start from row 2 (after header)
                            VenueEntity(
                                id = 0, // Will be set by database
                                sheetsId = rowNumber.toString(),
                                name = row[0].toString(),
                                description = row[1].toString(),
                                isActive = row[2].toString().equals("Active", ignoreCase = true),
                                lastModified = row[3].toString().toLongOrNull() ?: System.currentTimeMillis()
                            )
                        } catch (e: Exception) {
                            println("Failed to parse venue row ${index + 2}: ${e.message}")
                            null
                        }
                    } else {
                        println("Skipping venue row ${index + 2} - insufficient columns: ${row.size}")
                        null
                    }
                }
                
                println("Successfully parsed ${venues.size} venues")
                venues
                },
                operationName = "sync venues from sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync venues from sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw IOException(createNetworkErrorMessage("sync venues from Google Sheets", e), e)
            }
        }
    }

    suspend fun syncAllFromSheetsWithJobTypes(): Triple<List<Guest>, List<Volunteer>, List<Job>> {
        val guests = syncGuestsFromSheets()
        val volunteers = syncVolunteersFromSheets()
        val jobTypeConfigs = syncJobTypeConfigsFromSheets()
        val jobs = syncJobsFromSheets(jobTypeConfigs)
        return Triple(guests, volunteers, jobs)
    }
    
    suspend fun syncAllFromSheetsWithJobTypes(jobTypeConfigs: List<JobTypeConfig>): Triple<List<Guest>, List<Volunteer>, List<Job>> {
        val guests = syncGuestsFromSheets()
        val volunteers = syncVolunteersFromSheets()
        val jobs = syncJobsFromSheets(jobTypeConfigs)
        return Triple(guests, volunteers, jobs)
    }
    
    // Public access methods for validators
    fun getSheetsService() = sheetsService
    fun getContext() = context
    
    // Test method to verify API connection
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            println("=== TESTING GOOGLE SHEETS CONNECTION ===")
            
            if (sheetsService == null) {
                println("Sheets service is null, initializing...")
                initializeSheetsService()
            }
            
            val spreadsheetId = settingsManager.getSpreadsheetId()
            println("Spreadsheet ID: $spreadsheetId")
            
            if (spreadsheetId.isBlank() || spreadsheetId == "YOUR_SPREADSHEET_ID_HERE") {
                throw IOException("Spreadsheet ID is not configured properly. Please set it in Settings.")
            }
            
            // Try to get spreadsheet metadata
            println("Attempting to connect to spreadsheet...")
            val spreadsheet = sheetsService?.spreadsheets()?.get(spreadsheetId)?.execute()
            if (spreadsheet != null) {
                println("✅ Successfully connected to spreadsheet: ${spreadsheet.properties?.title}")
                
                // Test reading from each sheet
                val guestSheetName = settingsManager.getGuestListSheet()
                val volunteerSheetName = settingsManager.getVolunteerSheet()
                val jobsSheetName = settingsManager.getJobsSheet()
                
                println("Testing sheet access...")
                println("- Guest sheet: $guestSheetName")
                println("- Volunteer sheet: $volunteerSheetName")
                println("- Jobs sheet: $jobsSheetName")
                
                // Test guest sheet access
                try {
                    val guestResponse = sheetsService?.spreadsheets()?.values()?.get(
                        spreadsheetId, "${guestSheetName}!A1:I1"
                    )?.execute()
                    println("✅ Guest sheet accessible, headers: ${guestResponse?.getValues()?.firstOrNull()}")
                } catch (e: Exception) {
                    println("❌ Guest sheet error: ${e.message}")
                }
                
                // Test volunteer sheet access
                try {
                    val volunteerResponse = sheetsService?.spreadsheets()?.values()?.get(
                        spreadsheetId, "${volunteerSheetName}!A1:K1"
                    )?.execute()
                    println("✅ Volunteer sheet accessible, headers: ${volunteerResponse?.getValues()?.firstOrNull()}")
                } catch (e: Exception) {
                    println("❌ Volunteer sheet error: ${e.message}")
                }
                
                // Test jobs sheet access
                try {
                    val jobsResponse = sheetsService?.spreadsheets()?.values()?.get(
                        spreadsheetId, "${jobsSheetName}!A1:H1"
                    )?.execute()
                    println("✅ Jobs sheet accessible, headers: ${jobsResponse?.getValues()?.firstOrNull()}")
                } catch (e: Exception) {
                    println("❌ Jobs sheet error: ${e.message}")
                }
                
                // Test JobTypes sheet access
                try {
                    val jobTypesResponse = sheetsService?.spreadsheets()?.values()?.get(
                        spreadsheetId, "JobTypes!A1:I1"
                    )?.execute()
                    println("✅ JobTypes sheet accessible, headers: ${jobTypesResponse?.getValues()?.firstOrNull()}")
                } catch (e: Exception) {
                    println("❌ JobTypes sheet error: ${e.message}")
                }
                
                return@withContext true
            } else {
                throw IOException("Failed to retrieve spreadsheet")
            }
        } catch (e: Exception) {
            println("❌ Connection test failed: ${e.message}")
            e.printStackTrace()
            
            // Provide specific error messages for common issues
            when {
                e.message?.contains("403") == true -> {
                    println("❌ Permission denied. Check if the service account has access to the spreadsheet.")
                }
                e.message?.contains("404") == true -> {
                    println("❌ Spreadsheet not found. Check if the spreadsheet ID is correct.")
                }
                e.message?.contains("400") == true -> {
                    println("❌ Bad request. Check if the spreadsheet ID format is correct.")
                }
                e.message?.contains("Service account key file not found") == true -> {
                    println("❌ Service account key file missing. Please upload it in Settings.")
                }
                e.message?.contains("Failed to initialize") == true -> {
                    println("❌ Authentication failed. Check if the service account key is valid.")
                }
            }
            
            return@withContext false
        }
    }
    
    // Deletion methods for Google Sheets
    suspend fun deleteJobFromSheets(jobId: String, sheetsId: String?) = withContext(Dispatchers.IO) {
        try {
            if (sheetsId == null) {
                println("Cannot delete job from sheets - no sheetsId provided")
                return@withContext
            }
            
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val spreadsheetId = settingsManager.getSpreadsheetId()
                    val sheetName = settingsManager.getJobsSheet()
                    
                    // sheetsId is the row number, so use it directly
                    val actualRowNumber = sheetsId.toIntOrNull()
                    if (actualRowNumber == null) {
                        println("Invalid sheetsId format: $sheetsId (expected row number)")
                        throw IOException("Invalid sheetsId format: $sheetsId (expected row number)")
                    }
                    
                    // Get the sheet ID first
                    val spreadsheet = sheetsService?.spreadsheets()?.get(spreadsheetId)?.execute()
                    val sheet = spreadsheet?.sheets?.find { it.properties?.title == sheetName }
                    val sheetId = sheet?.properties?.sheetId
                    
                    if (sheetId != null) {
                        println("Deleting job from sheet: $sheetName, sheetId: $sheetId, row: $actualRowNumber")
                        
                        // Actually delete the row using batchUpdate
                        val deleteRequest = Request()
                            .setDeleteDimension(
                                DeleteDimensionRequest()
                                    .setRange(
                                        DimensionRange()
                                            .setSheetId(sheetId)
                                            .setDimension("ROWS")
                                            .setStartIndex(actualRowNumber - 1) // 0-based index
                                            .setEndIndex(actualRowNumber) // Delete one row
                                    )
                            )
                        
                        val batchUpdateRequest = BatchUpdateSpreadsheetRequest()
                            .setRequests(listOf(deleteRequest))
                        
                        val result = sheetsService?.spreadsheets()?.batchUpdate(spreadsheetId, batchUpdateRequest)?.execute()
                        println("Delete result: ${result?.replies?.size} replies")
                        
                        println("Successfully deleted job with sheetsId $sheetsId from row $actualRowNumber")
                    } else {
                        println("Could not find sheet ID for sheet: $sheetName")
                        throw IOException("Could not find sheet ID for sheet: $sheetName")
                    }
                },
                operationName = "delete job from sheets"
            )
        } catch (e: Exception) {
            println("Failed to delete job from sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw IOException(createNetworkErrorMessage("delete job from Google Sheets", e), e)
            }
        }
    }
    
    // ── Sheet Structure Validation & Repair ─────────────────────────────────

    private data class SheetDefinition(val name: String, val headers: List<String>)

    private fun getSheetDefinitions(): List<SheetDefinition> = listOf(
        SheetDefinition(
            settingsManager.getGuestListSheet(),
                listOf("Name", "Email", "Phone", "Invitations", "Venue", "Notes", "Volunteer Benefit", "Last Modified", "NFC UID", "ID")
        ),
        SheetDefinition(
            settingsManager.getVolunteerGuestListSheet(),
                listOf("Name", "Last Name Abbreviation", "Invitations", "Venue", "Notes", "Volunteer Benefit", "Last Modified", "NFC UID")
        ),
        SheetDefinition(
            settingsManager.getVolunteerSheet(),
                listOf("ID", "Name", "Abbreviation", "Email", "Phone", "Date of Birth", "Gender", "Rank", "Active", "Last Modified", "NFC UID")
        ),
        SheetDefinition(
            settingsManager.getJobsSheet(),
            listOf("Volunteer ID", "Job Type", "Venue", "Date", "Shift Time", "Notes", "Last Modified", "Used")
        ),
        SheetDefinition(
            "JobTypes",
            listOf("Name", "Status", "Shift Type", "Orion Type", "Requires Time", "Benefit System", "Manual Rewards", "Description", "Last Modified")
        ),
        SheetDefinition(
            settingsManager.getVenuesSheet(),
            listOf("Name", "Description", "Status", "Last Modified")
        ),
        SheetDefinition(
            settingsManager.getTempGuestListSheet(),
            listOf(
                "Modification Date",
                "Event Date",
                "Artist/Group",
                "Artist Contact Phone",
                "Guest Name",
                "Comment",
                "ID"
            )
        )
    )

    data class TempGuestRow(
        val rowNumber: Int,
        val modificationDate: java.time.LocalDate,
        val eventDate: java.time.LocalDate,
        val artistName: String,
        val artistContactPhone: String,
        val guestName: String,
        val comment: String,
        val nanoId: String = ""
    )

    suspend fun syncTempGuestsFromSheets(): List<TempGuestRow> = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }

            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val spreadsheetId = settingsManager.getSpreadsheetId()
                    val sheetName = settingsManager.getTempGuestListSheet()
                    val range = "${sheetName}!A2:G"

                    val response = sheetsService?.spreadsheets()?.values()?.get(
                        spreadsheetId,
                        range
                    )?.execute()

                    if (response == null) {
                        throw IOException("Failed to retrieve temporary guests from Google Sheets - no response received")
                    }

                    val values = response.getValues() ?: emptyList()
                    println("Retrieved ${values.size} temporary guest rows from sheets")

                    val formatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
                    val tempGuestsToFixInSheets = mutableListOf<Pair<Int, String>>()

                    val result = values.mapIndexedNotNull { index, row ->
                        if (row.size >= 5) {
                            try {
                                val rowNumber = index + 2
                                val modificationDate = java.time.LocalDate.parse(row[0].toString().trim(), formatter)
                                val eventDate = java.time.LocalDate.parse(row[1].toString().trim(), formatter)
                                val guestName = row[4].toString()

                                // Parse NanoID from column G (index 6)
                                val rawNanoId = if (row.size > 6) row[6].toString() else ""
                                val needsFix = NanoIdGenerator.needsRegeneration(rawNanoId)
                                val validNanoId = NanoIdGenerator.ensureValidNanoId(rawNanoId, guestName)
                                if (needsFix) {
                                    tempGuestsToFixInSheets.add(Pair(rowNumber, validNanoId))
                                }

                                TempGuestRow(
                                    rowNumber = rowNumber,
                                    modificationDate = modificationDate,
                                    eventDate = eventDate,
                                    artistName = row[2].toString(),
                                    artistContactPhone = row[3].toString(),
                                    guestName = guestName,
                                    comment = if (row.size > 5) row[5].toString() else "",
                                    nanoId = validNanoId
                                )
                            } catch (e: Exception) {
                                println("Failed to parse temp guest row ${index + 2}: ${e.message}")
                                null
                            }
                        } else {
                            println("Skipping temp guest row ${index + 2} - insufficient columns: ${row.size}")
                            null
                        }
                    }

                    // Write back missing or invalid NanoIDs to column G
                    if (tempGuestsToFixInSheets.isNotEmpty()) {
                        println("📝 Writing ${tempGuestsToFixInSheets.size} temp guest NanoID(s) to Google Sheets...")
                        tempGuestsToFixInSheets.forEach { (row, nanoId) ->
                            try {
                                val fixRange = ValueRange().setValues(listOf(listOf(nanoId)))
                                sheetsService?.spreadsheets()?.values()?.update(
                                    spreadsheetId,
                                    "${sheetName}!G$row:G$row",
                                    fixRange
                                )?.setValueInputOption("RAW")?.execute()
                                println("✅ Set NanoID for temp guest row $row: $nanoId")
                            } catch (e: Exception) {
                                println("⚠️ Failed to write NanoID for temp guest row $row: ${e.message}")
                            }
                        }
                    }

                    result
                },
                operationName = "sync temp guests from sheets"
            )
        } catch (e: Exception) {
            println("Failed to sync temp guests from sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw IOException(createNetworkErrorMessage("sync temp guests from Google Sheets", e), e)
            }
        }
    }

    suspend fun updateTemporaryGuestInSheets(guest: Guest) = withContext(Dispatchers.IO) {
        try {
            if (!guest.isTemporaryGuest) {
                println("Skipping updateTemporaryGuestInSheets for non-temporary guest: ${guest.name}")
                return@withContext
            }
            if (sheetsService == null) {
                initializeSheetsService()
            }

            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val spreadsheetId = settingsManager.getSpreadsheetId()
                    val sheetName = settingsManager.getTempGuestListSheet()
                    val zone = java.time.ZoneId.of("Europe/Zurich")
                    val formatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

                    val rowNumber = guest.sheetsId?.toIntOrNull()
                    if (rowNumber == null) {
                        throw IOException("Temporary guest has no valid sheets row ID for update: ${guest.sheetsId}")
                    }

                    val eventDate = guest.temporaryEventDate?.let {
                        java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate().format(formatter)
                    } ?: throw IOException("Temporary guest has no event date")

                    val today = java.time.LocalDate.now(zone).format(formatter)
                    val values = listOf(
                        today,
                        eventDate,
                        guest.temporaryArtistName,
                        guest.temporaryContactPhone,
                        guest.name,
                        guest.notes,
                        guest.nanoId
                    )

                    val valueRange = ValueRange().setValues(listOf(values))
                    sheetsService?.spreadsheets()?.values()?.update(
                        spreadsheetId,
                        "${sheetName}!A$rowNumber:G$rowNumber",
                        valueRange
                    )?.setValueInputOption("RAW")?.execute()

                    println("Successfully updated temporary guest in sheets at row $rowNumber: ${guest.name}")
                },
                operationName = "update temporary guest in sheets"
            )
        } catch (e: Exception) {
            println("Failed to update temporary guest in sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw IOException(createNetworkErrorMessage("update temporary guest in Google Sheets", e), e)
            }
        }
    }

    suspend fun deleteTemporaryGuestFromSheets(sheetsId: String?) = withContext(Dispatchers.IO) {
        try {
            if (sheetsId == null) {
                println("Cannot delete temporary guest from sheets - no sheetsId provided")
                return@withContext
            }
            if (sheetsService == null) {
                initializeSheetsService()
            }

            ApiRateLimitHandler.executeWithRetry(
                operation = {
                    val spreadsheetId = settingsManager.getSpreadsheetId()
                    val sheetName = settingsManager.getTempGuestListSheet()
                    val rowNumber = sheetsId.toIntOrNull()
                        ?: throw IOException("Invalid sheetsId format for temporary guest: $sheetsId")

                    val spreadsheet = sheetsService?.spreadsheets()?.get(spreadsheetId)?.execute()
                    val sheetId = spreadsheet?.sheets
                        ?.find { it.properties?.title == sheetName }
                        ?.properties?.sheetId
                        ?: throw IOException("Could not find sheet ID for sheet: $sheetName")

                    val deleteRequest = Request()
                        .setDeleteDimension(
                            DeleteDimensionRequest().setRange(
                                DimensionRange()
                                    .setSheetId(sheetId)
                                    .setDimension("ROWS")
                                    .setStartIndex(rowNumber - 1)
                                    .setEndIndex(rowNumber)
                            )
                        )

                    val batchUpdateRequest = BatchUpdateSpreadsheetRequest()
                        .setRequests(listOf(deleteRequest))

                    sheetsService?.spreadsheets()?.batchUpdate(spreadsheetId, batchUpdateRequest)?.execute()
                    println("Successfully deleted temporary guest from sheets at row $rowNumber")
                },
                operationName = "delete temporary guest from sheets"
            )
        } catch (e: Exception) {
            println("Failed to delete temporary guest from sheets: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("Rate limit") == true) {
                throw IOException(ApiRateLimitHandler.getBriefRateLimitMessage(), e)
            } else {
                throw IOException(createNetworkErrorMessage("delete temporary guest from Google Sheets", e), e)
            }
        }
    }

    /**
     * Validates every expected sheet tab exists with correct headers, repairing
     * as needed. Data rows are never modified -- when a header is missing and
     * row 1 contains data, a new row is inserted at the top so existing data
     * shifts down.
     *
     * API budget: 2 calls when everything is OK (metadata + batchGet),
     * up to 4 when repairs are needed.
     *
     * @return true on success (with or without repairs), false on error.
     */
    suspend fun validateAndRepairSheetsStructure(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) initializeSheetsService()

            val spreadsheetId = settingsManager.getSpreadsheetId()
            if (spreadsheetId.isBlank() || spreadsheetId == "YOUR_SPREADSHEET_ID_HERE") {
                return@withContext false
            }

            val definitions = getSheetDefinitions()

            // Step 1 -- get metadata (1 API call)
            val spreadsheet = sheetsService?.spreadsheets()?.get(spreadsheetId)?.execute()
                ?: throw IOException("Failed to get spreadsheet metadata")
            val existingNames = spreadsheet.sheets
                ?.mapNotNull { it.properties?.title }?.toHashSet() ?: hashSetOf()

            // Step 2 -- create missing tabs (0-1 API call)
            val (existing, missing) = definitions.partition { it.name in existingNames }

            if (missing.isNotEmpty()) {
                println("➕ Creating ${missing.size} missing sheet tab(s)")
                sheetsService?.spreadsheets()?.batchUpdate(spreadsheetId,
                    BatchUpdateSpreadsheetRequest().setRequests(missing.map { def ->
                        Request().setAddSheet(AddSheetRequest().setProperties(
                            SheetProperties().setTitle(def.name)))
                    })
                )?.execute()
            }

            // Step 3 -- read row 1 from existing tabs (1 API call via batchGet)
            val currentHeaders: List<Pair<SheetDefinition, List<String>>> =
                if (existing.isNotEmpty()) {
                    val ranges = existing.map { "'${it.name}'!A1:Z1" }
                    val batchGet = sheetsService?.spreadsheets()?.values()
                        ?.batchGet(spreadsheetId)?.setRanges(ranges)?.execute()
                    existing.mapIndexed { i, def ->
                        val row = batchGet?.valueRanges?.getOrNull(i)
                            ?.getValues()?.firstOrNull()
                            ?.map { it.toString() } ?: emptyList()
                        def to row
                    }
                } else emptyList()

            // Step 4 -- decide repairs
            val headerWrites = mutableListOf<ValueRange>()
            val rowInsertSheetIds = mutableListOf<Int>()

            // New tabs always need headers written
            for (def in missing) {
                headerWrites.add(ValueRange()
                    .setRange("'${def.name}'!A1")
                    .setValues(listOf(def.headers)))
            }

            // Only fetch updated metadata when we need sheet IDs for row inserts
            var metaForInsert: com.google.api.services.sheets.v4.model.Spreadsheet? = null

            for ((def, row) in currentHeaders) {
                if (row == def.headers) continue // already correct

                if (row.isEmpty()) {
                    // Empty sheet -- just write headers
                    headerWrites.add(ValueRange()
                        .setRange("'${def.name}'!A1")
                        .setValues(listOf(def.headers)))
                    continue
                }

                // Count how many cells match expected headers (integer math, no floats)
                val matchCount = row.zip(def.headers).count { (c, e) ->
                    c.equals(e, ignoreCase = true)
                }
                val isCorruptedHeader = matchCount * 5 >= def.headers.size * 2 // ≥ 40 %

                if (isCorruptedHeader) {
                    // Partial header match -- safe to overwrite row 1
                    println("🔧 '${def.name}' header corrupted ($matchCount/${def.headers.size} match) - overwriting")
                    headerWrites.add(ValueRange()
                        .setRange("'${def.name}'!A1")
                        .setValues(listOf(def.headers)))
                } else {
                    // Row 1 is actual data -- insert a new row to preserve it
                    println("⚠️ '${def.name}' header missing (row 1 is data) - inserting header row")
                    if (metaForInsert == null) {
                        metaForInsert = if (missing.isNotEmpty()) {
                            sheetsService?.spreadsheets()?.get(spreadsheetId)?.execute()
                        } else spreadsheet
                    }
                    val sheetId = metaForInsert?.sheets
                        ?.find { it.properties?.title == def.name }
                        ?.properties?.sheetId
                    if (sheetId != null) {
                        rowInsertSheetIds.add(sheetId)
                        headerWrites.add(ValueRange()
                            .setRange("'${def.name}'!A1")
                            .setValues(listOf(def.headers)))
                    }
                }
            }

            // Step 5 -- insert rows first so data shifts down (0-1 API call)
            if (rowInsertSheetIds.isNotEmpty()) {
                sheetsService?.spreadsheets()?.batchUpdate(spreadsheetId,
                    BatchUpdateSpreadsheetRequest().setRequests(rowInsertSheetIds.map { sid ->
                        Request().setInsertDimension(InsertDimensionRequest().setRange(
                            DimensionRange().setSheetId(sid)
                                .setDimension("ROWS").setStartIndex(0).setEndIndex(1)))
                    })
                )?.execute()
            }

            // Step 6 -- write all headers in one batch (0-1 API call)
            if (headerWrites.isNotEmpty()) {
                sheetsService?.spreadsheets()?.values()?.batchUpdate(spreadsheetId,
                    BatchUpdateValuesRequest()
                        .setValueInputOption("RAW")
                        .setData(headerWrites)
                )?.execute()
                println("✅ Repaired ${headerWrites.size} sheet header(s)")
            }

            true
        } catch (e: Exception) {
            println("❌ Sheet structure validation failed: ${e.message}")
            false
        }
    }

    /**
     * Clear a specific range in a Google Sheet to prevent duplicate data
     */
    private suspend fun clearSheetRange(range: String) = withContext(Dispatchers.IO) {
        try {
            if (sheetsService == null) {
                initializeSheetsService()
            }
            
            val clearRequest = ClearValuesRequest()
            val response = sheetsService?.spreadsheets()?.values()?.clear(
                settingsManager.getSpreadsheetId(),
                range,
                clearRequest
            )?.execute()
            
            if (response == null) {
                throw IOException("Failed to clear sheet range $range - no response received")
            }
            
            println("✅ Cleared sheet range: $range")
        } catch (e: Exception) {
            println("❌ Failed to clear sheet range $range: ${e.message}")
            // Don't throw here - clearing is best effort, we can still proceed with upload
        }
    }
}