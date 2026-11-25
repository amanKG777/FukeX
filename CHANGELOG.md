# Changelog

## [0.2.0] - 2026-11-25

### Features
* Added a voice assistant: press and hold Volume Down (or optionally shake twice) to play songs, queue tracks, seek, control volume or amplifier, and ask what is playing.
* Added a background playback engine with media notifications, lock screen controls, headset button support, and Android Auto / Wear OS support.
* Added a Music Library to browse songs on your device by Songs, Artists, Albums, or Genres.
* Added an Up Next queue with "Play next" and "Add to queue" options.
* Added a 10-band equalizer with presets, a preamp, and separate profiles for speaker, wired headphones, Bluetooth, and USB audio.
* Added synced lyrics from `.lrc` files or lrclib.net, with optional TalkBack line-by-line reading.
* Added voice assistant settings: custom TTS voice selection, shake sensitivity, background Volume Key Shortcut, and customizable spoken feedback or beep-only alerts.
* Added context-aware voice navigation to switch between search results and song versions ("Next result", "Previous result", "Option 2").
* Added an in-app updater that checks GitHub for new releases, verifies the APK, and installs updates.
* Added an optional downloadable visual icon pack.
* Added experimental YouTube search and playback.
* Added in-app documentation and license viewer, and updated the user documentation.

### Bug Fixes
* Fixed voice recognition language handling so the assistant respects your device language properly.
* Fixed an issue where shake detection could trigger multiple times at once.
* Ignored Volume Down hold during phone and VoIP calls so call volume is not interrupted.
* Reduced the app's APK download size by removing unused library resources and extra locales.
