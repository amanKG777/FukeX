package com.boostofstudios.fukex.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.boostofstudios.fukex.data.Playlist
import com.boostofstudios.fukex.data.SettingsManager
import com.boostofstudios.fukex.formatSize
import com.boostofstudios.fukex.playback.PlaybackEngine
import com.boostofstudios.fukex.trackTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// A slider TalkBack reads as one control: label, value and swipe-to-adjust
@Composable
internal fun AccessibleSlider(
	label: String,
	value: Float,
	range: ClosedFloatingPointRange<Float>,
	steps: Int,
	stateText: String,
	onChange: (Float) -> Unit
) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 8.dp)
			.clearAndSetSemantics {
				contentDescription = label
				setProgress { target ->
					onChange(target.coerceIn(range.start, range.endInclusive))
					true
				}
				progressBarRangeInfo = ProgressBarRangeInfo(current = value, range = range, steps = steps)
				stateDescription = stateText
			}
	) {
		Text("$label: $stateText", style = MaterialTheme.typography.bodyLarge)
		Slider(
			value = value,
			onValueChange = onChange,
			valueRange = range,
			steps = steps,
			modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}
		)
	}
}

// Settings are written straight to SettingsManager; PlaybackEngine applies them as they change.
@Composable
fun PlaybackEffectsDialog(onDismiss: () -> Unit) {
	val context = LocalContext.current
	var speed by remember { mutableFloatStateOf(SettingsManager.getPlaybackSpeed(context)) }
	var pitch by remember { mutableFloatStateOf(SettingsManager.getPlaybackPitch(context)) }
	var bass by remember { mutableIntStateOf(SettingsManager.getBassBoost(context)) }
	AlertDialog(
		modifier = Modifier.semantics { paneTitle = "Playback Effects" },
		onDismissRequest = onDismiss,
		title = { Text("Playback Effects") },
		text = {
			Column {
				AccessibleSlider("Playback Speed", speed, 0.5f..2.0f, 15, String.format("%.2fx", speed)) {
					speed = (Math.round(it * 10f) / 10f).coerceIn(0.5f, 2.0f)
					SettingsManager.setPlaybackSpeed(context, speed)
				}
				AccessibleSlider("Playback Pitch", pitch, 0.5f..2.0f, 15, String.format("%.2fx", pitch)) {
					pitch = (Math.round(it * 10f) / 10f).coerceIn(0.5f, 2.0f)
					SettingsManager.setPlaybackPitch(context, pitch)
				}
				AccessibleSlider("Bass Boost", bass.toFloat(), 0f..1000f, 10, if (bass == 0) "Disabled" else "Level $bass") {
					bass = (Math.round(it / 100f) * 100).coerceIn(0, 1000)
					SettingsManager.setBassBoost(context, bass)
				}
			}
		},
		confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
	)
}

@Composable
fun TrackSearchDialog(playlist: Playlist, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
	val context = LocalContext.current
	var query by remember { mutableStateOf("") }
	AlertDialog(
		modifier = Modifier.semantics { paneTitle = "Search Track" },
		onDismissRequest = onDismiss,
		title = { Text("Search Track") },
		text = {
			Column {
				TextField(
					value = query,
					onValueChange = { query = it },
					placeholder = { Text("Track name...") },
					modifier = Modifier.fillMaxWidth()
				)
				Spacer(Modifier.height(8.dp))
				val matches = playlist.uris.mapIndexed { index, uri -> index to trackTitle(uri, index, context) }
					.filter { it.second.contains(query, ignoreCase = true) }
				LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
					items(matches) { (index, title) ->
						ListItem(
							headlineContent = { Text(title) },
							modifier = Modifier.clickable { onPick(index) }
						)
					}
				}
			}
		},
		confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
	)
}

@Composable
fun PlaylistInfoDialog(playlist: Playlist, onDismiss: () -> Unit) {
	val context = LocalContext.current
	var totalSize by remember { mutableLongStateOf(0L) }
	var unmeasured by remember { mutableIntStateOf(0) }
	LaunchedEffect(playlist.uris) {
		val result = withContext(Dispatchers.IO) {
			var size = 0L
			var missing = 0
			playlist.uris.forEach { uri ->
				val length = when {
					uri.scheme == "file" -> uri.path?.let { java.io.File(it).length() } ?: 0L
					uri.scheme?.startsWith("smb") == true -> 0L
					else -> try {
						context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
					} catch (e: Exception) {
						0L
					}
				}
				if (length > 0L) size += length else missing++
			}
			size to missing
		}
		totalSize = result.first
		unmeasured = result.second
	}
	AlertDialog(
		modifier = Modifier.semantics { paneTitle = "Playlist Info" },
		onDismissRequest = onDismiss,
		title = { Text("Playlist Info") },
		text = {
			Column {
				Text("Name: ${playlist.name}")
				Text("Total Tracks: ${playlist.uris.size}")
				Text("Total Size: ${formatSize(totalSize)}")
				if (unmeasured > 0) {
					Text("$unmeasured track(s) could not be measured, such as tracks on an SMB share.")
				}
				Text("Locked: ${if (playlist.isHidden) "Yes" else "No"}")
			}
		},
		confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
	)
}

@Composable
fun QueueDialog(engine: PlaybackEngine, hideTitles: Boolean, onDismiss: () -> Unit) {
	val context = LocalContext.current
	val state by engine.state.collectAsState()
	AlertDialog(
		modifier = Modifier.semantics { paneTitle = "Up Next" },
		onDismissRequest = onDismiss,
		title = { Text("Up Next: ${if (hideTitles) "Locked playlist" else state.queueName}") },
		text = {
			if (state.queue.isEmpty() || hideTitles) {
				Text(if (hideTitles) "Unlock the playlist to see its queue." else "Nothing is queued.")
			} else {
				LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
					items(state.queue.size) { i ->
						val item = state.queue[i]
						val title = trackTitle(item.uri, item.playlistIndex ?: i, context)
						val isCurrent = i == state.currentIndex
						ListItem(
							headlineContent = {
								Text(title, color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
							},
							supportingContent = when {
								isCurrent -> { { Text("Now playing") } }
								item.playlistIndex == null -> { { Text("Queued") } }
								else -> null
							},
							leadingContent = {
								Icon(if (isCurrent) FxIcons.PlayArrow else FxIcons.QueueMusic, contentDescription = null)
							},
							trailingContent = {
								if (!isCurrent) {
									IconButton(onClick = { engine.removeFromQueue(i) }, modifier = Modifier.clearAndSetSemantics { }) {
										Icon(FxIcons.Close, contentDescription = null)
									}
								}
							},
							modifier = Modifier
								.clickable { engine.playIndex(i) }
								.semantics {
									if (!isCurrent) {
										customActions = listOf(CustomAccessibilityAction("Remove from queue") {
											engine.removeFromQueue(i)
											true
										})
									}
								}
						)
					}
				}
			}
		},
		confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
	)
}
