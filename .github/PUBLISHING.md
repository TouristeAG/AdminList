# Release 2.0.0 — GitHub publishing checklist

Use this when tagging and publishing **NoctuList 2.0.0** on GitHub.

## 1. Verify version files (already set for 2.0.0)

| File | Field | Value |
|------|--------|--------|
| [`version.json`](version.json) | `latestVersionCode` | `26` |
| [`version.json`](version.json) | `latestVersionName` | `2.0.0` |
| [`version.json`](version.json) | `minSupportedVersionCode` | `25` (forces update below 1.1.1) |
| Gradle / Android | reads `version.json` | `app/build.gradle`, `shared/build.gradle.kts`, `desktopApp/build.gradle.kts` |
| UI strings | `app_version` | `NoctuList 2.0.0` (all locales) |

## 2. Build artifacts

```bash
# Android release APK
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
# Rename for release: app-release-2.0.0.apk

# Desktop (macOS — run on Mac with Temurin JDK 17)
./scripts/package-desktop.sh
# → desktopApp/build/compose/packaged/…/NoctuList-2.0.0.dmg (etc.)

# Windows + Linux: use GitHub Actions (see .github/workflows/desktop-*.yml)
#   gh workflow run desktop-linux.yml
#   gh workflow run desktop-windows.yml
```

Expected GitHub asset names (must match `version.json` URLs):

- `app-release-2.0.0.apk`
- `NoctuList-2.0.0.dmg`
- `NoctuList-2.0.0.msi`
- `NoctuList-2.0.0.exe`
- `NoctuList-2.0.0.deb`
- `NoctuList-2.0.0.AppImage`

## 3. Git tag & GitHub Release

1. Commit all 2.0.0 version bumps and feature work.
2. Create an annotated tag: `git tag -a 2.0.0 -m "NoctuList 2.0.0"`
3. Push: `git push origin main && git push origin 2.0.0`
4. Open **Releases → Draft a new release**, choose tag **2.0.0**.
5. Title: **NoctuList 2.0.0**
6. Paste the release body from [`.github/release-notes/2.0.0.md`](release-notes/2.0.0.md).
7. Upload all platform binaries listed above.
8. Publish release (triggers Linux desktop workflow if configured).

## 4. Update manifest for in-app updates

Apps fetch the update manifest from:

`https://raw.githubusercontent.com/TouristeAG/AdminList/main/version.json`

After publishing binaries, copy the root [`version.json`](version.json) to that **AdminList** repo (or your canonical manifest branch) so existing installs see the 2.0.0 update prompt.

## 5. Smoke test

- [ ] Fresh install: Firebase create-org + join flow
- [ ] Sheets-only institution still syncs
- [ ] Manual account credit (+/−) on guest and volunteer
- [ ] Profile photo upload (if Storage enabled)
- [ ] Multi-org switcher
- [ ] In-app update dialog shows 2.0.0 changelog on a 1.1.1 device
