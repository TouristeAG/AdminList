# Event Manager - Tablet Application

A comprehensive Android tablet application for managing guest lists and volunteers for two concert venues: **Groove** and **Le Terreau**.

## Features

### 🎫 Guest List Management
- Add, edit, and remove guests
- Manage invitation quotas per guest
- Venue-specific guest lists (Groove & Le Terreau)
- Automatic sync with Google Sheets
- Support for volunteer benefits integration

### 👥 Volunteer Management
- Add new volunteers with complete profile information
- Track volunteer jobs and shifts
- Automatic rank calculation based on volunteer activity
- Job history tracking
- Real-time benefits calculation

### 🏆 Volunteer Ranking System
- **Nova**: Shift before midnight → Free entry + friend invitation + 2 drink tokens + 50% bar discount
- **Étoile**: Shift after midnight → Free entry + friend invitation for the month
- **Galaxie**: 3+ shifts/meetings per month → Free entry + 50% bar discount for all monthly events
- **Orion**: Committee/coordination roles → Friend invitation + 50% bar discount + extraordinary benefits
- **Vétéran**: Former Orion rank holders → Year-long guest list access + 50% bar discount

### 📊 Benefits Overview
- Real-time benefits calculation for all volunteers
- Clear benefit descriptions and validity periods
- Easy verification for staff during events

### 🔄 Google Sheets Integration
- Bidirectional sync with Google Sheets
- Automatic sync after modifications
- Manual sync button for staff
- Support for multiple tablets syncing the same data

## Technical Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM with Repository pattern
- **Database**: Room (SQLite)
- **Dependency Injection**: Hilt
- **API Integration**: Google Sheets API v4
- **Navigation**: Navigation Compose
- **Async**: Coroutines + Flow

## Project Structure

```
app/
├── src/main/java/com/eventmanager/app/
│   ├── data/
│   │   ├── dao/           # Room DAOs
│   │   ├── database/      # Database configuration
│   │   ├── models/        # Data models
│   │   ├── remote/        # Google Sheets service
│   │   └── repository/    # Repository pattern
│   ├── di/                # Dependency injection
│   ├── ui/
│   │   ├── components/    # Reusable UI components
│   │   ├── screens/       # Main screens
│   │   ├── theme/         # Material Design theme
│   │   └── viewmodel/     # ViewModels
│   └── MainActivity.kt
```

## Setup Instructions

### 1. Google Sheets API Setup

1. Go to the [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the Google Sheets API
4. Create credentials (OAuth 2.0 Client ID) for Android
5. Download the `google-services.json` file
6. Place it in the `app/` directory

### 2. Configure Google Sheets

Create a Google Sheets document with three sheets:

#### Sheet 1: "GuestList"
| Column A | Column B | Column C | Column D | Column E |
|----------|----------|----------|----------|----------|
| Name | Invitations | Venue | Notes | IsVolunteerBenefit |

#### Sheet 2: "Volunteers"
| Column A | Column B | Column C | Column D | Column E | Column F | Column G |
|----------|----------|----------|----------|----------|----------|----------|
| Name | LastNameAbbr | DateOfBirth | Email | Phone | CurrentRank | IsActive |

#### Sheet 3: "Shifts"
| Column A | Column B | Column C | Column D | Column E | Column F | Column G |
|----------|----------|----------|----------|----------|----------|----------|
| VolunteerID | JobType | Venue | Date | ShiftTime | IsCompleted | Notes |

### 3. Update Configuration

In `GoogleSheetsService.kt`, update the `SPREADSHEET_ID` constant with your Google Sheets ID:

```kotlin
private const val SPREADSHEET_ID = "YOUR_SPREADSHEET_ID_HERE"
```

### 4. Build and Run

1. Open the project in Android Studio
2. Sync the project with Gradle files
3. Build and run on a tablet device or emulator

## Usage Guide

### For Staff Members

1. **Guest List Management**:
   - Switch between Groove and Le Terreau venues using the filter chips
   - Add new guests using the "Add Guest" button
   - Edit existing guests by tapping the edit icon
   - Remove guests using the delete icon (volunteer benefit guests cannot be deleted)

2. **Volunteer Management**:
   - Add new volunteers with complete information
   - Record jobs done by volunteers
   - View volunteer ranks and benefits automatically calculated
   - Track job history for each volunteer

3. **Benefits Verification**:
   - Check the Benefits screen to see all volunteer benefits
   - Verify what benefits each volunteer is entitled to
   - Benefits are automatically calculated based on recent activity

4. **Synchronization**:
   - Use the "Sync" button to manually synchronize with Google Sheets
   - Data automatically syncs after each modification
   - Multiple tablets can work with the same data

### Tablet Optimization

The application is specifically designed for tablet use with:
- Landscape orientation lock
- Large touch targets for easy interaction
- Optimized layouts for tablet screen sizes
- Clear typography and spacing
- Material Design 3 components

## Data Models

### Guest
- Name, email, phone number, invitation count, venue assignment
- Support for volunteer benefit guests
- Notes field for additional information

### Volunteer
- Complete profile with contact information
- Current rank based on activity
- Active/inactive status

### Job
- Links volunteers to specific work done
- Venue and date tracking
- Shift time classification (before/after midnight)
- Job type categorization

### Benefits
- Automatic calculation based on volunteer rank
- Validity period tracking
- Comprehensive benefit descriptions

## Contributing

This application is designed for the specific needs of Groove and Le Terreau venues. For modifications or enhancements, please ensure:

1. Maintain tablet-optimized UI
2. Preserve Google Sheets sync functionality  
3. Keep volunteer ranking logic intact
4. Test with multiple tablet synchronization

## Support

For technical support or questions about the volunteer ranking system, please refer to the original requirements document or contact the development team.

