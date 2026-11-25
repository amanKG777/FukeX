# Changelog

All notable changes to FukeX are documented in this file.

## [0.2.0] - 2026-11-25

### Added
- **Background Playback Engine**: Dedicated background playback engine with media notifications, lock screen controls, headset button support, and Android Auto / Wear OS browse tree.
- **Music Library & Up Next Queue**: Browse device music by Songs, Artists, Albums, or Genres, and queue tracks with "Play next" and "Add to queue".
- **10-Band Equalizer**: Built-in 10-band equalizer with presets, preamp, and separate profiles for speaker, wired headphones, Bluetooth, and USB audio.
- **Synced Lyrics**: View `.lrc` lyrics or look them up on lrclib.net, with optional TalkBack line-by-line reading.
- **Voice Assistant**:
  - Press and hold Volume Down to wake the assistant (with optional background accessibility shortcut); shake-to-wake is optional with adjustable sensitivity.
  - Custom TTS engine selection and customizable spoken feedback events (listening, searching, playing, switching results, controls, errors).
  - Context-aware navigation across playlist, library, and YouTube results ("Next result", "Previous result", "Option 2").
- **In-App Updater**: Checks GitHub releases with semantic version comparison, verifies checksum, package, version, and signing key, and hands off to the system installer.
- **Optional Visual Icon Pack**: Downloadable icon pack from GitHub with SHA-256 checksum verification.
- **Documentation & License**: In-app documentation and license viewer, standalone HTML docs, and updated user documentation.
- **Experimental YouTube Support**: Search and stream YouTube audio via NewPipe Extractor.

### Changed
- Reduced release APK size with narrower R8 keep rules, locale filtering (`en`, `hi`, `es`), and resource exclusions.
- Hardened voice assistant speech recognition language tagging and prevented duplicate shake sensor listeners.

## [0.1.0] - 2026-08-15

### Added
- Initial public release of FukeX, an accessible Android media player built for TalkBack.
- Multi-playlist tabbed interface with import/export (JSON) and per-playlist resume positions.
- Playlist hiding/locking with PIN, password, or biometric unlock and configurable lock timeout.
- Local file picker and SMB share streaming with 500 MB media cache.
- Crossfading and gapless playback with per-event fade sliders, volume amplifier (up to 25 dB), ReplayGain, playback speed, pitch, and bass boost.
