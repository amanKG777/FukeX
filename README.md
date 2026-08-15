# FukeX

FukeX is a lightweight, open source Android media player written in Kotlin, built from the ground up with screen reader accessibility in mind. It's fully navigable with TalkBack, and pairs a simple UI with features other players leave out — crossfading, gapless playback, SMB streaming, playlist locking, and more.

Full user documentation, covering every feature and how to use it, is available in [documentation.md](documentation.md).

## Downloads

Grab the latest signed APK from the [Releases](../../releases/latest) page, or from [boostofstudios.com/fukex](https://boostofstudios.com/fukex).

## What it's built with

* **Kotlin** with **Jetpack Compose** (Material 3) for the UI
* **Media3 ExoPlayer** for audio playback
* **jcifs-ng** for SMB share streaming
* **AndroidX Biometric** for fingerprint-locked playlists
* **AndroidX DocumentFile / Media** for storage and notification integration

Minimum supported Android version is 8.0 (API 26).

## Building from source

1. Clone the repo and open it in Android Studio, or build from the command line.
2. Debug build (unsigned, installable as-is):

   ```
   ./gradlew assembleDebug
   ```

3. Release build: release builds are signed via a `keystore.properties` file (gitignored) placed next to `app/build.gradle.kts`:

   ```properties
   storeFile=path/to/your.jks
   storePassword=your-store-password
   keyAlias=your-key-alias
   keyPassword=your-key-password
   ```

   Without it, `assembleRelease` still runs (minified/shrunk) but produces an unsigned APK. With it:

   ```
   ./gradlew assembleRelease
   ```

The output APK lands in `app/build/outputs/apk/`.

## License

MIT — see [LICENSE](LICENSE).
