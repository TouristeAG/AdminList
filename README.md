## Adminlist

A production-ready Android tablet application for managing **guest lists**, **volunteers**, and **shifts** for two concert venues: **Groove** and **Le Terreau**, with a robust **two‑way Google Sheets synchronization** system.

---

## Table of Contents

- [Overview](#overview)
- [Main Features](#main-features)
  - [Guest List Management](#guest-list-management)
  - [Volunteer Management](#volunteer-management)
  - [Shift Tracking](#shift-tracking)
  - [Volunteer Ranking & Benefits System](#volunteer-ranking--benefits-system)
  - [Benefits Overview Screen](#benefits-overview-screen)
  - [Google Sheets Two‑Way Sync](#google-sheets-two-way-sync)
  - [Tablet-Optimized UI](#tablet-optimized-ui)
- [App Screens & Workflows](#app-screens--workflows)
  - [Guests Screen](#guests-screen)
  - [Volunteers Screen](#volunteers-screen)
  - [Shifts & Shift Types Screens](#shifts--shift-types-screens)
  - [Volunteer Guest List](#volunteer-guest-list)
  - [Venues Management](#venues-management)
  - [Statistics / Dashboard](#statistics--dashboard)
  - [Settings & Sync Controls](#settings--sync-controls)
- [Technical Overview](#technical-overview)
  - [Tech Stack](#tech-stack)
  - [Architecture](#architecture)
  - [Project Structure](#project-structure)
- [Google Sheets Integration](#google-sheets-integration)
  - [Sheets Structure](#sheets-structure)
  - [Two‑Way Sync Rules](#two-way-sync-rules)
- [Volunteer Ranks & Benefits](#volunteer-ranks--benefits)
- [Performance Optimizations](#performance-optimizations)
- [Installation & Setup](#installation--setup)
  - [1. Clone & Open the Project](#1-clone--open-the-project)
  - [2. Configure Google Cloud & Service Account](#2-configure-google-cloud--service-account)
  - [3. Create and Configure the Spreadsheet](#3-create-and-configure-the-spreadsheet)
  - [4. Configure the App](#4-configure-the-app)
  - [5. Run on Tablet](#5-run-on-tablet)
- [Usage Guide for Staff](#usage-guide-for-staff)
- [Troubleshooting](#troubleshooting)
- [For Developers](#for-developers)
- [Additional Documentation](#additional-documentation)

---

## Overview

The **Event Manager** app is designed for **tablet use in the field during events**. It centralizes:

- **Guest lists** for Groove and Le Terreau  
- **Volunteers, their shifts, and historical activity**  
- **Automatic rank and benefits calculation**  
- **Two‑way synchronization** with a central **Google Sheets** document to keep multiple tablets and staff aligned.

The app is optimized for **landscape tablets** and aims to be **fast, robust, and simple to use** during real events.

---

## Main Features

### Guest List Management

- **Separate guest lists per venue**: Groove and Le Terreau.
- **Add / edit / delete guests** with:
  - Name
  - Number of invitations
  - Venue
  - Notes
  - Flag for **volunteer benefit guests** (protected from deletion).
- **Invitation quotas** per guest.
- **Automatic sync** to Google Sheets after any change.
- **Manual sync button** for explicit updates before / during events.

### Volunteer Management

- **Full volunteer profiles**:
  - Name and last name abbreviation
  - Contact (email, phone)
  - Date of birth
  - Rank
  - Active / inactive status
- **Job and shift tracking** linked to volunteers.
- **Automatic rank calculation** based on activity and committee roles.
- **Job history** is visible so staff can see a volunteer’s past contributions.

### Shift Tracking

- Record **shifts** with:
  - Linked volunteer
  - Job type
  - Venue
  - Date
  - Shift time (before or after midnight)
  - Notes and completion status
- Shift data is used to:
  - Calculate **ranks**
  - Derive **benefits**
  - Power **statistics / dashboards**

### Volunteer Ranking & Benefits System

Ranks are calculated from **shift history** and **roles**. Core ranks:

- **NOVA**: Shift **before midnight**
- **ÉTOILE**: Shift **after midnight**
- **GALAXIE**: **3+ shifts/meetings per month**
- **ORION**: Committee / coordination roles (manual assignment)
- **VÉTÉRAN**: Former ORION volunteers

See [Volunteer Ranks & Benefits](#volunteer-ranks--benefits) for exact benefits.

### Benefits Overview Screen

- **Real-time calculation** of benefits from rank and recent activity.
- **Clear descriptions** of what each volunteer is entitled to:
  - Free entries
  - Guest invitations
  - Drink tokens
  - Bar discounts
- Designed so **door and bar staff** can quickly verify benefits during events.

### Google Sheets Two‑Way Sync

- Uses a **service account** and **Google Sheets API v4**.
- **Backup mode (Local → Sheets)**:
  - Every local change triggers a **full dataset backup for that feature**.
  - Guarantees Sheets always has a complete up‑to‑date backup.
- **Sync mode (Sheets → Local)**:
  - Manual or page‑change sync pulls the latest data **from Sheets to the tablet**, replacing local data.
  - Ensures all tablets and staff see a consistent picture.

### Tablet-Optimized UI

- Designed and tested for **Android tablets** in **landscape**:
  - Large touch targets
  - Clear typography and spacing
  - Material Design 3 components
  - Layouts tuned for **multi-column** tablet views
- Ideal for **front-of-house** staff and volunteers during events.

---

## App Screens & Workflows

### Guests Screen

- **Venue filter** (Groove / Le Terreau) using chips or tabs.
- **Add Guest** button to quickly add new entries.
- **Edit / delete icons** on each guest row.
- Guests linked to **volunteer benefits** cannot be deleted, to preserve benefit history.
- Data is **immediately synced** and visible to other devices via Sheets.

### Volunteers Screen

- Displays all volunteers with:
  - Name & abbreviation
  - Rank
  - Active status
- From here you can:
  - Add / edit volunteer profiles
  - View their **job history**
  - See **current rank** and implied benefits

### Shifts & Shift Types Screens

- **Shifts Screen**:
  - Add new shifts for volunteers  
  - Set venue, date, time (before/after midnight), and job type
  - Mark completion and add notes

- **Shift Types Screen**:
  - Configure available **job types**
  - Define:
    - Status (active/inactive)
    - Whether it counts as ORION-type work
    - Whether it requires time
    - Description

### Volunteer Guest List

- Dedicated logic + sheet for **volunteers who earn guest-list spots**.
- Automatically generates **guest list entries** for volunteers based on rank and rules.
- Ensures benefits are **accurately reflected at the door**.

### Venues Management

- **Venues Sheet** in Google Sheets:
  - Name
  - Description
  - Status
  - Last Modified
- In-app tools to manage and sync venues, ensuring guest lists and shifts always refer to valid venues.

### Statistics / Dashboard

- Stats screen (when enabled) provides:
  - **Volunteer guest list data**
  - **Volunteer invites statistics**
  - **Venue shift distribution**
  - **Total shift counts**
- Expensive calculations are offloaded to background threads to **avoid UI freezes**.

### Settings & Sync Controls

- **Settings tab** includes:
  - Spreadsheet ID configuration
  - Sheet names configuration (Guests, Volunteers, Shifts, Shift Types, Volunteer Guest List, Venues)
  - Service account key configuration / status
  - **Test Connection** action
  - Optional **Debug Mode** for detailed logs
- **Sync controls**:
  - Manual sync
  - Backup triggers
  - Data structure validation / auto-fix

---

## Technical Overview

### Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVVM + Repository pattern
- **Database**: Room (SQLite)
- **Dependency Injection**: Hilt (or DI modules, depending on branch)
- **Async**: Coroutines + Flow / StateFlow
- **Navigation**: Navigation Compose
- **Cloud Integration**: Google Sheets API v4 using a service account

### Architecture

- **MVVM**:
  - ViewModels manage UI state and handle sync logic.
  - Repositories abstract the database and network layers.
- **Room**:
  - Local persistence with proper indices for performance.
- **TwoWaySyncSystem**:
  - `TwoWaySyncService`, `SyncManager`, `DataStructureValidator`, and `EventManagerViewModel` coordinate all sync operations and validations.
- **StateFlow / Compose**:
  - UI reacts to changes via `StateFlow` for sync status, errors, and data updates.

### Project Structure

app/
  src/main/java/com/eventmanager/app/
    data/
      dao/          # Room DAOs
      database/     # Database configuration
      models/       # Data models (Guest, Volunteer, Job, JobType, Venue, etc.)
      remote/       # Google Sheets service(s)
      repository/   # Repository implementations
      sync/         # Two-way sync system & configuration
    di/             # Dependency injection (Hilt/modules)
    ui/
      components/   # Reusable Compose components
      screens/      # Screen-level Composables
      theme/        # Material 3 theme
      viewmodel/    # ViewModels (EventManagerViewModel, etc.)
    MainActivity.kt # App entry point
