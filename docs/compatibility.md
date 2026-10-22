# Interface coverage and validation

Rommayo loads the complete RomM website. It does not maintain a second implementation of the library or remove server features based on device. What a user can do still depends on that user's RomM permissions, the server version, browser capabilities, BIOS/core setup and the device.

Initial live validation used RomM 5.4.0, a Galaxy S24 Ultra and macOS Apple Silicon. No ROMs, BIOS files, server addresses or credentials from that environment are included in the release.

| Area | Implementation | Validation |
| --- | --- | --- |
| Username/password login | RomM Basic login → native/browser session cookies | Live Android, desktop and web login checks |
| Session restoration | Native cookie store; password never persisted by wrapper | Automated desktop restart; Android force-stop check |
| Search, platforms, collections, recently played, details | Actual RomM pages | Live library access; navigation checks |
| Favorites, notes, saves, metadata and administration | Actual RomM pages and server permissions | Interface inherited; each operation not exhaustively tested |
| HTTP and generated blob downloads | Desktop save dialog; Android DownloadManager / document picker | Automated desktop HTTP/blob download checks; Android implementation needs broad device testing |
| Uploads | Browser/Android document picker | Automated desktop file selection and proxy streaming checks |
| Browser emulation | RomM's own players, engines and configured cores | Player smoke check; no all-platform game certification |
| SSO | RomM sign-in with explicit external-origin navigation | Implemented; provider-specific flows not certified |
| Gamepads / fullscreen | Native web engine; Steam Input mouse navigation when needed | Fullscreen host implemented; physical Deck/gamepad testing pending |
| Web streaming | HTTP streaming, ranges, WebSocket upgrades | Automated forwarding checks; remote emulator streaming not certified |

HTTPS is needed for some browser APIs. An HTTP LAN server may browse correctly while gamepads, microphone access, WebRTC or threaded emulator features are unavailable. Rommayo never bypasses certificate errors or Chromium's same-origin checks.

Android uses the installed Android System WebView and requires Android 10+. Keep WebView updated. General native bridges are only exposed to the packaged connection screen; the RomM view has a restricted, token-gated download helper. File, camera, microphone, notifications, WebAuthn and unusual popup flows can differ from a full browser. Device permissions beyond fullscreen/pointer lock are not automatically granted on desktop. If a server feature needs a web API the host does not support, use a full browser and report the gap.

The theme supports RomM's Vuetify pages and 5.4's component tokens. Disable it on the connection screen if a newer server theme changes unexpectedly. Public alpha means **full interface access with explicit platform caveats**, not a claim of universal emulator compatibility.
