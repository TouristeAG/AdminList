# Desktop package icons for Compose Desktop / jpackage
#
# Required formats (PNG alone is ignored on Windows/macOS installers → Java icon):
#   icon.icns  — macOS DMG / .app
#   icon.ico   — Windows MSI / EXE
#   icon.png   — Linux DEB / AppImage (also source artwork)
#
# Regenerate from icon.png (same asset as Android white launcher):
#   python desktopApp/icons/generate_icons.py
