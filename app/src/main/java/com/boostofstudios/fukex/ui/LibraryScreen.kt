package com.boostofstudios.fukex.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.boostofstudios.fukex.data.LibraryGrouping
import com.boostofstudios.fukex.data.LibraryTrack
import com.boostofstudios.fukex.data.MediaLibrary
import com.boostofstudios.fukex.data.Playlist
import com.boostofstudios.fukex.formatTime
import com.boostofstudios.fukex.playback.PlaybackEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun audioPermission() =
	if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasLibraryAccess(context: android.content.Context): Boolean =
	ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED ||
		(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && android.os.Environment.isExternalStorageManager())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
	engine: PlaybackEngine,
	playlists: List<Playlist>,
	onAddToPlaylist: (Playlist, List<Uri>) -> Unit,
	onBack: () -> Unit
) {
	val context = LocalContext.current
	var hasAccess by remember { mutableStateOf(hasLibraryAccess(context)) }
	var tracks by remember { mutableStateOf<List<LibraryTrack>?>(null) }
	var grouping by rememberSaveable { mutableStateOf(LibraryGrouping.SONGS) }
	var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
	var query by rememberSaveable { mutableStateOf("") }
	var pendingAdd by remember { mutableStateOf<List<Uri>?>(null) }
	val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
		hasAccess = granted || hasLibraryAccess(context)
	}
	androidx.activity.compose.BackHandler {
		if (openGroup != null) openGroup = null else onBack()
	}
	LaunchedEffect(hasAccess) {
		if (hasAccess) tracks = withContext(Dispatchers.IO) { MediaLibrary.scan(context) }
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(openGroup ?: "Music Library", modifier = Modifier.semantics { heading() }) },
				navigationIcon = {
					IconButton(onClick = { if (openGroup != null) openGroup = null else onBack() }) {
						Icon(FxIcons.ArrowBack, contentDescription = if (openGroup != null) "Back to ${grouping.label}" else "Back to playlists")
					}
				}
			)
		}
	) { padding ->
		Column(modifier = Modifier.fillMaxSize().padding(padding)) {
			val all = tracks
			when {
				!hasAccess -> Column(Modifier.padding(16.dp)) {
					Text("FukeX needs permission to read your music to show your library.")
					Button(onClick = { permissionLauncher.launch(audioPermission()) }, modifier = Modifier.padding(top = 16.dp)) {
						Text("Allow access")
					}
				}
				all == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
				all.isEmpty() -> Text("No music found on this device.", modifier = Modifier.padding(16.dp))
				else -> {
					if (openGroup == null) {
						TabRow(selectedTabIndex = grouping.ordinal) {
							LibraryGrouping.entries.forEach { g ->
								Tab(selected = grouping == g, onClick = { grouping = g }, text = { Text(g.label) })
							}
						}
					}
					TextField(
						value = query,
						onValueChange = { query = it },
						placeholder = { Text("Search songs, artists, albums") },
						singleLine = true,
						modifier = Modifier.fillMaxWidth().padding(8.dp)
					)
					val filtered = if (query.isBlank()) all else all.filter {
						it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) || it.genre.contains(query, true)
					}
					val group = openGroup
					if (grouping == LibraryGrouping.SONGS || group != null) {
						val songs = if (group == null) filtered else filtered.filter { MediaLibrary.groupKey(it, grouping) == group }
						SongList(
							songs = songs,
							queueName = group ?: "Music Library",
							engine = engine,
							showGroupActions = group != null,
							onAdd = { pendingAdd = it }
						)
					} else {
						GroupList(filtered, grouping) { openGroup = it }
					}
				}
			}
		}
	}
	pendingAdd?.let { uris ->
		AddToPlaylistDialog(
			playlists = playlists,
			onPick = { playlist ->
				onAddToPlaylist(playlist, uris)
				android.widget.Toast.makeText(context, "Added to ${playlist.name}", android.widget.Toast.LENGTH_SHORT).show()
				pendingAdd = null
			},
			onDismiss = { pendingAdd = null }
		)
	}
}

@Composable
private fun GroupList(tracks: List<LibraryTrack>, grouping: LibraryGrouping, onOpen: (String) -> Unit) {
	val groups = remember(tracks, grouping) {
		tracks.groupBy { MediaLibrary.groupKey(it, grouping) }.toSortedMap(String.CASE_INSENSITIVE_ORDER).toList()
	}
	val icon = when (grouping) {
		LibraryGrouping.ARTISTS -> FxIcons.Person
		LibraryGrouping.ALBUMS -> FxIcons.Album
		else -> FxIcons.Category
	}
	LazyColumn {
		items(groups.size) { i ->
			val (name, songs) = groups[i]
			ListItem(
				headlineContent = { Text(name) },
				supportingContent = { Text("${songs.size} ${if (songs.size == 1) "song" else "songs"}") },
				leadingContent = { Icon(icon, null) },
				modifier = Modifier.clickable { onOpen(name) }
			)
		}
	}
}

@Composable
private fun SongList(
	songs: List<LibraryTrack>,
	queueName: String,
	engine: PlaybackEngine,
	showGroupActions: Boolean,
	onAdd: (List<Uri>) -> Unit
) {
	LazyColumn {
		if (showGroupActions && songs.isNotEmpty()) {
			item {
				Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					Button(onClick = { engine.playTracks(queueName, songs.map { it.uri }, 0) }) {
						Icon(FxIcons.PlayArrow, null)
						Text("Play all")
					}
					OutlinedButton(onClick = { onAdd(songs.map { it.uri }) }) {
						Text("Add all to playlist")
					}
				}
			}
		}
		items(songs.size) { i ->
			val song = songs[i]
			val actions = listOf(
				MenuAction("Play next", FxIcons.QueueMusic) { engine.playNext(song.uri) },
				MenuAction("Add to queue", FxIcons.QueueMusic) { engine.addToQueue(song.uri) },
				MenuAction("Add to playlist", FxIcons.PlaylistAdd) { onAdd(listOf(song.uri)) }
			)
			ListItem(
				headlineContent = { Text(song.title) },
				supportingContent = { Text("${song.artist} · ${song.album} · ${formatTime(song.durationMs)}") },
				leadingContent = { Icon(FxIcons.MusicNote, null) },
				trailingContent = {
					Box(modifier = Modifier.clearAndSetSemantics { }) { OverflowMenu("Song options", actions) }
				},
				modifier = Modifier
					.clickable { engine.playTracks(queueName, songs.map { it.uri }, i) }
					.semantics {
						customActions = actions.map { action ->
							CustomAccessibilityAction(action.label) {
								action.onClick()
								true
							}
						}
					}
			)
		}
	}
}
