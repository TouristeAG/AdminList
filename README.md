# NoctuList

**Guest lists, volunteers, door check-in, and internal POS** for live music venues — built as a Kotlin Multiplatform app for Android tablets/phones and desktop (macOS, Windows, Linux).

Data lives locally in **Room (SQLite)** and stays in sync across devices via **Google Sheets** (service account).

[![Version](https://img.shields.io/badge/version-1.1.1-blue)](https://github.com/TouristeAG/NoctuList/releases)
[![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20macOS%20%7C%20Windows%20%7C%20Linux-brightgreen)](https://github.com/TouristeAG/NoctuList/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/multiplatform.html)
[![Compose](https://img.shields.io/badge/UI-Compose%20Multiplatform-4285F4)](https://www.jetbrains.com/compose-multiplatform/)

---

## Features

- **Guest list management** — permanent guests, temporary event guests, invitations, notes, optional NFC UIDs
- **Volunteer roster & shifts** — profiles, jobs history, configurable job types
- **Benefit system** — ranks and perks (Nova, Galaxie, Orion, Veteran, manual rewards) driven by job-type configuration
- **Billeterie mode** — lightweight door check-in with QR/barcode scanning and night-focused guest views
- **Internal POS** — sell merch / bar / entry against guest or volunteer account credit, with optional cash remainder
- **People counter & announcements** — venue-scoped headcount and staff messages synced through Sheets
- **Multi-device sync** — Google Sheets as the shared backend (no custom server required)
- **Appearance & localization** — themes, color profiles, background animations, 7 languages
- **Hardware support** — NFC (built-in or USB/BLE readers), QR scanning, optional biometric admin login

---

## Modes

Every launch starts on a welcome screen with three entry points:

| Mode | Who it's for | Access |
|------|----------------|--------|
| **Admin** | Full back-office (guests, volunteers, shifts, benefits, settings) | Auth via NFC, QR, or enrolled biometrics |
| **Billeterie** | Door / ticketing tablets | No full admin session — check-in focused |
| **Internal POS** | Bar / merch selling | Opens from welcome; does not require admin |

Admin sessions end after idle timeout or when the screen turns off, so shared tablets don't stay logged in.

---

## Download

Prebuilt binaries are on the [Releases](https://github.com/TouristeAG/NoctuList/releases) page:

| Platform | Artifact |
|----------|----------|
| Android | `.apk` |
| macOS | `.dmg` |
| Windows | `.msi` / `.exe` |
| Linux | `.deb` / `.AppImage` |

In-app updates check `version.json` against the published release manifest.

---

## Quick start (end users)

1. Install the app for your platform.
2. On first launch, complete the **setup wizard** (language, theme, Google Sheets spreadsheet ID + service account key).
3. Share your spreadsheet with the service account email (**Editor** access).
4. Choose **Admin**, **Billeterie**, or **POS** from the welcome screen.

Detailed sheet structure and sync notes are in [Google Sheets setup](#google-sheets-sync) below.

---

## Tech stack

| Layer | Choice |
|-------|--------|
| Language | Kotlin Multiplatform |
| UI | Compose Multiplatform + Material 3 |
| Local data | Room (bundled SQLite) |
| Sync | Google Sheets API v4 (service account) |
| Architecture | MVVM + repositories + Coroutines / Flow |
| Hosts | `app/` (Android), `desktopApp/` (JVM desktop) |
| Shared code | `shared/` (`commonMain`, `androidMain`, `desktopMain`) |

**Requirements:** JDK 17+, Android Studio (or SDK) for Android builds, Gradle wrapper included in the repo.

---

## Project layout

```
NoctuList/
├── app/                 # Android application host
├── desktopApp/          # Compose Desktop host + packaging
├── shared/              # KMP UI, Room, Sheets sync, POS, benefits
├── scripts/             # Tooling (e.g. i18n parity check)
├── version.json         # Version + release download URLs
└── .github/workflows/   # CI (Linux desktop packages, i18n check)
```

---

## Build & run

### Android

1. Open the project in **Android Studio**.
2. Sync Gradle, then run on a device or emulator (tablets preferred).
3. Complete the in-app setup wizard (or Settings) with a spreadsheet ID and service account JSON.

```bash
./gradlew :app:assembleDebug
```

### Desktop

```bash
./gradlew :desktopApp:run
```

### Package installers

Compose Desktop packages must be built **on the target OS** (no cross-compilation):

```bash
./gradlew :desktopApp:packageReleaseDmg        # macOS
./gradlew :desktopApp:packageReleaseMsi        # Windows
./gradlew :desktopApp:packageReleaseExe        # Windows
./gradlew :desktopApp:packageReleaseDeb        # Linux
./gradlew :desktopApp:packageReleaseAppImage   # Linux
```

Outputs land under `desktopApp/build/compose/binaries/main-release/`.

**Linux packaging prerequisites** (Ubuntu/Debian):

```bash
sudo apt-get update
sudo apt-get install -y fakeroot binutils libfuse2
```

Linux `.deb` / AppImage formats are enabled when Gradle runs on Linux. Publishing a GitHub Release also triggers `.github/workflows/desktop-linux.yml`.

### Release checklist

1. Bump version in `version.json` (and keep `desktopApp` version aligned — it reads from that file).
2. Build platform artifacts (APK, DMG, MSI/EXE, DEB/AppImage).
3. Attach them to the GitHub Release and update download URLs in `version.json`.
4. Smoke-test in-app update on each platform.

---

## Google Sheets sync

NoctuList uses a **Google Cloud service account** (JSON key uploaded in the setup wizard or Settings — not end-user OAuth).

**Minimal setup:**

1. Enable the **Google Sheets API** in a Google Cloud project.
2. Create a service account and download a JSON key.
3. Create a spreadsheet and share it with the service account email as **Editor**.
4. Paste the spreadsheet ID and upload the key in the app.
5. Ensure the expected tabs exist (names are mostly configurable in Settings).

### Default tabs

| Tab | Purpose |
|-----|---------|
| Guest List | Permanent guests |
| Volunteer Guest List | Benefit-linked guest rows |
| Volunteers | Volunteer roster |
| Shifts | Jobs history |
| **JobTypes** | Job type / benefit / wallet credit config *(fixed name)* |
| Venues | Venues, people counter, announcements |
| Temp Guest List | One-off event guests |
| Sales | POS catalog |
| Transfers | Wallet / POS ledger |

The app can repair or normalize many headers on sync. The **`JobTypes`** tab name is fixed in code — that sheet must be named exactly `JobTypes`.

> Older notes in `GOOGLE_SHEETS_SETUP.md` may be outdated (credentials are uploaded in-app, not via bundled assets). Prefer the in-app connection test and the table above.

---

## Platform support

| Feature | Android | Desktop |
|---------|---------|---------|
| NFC | Built-in + optional BLE ACS reader | USB PC/SC (preferred); BLE on Windows via ACS BT PC/SC + Management Tool |
| QR scan | Camera | Webcam or file picker |
| Biometric admin login | Fingerprint / Face unlock | Touch ID, Windows Hello, Linux Polkit |
| Dynamic launcher icons | Yes | — |
| POS + PDF accounting reports | Yes | Yes |
| In-app updates | APK | Platform installer |
| Charts / graph export | Canvas graphs | Summary + XLSX/JPG export |

**Desktop keyboard shortcuts:** `Ctrl+,` / `Cmd+,` (Settings), `Ctrl+F` / `Cmd+F` (guest search), `Esc` (dismiss overlays).

### External NFC readers (desktop)

Desktop NFC uses **PC/SC only** (no raw USB / GATT stack like Android). Windows ACS readers need the correct OS drivers for UID reads to work.

#### USB (recommended on Windows)

Examples: ACR122U, ACR1255U-J1 with the physical switch set to **USB**.

1. Install the **ACS USB PC/SC** driver from the [ACR1255U-J1 driver page](https://www.acs.com.hk/en/driver/340/acr1255u-j1-usb-nfc-reader-with-bluetooth-interface/) (or [ACR122U](https://www.acs.com.hk/en/driver/3/acr122u-usb-nfc-reader/)). Prefer ACS over a bare Microsoft Usbccid binding.
2. Confirm a **PICC** (contactless) interface in Device Manager / **Settings → External reader → List PC/SC** — do not use an ICC-only entry for NFC.
3. If the reader beeps but **Test USB** shows no UID and Escape is blocked under Microsoft CCID: use **Enable Escape Command (admin)** in Settings (sets `EscapeCommandEnable=1`), then unplug/replug (or reboot).
4. Hold a card and run **Test USB reader** — expect a real `UID: …` line.

On macOS/Linux: ACS USB PC/SC (or `pcscd` + CCID) and the same List / Test flow.

#### Bluetooth ACR1255U-J1 (Windows only)

Windows Bluetooth pairing alone is **not** enough (connect/disconnect loops are common without the ACS tool).

1. Install the **ACS Bluetooth PC/SC** MSI from the same ACS product driver page.
2. Switch the reader to Bluetooth and pair it in Windows Settings.
3. Open **ACS Bluetooth Device Management Tool** and **install** the reader so it is registered with PC/SC.
4. Confirm a **BLE** entry under **List PC/SC**, then select it in the app.

**Product recommendation:** prefer **USB mode** for door/POS on Windows. macOS/Linux have no ACS BLE PC/SC driver — use USB.

---

## Localization

UI strings ship in:

- English, French, Spanish, Hindi, Latin  
- Chinese (Simplified), Chinese (Traditional)

New user-facing strings must be added to all locale `strings.xml` files. CI runs `scripts/check-i18n.py` on string changes.

---

## Contributing

- Keep **Sheets column contracts** compatible unless you update sync and docs together (`JobTypes` name is especially sensitive).
- Keep **benefit** and **wallet / POS ledger** rules consistent (`BenefitCalculator`, `AccountTransfer`, Transfers sheet).
- Prefer layouts that work on **tablet and phone** touch targets.
- Add strings in **all** locales; run the i18n check locally if you edit copy.

Product wording for perk amounts and benefit copy is owned by **in-app strings** — treat those as the source of truth when docs and UI diverge.

---

## License & credits

Built for venue operations (notably Groove / Le Terreau) by **Collectif Nocturne**.

See [Releases](https://github.com/TouristeAG/NoctuList/releases) for changelogs and binaries.
