package com.boostofstudios.fukex.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.boostofstudios.fukex.data.LoadedPack

// The downloaded visual icon pack, if one is installed and switched on. Provided once at the root.
val LocalIconPack = staticCompositionLocalOf<LoadedPack?> { null }

// Every icon the app draws. Each one is the pack's version when a pack is active and the
// built-in Material icon otherwise, so the app looks complete with or without a pack.
// Descriptions stay at the call sites, so what a screen reader announces never depends on the pack.
object FxIcons {
	@Composable
	private fun pick(id: String, fallback: ImageVector): ImageVector = LocalIconPack.current?.icon(id) ?: fallback

	val Add: ImageVector @Composable get() = pick("add", Icons.Default.Add)
	val Album: ImageVector @Composable get() = pick("album", Icons.Default.Album)
	val ArrowBack: ImageVector @Composable get() = pick("back", Icons.AutoMirrored.Filled.ArrowBack)
	val ArrowDropDown: ImageVector @Composable get() = pick("dropdown", Icons.Default.ArrowDropDown)
	val ArrowForward: ImageVector @Composable get() = pick("forward", Icons.AutoMirrored.Filled.ArrowForward)
	val AudioFile: ImageVector @Composable get() = pick("audio_file", Icons.Default.AudioFile)
	val Category: ImageVector @Composable get() = pick("category", Icons.Default.Category)
	val Close: ImageVector @Composable get() = pick("close", Icons.Default.Close)
	val CloudDownload: ImageVector @Composable get() = pick("cloud_download", Icons.Default.CloudDownload)
	val Delete: ImageVector @Composable get() = pick("delete", Icons.Default.Delete)
	val Equalizer: ImageVector @Composable get() = pick("equalizer", Icons.Default.Equalizer)
	val FastForward: ImageVector @Composable get() = pick("fast_forward", Icons.Default.FastForward)
	val FileDownload: ImageVector @Composable get() = pick("file_download", Icons.Default.FileDownload)
	val FileUpload: ImageVector @Composable get() = pick("file_upload", Icons.Default.FileUpload)
	val Fingerprint: ImageVector @Composable get() = pick("fingerprint", Icons.Default.Fingerprint)
	val Folder: ImageVector @Composable get() = pick("folder", Icons.Default.Folder)
	val GraphicEq: ImageVector @Composable get() = pick("graphic_eq", Icons.Default.GraphicEq)
	val Info: ImageVector @Composable get() = pick("info", Icons.Default.Info)
	val InsertDriveFile: ImageVector @Composable get() = pick("file", Icons.Default.InsertDriveFile)
	val LibraryMusic: ImageVector @Composable get() = pick("library_music", Icons.Default.LibraryMusic)
	val Lock: ImageVector @Composable get() = pick("lock", Icons.Default.Lock)
	val LockClock: ImageVector @Composable get() = pick("lock_clock", Icons.Default.LockClock)
	val LockOpen: ImageVector @Composable get() = pick("lock_open", Icons.Default.LockOpen)
	val Lyrics: ImageVector @Composable get() = pick("lyrics", Icons.Default.Lyrics)
	val Mic: ImageVector @Composable get() = pick("mic", Icons.Default.Mic)
	val MoreVert: ImageVector @Composable get() = pick("more", Icons.Default.MoreVert)
	val MusicNote: ImageVector @Composable get() = pick("music_note", Icons.Default.MusicNote)
	val Pause: ImageVector @Composable get() = pick("pause", Icons.Default.Pause)
	val Person: ImageVector @Composable get() = pick("person", Icons.Default.Person)
	val PlayArrow: ImageVector @Composable get() = pick("play", Icons.Default.PlayArrow)
	val PlayCircle: ImageVector @Composable get() = pick("play_circle", Icons.Default.PlayCircle)
	val PlaylistAdd: ImageVector @Composable get() = pick("playlist_add", Icons.AutoMirrored.Filled.PlaylistAdd)
	val QueueMusic: ImageVector @Composable get() = pick("queue_music", Icons.AutoMirrored.Filled.QueueMusic)
	val Search: ImageVector @Composable get() = pick("search", Icons.Default.Search)
	val Settings: ImageVector @Composable get() = pick("settings", Icons.Default.Settings)
	val SettingsVoice: ImageVector @Composable get() = pick("voice", Icons.Default.SettingsVoice)
	val SkipNext: ImageVector @Composable get() = pick("skip_next", Icons.Default.SkipNext)
	val SkipPrevious: ImageVector @Composable get() = pick("skip_previous", Icons.Default.SkipPrevious)
	val VolumeOff: ImageVector @Composable get() = pick("volume_off", Icons.AutoMirrored.Filled.VolumeOff)
	val VolumeUp: ImageVector @Composable get() = pick("volume_up", Icons.AutoMirrored.Filled.VolumeUp)

	// Decorative extras that only exist in a pack (headings, dialogs); null without one
	@Composable
	fun decoration(id: String): ImageVector? = LocalIconPack.current?.icon(id)
}
