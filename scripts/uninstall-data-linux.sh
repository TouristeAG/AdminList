#!/usr/bin/env bash
# Removes all NoctuList data from this Linux machine.
# Run this AFTER removing the NoctuList package (dpkg -r noctulist / rm NoctuList.AppImage),
# or from the app itself.
#
# What is deleted:
#   ~/.noctulist/                              — database, auth tokens, credentials, cache, exports
#   ~/.java/.userPrefs/com/eventmanager/       — Java Preferences (settings)
#
# Usage:
#   chmod +x scripts/uninstall-data-linux.sh
#   ./scripts/uninstall-data-linux.sh
set -euo pipefail

echo "NoctuList data removal — Linux"
echo "================================"

confirm() {
  read -r -p "$1 [y/N] " response
  case "$response" in
    [yY][eE][sS]|[yY]) true ;;
    *) false ;;
  esac
}

confirm "This will permanently delete all NoctuList data. Continue?" || {
  echo "Aborted."
  exit 0
}

# ── App data directory ────────────────────────────────────────────────────────
DATA_DIR="$HOME/.noctulist"
if [[ -d "$DATA_DIR" ]]; then
  echo "Deleting $DATA_DIR …"
  rm -rf "$DATA_DIR"
  echo "  ✓ Done"
else
  echo "  (not found: $DATA_DIR)"
fi

# ── Java Preferences ──────────────────────────────────────────────────────────
# On Linux, java.util.prefs writes to ~/.java/.userPrefs/ as XML files.
JAVA_PREFS_DIR="$HOME/.java/.userPrefs/com/eventmanager"
if [[ -d "$JAVA_PREFS_DIR" ]]; then
  echo "Deleting Java Preferences at $JAVA_PREFS_DIR …"
  rm -rf "$JAVA_PREFS_DIR"
  echo "  ✓ Done"
else
  echo "  (no Java preferences found at $JAVA_PREFS_DIR)"
fi

echo ""
echo "All NoctuList data has been removed."
