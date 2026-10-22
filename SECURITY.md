# Security

Report security issues privately using GitHub's private vulnerability reporting for this repository when available, or contact the maintainer privately. Do not post passwords, cookies, keystores or private server URLs in issues.

The desktop shell uses sandboxing, context isolation and a separate session partition per server origin. Remote RomM content has no Node integration, preload or native shell IPC bridge. The local shell validates IPC senders. External navigation requires an explicit choice, and non-HTTP(S) links are rejected.

Android separates the packaged connection UI from the server WebView. The connection bridge is origin restricted and main-frame only; the server can use only an origin-restricted, token-gated generated-file helper. Native backups are disabled. Release WebView debugging is off. File transfers require a native download/save or file selection flow.

Passwords exist in memory during login and are not persisted by the wrapper. RomM's session cookies are stored by the native web engine or browser, protected by that platform's app data mechanisms. Treat the device profile as sensitive. No additional encryption guarantee is made beyond the OS/web engine's storage protection. The web edition is a trusted proxy and sees credentials in memory while forwarding login requests.

HTTP requires acknowledgement because traffic is unencrypted. TLS validation is never bypassed. Connect only to servers you trust; a server controls the HTML it serves. Keep Electron and Android System WebView current.

Use RomM's own logout to revoke a current session. **Forget sessions** clears the native app's saved web data and server history on the device; it does not claim to revoke server-side sessions already copied elsewhere.
