#!/usr/bin/env bash
#
# Build (if needed) and install the DEBUG APK onto a connected phone.
#
# Discovers the connected device automatically. If several are attached, pass a
# serial as the first argument or set ANDROID_SERIAL.
#
# Usage:
#   scripts/deploy-debug.sh [serial]
#   SKIP_BUILD=1 scripts/deploy-debug.sh      # install an already-built APK

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

APK="$APP_APK_DIR/debug/app-debug.apk"

if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  "$SCRIPT_DIR/build-debug.sh"
fi
[[ -f "$APK" ]] || die "APK not found at $APK. Run scripts/build-debug.sh first (or unset SKIP_BUILD)."

SERIAL="$(pick_device "${1:-}")"
ADB="$(resolve_adb)"
log "Installing debug APK on $SERIAL ..."
"$ADB" -s "$SERIAL" install -r -d "$APK"
log "Done. Launching app..."
"$ADB" -s "$SERIAL" shell monkey -p com.example.androidalarm -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || \
  warn "Installed, but couldn't auto-launch. Open the app from the launcher."
log "Debug build deployed to $SERIAL."
