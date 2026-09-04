# Release 2.1.0 — GitHub publishing checklist

Use this when tagging and publishing **NoctuList 2.1.0** on GitHub.

## 1. Verify version files (already set for 2.1.0)

| File | Field | Value |
|------|--------|--------|
| [`version.json`](version.json) | `latestVersionCode` | `27` |
| [`version.json`](version.json) | `latestVersionName` | `2.1.0` |
| [`version.json`](version.json) | `minSupportedVersionCode` | `26` (forces update below 2.0.0) |
| Gradle / Android | reads `version.json` | `app/build.gradle`, `shared/build.gradle.kts`, `desktopApp/build.gradle.kts` |
| UI strings | `app_version` | `NoctuList 2.1.0` (all locales) |

## 2. Build artifacts

```bash
# Android release APK
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
# Rename for release: app-release-2.1.0.apk

# Desktop macOS — MUST build on EACH architecture separately
# Compose Desktop bundles the host JVM; there is no cross-compilation.
#
#   On an Apple Silicon (M-series) Mac → NoctuList-2.1.0-arm64.dmg
#   On an Intel Mac                    → NoctuList-2.1.0-x86_64.dmg
#
# Required JDK: Temurin 17 (NOT Homebrew) — https://adoptium.net/
#   arm64: download "macOS aarch64" build
#   x86_64: download "macOS x64" build
#
./scripts/package-desktop.sh
# → desktopApp/build/compose/packaged/…/NoctuList-2.1.0-arm64.dmg  (on Apple Silicon)
# → desktopApp/build/compose/packaged/…/NoctuList-2.1.0-x86_64.dmg (on Intel)

# Windows + Linux: use GitHub Actions (see .github/workflows/desktop-*.yml)
#   gh workflow run desktop-linux.yml
#   gh workflow run desktop-windows.yml
```

Expected GitHub asset names (must match `version.json` URLs):

- `app-release-2.1.0.apk`
- `NoctuList-2.1.0-arm64.dmg`   ← Apple Silicon (M1/M2/M3/M4)
- `NoctuList-2.1.0-x86_64.dmg`  ← Intel Mac
- `NoctuList-2.1.0.msi`
- `NoctuList-2.1.0.exe`
- `NoctuList-2.1.0.deb`
- `NoctuList-2.1.0.AppImage`

> **Why two DMGs?** Compose Desktop bundles the JVM of the build host.
> An x86_64 DMG launched on Apple Silicon requires Rosetta 2 to be installed,
> may be blocked by Gatekeeper if the app is unsigned, and runs with a performance
> penalty. Ship a native arm64 DMG so Apple Silicon users get a first-class experience.

## 3. Git tag & GitHub Release

1. Commit all 2.1.0 version bumps and feature work.
2. Create an annotated tag: `git tag -a 2.1.0 -m "NoctuList 2.1.0"`
3. Push: `git push origin main && git push origin 2.1.0`
4. Open **Releases → Draft a new release**, choose tag **2.1.0**.
5. Title: **NoctuList 2.1.0**
6. Paste the release body from [`.github/release-notes/2.1.0.md`](release-notes/2.1.0.md).
7. Upload all platform binaries listed above.
8. Publish release (triggers Linux desktop workflow if configured).

## 4. Update manifest for in-app updates

Apps fetch the update manifest from:

`https://raw.githubusercontent.com/TouristeAG/AdminList/main/version.json`

After publishing binaries, copy the root [`version.json`](version.json) to that **AdminList** repo (or your canonical manifest branch) so existing installs see the 2.1.0 update prompt.

## 5. Smoke test

- [ ] Fresh install: Firebase create-org + join flow
- [ ] Sheets-only institution still syncs
- [ ] Wide billetterie / people counter on a large tablet or desktop
- [ ] Announcements create / acknowledge
- [ ] Manual account credit (+/−) on guest and volunteer
- [ ] In-app update dialog shows 2.1.0 changelog on a 2.0.0 device
