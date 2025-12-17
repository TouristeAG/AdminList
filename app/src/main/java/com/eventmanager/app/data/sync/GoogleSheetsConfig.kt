
package com.eventmanager.app.data.sync

/**
 * Configuration class for Google Sheets integration
 * 
 * To set up Google Sheets integration:
 * 
 * 1. Create a Google Cloud Project
 * 2. Enable the Google Sheets API
 * 3. Create a Service Account
 * 4. Download the service account JSON key file
 * 5. Place the JSON file in app/src/main/assets/ as "service_account_key.json"
 * 6. Update the SPREADSHEET_ID below with your actual spreadsheet ID
 *    - "Guest List" (columns: Name, Invitations, Venue, Notes, Volunteer Benefit, Last Modified)
 *    - "Volunteers" (columns: ID, Name, Abbreviation, Email, Phone, Date of Birth, Gender, Rank, Active, Last Modified)
 *    - "Shifts" (columns: Volunteer ID, Shift Type, Venue, Date, Shift Time, Notes, Last Modified)
 *    - "Shift Types" (columns: Name, Status, Shift Type, Orion Type, Requires Time, Description, Last Modified)
 *    - "Volunteer Guest List" (columns: Name, Last Name Abbreviation, Invitations, Venue, Notes, Volunteer Benefit, Last Modified)
 *    - "Venues" (columns: Name, Description, Active, Last Modified)
 */
object GoogleSheetsConfig { 
    // Replace with your actual Google Spreadsheet ID
    const val SPREADSHEET_ID = "YOUR_SPREADSHEET_ID_HERE"
    
    // Sheet names
    const val GUEST_LIST_SHEET = "Guest List"
    const val VOLUNTEER_GUEST_LIST_SHEET = "Volunteer Guest List"
    const val VOLUNTEER_SHEET = "Volunteers"
    const val JOBS_SHEET = "Shifts"
    const val JOB_TYPES_SHEET = "Shift Types"
    const val VENUES_SHEET = "Venues"
    
    // Service account key file name (should be placed in assets folder)
    const val SERVICE_ACCOUNT_KEY_FILE = "service_account_key.json"
    
    // Required scopes for Google Sheets API
    const val SCOPES = "https://www.googleapis.com/auth/spreadsheets"
}

