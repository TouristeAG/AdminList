#!/usr/bin/env bash
# Package NoctuList desktop installers. Requires Temurin/Oracle JDK 17 (not Homebrew JDK).
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "Tip: set JAVA_HOME to Temurin JDK 17 if packaging fails (Homebrew JDK is unsupported)."
fi

./gradlew :desktopApp:packageDmg :desktopApp:packageMsi :desktopApp:packageReleaseDistributionForCurrentOs

echo ""
echo "Artifacts: desktopApp/build/compose/binaries/"
echo "macOS signing: export MACOS_SIGN_IDENTITY and NOTARY_APPLE_ID for notarization (manual step)."
echo "Windows signing: set WINDOWS_SIGN_TOOL and certificate path before packaging on Windows."
