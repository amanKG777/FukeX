package com.boostofstudios.fukex.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.boostofstudios.fukex.BuildConfig
import com.boostofstudios.fukex.DocumentationScreen
import com.boostofstudios.fukex.FilePickerScreen
import com.boostofstudios.fukex.MainActivity
import com.boostofstudios.fukex.SettingsScreen
import com.boostofstudios.fukex.SmbPickerScreen
import com.boostofstudios.fukex.VoicePlayRequests
import com.boostofstudios.fukex.data.LockTimeout
import com.boostofstudios.fukex.data.Playlist
import com.boostofstudios.fukex.data.PlaylistManager
import com.boostofstudios.fukex.data.PlaylistSecurity
import com.boostofstudios.fukex.data.SettingsManager
import com.boostofstudios.fukex.data.UpdateManager
import com.boostofstudios.fukex.data.UpdateState
import com.boostofstudios.fukex.playback.PlaybackEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.util.UUID

private const val TAG = "FukeX"

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
	val context = LocalContext.current
	var loaded by remember { mutableStateOf<List<Playlist>?>(null) }
	LaunchedEffect(Unit) {
		val stored = withContext(Dispatchers.IO) { PlaylistManager.loadPlaylists(context) }
		loaded = stored.ifEmpty {
			listOf(Playlist(UUID.randomUUID().toString(), "Default Playlist", emptyList()))
		}
	}
	val initialPlaylists = loaded
	if (initialPlaylists == null) {
		Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			CircularProgressIndicator()
		}
		return
	}
	MainScreenContent(initialPlaylists, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenContent(initialPlaylists: List<Playlist>, modifier: Modifier = Modifier) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val engine = remember { PlaybackEngine.get(context) }
	val engineState by engine.state.collectAsState()
	var playlists by remember { mutableStateOf(initialPlaylists) }
	var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
	var unlockedPlaylistIds by remember { mutableStateOf(setOf<String>()) }
	var lastActiveTimes by remember { mutableStateOf(mapOf<String, Long>()) }

	var showNameDialog by remember { mutableStateOf(false) }
	var showPlaylistActions by remember { mutableStateOf<Playlist?>(null) }
	var exportTarget by remember { mutableStateOf<Playlist?>(null) }
	var showPinDialog by remember { mutableStateOf<Playlist?>(null) }
	var showPinSetupDialog by remember { mutableStateOf<Playlist?>(null) }
	var showConfirmUnlockDialog by remember { mutableStateOf<Playlist?>(null) }
	var showPlaylistSelectionDialog by remember { mutableStateOf(false) }
	var showFilePicker by remember { mutableStateOf(false) }
	var showSmbPicker by remember { mutableStateOf(false) }
	var showAddMediaDialog by remember { mutableStateOf(false) }
	var showYoutubeSearch by remember { mutableStateOf(false) }
	var showSettings by remember { mutableStateOf(false) }
	var showLibrary by remember { mutableStateOf(false) }
	var showEqualizer by remember { mutableStateOf(false) }
	var showLyrics by remember { mutableStateOf(false) }
	var showExitDialog by remember { mutableStateOf(false) }
	var showStorageRationale by remember { mutableStateOf(false) }
	val updateState by UpdateManager.state.collectAsState()
	var showIconPackPrompt by remember { mutableStateOf(!SettingsManager.isIconPackPromptDone(context)) }
	val openDocs by VoicePlayRequests.openDocs.collectAsState()

	val currentlyPlayingPlaylistId = if (engineState.isPlaying) engineState.playlistId else null
	val visiblePlaylists = playlists.filter { !it.isHidden || it.id in unlockedPlaylistIds }
	// The engine keeps playing a playlist after it locks; don't show its tracks while it's locked
	val hidePlayingDetails = playlists.any { it.id == engineState.playlistId && it.isHidden && it.id !in unlockedPlaylistIds }
	val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

	fun savePlaylists(newList: List<Playlist>) {
		playlists = newList
		scope.launch(Dispatchers.IO) { PlaylistManager.savePlaylists(context, newList) }
	}

	fun updatePlaylist(updated: Playlist) {
		savePlaylists(playlists.map { if (it.id == updated.id) updated else it })
		engine.onPlaylistUpdated(updated)
		lastActiveTimes = lastActiveTimes + (updated.id to System.currentTimeMillis())
	}

	fun removePlaylist(playlist: Playlist) {
		savePlaylists(playlists.filter { it.id != playlist.id })
		engine.onPlaylistRemoved(playlist.id)
		selectedTabIndex = 0
	}

	fun movePlaylist(playlist: Playlist, offset: Int) {
		val idx = playlists.indexOf(playlist)
		val target = idx + offset
		if (idx == -1 || target !in playlists.indices) return
		val newList = playlists.toMutableList()
		java.util.Collections.swap(newList, idx, target)
		savePlaylists(newList)
	}

	fun addToSelectedPlaylist(uris: List<Uri>) {
		if (uris.isEmpty() || visiblePlaylists.isEmpty()) return
		val current = visiblePlaylists[selectedTabIndex.coerceIn(0, visiblePlaylists.lastIndex)]
		updatePlaylist(current.copy(uris = current.uris + uris))
	}

	fun showUnlocked(playlist: Playlist) {
		val nextUnlockedIds = unlockedPlaylistIds + playlist.id
		unlockedPlaylistIds = nextUnlockedIds
		lastActiveTimes = lastActiveTimes + (playlist.id to System.currentTimeMillis())
		val targetIdx = playlists.filter { !it.isHidden || it.id in nextUnlockedIds }.indexOfFirst { it.id == playlist.id }
		if (targetIdx != -1) selectedTabIndex = targetIdx
	}

	fun authenticateWithBiometrics(onSuccess: () -> Unit, onError: () -> Unit) {
		val fragmentActivity = context as? FragmentActivity ?: return
		val prompt = BiometricPrompt(fragmentActivity, ContextCompat.getMainExecutor(context),
			object : BiometricPrompt.AuthenticationCallback() {
				override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
				override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onError()
			})
		prompt.authenticate(
			BiometricPrompt.PromptInfo.Builder()
				.setTitle("Unlock Playlist")
				.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
				.build()
		)
	}

	SideEffect { VoicePlayRequests.unlockedPlaylistIds = unlockedPlaylistIds }
	val focusPlaylistId by VoicePlayRequests.focusPlaylistId.collectAsState()
	LaunchedEffect(focusPlaylistId) {
		val id = focusPlaylistId ?: return@LaunchedEffect
		val targetIdx = visiblePlaylists.indexOfFirst { it.id == id }
		if (targetIdx != -1) selectedTabIndex = targetIdx
		VoicePlayRequests.focusPlaylistId.value = null
	}
	LaunchedEffect(engineState.isPlaying, engineState.playlistId) {
		val id = engineState.playlistId
		if (engineState.isPlaying && id != null) {
			lastActiveTimes = lastActiveTimes + (id to System.currentTimeMillis())
		}
	}

	androidx.activity.compose.BackHandler(
		enabled = !showSettings && !showFilePicker && !showSmbPicker && !showNameDialog &&
			!showLibrary && !showEqualizer && !showLyrics && !showYoutubeSearch && !openDocs
	) {
		if (SettingsManager.isExitPromptEnabled(context)) {
			showExitDialog = true
		} else {
			(context as? MainActivity)?.finish()
		}
	}
	LaunchedEffect(Unit) {
		UpdateManager.check(manual = false)
	}
	LaunchedEffect(Unit) {
		while (true) {
			kotlinx.coroutines.delay(10000)
			val timeout = SettingsManager.getLockTimeout(context)
			if (timeout != LockTimeout.IMMEDIATE && timeout != LockTimeout.SCREEN_LOCK) {
				val now = System.currentTimeMillis()
				val toLock = unlockedPlaylistIds.filter { id ->
					id != currentlyPlayingPlaylistId &&
						(now - (lastActiveTimes[id] ?: now)) > (timeout.minutes * 60 * 1000)
				}
				if (toLock.isNotEmpty()) {
					unlockedPlaylistIds = unlockedPlaylistIds - toLock.toSet()
				}
			}
		}
	}
	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_STOP) {
				val timeout = SettingsManager.getLockTimeout(context)
				if (timeout == LockTimeout.SCREEN_LOCK || timeout == LockTimeout.IMMEDIATE) {
					unlockedPlaylistIds = emptySet()
				}
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}

	val permissionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
	LaunchedEffect(Unit) {
		val permsToRequest = mutableListOf<String>()
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
			if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
				permsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
			}
		}
		if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
			if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
				permsToRequest.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
			}
		}
		if (permsToRequest.isNotEmpty()) permissionsLauncher.launch(permsToRequest.toTypedArray())
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
			showStorageRationale = !android.os.Environment.isExternalStorageManager()
		}
	}

	val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
		uri?.let {
			scope.launch {
				try {
					val jsonString = withContext(Dispatchers.IO) {
						context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }
					}
					if (jsonString != null) {
						val json = JSONObject(jsonString)
						val urisJson = json.getJSONArray("uris")
						val uris = (0 until urisJson.length()).map { i -> Uri.parse(urisJson.getString(i)) }
						val newList = playlists + Playlist(id = UUID.randomUUID().toString(), name = json.getString("name"), uris = uris)
						savePlaylists(newList)
						selectedTabIndex = newList.count { p -> !p.isHidden || p.id in unlockedPlaylistIds } - 1
					}
				} catch (e: Exception) {
					Log.e(TAG, "Could not import playlist", e)
					android.widget.Toast.makeText(context, "That file is not a FukeX playlist.", android.widget.Toast.LENGTH_LONG).show()
				}
			}
		}
	}
	val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
		val playlist = exportTarget
		exportTarget = null
		if (uri == null || playlist == null) return@rememberLauncherForActivityResult
		scope.launch(Dispatchers.IO) {
			try {
				val json = JSONObject()
				json.put("name", playlist.name)
				val urisJson = JSONArray()
				playlist.uris.forEach { urisJson.put(it.toString()) }
				json.put("uris", urisJson)
				context.contentResolver.openOutputStream(uri)?.use { os ->
					OutputStreamWriter(os).use { writer -> writer.write(json.toString()) }
				}
			} catch (e: Exception) {
				Log.e(TAG, "Could not export playlist", e)
			}
		}
	}

	fun export(playlist: Playlist) {
		exportTarget = playlist
		exportLauncher.launch("${playlist.name}.json")
	}

	fun toggleHidden(playlist: Playlist) {
		if (playlist.isHidden) showConfirmUnlockDialog = playlist else showPinSetupDialog = playlist
	}

	Box(modifier = Modifier.fillMaxSize()) {
		Scaffold(
			modifier = Modifier.semantics { isTraversalGroup = true },
			topBar = {
				TopAppBar(
					title = { Text("FukeX", modifier = Modifier.semantics { heading() }) },
					actions = {
						IconButton(onClick = { showLibrary = true }) {
							Icon(FxIcons.LibraryMusic, contentDescription = "Music Library")
						}
						IconButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
							Icon(FxIcons.FileDownload, contentDescription = "Import Playlist")
						}
						if (playlists.any { it.isHidden && it.id !in unlockedPlaylistIds }) {
							IconButton(onClick = { showPlaylistSelectionDialog = true }) {
								Icon(FxIcons.Lock, contentDescription = "View Locked Playlists")
							}
						}
						IconButton(onClick = { showSettings = true }) {
							Icon(FxIcons.Settings, contentDescription = "Settings")
						}
					}
				)
			},
			floatingActionButton = {
				FloatingActionButton(onClick = { showAddMediaDialog = true }) {
					Icon(FxIcons.Add, contentDescription = "Add Media to Playlist")
				}
			}
		) { scaffoldPadding ->
			Column(modifier = modifier.fillMaxSize().padding(scaffoldPadding).semantics { traversalIndex = -1f }) {
				val safeSelectedTabIndex = selectedTabIndex.coerceAtMost(visiblePlaylists.lastIndex).coerceAtLeast(0)
				ScrollableTabRow(
					selectedTabIndex = safeSelectedTabIndex,
					edgePadding = 16.dp,
					modifier = Modifier.fillMaxWidth(),
					indicator = { tabPositions ->
						val safeIndex = selectedTabIndex.coerceAtMost(tabPositions.lastIndex)
						if (safeIndex >= 0) {
							TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tabPositions[safeIndex]))
						}
					}
				) {
					visiblePlaylists.forEachIndexed { index, playlist ->
						Tab(
							selected = selectedTabIndex == index,
							onClick = { selectedTabIndex = index },
							modifier = Modifier.semantics {
								val actions = mutableListOf(
									CustomAccessibilityAction("Create new playlist") {
										showNameDialog = true
										true
									}
								)
								if (playlists.size > 1) {
									val idx = playlists.indexOf(playlist)
									if (idx > 0) actions.add(CustomAccessibilityAction("Move Left") { movePlaylist(playlist, -1); true })
									if (idx < playlists.size - 1) actions.add(CustomAccessibilityAction("Move Right") { movePlaylist(playlist, 1); true })
									actions.add(CustomAccessibilityAction(if (playlist.isHidden) "Unlock" else "Hide") { toggleHidden(playlist); true })
								}
								actions.add(CustomAccessibilityAction("Export") { export(playlist); true })
								if (playlists.size > 1) {
									actions.add(CustomAccessibilityAction("Remove") { removePlaylist(playlist); true })
								}
								customActions = actions
							},
							text = {
								Row(verticalAlignment = Alignment.CenterVertically) {
									if (playlist.isHidden) {
										Icon(FxIcons.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
										Spacer(Modifier.width(4.dp))
									}
									Text(playlist.name)
									IconButton(
										onClick = { showPlaylistActions = playlist },
										modifier = Modifier.clearAndSetSemantics { }
									) {
										Icon(FxIcons.MoreVert, contentDescription = null)
									}
								}
							}
						)
					}
				}
				Box(modifier = Modifier.weight(1f)) {
					if (selectedTabIndex < visiblePlaylists.size) {
						val currentPlaylist = visiblePlaylists[selectedTabIndex]
						key(currentPlaylist.id) {
							PlayerScreen(
								playlist = currentPlaylist,
								engine = engine,
								canModifyPlaylist = playlists.size > 1,
								hidePlayingDetails = hidePlayingDetails,
								onHide = { toggleHidden(currentPlaylist) },
								onRemove = { removePlaylist(currentPlaylist) },
								onUpdatePlaylist = { updatePlaylist(it) },
								onOpenEqualizer = { showEqualizer = true },
								onOpenLyrics = { showLyrics = true },
								modifier = Modifier.fillMaxSize()
							)
						}
					}
				}
			}
		}

		if (showExitDialog) {
			ExitDialog(onConfirm = { (context as? MainActivity)?.finish() }, onDismiss = { showExitDialog = false })
		}
		UpdateDialogs()
		// Asked once, and only after the more important first-run dialogs are out of the way
		if (showIconPackPrompt && !showStorageRationale && updateState is UpdateState.Idle) {
			IconPackPrompt(onFinished = {
				SettingsManager.setIconPackPromptDone(context, true)
				showIconPackPrompt = false
			})
		}
		if (showStorageRationale) {
			StorageRationaleDialog(
				onOpenSettings = {
					showStorageRationale = false
					try {
						val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
						intent.data = Uri.parse("package:${context.packageName}")
						context.startActivity(intent)
					} catch (e: Exception) {
						Log.e(TAG, "Could not open all files access settings", e)
					}
				},
				onDismiss = { showStorageRationale = false }
			)
		}
		showPlaylistActions?.let { playlist ->
			PlaylistActionsDialog(
				playlist = playlist,
				canModify = playlists.size > 1,
				onMoveLeft = { movePlaylist(playlist, -1); showPlaylistActions = null },
				onMoveRight = { movePlaylist(playlist, 1); showPlaylistActions = null },
				onToggleHidden = { toggleHidden(playlist); showPlaylistActions = null },
				onExport = { export(playlist); showPlaylistActions = null },
				onRemove = { removePlaylist(playlist); showPlaylistActions = null },
				onDismiss = { showPlaylistActions = null }
			)
		}
		if (showPlaylistSelectionDialog) {
			LockedPlaylistPickerDialog(
				lockedPlaylists = playlists.filter { it.isHidden && it.id !in unlockedPlaylistIds },
				onPick = { playlist ->
					showPlaylistSelectionDialog = false
					if (SettingsManager.isBiometricEnabled(context)) {
						authenticateWithBiometrics(onSuccess = { showUnlocked(playlist) }, onError = { showPinDialog = playlist })
					} else {
						showPinDialog = playlist
					}
				},
				onDismiss = { showPlaylistSelectionDialog = false }
			)
		}
		showPinSetupDialog?.let { playlist ->
			PinSetupDialog(
				onConfirm = { secret, authType ->
					val salt = PlaylistSecurity.newSalt()
					val hashed = PlaylistSecurity.hash(secret, salt)
					savePlaylists(playlists.map {
						if (it.id == playlist.id) it.copy(isHidden = true, pinHash = hashed, pinSalt = salt, authType = authType) else it
					})
					selectedTabIndex = 0
					showPinSetupDialog = null
				},
				onDismiss = { showPinSetupDialog = null }
			)
		}
		showPinDialog?.let { playlist ->
			PlaylistSecretDialog(
				playlist = playlist,
				title = "Unlock ${playlist.name}",
				message = null,
				confirmLabel = "View",
				onVerified = {
					showPinDialog = null
					showUnlocked(playlist)
				},
				onDismiss = { showPinDialog = null }
			)
		}
		showConfirmUnlockDialog?.let { playlist ->
			PlaylistSecretDialog(
				playlist = playlist,
				title = "Permanently Unlock ${playlist.name}",
				message = "Enter your ${playlist.authType.name} to permanently unlock this playlist.",
				confirmLabel = "Unlock",
				onVerified = {
					savePlaylists(playlists.map {
						if (it.id == playlist.id) it.copy(isHidden = false, pinHash = null, pinSalt = null) else it
					})
					unlockedPlaylistIds = unlockedPlaylistIds - playlist.id
					showConfirmUnlockDialog = null
				},
				onDismiss = { showConfirmUnlockDialog = null }
			)
		}
		if (showNameDialog) {
			NamePlaylistDialog(
				onCreate = { name ->
					val newList = playlists + Playlist(id = UUID.randomUUID().toString(), name = name, uris = emptyList())
					savePlaylists(newList)
					val nextVisibleIndex = newList.count { !it.isHidden || it.id in unlockedPlaylistIds } - 1
					if (nextVisibleIndex >= 0) selectedTabIndex = nextVisibleIndex
					showNameDialog = false
				},
				onDismiss = { showNameDialog = false }
			)
		}
		if (showAddMediaDialog) {
			AddMediaDialog(
				onLocal = { showAddMediaDialog = false; showFilePicker = true },
				onLibrary = { showAddMediaDialog = false; showLibrary = true },
				onSmb = { showAddMediaDialog = false; showSmbPicker = true },
				onYoutube = { showAddMediaDialog = false; showYoutubeSearch = true },
				onDismiss = { showAddMediaDialog = false }
			)
		}
		if (showYoutubeSearch) {
			YoutubeSearchScreen(
				onBack = { showYoutubeSearch = false },
				onVideoSelected = { uri ->
					showYoutubeSearch = false
					addToSelectedPlaylist(listOf(Uri.parse(uri)))
				}
			)
		}
		if (showSmbPicker) {
			SmbPickerScreen(
				onFilesSelected = { uris ->
					showSmbPicker = false
					addToSelectedPlaylist(uris)
				},
				onCancel = { showSmbPicker = false }
			)
		}
		if (showFilePicker) {
			FilePickerScreen(
				onFilesSelected = { uris ->
					showFilePicker = false
					addToSelectedPlaylist(uris)
				},
				onCancel = { showFilePicker = false }
			)
		}
		if (showLibrary) {
			LibraryScreen(
				engine = engine,
				playlists = visiblePlaylists,
				onAddToPlaylist = { playlist, uris ->
					val latest = playlists.firstOrNull { it.id == playlist.id } ?: return@LibraryScreen
					updatePlaylist(latest.copy(uris = latest.uris + uris))
				},
				onBack = { showLibrary = false }
			)
		}
		if (showEqualizer) {
			EqualizerScreen(engine = engine, onBack = { showEqualizer = false })
		}
		if (showLyrics) {
			LyricsScreen(engine = engine, hideDetails = hidePlayingDetails, onBack = { showLyrics = false })
		}
		if (showSettings) {
			SettingsScreen(onBack = { showSettings = false })
		}
		if (openDocs) {
			DocumentationScreen(onBack = { VoicePlayRequests.openDocs.value = false })
		}
	}
}
