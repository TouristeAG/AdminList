# Changelog

All notable changes to NoctuList are documented here. Version numbers follow [Semantic Versioning](https://semver.org/). Build metadata lives in [`version.json`](version.json) (single source of truth for Android, desktop, and in-app update checks).

---

## 2.1.0 — 2026-09-04

Bug-fix and UI polish release on top of the 2.0 Firebase stack.

### UI & billetterie

- **Wide billetterie / welcome layouts** for large tablets and desktop screens.
- Clearer **announcements** UI and settings; improved **people counter** layout and priority controls.
- Smoother **space transitions**, startup splash, and typography (Nunito).
- Settings and admin auth polish; desktop guest-list and settings refinements.

### Firebase & setup

- More reliable **org bootstrap / join** flows and clearer permission-denied messaging.
- Updated **Firestore rules** guidance and in-app tutorial alignment.
- Local admin grant and related security edge cases hardened.
- **Firestore rules version 5** — account writes no longer fail on a brand-new organization. The
  rules read `institutionSettings/purchase_credit_buffer` to validate balances, and on a database
  where that document does not exist yet the lookup denied every account write. An unset or
  malformed buffer now means "no overdraft". **Republish `firebase/firestore.rules`.**
- **Sheets → Firebase migration** no longer tries to claim org admin unconditionally. It probes
  membership first, so migrating into an organization that another account already set up reports
  what to do instead of an opaque `PERMISSION_DENIED`, and it no longer rotates the invitation
  code that team devices already hold.
- Institution settings are pushed **before** account balances during migration, so the buffer the
  rules validate against is already in place.
- Documented that publishing rules on a Workspace-owned project requires Owner, Editor, or
  *Firebase Rules Admin* — without it the database stays on the deny-all production default.
- **Organization IDs are normalized as you type.** Entering an institution name such as
  "Collectif Nocturne" produced an ID that Firestore cannot use as a path segment: the setup
  wizard's *Continue* button stayed greyed out with nothing explaining why. Spaces now become
  hyphens, accents are folded to ASCII, and an invalid ID shows the expected format.
- The **join step of the setup wizard** lists what is still missing (organization, project keys,
  Google Sign-In credentials, invitation code) instead of silently disabling *Continue*.
- **A failed migration can no longer strand a device.** The backend switch was announced on the
  source database before the target one, so a refused write on the target rolled the local backend
  back while the announcement already pointed elsewhere — leaving a non-dismissable "connect to the
  new database" dialog that could never be satisfied. The target is announced first, the local
  switch is never reverted afterwards, and failing to notify the old backend is now reported as a
  partial success rather than a failure.
- The migration wizard shows an explicit **"Migration finished — close"** button on success, so
  admins no longer dismiss a completed migration through *Cancel*.
- The follow-migration screen now leads with the same **QR code / invitation code** flow as
  "join an organization", validates a scanned code, and offers a **device reset** as a last resort.
- **Sheets → Firebase migration** accepts **one destination organization only**. Extra orgs are
  still added later in Admin → Firebase sync; multiple IDs during migration made the target ambiguous.
- **Security fix — first-admin setup could be triggered on an existing, non-empty organization.**
  At startup, an offline or misconfigured Firestore SDK was reported as a successful sync ("local
  Room remains source of truth"), which the first-admin gate then read as proof the remote
  organization was empty whenever the local database happened to be empty too. That opened the
  passwordless "create the first admin" wizard on a device that simply never reached the server,
  letting anyone at that device grant admin rights to themselves or to any guest/volunteer. The
  gate now requires a genuine server-confirmed Firestore response before trusting local emptiness;
  an unreachable server is treated the same as a failed sync — no setup offered.

### POS

- **Permanent guests can be granted a bar discount.** Adding or editing a permanent guest now offers
  a *Bar Discount (%)* field, defaulting to 0. When it is above 0, the POS shows the same discount
  badge next to the guest profile as it does for volunteers, and the sale applies it identically:
  account credit is always debited at full price and the percentage only reduces the cash/card
  remainder of discount-eligible lines. The guest profile panel lists the granted percentage.
  A guest at 0% looks and behaves exactly as before.
  The field is **Firebase-only** — like profile photos it is never written to Google Sheets, and it
  stays hidden and inactive on the Sheets backend.
- **The product grid now fills the screen.** Column count was picked by maximising cell area, which
  on any catalogue too long to fit on screen always resolved to a single very wide column — a
  desktop pane with room for four or five products per row showed one. Columns are now derived from
  the available width and the device class (a phone pane stays at two, a tablet reaches five, a wide
  desktop up to nine), and tile height still stretches to fill the viewport when everything fits.
- **Product sub-categories.** A product can now be filed under a sub-category of its general
  category — *Alcohol*, *Deposits* or *Non-alcoholic* inside *Bar*, for example. Sub-categories are
  created and deleted by hand from the sales-item editor, and the POS shows them as a horizontal
  filter bar above the product grid, to the right of the general category rail.
  The bar only appears for sub-categories that actually contain a product, and disappears entirely
  when none are defined or none are assigned — in that case the POS behaves exactly as before.
  Deleting a sub-category also clears it from every product that used it.
  **Firebase-only**: the catalogue is an institution setting and the per-product link is a Firestore
  field, so the Google Sheets product contract is unchanged and the feature stays hidden on Sheets.

### Performance & reliability

- Hot-path ViewModel and profile-photo cache optimizations (fewer redundant recompositions / reloads).
- Desktop Firestore realtime capability and logging improvements.
- Assorted bug fixes across sync, date/time, and POS credit paths.

### Upgrade notes

- **Version code:** 27 (`2.1.0`). Minimum supported code is **26** (2.0.0) — devices below 2.0.0 are prompted to update; 2.0.0 gets an optional upgrade.
- **Publishing:** tag the release `2.1.0`, attach platform artifacts, then update the public update manifest (`version.json` on the AdminList mirror if used).

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
