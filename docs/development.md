# Development and releases

Requirements: Node.js 24+, npm, and for Android JDK 21, Android SDK 36 and accepted SDK licenses. Gradle 8.14.3 is wrapped in the repository. Desktop packaging uses Electron 45 and electron-builder.

```sh
npm ci
npm test
npm run test:desktop
cd android
./gradlew assembleDebug lintDebug
```

On a Linux CI host without a display, run desktop integration tests under `xvfb-run -a`. CI alone passes `--no-sandbox` to the test binary because hosted runners may disable user namespaces; application builds retain their normal sandbox defaults.

Build packages with `npm run dist`, or `npm run dist:linux` for an x64 AppImage. Desktop signing/notarization is separate: initial Windows/macOS artifacts are unsigned. Windows may show an unknown publisher prompt; macOS may require the normal Privacy & Security approval. No system-wide security settings need to be disabled.

Android release signing uses environment variables:

```text
ROMMAYO_KEYSTORE        Absolute keystore path
ROMMAYO_STORE_PASSWORD Keystore password
ROMMAYO_KEY_ALIAS      Signing key alias
ROMMAYO_KEY_PASSWORD   Key password
```

Never commit these values or a keystore. Without signing variables, `assembleRelease` produces an unsigned APK for manual signing. Keep the same release identity for updates.

The release workflow uses GitHub repository secrets `ROMMAYO_KEYSTORE_BASE64`, `ROMMAYO_STORE_PASSWORD`, `ROMMAYO_KEY_ALIAS` and `ROMMAYO_KEY_PASSWORD`. It decodes the key into the ephemeral runner's temporary directory. Pull-request CI has no signing credentials.

To release, update both package and Android versions, run the checks, and tag `vX.Y.Z`. The tag workflow builds a signed Android APK, a Linux x64 AppImage, a Windows x64 installer and a macOS arm64 disk image, then publishes a prerelease. Check the workflow before promoting an alpha to a stable release.

Integration tests use a local fixture with synthetic content. Live testing must use operator-provided credentials kept outside the repository. Do not commit live screenshots containing private collection or account information. The tracked screenshots show only the empty connection screen.
