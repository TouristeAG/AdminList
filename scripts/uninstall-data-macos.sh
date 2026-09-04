#!/usr/bin/env bash
# Removes all NoctuList data from this macOS machine.
# Run this AFTER dragging NoctuList.app to the Trash, or from the app itself.
#
# What is deleted:
#   ~/.noctulist/               — database, auth tokens, credentials, cache, exports
#   ~/Library/Logs/NoctuList/  — crash reports
#   Java Preferences node       — settings stored in ~/Library/Preferences/
#
# Usage:
#   chmod +x scripts/uninstall-data-macos.sh
#   ./scripts/uninstall-data-macos.sh
set -euo pipefail

echo "NoctuList data removal — macOS"
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

# ── Crash reports ─────────────────────────────────────────────────────────────
CRASH_DIR="$HOME/Library/Logs/NoctuList"
if [[ -d "$CRASH_DIR" ]]; then
  echo "Deleting $CRASH_DIR …"
  rm -rf "$CRASH_DIR"
  echo "  ✓ Done"
else
  echo "  (not found: $CRASH_DIR)"
fi

# ── Java Preferences ──────────────────────────────────────────────────────────
# On macOS, java.util.prefs writes into ~/Library/Preferences/com.apple.java.util.prefs.plist.
# We remove only the NoctuList node using the 'defaults' command.
PREFS_DOMAIN="com.apple.java.util.prefs"
PREFS_KEY="/com/eventmanager/app/noctulist/"

echo "Removing Java Preferences node …"
if defaults read "$PREFS_DOMAIN" "$PREFS_KEY" &>/dev/null; then
  defaults delete "$PREFS_DOMAIN" "$PREFS_KEY" 2>/dev/null || true
  echo "  ✓ Done"
else
  echo "  (no preferences found)"
fi

echo ""
echo "All NoctuList data has been removed."
