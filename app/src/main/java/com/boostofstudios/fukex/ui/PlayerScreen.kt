package com.boostofstudios.fukex.ui

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.boostofstudios.fukex.data.Playlist
import com.boostofstudios.fukex.data.SettingsManager
import com.boostofstudios.fukex.formatTime
import com.boostofstudios.fukex.playback.PlaybackEngine
import com.boostofstudios.fukex.trackTitle

internal data class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

// An overflow button that shows a menu when tapped, and offers the same items to TalkBack as custom actions
@Composable
internal fun OverflowMenu(description: String, actions: List<MenuAction>) {
	var expanded by remember { mutableStateOf(false) }
	Box {
		IconButton(
			onClick = { expanded = true },
			modifier = Modifier.semantics {
				contentDescription = description
				customActions = actions.map { action ->
					CustomAccessibilityAction(action.label) {
						action.onClick()
						true
					}
				}
			}
		) {
			Icon(FxIcons.MoreVert, contentDescription = null)
		}
		DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
			actions.forEach { action ->
				DropdownMenuItem(
					text = { Text(action.label) },
					onClick = { action.onClick(); expanded = false },
					leadingIcon = { Icon(action.icon, null) }
				)
			}
		}
	}
}

@Composable
fun PlayerScreen(
	playlist: Playlist,
	engine: PlaybackEngine,
	canModifyPlaylist: Boolean,
	// True when the engine is playing a locked playlist the user can't currently see
	hidePlayingDetails: Boolean,
	onHide: () -> Unit,
	onRemove: () -> Unit,
	onUpdatePlaylist: (Playlist) -> Unit,
	onOpenEqualizer: () -> Unit,
	onOpenLyrics: () -> Unit,
	modifier: Modifier = Modifier
) {
	val context = LocalContext.current
	val state by engine.state.collectAsState()
	val playingHere = state.playlistId == playlist.id
	val playingIndex = if (playingHere) state.currentItem?.playlistIndex ?: -1 else -1
	var showSearchDialog by remember { mutableStateOf(false) }
	var showInfoDialog by remember { mutableStateOf(false) }
	var showQueueDialog by remember { mutableStateOf(false) }
	var showEffectsDialog by remember { mutableStateOf(false) }

	fun swap(index: Int, other: Int) {
		val newUris = playlist.uris.toMutableList()
		java.util.Collections.swap(newUris, index, other)
		onUpdatePlaylist(playlist.copy(uris = newUris))
	}

	fun removeTrack(index: Int) {
		val newUris = playlist.uris.toMutableList()
		newUris.removeAt(index)
		onUpdatePlaylist(playlist.copy(uris = newUris))
	}

	Column(modifier = modifier) {
		LazyColumn(modifier = Modifier.weight(1f)) {
			items(playlist.uris.size) { index ->
				val uri = playlist.uris[index]
				val isSelected = index == playingIndex
				val trackActions = buildList {
					add(MenuAction("Play next", FxIcons.QueueMusic) { engine.playNext(uri) })
					add(MenuAction("Add to queue", FxIcons.QueueMusic) { engine.addToQueue(uri) })
					if (index > 0) add(MenuAction("Move Up", FxIcons.AudioFile) { swap(index, index - 1) })
					if (index < playlist.uris.size - 1) add(MenuAction("Move Down", FxIcons.AudioFile) { swap(index, index + 1) })
					add(MenuAction("Remove", FxIcons.Delete) { removeTrack(index) })
				}
				ListItem(
					headlineContent = {
						Text(
							trackTitle(uri, index, context),
							color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
						)
					},
					leadingContent = {
						if (isSelected) {
							Icon(FxIcons.PlayArrow, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary)
						} else {
							Icon(FxIcons.AudioFile, contentDescription = null)
						}
					},
					trailingContent = {
						// TalkBack users get these as custom actions on the row instead
						Box(modifier = Modifier.clearAndSetSemantics { }) {
							OverflowMenu("Track options", trackActions)
						}
					},
					modifier = Modifier
						.clickable { engine.playPlaylist(playlist, index) }
						.semantics {
							customActions = trackActions.map { action ->
								CustomAccessibilityAction(action.label) {
									action.onClick()
									true
								}
							}
						}
				)
			}
		}
		NowPlayingBar(
			playlist = playlist,
			engine = engine,
			hideDetails = hidePlayingDetails,
			moreActions = buildList {
				add(MenuAction("Search within playlist", FxIcons.Search) { showSearchDialog = true })
				add(MenuAction("Playlist info", FxIcons.Info) { showInfoDialog = true })
				add(MenuAction("Up next", FxIcons.QueueMusic) { showQueueDialog = true })
				add(MenuAction("Lyrics", FxIcons.Lyrics, onOpenLyrics))
				add(MenuAction("Equalizer", FxIcons.Equalizer, onOpenEqualizer))
				add(amplifierAction())
				add(MenuAction("Playback Effects", FxIcons.GraphicEq) { showEffectsDialog = true })
				if (canModifyPlaylist) {
					add(MenuAction(
						if (playlist.isHidden) "Unlock Playlist" else "Hide Playlist",
						if (playlist.isHidden) FxIcons.LockOpen else FxIcons.Lock,
						onHide
					))
					add(MenuAction("Remove Playlist", FxIcons.Delete, onRemove))
				}
			}
		)
	}
	if (showEffectsDialog) PlaybackEffectsDialog(onDismiss = { showEffectsDialog = false })
	if (showSearchDialog) {
		TrackSearchDialog(
			playlist = playlist,
			onPick = { index ->
				engine.playPlaylist(playlist, index)
				showSearchDialog = false
			},
			onDismiss = { showSearchDialog = false }
		)
	}
	if (showInfoDialog) PlaylistInfoDialog(playlist, onDismiss = { showInfoDialog = false })
	if (showQueueDialog) QueueDialog(engine, hideTitles = hidePlayingDetails, onDismiss = { showQueueDialog = false })
}

// Follows the setting, so a voice command that turns the amplifier on is reflected here
@Composable
private fun amplifierAction(): MenuAction {
	val context = LocalContext.current
	var enabled by remember { mutableStateOf(SettingsManager.isAmplifierEnabled(context)) }
	var level by remember { mutableIntStateOf(SettingsManager.getAmplifierLevel(context)) }
	DisposableEffect(context) {
		val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
			if (key == "amplifier_enabled") enabled = SettingsManager.isAmplifierEnabled(context)
			if (key == "amplifier_level") level = SettingsManager.getAmplifierLevel(context)
		}
		SettingsManager.registerChangeListener(context, listener)
		onDispose { SettingsManager.unregisterChangeListener(context, listener) }
	}
	return MenuAction(
		if (enabled) "Disable Amplifier" else "Enable Amplifier (Boost ${level / 100}dB)",
		if (enabled) FxIcons.VolumeUp else FxIcons.VolumeOff
	) {
		SettingsManager.setAmplifierEnabled(context, !enabled)
	}
}

@Composable
private fun NowPlayingBar(
	playlist: Playlist,
	engine: PlaybackEngine,
	hideDetails: Boolean,
	moreActions: List<MenuAction>
) {
	val state by engine.state.collectAsState()
	val progress by engine.progress.collectAsState()
	var isSeeking by remember { mutableStateOf(false) }
	var seekFraction by remember { mutableFloatStateOf(0f) }
	val fraction = if (isSeeking) seekFraction else progress.fraction
	val shownTime = if (isSeeking) (seekFraction * progress.durationMs).toLong() else progress.positionMs
	val hasQueue = state.queue.isNotEmpty()

	Surface(shadowElevation = 8.dp) {
		Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
			val label = when {
				!hasQueue && playlist.uris.isEmpty() -> "Now Playing: ${playlist.name} - No tracks here"
				!hasQueue -> "Ready to play ${playlist.name}"
				hideDetails -> "Now Playing: Locked playlist"
				else -> "Now Playing: ${state.currentTitle} - ${state.queueName}, track ${state.currentIndex + 1} of ${state.queue.size}"
			}
			Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.clearAndSetSemantics {
						contentDescription = "Track Progress"
						setProgress { target ->
							val targetMs = (target * progress.durationMs).toLong()
							if (targetMs >= 0) engine.seekTo(targetMs)
							true
						}
						progressBarRangeInfo = ProgressBarRangeInfo(current = fraction, range = 0f..1f)
						stateDescription = "${formatTime(shownTime)} of ${formatTime(progress.durationMs)}"
					}
			) {
				Slider(
					value = fraction,
					onValueChange = {
						isSeeking = true
						seekFraction = it
					},
					onValueChangeFinished = {
						isSeeking = false
						engine.seekTo((seekFraction * progress.durationMs).toLong())
					},
					enabled = hasQueue,
					modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}
				)
				Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
					Text(formatTime(shownTime))
					Text(formatTime(progress.durationMs))
				}
			}
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceEvenly,
				verticalAlignment = Alignment.CenterVertically
			) {
				IconButton(onClick = { engine.previous() }, enabled = state.currentIndex > 0) {
					Icon(FxIcons.SkipPrevious, contentDescription = "Previous")
				}
				IconButton(onClick = {
					if (hasQueue) {
						engine.togglePlayPause()
					} else if (playlist.uris.isNotEmpty()) {
						val (index, position) = engine.resumePointFor(playlist)
						engine.playPlaylist(playlist, index, position)
					}
				}) {
					Icon(
						if (state.isPlaying) FxIcons.Pause else FxIcons.PlayArrow,
						contentDescription = if (state.isPlaying) "Pause" else "Play"
					)
				}
				IconButton(onClick = { engine.next() }, enabled = hasQueue && state.currentIndex < state.queue.lastIndex) {
					Icon(FxIcons.SkipNext, contentDescription = "Next")
				}
				OverflowMenu("More Options", moreActions)
			}
		}
	}
}
