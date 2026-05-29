package com.boostofstudios.fukex

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.boostofstudios.fukex.data.AuthType
import com.boostofstudios.fukex.data.Playlist
import com.boostofstudios.fukex.data.PlaylistManager
import com.boostofstudios.fukex.ui.theme.FukeXTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.OutputStreamWriter
import java.util.UUID

class MainActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent {
			FukeXTheme {
				Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
					MainScreen(modifier = Modifier.padding(innerPadding))
				}
			}
		}
	}
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	var playlists by remember { mutableStateOf(PlaylistManager.loadPlaylists(context)) }
	var selectedTabIndex by remember { mutableIntStateOf(0) }
	var showOptionsDialog by remember { mutableStateOf(false) }
	var showNameDialog by remember { mutableStateOf(false) }
	var playlistName by remember { mutableStateOf("") }
	var tempPlaylistUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
	var isScanning by remember { mutableStateOf(false) }
	var showPlaylistActions by remember { mutableStateOf<Playlist?>(null) }
	var showPinDialog by remember { mutableStateOf<Playlist?>(null) }
	var pinInput by remember { mutableStateOf("") }
	var showPinSetupDialog by remember { mutableStateOf<Playlist?>(null) }
	var setupAuthType by remember { mutableStateOf(AuthType.PIN) }
	var unlockedPlaylistIds by remember { mutableStateOf(setOf<String>()) }
	var showConfirmUnlockDialog by remember { mutableStateOf<Playlist?>(null) }
	var showPlaylistSelectionDialog by remember { mutableStateOf(false) }
	var authError by remember { mutableStateOf("") }

	val filePickerLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenDocument()
	) { uri: Uri? ->
		uri?.let {
			try {
				context.contentResolver.takePersistableUriPermission(
					it,
					Intent.FLAG_GRANT_READ_URI_PERMISSION
				)
			} catch (e: Exception) {
				e.printStackTrace()
			}
			val newPlaylist = Playlist(
				id = UUID.randomUUID().toString(),
				name = it.lastPathSegment ?: "Single File",
				uris = listOf(it)
			)
			val newList = playlists + newPlaylist
			playlists = newList
			scope.launch {
				withContext(Dispatchers.IO) {
					PlaylistManager.savePlaylists(context, newList)
				}
			}
			selectedTabIndex = newList.count { !it.isHidden || it.id in unlockedPlaylistIds }
		}
	}
	val folderPickerLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenDocumentTree()
	) { uri: Uri? ->
		uri?.let { treeUri ->
			scope.launch {
				isScanning = true
				withContext(Dispatchers.IO) {
					try {
						context.contentResolver.takePersistableUriPermission(
							treeUri,
							Intent.FLAG_GRANT_READ_URI_PERMISSION
						)
						val documentFile = DocumentFile.fromTreeUri(context, treeUri)
						val mediaExtensions = setOf("mp3", "m4a", "wav", "flac", "ogg", "mp4", "mkv", "avi", "mov", "wmv", "webm")
						val files = documentFile?.listFiles()?.filter { file ->
							val extension = file.name?.substringAfterLast('.', "")?.lowercase()
							file.isFile && (
								file.type?.startsWith("video/") == true || 
								file.type?.startsWith("audio/") == true || 
								mediaExtensions.contains(extension)
							)
						}?.sortedBy { it.name }?.map { it.uri } ?: emptyList()
						tempPlaylistUris = files
					} catch (e: Exception) {
						e.printStackTrace()
					}
				}
				isScanning = false
				if (tempPlaylistUris.isNotEmpty()) {
					showNameDialog = true
				}
			}
		}
	}
	val importLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.OpenDocument()
	) { uri: Uri? ->
		uri?.let {
			scope.launch {
				try {
					val jsonString = withContext(Dispatchers.IO) {
						context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }
					}
					if (jsonString != null) {
						val json = JSONObject(jsonString)
						val urisJson = json.getJSONArray("uris")
						val uris = mutableListOf<Uri>()
						for (i in 0 until urisJson.length()) {
							uris.add(Uri.parse(urisJson.getString(i)))
						}
						val newPlaylist = Playlist(
							id = UUID.randomUUID().toString(),
							name = json.getString("name"),
							uris = uris
						)
						val newList = playlists + newPlaylist
						playlists = newList
						withContext(Dispatchers.IO) {
							PlaylistManager.savePlaylists(context, newList)
						}
						selectedTabIndex = newList.count { !it.isHidden || it.id in unlockedPlaylistIds }
					}
				} catch (e: Exception) {
					e.printStackTrace()
				}
			}
		}
	}
	val exportLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.CreateDocument("application/json")
	) { uri: Uri? ->
		uri?.let { exportUri ->
			showPlaylistActions?.let { playlist ->
				scope.launch(Dispatchers.IO) {
					try {
						val json = JSONObject()
						json.put("name", playlist.name)
						val urisJson = JSONArray()
						playlist.uris.forEach { urisJson.put(it.toString()) }
						json.put("uris", urisJson)
						context.contentResolver.openOutputStream(exportUri)?.use { os ->
							OutputStreamWriter(os).use { writer ->
								writer.write(json.toString())
							}
						}
					} catch (e: Exception) {
						e.printStackTrace()
					}
				}
			}
		}
	}
	val visiblePlaylists = playlists.filter { !it.isHidden || it.id in unlockedPlaylistIds }
	Column(modifier = modifier.fillMaxSize()) {
		if (isScanning) {
			LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
		}
		ScrollableTabRow(
			selectedTabIndex = selectedTabIndex,
			edgePadding = 16.dp,
			modifier = Modifier.fillMaxWidth(),
			indicator = { tabPositions ->
				val safeIndex = kotlin.math.min(selectedTabIndex, tabPositions.lastIndex)
				if (safeIndex >= 0) {
					TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[safeIndex]))
				}
			}
		) {
			Tab(
				selected = selectedTabIndex == 0,
				onClick = { selectedTabIndex = 0 },
				text = { Text("Home") }
			)
			visiblePlaylists.forEachIndexed { index, playlist ->
				Tab(
					selected = selectedTabIndex == index + 1,
					onClick = { selectedTabIndex = index + 1 },
					modifier = Modifier.semantics {
						customActions = listOf(
							CustomAccessibilityAction("Move Up") {
								val idx = playlists.indexOf(playlist)
								if (idx > 0) {
									val newList = playlists.toMutableList()
									java.util.Collections.swap(newList, idx, idx - 1)
									playlists = newList
									scope.launch(Dispatchers.IO) {
										PlaylistManager.savePlaylists(context, newList)
									}
									true
								} else false
							},
							CustomAccessibilityAction("Move Down") {
								val idx = playlists.indexOf(playlist)
								if (idx < playlists.size - 1) {
									val newList = playlists.toMutableList()
									java.util.Collections.swap(newList, idx, idx + 1)
									playlists = newList
									scope.launch(Dispatchers.IO) {
										PlaylistManager.savePlaylists(context, newList)
									}
									true
								} else false
							},
							CustomAccessibilityAction(if (playlist.isHidden) "Unlock" else "Hide") {
								if (playlist.isHidden) {
									showConfirmUnlockDialog = playlist
								} else {
									showPinSetupDialog = playlist
								}
								true
							},
							CustomAccessibilityAction("Export") {
								showPlaylistActions = playlist
								exportLauncher.launch("${playlist.name}.json")
								true
							},
							CustomAccessibilityAction("Remove") {
								val newList = playlists.filter { it.id != playlist.id }
								playlists = newList
								scope.launch(Dispatchers.IO) {
									PlaylistManager.savePlaylists(context, newList)
								}
								selectedTabIndex = 0
								true
							}
						)
					},
					text = {
						Row(verticalAlignment = Alignment.CenterVertically) {
							if (playlist.isHidden) {
								Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
								Spacer(Modifier.width(4.dp))
							}
							Text(playlist.name)
							IconButton(
								onClick = { showPlaylistActions = playlist },
								modifier = Modifier.clearAndSetSemantics { }
							) {
								Icon(Icons.Default.MoreVert, contentDescription = null)
							}
						}
					}
				)
			}
		}
		Box(modifier = Modifier.weight(1f)) {
			if (selectedTabIndex == 0) {
				Column(
					modifier = Modifier.fillMaxSize().padding(16.dp),
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.Center
				) {
					Text(
						text = "FukeX",
						style = MaterialTheme.typography.headlineLarge,
						modifier = Modifier.semantics { contentDescription = "FukeX" }
					)
					Spacer(modifier = Modifier.height(32.dp))
					Row {
						Button(onClick = { showOptionsDialog = true }) {
							Icon(Icons.Default.PlayArrow, contentDescription = null)
							Spacer(Modifier.width(8.dp))
							Text("Play")
						}
						Spacer(Modifier.width(16.dp))
						Button(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
							Icon(Icons.Default.FileDownload, contentDescription = null)
							Spacer(Modifier.width(8.dp))
							Text("Import Playlist")
						}
					}
					if (playlists.any { it.isHidden && it.id !in unlockedPlaylistIds }) {
						Spacer(modifier = Modifier.height(16.dp))
						TextButton(onClick = { 
							showPlaylistSelectionDialog = true
						}) {
							Text("View Locked Playlists")
						}
					}
				}
			} else if (selectedTabIndex <= visiblePlaylists.size) {
				val currentPlaylist = visiblePlaylists[selectedTabIndex - 1]
				key(currentPlaylist.id) {
					VLCPlayer(
						playlist = currentPlaylist,
						modifier = Modifier.fillMaxSize(),
						onProgressUpdate = { index, pos ->
							scope.launch(Dispatchers.IO) {
								PlaylistManager.updatePlaylistProgress(context, currentPlaylist.id, index, pos)
							}
						},
						onBack = { selectedTabIndex = 0 },
						onHide = {
							if (currentPlaylist.isHidden) {
								showConfirmUnlockDialog = currentPlaylist
							} else {
								showPinSetupDialog = currentPlaylist
							}
						},
						onRemove = {
							val newList = playlists.filter { it.id != currentPlaylist.id }
							playlists = newList
							scope.launch(Dispatchers.IO) {
								PlaylistManager.savePlaylists(context, newList)
							}
							selectedTabIndex = 0
						}
					)
				}
			}
		}
	}
	showPlaylistActions?.let { playlist ->
		AlertDialog(
			modifier = Modifier.semantics { paneTitle = "Actions: ${playlist.name}" },
			onDismissRequest = { showPlaylistActions = null },
			title = { Text("Actions: ${playlist.name}") },
			text = {
				Column {
					ListItem(
						headlineContent = { Text("Move Up") },
						leadingContent = { Icon(Icons.Default.ArrowUpward, null) },
						modifier = Modifier.clickable {
							val idx = playlists.indexOf(playlist)
							if (idx > 0) {
								val newList = playlists.toMutableList()
								java.util.Collections.swap(newList, idx, idx - 1)
								playlists = newList
								scope.launch(Dispatchers.IO) {
									PlaylistManager.savePlaylists(context, newList)
								}
							}
							showPlaylistActions = null
						}
					)
					ListItem(
						headlineContent = { Text("Move Down") },
						leadingContent = { Icon(Icons.Default.ArrowDownward, null) },
						modifier = Modifier.clickable {
							val idx = playlists.indexOf(playlist)
							if (idx < playlists.size - 1) {
								val newList = playlists.toMutableList()
								java.util.Collections.swap(newList, idx, idx + 1)
								playlists = newList
								scope.launch(Dispatchers.IO) {
									PlaylistManager.savePlaylists(context, newList)
								}
							}
							showPlaylistActions = null
						}
					)
					if (playlist.isHidden) {
						ListItem(
							headlineContent = { Text("Unlock Playlist") },
							leadingContent = { Icon(Icons.Default.LockOpen, null) },
							modifier = Modifier.clickable {
								showConfirmUnlockDialog = playlist
								showPlaylistActions = null
							}
						)
					} else {
						ListItem(
							headlineContent = { Text("Hide Playlist") },
							leadingContent = { Icon(Icons.Default.Lock, null) },
							modifier = Modifier.clickable {
								showPinSetupDialog = playlist
								showPlaylistActions = null
							}
						)
					}
					ListItem(
						headlineContent = { Text("Export Playlist") },
						leadingContent = { Icon(Icons.Default.FileUpload, null) },
						modifier = Modifier.clickable {
							exportLauncher.launch("${playlist.name}.json")
						}
					)
					ListItem(
						headlineContent = { Text("Remove") },
						leadingContent = { Icon(Icons.Default.Delete, null) },
						modifier = Modifier.clickable {
							val newList = playlists.filter { it.id != playlist.id }
							playlists = newList
							scope.launch(Dispatchers.IO) {
								PlaylistManager.savePlaylists(context, newList)
							}
							selectedTabIndex = 0
							showPlaylistActions = null
						}
					)
				}
			},
			confirmButton = {}
		)
	}
	if (showPlaylistSelectionDialog) {
		AlertDialog(
			modifier = Modifier.semantics { paneTitle = "Select Playlist to View" },
			onDismissRequest = { showPlaylistSelectionDialog = false },
			title = { Text("Select Playlist to View") },
			text = {
				Column {
					playlists.filter { it.isHidden && it.id !in unlockedPlaylistIds }.forEach { playlist ->
						ListItem(
							headlineContent = { Text(playlist.name) },
							modifier = Modifier.clickable {
								showPinDialog = playlist
								showPlaylistSelectionDialog = false
							}
						)
					}
				}
			},
			confirmButton = {
				TextButton(onClick = { showPlaylistSelectionDialog = false }) { Text("Cancel") }
			}
		)
	}
	showPinSetupDialog?.let { playlist ->
		var expanded by remember { mutableStateOf(false) }
		AlertDialog(
			modifier = Modifier.semantics { paneTitle = "Set PIN/Password to hide playlist" },
			onDismissRequest = { showPinSetupDialog = null },
			title = { Text("Set PIN/Password to hide playlist") },
			text = {
				Column {
					Text("Enter a PIN or Password to hide this playlist. Note: If you forget it, the playlist cannot be recovered.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
					Spacer(Modifier.height(8.dp))
					Box {
						OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
							Text("Type: ${setupAuthType.name}")
						}
						DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
							DropdownMenuItem(
								text = { Text("PIN") },
								onClick = { setupAuthType = AuthType.PIN; expanded = false }
							)
							DropdownMenuItem(
								text = { Text("Password") },
								onClick = { setupAuthType = AuthType.PASSWORD; expanded = false }
							)
						}
					}
					Spacer(Modifier.height(8.dp))
					TextField(
						value = pinInput,
						onValueChange = { pinInput = it },
						placeholder = { Text(if (setupAuthType == AuthType.PIN) "4-6 digit PIN" else "Password") },
						keyboardOptions = KeyboardOptions(
							keyboardType = if (setupAuthType == AuthType.PIN) KeyboardType.Number else KeyboardType.Password
						),
						modifier = Modifier.fillMaxWidth()
					)
				}
			},
			confirmButton = {
				Button(onClick = {
					if (pinInput.length >= 4) {
						val newList = playlists.map { 
							if (it.id == playlist.id) it.copy(isHidden = true, pin = pinInput, authType = setupAuthType) else it 
						}
						playlists = newList
						scope.launch(Dispatchers.IO) {
							PlaylistManager.savePlaylists(context, newList)
						}
						selectedTabIndex = 0
						pinInput = ""
						showPinSetupDialog = null
					}
				}) { Text("Hide") }
			}
		)
	}
	showPinDialog?.let { playlist ->
		AlertDialog(
			modifier = Modifier.semantics { paneTitle = "Unlock ${playlist.name}" },
			onDismissRequest = { 
				showPinDialog = null
				authError = ""
				pinInput = ""
			},
			title = { Text("Unlock ${playlist.name}") },
			text = {
				Column {
					if (authError.isNotEmpty()) {
						Text(authError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
						Spacer(Modifier.height(4.dp))
					}
					TextField(
						value = pinInput,
						onValueChange = { pinInput = it; authError = "" },
						placeholder = { Text("Enter ${playlist.authType.name}") },
						keyboardOptions = KeyboardOptions(
							keyboardType = if (playlist.authType == AuthType.PIN) KeyboardType.Number else KeyboardType.Password
						),
						modifier = Modifier.fillMaxWidth()
					)
				}
			},
			confirmButton = {
				Button(onClick = {
					if (pinInput == playlist.pin) {
						val nextUnlockedIds = unlockedPlaylistIds + playlist.id
						unlockedPlaylistIds = nextUnlockedIds
						authError = ""
						pinInput = ""
						showPinDialog = null
						
						// Calculate target index in the next state of visiblePlaylists
						val nextVisible = playlists.filter { !it.isHidden || it.id in nextUnlockedIds }
						val targetIdx = nextVisible.indexOfFirst { it.id == playlist.id }
						if (targetIdx != -1) {
							selectedTabIndex = targetIdx + 1
						}
					} else {
						authError = "Incorrect ${playlist.authType.name}"
					}
				}) { Text("View") }
			}
		)
	}
	showConfirmUnlockDialog?.let { playlist ->
		AlertDialog(
			modifier = Modifier.semantics { paneTitle = "Permanently Unlock ${playlist.name}" },
			onDismissRequest = { 
				showConfirmUnlockDialog = null
				authError = ""
				pinInput = ""
			},
			title = { Text("Permanently Unlock ${playlist.name}") },
			text = {
				Column {
					if (authError.isNotEmpty()) {
						Text(authError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
						Spacer(Modifier.height(4.dp))
					}
					Text("Enter your ${playlist.authType.name} to permanently unlock this playlist.")
					Spacer(Modifier.height(8.dp))
					TextField(
						value = pinInput,
						onValueChange = { pinInput = it; authError = "" },
						placeholder = { Text("Enter ${playlist.authType.name}") },
						keyboardOptions = KeyboardOptions(
							keyboardType = if (playlist.authType == AuthType.PIN) KeyboardType.Number else KeyboardType.Password
						),
						modifier = Modifier.fillMaxWidth()
					)
				}
			},
			confirmButton = {
				Button(onClick = {
					if (pinInput == playlist.pin) {
						val newList = playlists.map { 
							if (it.id == playlist.id) it.copy(isHidden = false) else it 
						}
						playlists = newList
						unlockedPlaylistIds = unlockedPlaylistIds - playlist.id
						scope.launch(Dispatchers.IO) {
							PlaylistManager.savePlaylists(context, newList)
						}
						authError = ""
						pinInput = ""
						showConfirmUnlockDialog = null
					} else {
						authError = "Incorrect ${playlist.authType.name}"
					}
				}) { Text("Unlock") }
			}
		)
	}
	if (showOptionsDialog) {
		AlertDialog(
			modifier = Modifier.semantics { paneTitle = "Select what you would like to play" },
			onDismissRequest = { showOptionsDialog = false },
			title = { Text("Select what you would like to play") },
			text = {
				Column {
					Button(
						onClick = {
							showOptionsDialog = false
							folderPickerLauncher.launch(null)
						},
						modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
					) {
						Text("Playlist (Folder)")
					}
					Button(
						onClick = {
							showOptionsDialog = false
							filePickerLauncher.launch(arrayOf("*/*"))
						},
						modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
					) {
						Text("File")
					}
				}
			},
			confirmButton = {
				TextButton(onClick = { showOptionsDialog = false }) {
					Text("Cancel")
				}
			}
		)
	}
	if (showNameDialog) {
		AlertDialog(
			modifier = Modifier.semantics { paneTitle = "Name your playlist" },
			onDismissRequest = { showNameDialog = false },
			title = { Text("Name your playlist") },
			text = {
				TextField(
					value = playlistName,
					onValueChange = { playlistName = it },
					placeholder = { Text("Playlist Name") },
					singleLine = true,
					modifier = Modifier.fillMaxWidth()
				)
			},
			confirmButton = {
				Button(
					onClick = {
						if (playlistName.isNotBlank()) {
							val newPlaylist = Playlist(
								id = UUID.randomUUID().toString(),
								name = playlistName,
								uris = tempPlaylistUris
							)
							val newList = playlists + newPlaylist
							playlists = newList
							scope.launch(Dispatchers.IO) {
								PlaylistManager.savePlaylists(context, newList)
							}
							selectedTabIndex = newList.count { !it.isHidden || it.id in unlockedPlaylistIds }
							showNameDialog = false
							playlistName = ""
						}
					}
				) {
					Text("Create")
				}
			},
			dismissButton = {
				TextButton(onClick = { showNameDialog = false }) {
					Text("Cancel")
				}
			}
		)
	}
}


@Composable
fun VLCPlayer(
	playlist: Playlist,
	modifier: Modifier = Modifier,
	onProgressUpdate: (Int, Float) -> Unit,
	onBack: () -> Unit,
	onHide: () -> Unit,
	onRemove: () -> Unit
) {
	val context = LocalContext.current
	val exoPlayer = remember { ExoPlayer.Builder(context).build() }
	var currentIndex by remember { mutableIntStateOf(playlist.lastIndex) }
	var isPlaying by remember { mutableStateOf(false) }
	var progress by remember { mutableFloatStateOf(0f) }
	var currentTime by remember { mutableLongStateOf(0L) }
	var totalTime by remember { mutableLongStateOf(0L) }
	var isInitialPlayback by remember { mutableStateOf(true) }
	var showMoreOptions by remember { mutableStateOf(false) }
	var showSearchDialog by remember { mutableStateOf(false) }
	var showInfoDialog by remember { mutableStateOf(false) }
	var searchQuery by remember { mutableStateOf("") }
	var playlistSize by remember { mutableLongStateOf(0L) }

	LaunchedEffect(showInfoDialog) {
		if (showInfoDialog) {
			withContext(Dispatchers.IO) {
				var totalSize = 0L
				playlist.uris.forEach { uri ->
					try {
						context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
							totalSize += it.length
						}
					} catch (e: Exception) {
						// Ignore files that can't be read
					}
				}
				playlistSize = totalSize
			}
		}
	}

	fun playIndex(index: Int) {
		if (index in playlist.uris.indices) {
			val uri = playlist.uris[index]
			exoPlayer.setMediaItem(MediaItem.fromUri(uri))
			exoPlayer.prepare()
			exoPlayer.playWhenReady = true
			currentIndex = index
		}
	}

	LaunchedEffect(currentIndex) {
		playIndex(currentIndex)
	}

	LaunchedEffect(isPlaying) {
		if (isPlaying && isInitialPlayback && playlist.lastPosition > 0f) {
			val dur = exoPlayer.duration
			if (dur > 0) {
				exoPlayer.seekTo((playlist.lastPosition * dur).toLong())
			}
			isInitialPlayback = false
		}
	}

	LaunchedEffect(currentIndex, isPlaying) {
		while (isPlaying) {
			val pos = exoPlayer.currentPosition
			val dur = exoPlayer.duration
			currentTime = pos
			totalTime = if (dur > 0) dur else 0L
			progress = if (dur > 0) pos.toFloat() / dur.toFloat() else 0f
			kotlinx.coroutines.delay(500)
		}
	}

	LaunchedEffect(currentIndex, isPlaying) {
		while (isPlaying) {
			kotlinx.coroutines.delay(5000)
			onProgressUpdate(currentIndex, progress)
		}
	}

	DisposableEffect(currentIndex) {
		onDispose {
			onProgressUpdate(currentIndex, progress)
		}
	}

	DisposableEffect(Unit) {
		val listener = object : Player.Listener {
			override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
				isPlaying = isPlayingChanged
			}
			override fun onPlaybackStateChanged(playbackState: Int) {
				if (playbackState == Player.STATE_ENDED) {
					if (currentIndex < playlist.uris.size - 1) {
						currentIndex++
					} else {
						onBack()
					}
				}
			}
		}
		exoPlayer.addListener(listener)
		onDispose {
			exoPlayer.release()
		}
	}

	Column(modifier = modifier) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			IconButton(onClick = onBack) {
				Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
			}
			Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
				Text("Now Playing: ${playlist.name}", style = MaterialTheme.typography.labelSmall)
				Text(
					text = "Track ${currentIndex + 1} of ${playlist.uris.size}",
					style = MaterialTheme.typography.bodyMedium
				)
			}
		}
		AndroidView(
			factory = { ctx ->
				PlayerView(ctx).apply {
					player = exoPlayer
					useController = false
					layoutParams = ViewGroup.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT,
						ViewGroup.LayoutParams.MATCH_PARENT
					)
				}
			},
			modifier = Modifier.weight(1f)
		)
		Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
			Slider(
				value = progress,
				onValueChange = { 
					progress = it
					val newPos = (it * exoPlayer.duration).toLong()
					if (newPos >= 0) exoPlayer.seekTo(newPos)
				},
				modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Seek" }
			)
			Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
				Text(formatTime(currentTime))
				Text(formatTime(totalTime))
			}
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.SpaceEvenly,
				verticalAlignment = Alignment.CenterVertically
			) {
				IconButton(
					onClick = { if (currentIndex > 0) currentIndex-- },
					enabled = currentIndex > 0
				) {
					Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
				}
				IconButton(onClick = {
					val newTime = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
					exoPlayer.seekTo(newTime)
				}) {
					Icon(Icons.Default.FastRewind, contentDescription = "Rewind")
				}
				IconButton(onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() }) {
					Icon(
						if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 
						contentDescription = if (isPlaying) "Pause" else "Play"
					)
				}
				IconButton(onClick = {
					val dur = exoPlayer.duration
					val newTime = (exoPlayer.currentPosition + 10000).coerceAtMost(if (dur > 0) dur else 0)
					exoPlayer.seekTo(newTime)
				}) {
					Icon(Icons.Default.FastForward, contentDescription = "Forward")
				}
				IconButton(
					onClick = { if (currentIndex < playlist.uris.size - 1) currentIndex++ },
					enabled = currentIndex < playlist.uris.size - 1
				) {
					Icon(Icons.Default.SkipNext, contentDescription = "Next")
				}
				Box {
					IconButton(
						onClick = { showMoreOptions = true },
						modifier = Modifier.semantics {
							contentDescription = "More Options"
							customActions = listOf(
								CustomAccessibilityAction("Search within playlist") {
									showSearchDialog = true
									true
								},
								CustomAccessibilityAction("Playlist info") {
									showInfoDialog = true
									true
								},
								CustomAccessibilityAction(if (playlist.isHidden) "Unlock" else "Hide") {
									onHide()
									true
								},
								CustomAccessibilityAction("Remove") {
									onRemove()
									true
								}
							)
						}
					) {
						Icon(Icons.Default.MoreVert, contentDescription = null)
					}
					DropdownMenu(expanded = showMoreOptions, onDismissRequest = { showMoreOptions = false }) {
						DropdownMenuItem(
							text = { Text("Search within playlist") },
							onClick = { showSearchDialog = true; showMoreOptions = false },
							leadingIcon = { Icon(Icons.Default.Search, null) }
						)
						DropdownMenuItem(
							text = { Text("Playlist info") },
							onClick = { showInfoDialog = true; showMoreOptions = false },
							leadingIcon = { Icon(Icons.Default.Info, null) }
						)
						DropdownMenuItem(
							text = { Text(if (playlist.isHidden) "Unlock Playlist" else "Hide Playlist") },
							onClick = { onHide(); showMoreOptions = false },
							leadingIcon = { Icon(if (playlist.isHidden) Icons.Default.LockOpen else Icons.Default.Lock, null) }
						)
						DropdownMenuItem(
							text = { Text("Remove Playlist") },
							onClick = { onRemove(); showMoreOptions = false },
							leadingIcon = { Icon(Icons.Default.Delete, null) }
						)
					}
				}
			}
		}
	}

	if (showSearchDialog) {
		AlertDialog(
			modifier = Modifier.semantics { paneTitle = "Search Track" },
			onDismissRequest = { showSearchDialog = false },
			title = { Text("Search Track") },
			text = {
				Column {
					TextField(
						value = searchQuery,
						onValueChange = { searchQuery = it },
						placeholder = { Text("Track name...") },
						modifier = Modifier.fillMaxWidth()
					)
					Spacer(Modifier.height(8.dp))
					val filteredTracks = playlist.uris.mapIndexed { index, uri -> index to uri }
						.filter { it.second.lastPathSegment?.contains(searchQuery, ignoreCase = true) == true }

					LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
						items(filteredTracks) { (index, uri) ->
							ListItem(
								headlineContent = { Text(uri.lastPathSegment ?: "Track ${index + 1}") },
								modifier = Modifier.clickable {
									currentIndex = index
									showSearchDialog = false
									searchQuery = ""
								}
							)
						}
					}
				}
			},
			confirmButton = {
				TextButton(onClick = { showSearchDialog = false; searchQuery = "" }) { Text("Close") }
			}
		)
	}

	if (showInfoDialog) {
		AlertDialog(
			modifier = Modifier.semantics { paneTitle = "Playlist Info" },
			onDismissRequest = { showInfoDialog = false },
			title = { Text("Playlist Info") },
			text = {
				Column {
					Text("Name: ${playlist.name}")
					Text("Total Tracks: ${playlist.uris.size}")
					Text("Total Size: ${formatSize(playlistSize)}")
					Text("Encrypted: No")
				}
			},
			confirmButton = {
				TextButton(onClick = { showInfoDialog = false }) { Text("OK") }
			}
		)
	}
}

fun formatTime(ms: Long): String {
	val totalSeconds = ms / 1000
	val minutes = totalSeconds / 60
	val seconds = totalSeconds % 60
	return "%02d:%02d".format(minutes, seconds)
}

fun formatSize(size: Long): String {
	if (size <= 0) return "0 B"
	val units = arrayOf("B", "KB", "MB", "GB", "TB")
	val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
	return "%.2f %s".format(size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}