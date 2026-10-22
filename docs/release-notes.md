Install Rommayo for Android, macOS Apple Silicon or Intel, Windows, and Linux/Steam Deck. Packaged apps do not need Node.js, npm or a development server. Downloads: https://rommayo.quentinmayo.com/#downloads.

This release adds **Remember login** and **Log out**. Android and desktop retain a checked session between app restarts; an unchecked session is cleared before the next app launch. Android: Settings → Log out. Desktop: Log out in the toolbar or application menu. Logout contacts RomM, clears local authentication even if the server is unavailable, and preserves browser-local game saves and exported files. Passwords are never stored by Rommayo. The self-hosted web edition uses browser-session cookies when unchecked; browser session-restoration settings may retain them. Its Log out control ends the current session explicitly.

Android uses native, Steam-inspired library, search, recents, details and controller settings. WebViews handle gameplay and Full RomM. Desktop uses Electron and the full RomM interface. No games, BIOS files or keys are included. Physical Steam Deck and Xbox/retro-controller gameplay testing is pending.

Mac apps are ad-hoc signed, not Apple-notarized. Windows installers are unsigned. Linux AppImages require executable permission; Steam Deck users add the AppImage as a non-Steam app with --deck. Check SHA256SUMS.txt to verify downloaded assets.

Use only content you are authorized to access. Downloads and use are at your own risk. Independent of RomM and Valve.

Website: https://rommayo.quentinmayo.com · Support: https://buymeacoffee.com/quentinmayo
