# FukeX

FukeX is a lightweight, open source Android media player written in Kotlin, built from the ground up with screen reader accessibility in mind. It's fully navigable with TalkBack, and pairs a simple UI with features other players leave out — crossfading, gapless playback, SMB streaming, playlist locking, a 10-band equalizer, synced lyrics, a voice assistant, and more.

Full user documentation, covering every feature and how to use it, is available in [documentation.md](documentation.md).

## Downloads

Grab the latest signed APK from the [Releases](../../releases/latest) page, or from [boostofstudios.com/fukex](https://boostofstudios.com/fukex).

## Features

* Crossfading and gapless playback, with separate fade times per interruption type
* Playlists with PIN, password or fingerprint locking, import/export, and per-playlist resume
* Music library (songs, artists, albums, genres) and an Up Next queue
* SMB share streaming, and experimental YouTube search and playback
* 10-band equalizer with per-output settings (speaker, wired, Bluetooth, USB), amplifier, ReplayGain, speed and pitch
* Synced lyrics from `.lrc` files or lrclib.net
* Voice assistant: press and hold Volume Down (or optionally shake twice), then say things like "play <song>", "add <song> to queue", "forward 30 seconds", "what's playing"
* Optional visual icon pack, downloaded on request from `iconpacks/` on GitHub (see [iconpacks/README.md](iconpacks/README.md))
* In-app updates: checks GitHub releases with semantic version comparison, downloads the signed APK, verifies it (checksum, package, version and signing key) and hands it to the system installer
* Background playback engine with media notification, lock screen, Bluetooth and Android Auto controls

## What it's built with

* **Kotlin** with **Jetpack Compose** (Material 3) for the UI
* **Media3 ExoPlayer** for audio playback
* **jcifs-ng** for SMB share streaming
* **NewPipe Extractor** for the experimental YouTube support
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

Run the unit tests with:

```
./gradlew testDebugUnitTest
```

The output APK lands in `app/build/outputs/apk/`.

## Project layout

* `playback/` — `PlaybackEngine` (dual-player crossfade, focus, effects, queue), equalizer processor, output routing
* `data/` — playlists, settings, SMB, library, lyrics, equalizer settings, icon pack loader
* `ui/` — Compose screens: main, player, library, equalizer, lyrics, dialogs
* `iconpacks/` — the downloadable icon pack, its catalog and the generator script
* `docs/` — the README and documentation as standalone web pages (`docs/build.sh` rebuilds them)
* `PlaybackService.kt` — foreground service, media notification and Android Auto browse tree
* `VoiceAssistantManager.kt` / `VoiceCommandParser.kt` / `VoiceCommandHandler.kt` — listening and speech, text-to-command parsing (unit tested), and command execution

User documentation lives in `documentation.md`; a copy and its HTML rendering ship in `app/src/main/assets/` and show up in the app. Keep the three in sync when editing.

## License

MIT — see [LICENSE](LICENSE).
