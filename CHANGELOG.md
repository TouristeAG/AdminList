# Changelog

All notable changes to NoctuList are documented here. Version numbers follow [Semantic Versioning](https://semver.org/). Build metadata lives in [`version.json`](version.json) (single source of truth for Android, desktop, and in-app update checks).

---

## 2.0.0 — 2026-09-01

Major release: realtime Firebase sync, richer POS accounting, and a refreshed admin experience while keeping Google Sheets as a supported backend.

### Sync & backends

- **Firebase / Firestore backend** — realtime org sync with offline pending writes, snapshot listeners, and encrypted sensitive fields.
- **Backend choice** — each institution can run on **Firebase** (recommended for new setups) or **Google Sheets** (legacy / spreadsheet workflow).
- **In-app migration** — migrate from Sheets to Firebase with guided setup, Firestore rules clipboard, and optional Sheets mirror export.
- **Multi-organization mode** — configure and switch between several Firebase orgs on one project; org color tags and “all orgs” view for cross-venue staff.
- **Institution settings sync** — shared toggles (e.g. profile photos) propagate across org devices via Firestore.

### Profile photos

- Optional **profile photos** for guests and volunteers, stored in **Firebase Storage** when enabled.
- Upload, replace, and remove from detail panels; local image cache for fast UI.
- Storage rules bundled in-app with setup tutorial (`FIREBASE_SETUP.md`).

### Account credits & POS

- **Account ledger** — manual top-ups/debits, shift credits, POS sales, and reversals synced across devices.
- **Internal POS** — sell against guest/volunteer credit with cash remainder and rank-based bar discounts.
- **POS accounting reports** — evening or date-range PDF exports (sales, transfers, manual adjustments).
- Redesigned **manual account adjustment** dialog (quick amounts, balance preview, admin verification unchanged).

### Billeterie & door

- Improved billetterie / scanner flows on desktop and shared UI.
- Volunteer benefit check-in, QR/NFC admin auth, and people counter with device priority.

### UI & admin

- Settings reorganization: Firebase sync section, backend migration wizard, factory reset safeguards.
- Dashboard and stats graph export improvements (tables, category layouts).
- Volunteer inactive cleanup synced correctly with Firebase and Sheets.
- Desktop admin nav (bottom / left / right), themes, layout scale, 7 languages.

### Desktop & hardware

- Native file dialogs, improved **BLE/USB NFC** reader status and SoftDevice support (carried forward from 1.1.x).
- Linux `.deb` / `.AppImage`, Windows `.msi` / `.exe`, macOS `.dmg` packaging from `version.json`.

### Upgrade notes

- **Version code:** 26 (`2.0.0`). Minimum supported code is **25** (1.1.1) — devices below 1.1.1 are prompted to update; 1.1.1 gets an optional upgrade.
- **New Firebase orgs:** follow the in-app tutorial or [FIREBASE_SETUP.md](FIREBASE_SETUP.md). Sheets-only institutions can stay on Sheets or migrate when ready.
- **Publishing:** tag the release `2.0.0`, attach platform artifacts, then update the public update manifest (`version.json` on the release branch / AdminList mirror if used).

---

## 1.1.1

- Faster and more reliable desktop BLE NFC SoftDevice reading.
- Clearer NFC reader status and native file dialogs.
- Various desktop fixes.

## 1.1.0

- Initial public desktop release lineage; guest/volunteer management with Google Sheets sync.
