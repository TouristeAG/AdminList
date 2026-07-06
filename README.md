# Event Manager

Android application for **guest lists**, **volunteers**, **shifts/jobs**, and **volunteer benefits** for live music venues (notably **Groove** and **Le Terreau**). It is optimized for **tablets** and also supports **phones** (responsive layouts, scrollable navigation on small screens).

Data is stored locally in **Room (SQLite)** and kept in sync with **Google Sheets** using a **service account**.

---

## What you can do with the app

### First launch: setup wizard

On a fresh install, a **setup wizard** walks through language, theme, color profile, resolution scaling, Google Sheets connection (spreadsheet ID + service account key), and related options. Until setup is completed, the main app stays behind this flow.

### Welcome screen: two modes

After setup, every launch starts on a **welcome** screen:

1. **Admin (full manager)** — Authenticate (NFC or QR using a guest or volunteer marked as admin), then use the full back-office UI.
2. **Billeterie (door / ticketing)** — Lightweight mode for check-in: home stats, guest list focused on the night, barcode/QR scanner, and billeterie-specific settings. Intended for devices at the door without full admin access.

The **admin session** returns to the welcome screen after **idle timeout** (no touches for several minutes) or after the **screen was turned off** while admin was active, so shared tablets do not stay logged in indefinitely.

### Admin: main tabs

| Area | Purpose |
|------|--------|
| **Dashboard** | Clock, headline counts (guests, invites, volunteers, derived “total people”), optional **people counter**, optional **statistics graphs**, optional seasonal overlays. |
| **Guests** | Permanent guest list per **venue** (unlimited custom venues via the Venues sheet and in-app venue management). Invitation counts, notes, volunteer-benefit rows, **NanoID** for sync, optional **NFC UID**, admin flag, **temporary guests** for single events (artist, date, contact, batch add). |
| **Volunteers** | Profiles (including gender), active flag, optional NFC, jobs history entry points. |
| **Shifts** | Jobs linked to volunteers: job type name, venue, date, **shift time** (evening profited vs non-profited for classic shifts), notes, **“Entries left”** for consumable future free entries (see benefits). |
| **Benefits** | Live calculation of perks per volunteer, search/filter, dashboard-style aggregates (active benefits, bar discount, free entry, future entries, rank distribution). |
| **Settings** | Sync (spreadsheet ID, sheet names, auto-sync, manual sync), appearance (theme, color, animations, app icon aliases), localization (language, date/time formats, **venue day offset hours** for “when does the event day roll over”), email templates for QR/wallet passes, optional update manifest URLs, **job type management**, **venue management**, debug logging, etc. |

### Floating tools (admin)

- **QR scanner** (FAB): scan codes to match guests or volunteers and open detail / benefit panels.
- **Sync status** widget: quick view of sync state; errors can open dedicated dialogs (including device clock skew hints when relevant).

---

## Volunteer benefits (current system)

Benefits are **not** hard-coded only to “Nova = before midnight / Étoile = after midnight”. They are driven by **job type configuration** (`JobTypeConfig` in Room + the **`JobTypes`** sheet in Google Sheets).

### Job types (configuration)

Each named job type can be toggled active/inactive and defines:

- **Shift job** (`isShiftJob`) — Counts toward **NOVA** eligibility and toward **Galaxie** monthly activity when the job falls in the current month (with rules below).
- **Orion job** (`isOrionJob`) — Counts toward the **Orion** mandate window and later **Veteran** eligibility.
- **Requires shift time** — For **DEFAULT_SHIFT** Nova types, the app still distinguishes **evening profited** vs **evening non-profited** (stored in Sheets with human-readable labels).
- **Benefit system** — `STELLAR` (standard rank logic + Nova packages) or `MANUAL` (custom duration perks + optional pooled **future single-use entries**).
- **Nova job type** (for shift jobs) — Selects which **NOVA perk package** applies, e.g. default shift, meeting, photographer/videographer, graphic designer (event vs association-wide).
- **Manual rewards** (if `MANUAL`) — Duration in days, free drinks, bar %, free entry, invites, notes, and optional **future single-use entries** + invites per entry.

### Ranks and stacking (summary)

- **NOVA** — One unified **NOVA** benefit built from **all** shift-type jobs (the old **Étoile** split is no longer used for new logic; the enum remains for backward compatibility only).  
  - **Same-night** perks (free entry + friend + bar discount + drinks) apply only on the **calendar event day** of a qualifying shift (respecting the configured **date-change offset**).  
  - **Meetings** add **off-event** drinks (Orion volunteers do **not** get meeting perks).  
  - **Graphic designer (association)** adds a larger off-event drink allowance.  
  - **Future free entries** are pooled from jobs whose types grant them, using per-job **“Entries left”** in Sheets (supports `n left (+X inv.)` format).
- **GALAXIE** — **Three or more** qualifying contributions in the **current month** (shift jobs, including meetings in the count). **Orion** volunteers: **meetings** do not count toward the “3+” threshold. Benefit: free entry, bar discount, bonus drink, through month end (see in-app descriptions).
- **ORION** — Active during the **first year** from the first Orion job; then the volunteer may enter **Veteran** for the following year. Orion perks include free entry, friend invite, bar discount, guest list access, and flagged **extraordinary** perks (see live descriptions in the app).
- **VETERAN** — **Second year** after Orion start: extended perks (see app text).
- **SPECIAL** — **Manual** reward jobs: time-limited perks from `MANUAL` job types and/or redeemable **future event entries** even after the duration window ends.

The app may show **multiple active benefit rows** per person (e.g. NOVA + Galaxie + Orion) and an **aggregated** summary for quick scanning.

---

## Google Sheets integration

### Authentication

The app uses a **Google Cloud service account** JSON key (uploaded via setup or Settings, not end-user OAuth). Share the spreadsheet with the service account email (Editor) so the API can read/write.

### Spreadsheet ID

Set in **Settings** (persisted) or default `GoogleSheetsConfig.SPREADSHEET_ID` for development. The built-in **connection test** refuses a blank or placeholder ID.

### Sheet names

Most tabs are **configurable** in Settings (defaults in `GoogleSheetsConfig`). One important exception: **job type sync reads and writes a tab literally named `JobTypes`**. Ensure a sheet with that exact name exists with the header row below.

### Expected structure (headers)

The app can **repair/normalize** some headers; align new spreadsheets with what the sync expects:

| Tab | Role | Header columns (row 1) |
|-----|------|-------------------------|
| **Guest List** (name configurable) | Permanent guests | Name, Email, Phone, Invitations, Venue, Notes, Volunteer Benefit, Last Modified, NFC UID, ID, Admin |
| **Volunteer Guest List** | Benefit-linked guest rows | Name, Last Name Abbreviation, Invitations, Venue, Notes, Volunteer Benefit, Last Modified, NFC UID |
| **Volunteers** | Volunteer roster | ID, Name, Abbreviation, Email, Phone, Date of Birth, Gender, Rank, Active, Last Modified, NFC UID, Admin |
| **Shifts** (jobs) | Job history | Volunteer ID, Job Type, Venue, Date, Shift Time, Notes, Last Modified, **Entries left** |
| **`JobTypes`** (fixed name) | Benefit-driving config | Name, Status, Shift Type, Orion Type, Requires Time, **Benefit System**, **Manual Rewards**, Description, Last Modified, **Nova Job Type** |
| **Venues** | Venue list | Name, Description, Status, Last Modified |
| **Temp Guest List** | One-off event guests | Modification Date, Event Date, Artist/Group, Artist Contact Phone, Guest Name, Comment, ID |

**Shift time** values in the jobs sheet use labels such as **Evening shift (profited)** / **Evening shift (non-profited)**; legacy enum-style values may be migrated on sync.

**Entries left** supports forms like `2 left (+1 inv.)`, plain `2 left`, and legacy Yes/No style cells — see `parseJobBenefitFutureEntriesFromSheets` in code if you need exact parsing rules.

---

## Technical stack

- **Language:** Kotlin  
- **UI:** Jetpack Compose, Material 3  
- **Architecture:** MVVM, repository, Room, Hilt where applicable  
- **Networking:** Google Sheets API v4 (service account)  
- **Concurrency:** Coroutines + Flow  
- **IDs:** NanoIDs for volunteers and cross-device guest matching where applicable  

---

## Project layout (high level)

```
app/src/main/java/com/eventmanager/app/
├── data/
│   ├── dao/, database/, models/, repository/, sync/, utils/
├── di/
├── ui/
│   ├── components/    # Reusable UI (graphs, sync, scanner, etc.)
│   ├── screens/       # Feature screens (guests, volunteers, billeterie, …)
│   ├── theme/
│   └── viewmodel/
└── MainActivity.kt    # Root composition: wizard, welcome, admin, billeterie
```

---

## Build and run

1. Open the project in **Android Studio** (current Gradle / AGP as in repo).  
2. **Sync Gradle**.  
3. Run on a **tablet** or **phone** emulator/device.  
4. Complete **setup** (or configure Settings): spreadsheet ID + service account JSON + required sheet tabs.

No `google-services.json` is required for Sheets in this project path; credentials are the **service account** file the app stores after upload.

---

## Operational tips

- After changing **job types** or **venues**, use **Job type management** / **Venue management** from Settings or sync flows so Sheets and devices stay aligned.  
- If benefit totals look wrong near midnight, check **date change offset hours** in Settings (venue “day” boundary).  
- For door operations, prefer **Billeterie** mode on a dedicated device; use **Admin** only when staff need full CRUD and configuration.

---

## Desktop app (Kotlin Multiplatform)

A **desktop** build (`desktopApp`) shares the same Room database, Google Sheets sync, and most admin workflows as Android. Supported on **macOS**, **Windows**, and **Linux**.

Run locally (any desktop OS):

```bash
./gradlew :desktopApp:run
```

Package release installers (must be built **on the target OS** — Compose Desktop does not cross-compile):

```bash
./gradlew :desktopApp:packageReleaseDmg       # macOS → .dmg
./gradlew :desktopApp:packageReleaseMsi     # Windows → .msi
./gradlew :desktopApp:packageReleaseExe     # Windows → .exe
./gradlew :desktopApp:packageReleaseDeb     # Linux → .deb
./gradlew :desktopApp:packageReleaseAppImage # Linux → AppImage
```

Output binaries are under `desktopApp/build/compose/binaries/main-release/`.

### Linux build prerequisites

On Ubuntu/Debian before packaging:

```bash
sudo apt-get update
sudo apt-get install -y fakeroot binutils libfuse2
```

JDK 17+ is required. Linux package formats (`.deb`, AppImage) are enabled automatically when Gradle runs on Linux. A GitHub Actions workflow (`.github/workflows/desktop-linux.yml`) builds both artifacts when a GitHub Release is published.

### Linux runtime setup (door / hardware)

| Feature | Requirement |
|---------|-------------|
| USB NFC reader (ACR122U) | `pcscd` running, ACS driver, udev rules |
| BLE NFC reader (ACR1255U-J1) | Pair in system Bluetooth; reader visible in PC/SC |
| BLE reader discovery list | `bluez` + `bluetoothctl` in PATH |
| Webcam QR scan | V4L2/PipeWire; user in `video` group or portal permission |
| Biometric admin login | Polkit + enrolled fingerprint (`fprintd`) |

### Release checklist (all platforms)

1. Bump `packageVersion` in `desktopApp/build.gradle.kts` and `version.json`.
2. Build and attach artifacts to the GitHub Release:
   - APK (Android)
   - DMG (macOS)
   - MSI + EXE (Windows)
   - DEB + AppImage (Linux CI or local Linux build)
3. Update all download URLs in `version.json` and publish to the AdminList manifest repo (`TouristeAG/AdminList/main/version.json`).
4. Smoke-test in-app update on each platform.

### Platform differences (by design)

| Feature | Android | Desktop |
|---------|---------|---------|
| NFC | Built-in phone NFC + optional BLE ACS reader | USB **PC/SC** card reader (+ BLE via PC/SC after pairing) |
| QR scan | Camera preview | Webcam (ZXing) or file picker |
| Admin auth | NFC, QR, optional biometrics | NFC (PC/SC), QR, optional biometrics (Touch ID / Windows Hello / Linux Polkit) |
| BLE external reader | Supported | Supported via PC/SC + system Bluetooth pairing |
| Dynamic app icon | 12 launcher icons | Not supported |
| Resolution scale slider | Phone/tablet layout | Not used |
| Seasonal animations / haptics | Optional | Not included |
| Embedded WebView | In-app browser | Opens system browser |
| Dashboard charts | Full Canvas graphs | Summary stats + XLSX/JPG export |
| Debug logs UI | Settings → Developer | Settings → Developer (file logs in app data dir) |
| In-app updates | APK download | Platform installer (DMG / MSI / EXE / DEB / AppImage) |

Keyboard shortcuts on desktop: **Cmd+,** or **Ctrl+,** (Settings), **Cmd+F** or **Ctrl+F** (focus guest search), **Esc** (dismiss overlays).

---

## Contributing

When extending behavior:

- Preserve **Sheets compatibility** (column order, `JobTypes` tab name unless code is updated everywhere).  
- Keep **benefit math** in `BenefitCalculator` / models testable and consistent with documented sheet columns.  
- Respect **tablet and phone** layouts and touch targets.

For product or policy questions (e.g. exact CHF wallet amounts shown in Orion copy), treat the **in-app strings and `Benefit` descriptions** as the source of truth alongside this file.
