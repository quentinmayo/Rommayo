# Steam Deck

Rommayo ships as a Linux x64 AppImage and runs as a non-Steam game. It has no Steam store integration or Valve verification.

1. In Desktop Mode, download the Linux x64 AppImage from [Releases](https://github.com/quentinmayo/Rommayo/releases).
2. Move it to a stable location, for example `~/Applications/Rommayo.AppImage`, and make it executable from Properties → Permissions, or `chmod +x ~/Applications/Rommayo.AppImage`.
3. Open Steam → Games → Add a Non-Steam Game → Browse, and choose the AppImage.
4. In its Steam properties, set launch options to `--deck` for fullscreen startup.
5. Start Rommayo, connect your server, and use your existing RomM account.

Use a Steam Input layout with a mouse trackpad for library navigation; configure controller input in RomM's player for games. Rommayo leaves gameplay input to the browser engine and RomM. `F11` toggles fullscreen and `Esc` exposes the toolbar again. On Deck, map these to back buttons or use the Steam keyboard/menu. A touchscreen also works.

Use native Linux compatibility; do not force Proton for the AppImage. If the system lacks FUSE support, try `APPIMAGE_EXTRACT_AND_RUN=1` when launching from the terminal. Keep Chromium's sandbox enabled. Your server needs HTTPS for browser features that require a secure context.

The x64 AppImage is packaged for Deck, but physical Steam Deck controller, suspend/resume and gameplay certification remain to be done. See [compatibility](compatibility.md).
