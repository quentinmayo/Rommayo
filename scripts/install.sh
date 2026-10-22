#!/usr/bin/env bash
# Install a verified packaged release in your account; no sudo or system changes.
set -euo pipefail
APP='Rommayo'
SLUG='rommayo'
VERSION='0.2.4'
TAG='v0.2.4'
REPOSITORY='quentinmayo/Rommayo'
DECK=0
DRY_RUN=0
for option in "$@"; do
  case "$option" in
    --steam-deck) DECK=1 ;;
    --dry-run) DRY_RUN=1 ;;
    *) printf 'Unknown option: %s\n' "$option" >&2; exit 2 ;;
  esac
done
case "$(uname -s):$(uname -m)" in
  Linux:x86_64) PLATFORM=linux; ASSET="${APP}-${VERSION}-linux-x86_64.AppImage" ;;
  Darwin:arm64) PLATFORM=mac; ASSET="${APP}-${VERSION}-mac-arm64.dmg" ;;
  Darwin:x86_64) PLATFORM=mac; ASSET="${APP}-${VERSION}-mac-x64.dmg" ;;
  *) printf '%s has no packaged release for this platform. See the download page.\n' "$APP" >&2; exit 1 ;;
esac
if [ "$DECK" = 1 ] && [ "$PLATFORM" != linux ]; then
  printf 'Steam Deck setup requires Linux x64.\n' >&2; exit 1
fi
if [ "$PLATFORM" = linux ]; then
  PREFIX="${MAYO_INSTALL_PREFIX:-$HOME/.local}"
  TARGET="$PREFIX/share/$SLUG/$APP.AppImage"
else
  PREFIX="${MAYO_INSTALL_PREFIX:-$HOME}"
  TARGET="$PREFIX/Applications/$APP.app"
fi
printf '%s %s → %s\n' "$APP" "$TAG" "$TARGET"
if [ "$DRY_RUN" = 1 ]; then printf 'Asset: %s\nSteam Deck mode: %s\n' "$ASSET" "$DECK"; exit 0; fi
if [ "${EUID:-$(id -u)}" = 0 ]; then printf 'Run this installer as your normal user, without sudo.\n' >&2; exit 1; fi
command -v curl >/dev/null || { printf 'curl is required.\n' >&2; exit 1; }
WORK=$(mktemp -d)
MOUNTED=0
cleanup() {
  if [ "$MOUNTED" = 1 ]; then hdiutil detach "$WORK/mount" -quiet || true; fi
  rm -rf -- "$WORK"
}
trap cleanup EXIT
BASE="https://github.com/$REPOSITORY/releases/download/$TAG"
curl --fail --location --silent --show-error --retry 2 --connect-timeout 15 --max-time 900 "$BASE/$ASSET" -o "$WORK/$ASSET"
curl --fail --location --silent --show-error --retry 2 --connect-timeout 15 --max-time 60 "$BASE/SHA256SUMS.txt" -o "$WORK/SHA256SUMS.txt"
EXPECTED=$(awk -v name="$ASSET" '$2 == name { print $1; exit }' "$WORK/SHA256SUMS.txt")
if [[ ! "$EXPECTED" =~ ^[a-fA-F0-9]{64}$ ]]; then printf 'No valid checksum for this installer. Nothing was installed.\n' >&2; exit 1; fi
if command -v sha256sum >/dev/null; then ACTUAL=$(sha256sum "$WORK/$ASSET" | awk '{print $1}');
else ACTUAL=$(shasum -a 256 "$WORK/$ASSET" | awk '{print $1}'); fi
if [ "$ACTUAL" != "$EXPECTED" ]; then printf 'Checksum mismatch. Nothing was installed.\n' >&2; exit 1; fi
printf 'SHA-256 verified.\n'
if [ "$PLATFORM" = mac ]; then
  mkdir -p "$PREFIX/Applications" "$WORK/mount"
  hdiutil attach "$WORK/$ASSET" -readonly -nobrowse -mountpoint "$WORK/mount" -quiet
  MOUNTED=1
  [ -d "$WORK/mount/$APP.app" ] || { printf 'Unexpected installer layout.\n' >&2; exit 1; }
  ditto "$WORK/mount/$APP.app" "$PREFIX/Applications/.$SLUG-staged-$$.app"
  if [ -e "$TARGET" ]; then mv "$TARGET" "$TARGET.backup-$(date +%Y%m%d%H%M%S)"; fi
  mv "$PREFIX/Applications/.$SLUG-staged-$$.app" "$TARGET"
  printf 'Installed. Open %s in Finder. macOS packages are ad-hoc signed, not notarized.\n' "$TARGET"
else
  mkdir -p "$PREFIX/share/$SLUG" "$PREFIX/bin" "$PREFIX/share/applications"
  chmod 755 "$WORK/$ASSET"
  if [ -e "$TARGET" ]; then mv -f "$TARGET" "$TARGET.previous"; fi
  mv "$WORK/$ASSET" "$TARGET"
  LAUNCHER="$PREFIX/bin/$SLUG"
  {
    printf '#!/usr/bin/env bash\nset -e\nexport APPIMAGE_EXTRACT_AND_RUN=1\n'
    # Steam's injected overlay library stalls Electron startup on the tested Deck.
    if [ "$DECK" = 1 ]; then printf 'unset LD_PRELOAD\n'; fi
    printf 'exec '
    printf '%q ' "$TARGET"
    if [ "$DECK" = 1 ]; then printf '%s ' '--ozone-platform=x11' '--deck'; fi
    printf '"$@"\n'
  } > "$LAUNCHER"
  chmod 755 "$LAUNCHER"
  ESCAPED=${LAUNCHER//\\/\\\\}; ESCAPED=${ESCAPED//\"/\\\"}; ESCAPED=${ESCAPED//%/%%}
  cat > "$PREFIX/share/applications/$SLUG.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=$APP
Comment=Your next save point
Exec="$ESCAPED"
Icon=applications-games
Terminal=false
Categories=Game;
StartupWMClass=$SLUG
DESKTOP
  command -v update-desktop-database >/dev/null && update-desktop-database "$PREFIX/share/applications" || true
  printf 'Installed. Open %s from the application menu, or run:\n%s\n' "$APP" "$LAUNCHER"
  if [ "$DECK" = 1 ]; then
    printf '\nSteam Deck: in Desktop Mode, open Steam → Games → Add a Non-Steam Game. Select %s, then Add Selected Programs.\nReturn to Gaming Mode → Library → Non-Steam. The installed launcher already includes --deck; leave Compatibility / Proton disabled.\n' "$APP"
  fi
fi
printf 'Updates replace the app only. Your game files and app profile are preserved.\n'
