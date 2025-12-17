# Google Sheets Setup Instructions

This guide will help you set up Google Sheets synchronization for the Event Manager app.

## Prerequisites

- A Google account
- Access to Google Cloud Console
- The Event Manager app installed on your tablet

## Step-by-Step Setup

### 1. Create Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click "Select a project" at the top
3. Click "New Project"
4. Enter a project name (e.g., "Event Manager App")
5. Click "Create"

### 2. Enable Google Sheets API

1. In your project, go to "APIs & Services" > "Library"
2. Search for "Google Sheets API"
3. Click on "Google Sheets API"
4. Click "Enable"

### 3. Create Service Account

1. Go to "IAM & Admin" > "Service Accounts"
2. Click "Create Service Account"
3. Enter a name (e.g., "event-manager-service")
4. Enter a description (optional)
5. Click "Create and Continue"
6. Skip the "Grant access" step for now
7. Click "Done"

### 4. Generate Service Account Key

1. Find your service account in the list
2. Click on the service account email
3. Go to the "Keys" tab
4. Click "Add Key" > "Create new key"
5. Select "JSON" format
6. Click "Create"
7. The JSON file will download automatically

### 5. Add Key to App

1. Rename the downloaded JSON file to `service_account_key.json`
2. Place the file in: `app/src/main/assets/service_account_key.json`
3. If the `assets` folder doesn't exist, create it

### 6. Create Google Spreadsheet

1. Go to [Google Sheets](https://sheets.google.com/)
2. Click "Blank" to create a new spreadsheet
3. Copy the Spreadsheet ID from the URL:
   ```
   https://docs.google.com/spreadsheets/d/SPREADSHEET_ID_HERE/edit
   ```

### 7. Create Required Sheets

In your spreadsheet, create these six sheets exactly as named:

1. **Guest List** - for guest management
2. **Volunteers** - for volunteer management  
3. **Shifts** - for shift tracking
4. **Shift Types** - for shift type configuration
5. **Volunteer Guest List** - for automatic volunteer guest entries
6. **Venues** - for venue management

### 8. Share Spreadsheet with Service Account

1. In your spreadsheet, click "Share" (top right)
2. Add the service account email (found in the JSON file as `client_email`)
3. Give it "Editor" access
4. Click "Send"

### 9. Configure App Settings

1. Open the Event Manager app
2. Go to the "Settings" tab
3. Enter your Spreadsheet ID
4. Verify the sheet names are correct:
   - Guest List Sheet: "Guest List"
   - Volunteer Sheet: "Volunteers"
   - Shifts Sheet: "Shifts"
   - Shift Types Sheet: "Shift Types"
   - Volunteer Guest List Sheet: "Volunteer Guest List"
   - Venues Sheet: "Venues"
5. Click "Save Settings"
6. Click "Test Connection" to verify setup

## Troubleshooting

### Common Issues

**"Permission denied" error:**
- Make sure you shared the spreadsheet with the service account email
- Check that the service account has "Editor" access

**"Spreadsheet not found" error:**
- Verify the Spreadsheet ID is correct
- Make sure the spreadsheet exists and is accessible

**"Sheet not found" error:**
- Check that the sheet names match exactly (case-sensitive)
- Make sure all three sheets exist in the spreadsheet

**"Service account key not found" error:**
- Verify the JSON file is named `service_account_key.json`
- Check that it's in the correct location: `app/src/main/assets/`

### Getting Help

If you encounter issues:

1. Check the app's sync status in the top bar
2. Look for error messages in the Settings tab
3. Verify all steps were completed correctly
4. Try the "Test Connection" feature in Settings

## Security Notes

- Keep your service account key file secure
- Don't share the JSON key file publicly
- The service account only has access to spreadsheets you explicitly share with it
- You can revoke access anytime by removing the service account from the spreadsheet

## Data Structure

The app will automatically create the following columns in your sheets:

### Guest List Sheet
- Name
- Invitations
- Venue
- Notes
- Volunteer Benefit
- Last Modified

### Volunteers Sheet
- Name
- Abbreviation
- Email
- Phone
- Date of Birth
- Rank
- Active
- Last Modified

### Jobs Sheet
- Volunteer ID
- Job Type
- Venue
- Date
- Shift Time
- Notes
- Last Modified

### Venues Sheet
- Name
- Description
- Status
- Last Modified

## Success!

Once configured, your app will automatically sync data with Google Sheets whenever you:
- Add, edit, or delete guests
- Manage volunteers
- Track jobs
- Manage venues
- Use the manual sync button

The sync happens in real-time, so your data is always up-to-date across all devices!
