# Interface coverage and validation

Android implements the main library in native Views using RomM's API. Full RomM and desktop expose the complete upstream website. Features still depend on RomM permissions, server version, browser capabilities, BIOS/core setup and device.

Initial live validation used RomM 5.4.0, a Galaxy S24 Ultra and macOS Apple Silicon. No ROMs, BIOS files, server addresses or credentials from that environment are included in the release.

| Area | Implementation | Validation |
| --- | --- | --- |
| Username/password login | RomM Basic login → native/browser session cookies | Live Android, desktop and web login checks |
| Session restoration | Native cookie store; password never persisted by wrapper | Automated desktop restart; Android force-stop check |
| Android library, search, platforms, recents, details | Native Android Views and paged RomM API | Galaxy S24 Ultra native hierarchy and live library checks |
| Collections and advanced tools | Full RomM web fallback | Interface inherited; operations not exhaustively tested |
| Favorites, notes, saves, metadata and administration | Actual RomM pages and server permissions | Interface inherited; each operation not exhaustively tested |
| HTTP and generated blob downloads | Desktop save dialog; Android DownloadManager / document picker | Automated desktop HTTP/blob download checks; Android implementation needs broad device testing |
| Uploads | Browser/Android document picker | Automated desktop file selection and proxy streaming checks |
| Browser emulation | RomM's own players, engines and configured cores | Player smoke check; no all-platform game certification |
| SSO | RomM sign-in with explicit external-origin navigation | Implemented; provider-specific flows not certified |
| Android menu controllers | Android key/axis input, focus, A/B swap, dead zone, controller check | Injected A/B/X/Y/shoulder checks; physical Xbox/retro-handheld testing pending |
| Gameplay controllers / fullscreen | Browser player mapping and native web engine | Fullscreen host implemented; physical Deck/gamepad testing pending |
| Web streaming | HTTP streaming, ranges, WebSocket upgrades | Automated forwarding checks; remote emulator streaming not certified |

HTTPS is needed for some browser APIs. An HTTP LAN server may browse correctly while gamepads, microphone access, WebRTC or threaded emulator features are unavailable. Rommayo never bypasses certificate errors or Chromium's same-origin checks.

Android uses the installed Android System WebView and requires Android 10+. Keep WebView updated. Sign-in and library screens have no WebView. The isolated RomM/player view has only a restricted, token-gated download helper. File, camera, microphone, notifications, WebAuthn and unusual popup flows can differ from a full browser. Device permissions beyond fullscreen/pointer lock are not automatically granted on desktop. If a server feature needs a web API the host does not support, use a full browser and report the gap.

The theme supports RomM's Vuetify pages and 5.4's component tokens. Disable it on the connection screen if a newer server theme changes unexpectedly. Public alpha means **full interface access with explicit platform caveats**, not a claim of universal emulator compatibility.
