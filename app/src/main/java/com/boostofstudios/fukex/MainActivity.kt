package com.boostofstudios.fukex
import android.content.Intent
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.boostofstudios.fukex.data.AuthType
import com.boostofstudios.fukex.data.Playlist
import com.boostofstudios.fukex.data.PlaylistManager
import com.boostofstudios.fukex.data.PlaylistSecurity
import com.boostofstudios.fukex.data.PlayerCacheManager
import com.boostofstudios.fukex.ui.theme.FukeXTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.media3.common.C
import java.io.OutputStreamWriter
import java.util.UUID
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.MediaMetadataCompat
import android.app.PendingIntent
import com.boostofstudios.fukex.data.LockTimeout
import com.boostofstudios.fukex.data.SettingsManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private const val TAG = "FukeX"

class MainActivity : FragmentActivity() {
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

@Composable
private fun MainScreenContent(initialPlaylists: List<Playlist>, modifier: Modifier = Modifier) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	var playlists by remember { mutableStateOf(initialPlaylists) }
	var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
	var showNameDialog by remember { mutableStateOf(false) }
	var playlistName by remember { mutableStateOf("") }
	var showPlaylistActions by remember { mutableStateOf<Playlist?>(null) }
	var showPinDialog by remember { mutableStateOf<Playlist?>(null) }
	var pinInput by remember { mutableStateOf("") }
	var showPinSetupDialog by remember { mutableStateOf<Playlist?>(null) }
	var setupAuthType by remember { mutableStateOf(AuthType.PIN) }
	var unlockedPlaylistIds by remember { mutableStateOf(setOf<String>()) }
	var showConfirmUnlockDialog by remember { mutableStateOf<Playlist?>(null) }
	var showPlaylistSelectionDialog by remember { mutableStateOf(false) }
	var authError by remember { mutableStateOf("") }
	var showFilePicker by remember { mutableStateOf(false) }
	var showSmbPicker by remember { mutableStateOf(false) }
	var showAddMediaDialog by remember { mutableStateOf(false) }
	var showSettings by remember { mutableStateOf(false) }
	var showExitDialog by remember { mutableStateOf(false) }
	var showStorageRationale by remember { mutableStateOf(false) }
	var lastActiveTimes by remember { mutableStateOf(mapOf<String, Long>()) }
	var currentlyPlayingPlaylistId by remember { mutableStateOf<String?>(null) }
	val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
	androidx.activity.compose.BackHandler(enabled = !showSettings && !showFilePicker && !showSmbPicker && !showNameDialog) {
		if (SettingsManager.isExitPromptEnabled(context)) {
			showExitDialog = true
		} else {
			(context as? MainActivity)?.finish()
		}
	}
	if (showExitDialog) {
		AlertDialog(
			onDismissRequest = { showExitDialog = false },
			title = { Text("Exit FukeX") },
			text = { Text("Do you really wanna quit?") },
			confirmButton = {
				Button(onClick = { (context as? MainActivity)?.finish() }) {
					Text("Yes")
				}
			},
			dismissButton = {
				TextButton(onClick = { showExitDialog = false }) {
					Text("No")
				}
			}
		)
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
		onDispose {
			lifecycleOwner.lifecycle.removeObserver(observer)
		}
	}

	fun authenticateWithBiometrics(onSuccess: () -> Unit, onError: (String) -> Unit) {
		val executor = ContextCompat.getMainExecutor(context)
		val fragmentActivity = context as? FragmentActivity ?: return
		val biometricPrompt = BiometricPrompt(fragmentActivity, executor,

			object : BiometricPrompt.AuthenticationCallback() {
				override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
					super.onAuthenticationSucceeded(result)
					onSuccess()
				}

				override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
					super.onAuthenticationError(errorCode, errString)
					onError(errString.toString())
				}
			})
		val promptInfo = BiometricPrompt.PromptInfo.Builder()
			.setTitle("Unlock Playlist")
			.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
			.build()
		biometricPrompt.authenticate(promptInfo)
	}
	val permissionsLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.RequestMultiplePermissions()
	) {}
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
		if (permsToRequest.isNotEmpty()) {
			permissionsLauncher.launch(permsToRequest.toTypedArray())
		}
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
			showStorageRationale = !android.os.Environment.isExternalStorageManager()
		}
	}
	if (showStorageRationale) {
		AlertDialog(
			modifier = Modifier.semantics { paneTitle = "Storage access needed" },
			onDismissRequest = { showStorageRationale = false },
			title = { Text("Storage access needed") },
			text = { Text("FukeX uses its own file picker so it works properly with TalkBack. To browse your music, Android needs to grant it access to all files. You can skip this and still play from an SMB share.") },
			confirmButton = {
				Button(onClick = {
					showStorageRationale = false
					try {
						val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
						intent.data = Uri.parse("package:${context.packageName}")
						context.startActivity(intent)
					} catch (e: Exception) {
						Log.e(TAG, "Could not open all files access settings", e)
					}
				}) { Text("Open settings") }
			},
			dismissButton = {
				TextButton(onClick = { showStorageRationale = false }) { Text("Not now") }
			}
		)
	}
	val visiblePlaylists = playlists.filter { !it.isHidden || it.id in unlockedPlaylistIds }
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
						selectedTabIndex = newList.count { !it.isHidden || it.id in unlockedPlaylistIds } - 1
					}
				} catch (e: Exception) {
					Log.e(TAG, "Could not import playlist", e)
					android.widget.Toast.makeText(context, "That file is not a FukeX playlist.", android.widget.Toast.LENGTH_LONG).show()
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
						Log.e(TAG, "Could not export playlist", e)
					}
				}
			}
		}
	}
	Box(modifier = Modifier.fillMaxSize()) {
		Scaffold(
		modifier = Modifier.semantics { isTraversalGroup = true },
		topBar = {
			@OptIn(ExperimentalMaterial3Api::class)
			TopAppBar(
				title = { Text("FukeX", modifier = Modifier.semantics { heading() }) },
				actions = {
					IconButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
						Icon(Icons.Default.FileDownload, contentDescription = "Import Playlist")
					}
					if (playlists.any { it.isHidden && it.id !in unlockedPlaylistIds }) {
						IconButton(onClick = { showPlaylistSelectionDialog = true }) {
							Icon(Icons.Default.Lock, contentDescription = "View Locked Playlists")
						}
					}
					IconButton(onClick = { showSettings = true }) {
						Icon(Icons.Default.Settings, contentDescription = "Settings")
					}
				}
			)
		},
		floatingActionButton = {
			FloatingActionButton(onClick = { showAddMediaDialog = true }) {
				Icon(Icons.Default.Add, contentDescription = "Add Media to Playlist")
			}
		}
	) { scaffoldPadding ->
		Column(modifier = modifier.fillMaxSize().padding(scaffoldPadding).semantics { traversalIndex = -1f }) {
		val safeSelectedTabIndex = kotlin.math.min(selectedTabIndex, visiblePlaylists.lastIndex).coerceAtLeast(0)
		ScrollableTabRow(
			selectedTabIndex = safeSelectedTabIndex,
			edgePadding = 16.dp,
			modifier = Modifier.fillMaxWidth(),
			indicator = { tabPositions ->
				val safeIndex = kotlin.math.min(selectedTabIndex, tabPositions.lastIndex)
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
							if (idx > 0) {
								actions.add(
									CustomAccessibilityAction("Move Left") {
										val newList = playlists.toMutableList()
										java.util.Collections.swap(newList, idx, idx - 1)
										playlists = newList
										scope.launch(Dispatchers.IO) {
											PlaylistManager.savePlaylists(context, newList)
										}
										true
									}
								)
							}
							if (idx < playlists.size - 1) {
								actions.add(
									CustomAccessibilityAction("Move Right") {
										val newList = playlists.toMutableList()
										java.util.Collections.swap(newList, idx, idx + 1)
										playlists = newList
										scope.launch(Dispatchers.IO) {
											PlaylistManager.savePlaylists(context, newList)
										}
										true
									}
								)
							}
						}
						if (playlists.size > 1) {
							actions.add(
								CustomAccessibilityAction(if (playlist.isHidden) "Unlock" else "Hide") {
									if (playlist.isHidden) {
										showConfirmUnlockDialog = playlist
									} else {
										showPinSetupDialog = playlist
									}
									true
								}
							)
						}
						actions.add(
							CustomAccessibilityAction("Export") {
								showPlaylistActions = playlist
								exportLauncher.launch("${playlist.name}.json")
								true
							}
						)
						if (playlists.size > 1) {
							actions.add(
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
						}
						customActions = actions
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
			if (selectedTabIndex < visiblePlaylists.size) {
				val currentPlaylist = visiblePlaylists[selectedTabIndex]
				key(currentPlaylist.id) {
					AudioPlayerView(
						playlist = currentPlaylist,
						modifier = Modifier.fillMaxSize(),
						canModifyPlaylist = playlists.size > 1,
						onProgressUpdate = { index, pos ->
							scope.launch(Dispatchers.IO) {
								PlaylistManager.updatePlaylistProgress(context, currentPlaylist.id, index, pos)
							}
						},
						onPlayStateChanged = { playing ->
							currentlyPlayingPlaylistId = if (playing) currentPlaylist.id else null
							if (playing) {
								lastActiveTimes = lastActiveTimes + (currentPlaylist.id to System.currentTimeMillis())
							}
						},
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
						},
						onUpdatePlaylist = { updatedPlaylist ->
							val newList = playlists.map { if (it.id == updatedPlaylist.id) updatedPlaylist else it }
							playlists = newList
							scope.launch(Dispatchers.IO) {
								PlaylistManager.savePlaylists(context, newList)
							}
							lastActiveTimes = lastActiveTimes + (updatedPlaylist.id to System.currentTimeMillis())
						}
					)
				}
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
					if (playlists.size > 1) {
						ListItem(
							headlineContent = { Text("Move Left") },
							leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) },
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
							headlineContent = { Text("Move Right") },
							leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
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
					}
					ListItem(
						headlineContent = { Text("Export Playlist") },
						leadingContent = { Icon(Icons.Default.FileUpload, null) },
						modifier = Modifier.clickable {
							exportLauncher.launch("${playlist.name}.json")
						}
					)
					if (playlists.size > 1) {
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
								showPlaylistSelectionDialog = false
								if (SettingsManager.isBiometricEnabled(context)) {
									authenticateWithBiometrics(
										onSuccess = {
											val nextUnlockedIds = unlockedPlaylistIds + playlist.id
											unlockedPlaylistIds = nextUnlockedIds
											lastActiveTimes = lastActiveTimes + (playlist.id to System.currentTimeMillis())
											val nextVisible = playlists.filter { !it.isHidden || it.id in nextUnlockedIds }
											val targetIdx = nextVisible.indexOfFirst { it.id == playlist.id }
											if (targetIdx != -1) {
												selectedTabIndex = targetIdx
											}
										},
										onError = { showPinDialog = playlist }
									)
								} else {
									showPinDialog = playlist
								}
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
						val salt = PlaylistSecurity.newSalt()
						val hashed = PlaylistSecurity.hash(pinInput, salt)
						val newList = playlists.map {
							if (it.id == playlist.id) it.copy(isHidden = true, pinHash = hashed, pinSalt = salt, authType = setupAuthType) else it
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
					if (PlaylistSecurity.verify(pinInput, playlist)) {
						val nextUnlockedIds = unlockedPlaylistIds + playlist.id
						unlockedPlaylistIds = nextUnlockedIds
						lastActiveTimes = lastActiveTimes + (playlist.id to System.currentTimeMillis())
						authError = ""
						pinInput = ""
						showPinDialog = null
						val nextVisible = playlists.filter { !it.isHidden || it.id in nextUnlockedIds }
						val targetIdx = nextVisible.indexOfFirst { it.id == playlist.id }
						if (targetIdx != -1) {
							selectedTabIndex = targetIdx
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
					if (PlaylistSecurity.verify(pinInput, playlist)) {
						val newList = playlists.map {
							if (it.id == playlist.id) it.copy(isHidden = false, pinHash = null, pinSalt = null) else it
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
								uris = emptyList()
							)
							val newList = playlists + newPlaylist
							playlists = newList
							scope.launch(Dispatchers.IO) {
								PlaylistManager.savePlaylists(context, newList)
							}
							val nextVisibleIndex = newList.count { !it.isHidden || it.id in unlockedPlaylistIds } - 1
							if (nextVisibleIndex >= 0) {
								selectedTabIndex = nextVisibleIndex
							}
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
		if (showAddMediaDialog) {
			AlertDialog(
				onDismissRequest = { showAddMediaDialog = false },
				title = { Text("Add Media") },
				text = {
					Column {
						Text("Where would you like to add media from?")
					}
				},
				confirmButton = {
					TextButton(onClick = {
						showAddMediaDialog = false
						showFilePicker = true
					}) { Text("Local Device") }
				},
				dismissButton = {
					TextButton(onClick = {
						showAddMediaDialog = false
						showSmbPicker = true
					}) { Text("SMB Share") }
				}
			)
		}
		if (showSmbPicker) {
			SmbPickerScreen(
				onFilesSelected = { uris ->
					showSmbPicker = false
					if (uris.isNotEmpty() && visiblePlaylists.isNotEmpty()) {
						val safeIndex = kotlin.math.min(selectedTabIndex, visiblePlaylists.lastIndex)
						if (safeIndex >= 0) {
							val currentPlaylist = visiblePlaylists[safeIndex]
							val updatedPlaylist = currentPlaylist.copy(uris = currentPlaylist.uris + uris)
							val newList = playlists.map { if (it.id == currentPlaylist.id) updatedPlaylist else it }
							playlists = newList
							scope.launch(Dispatchers.IO) {
								PlaylistManager.savePlaylists(context, newList)
							}
							lastActiveTimes = lastActiveTimes + (currentPlaylist.id to System.currentTimeMillis())
						}
					}
				},
				onCancel = { showSmbPicker = false }
			)
		}
		if (showFilePicker) {
			FilePickerScreen(
				onFilesSelected = { uris ->
					showFilePicker = false
					if (uris.isNotEmpty() && visiblePlaylists.isNotEmpty()) {
						val safeIndex = kotlin.math.min(selectedTabIndex, visiblePlaylists.lastIndex)
						if (safeIndex >= 0) {
							val currentPlaylist = visiblePlaylists[safeIndex]
							val updatedPlaylist = currentPlaylist.copy(uris = currentPlaylist.uris + uris)
							val newList = playlists.map { if (it.id == currentPlaylist.id) updatedPlaylist else it }
							playlists = newList
							scope.launch(Dispatchers.IO) {
								PlaylistManager.savePlaylists(context, newList)
							}
							lastActiveTimes = lastActiveTimes + (currentPlaylist.id to System.currentTimeMillis())
						}
					}
				},
				onCancel = { showFilePicker = false }
			)
		}
		if (showSettings) {
			SettingsScreen(onBack = { showSettings = false })
		}
	}
}

fun createFukexPlayer(context: android.content.Context): androidx.media3.exoplayer.ExoPlayer {
	val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
		.setUsage(androidx.media3.common.C.USAGE_MEDIA)
		.setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
		.build()
	val appContext = context.applicationContext
	val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context)
	val customDataSourceFactory = androidx.media3.datasource.DataSource.Factory {
		val defaultDataSource = defaultDataSourceFactory.createDataSource()

		object : androidx.media3.datasource.DataSource {
			val smbDataSource = com.boostofstudios.fukex.data.SmbDataSource(appContext)
			var activeDataSource: androidx.media3.datasource.DataSource? = null

			override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
				defaultDataSource.addTransferListener(transferListener)
				smbDataSource.addTransferListener(transferListener)
			}

			override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
				activeDataSource = if (dataSpec.uri.scheme?.startsWith("smb") == true) smbDataSource else defaultDataSource
				return activeDataSource!!.open(dataSpec)
			}

			override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
				return activeDataSource!!.read(buffer, offset, length)
			}

			override fun getUri(): android.net.Uri? {
				return activeDataSource?.uri
			}

			override fun close() {
				activeDataSource?.close()
				activeDataSource = null
			}
		}
	}
	val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
		.setCache(PlayerCacheManager.getCache(context))
		.setUpstreamDataSourceFactory(customDataSourceFactory)
		.setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
	// Local files are already on disk, so only remote sources go through the cache.
	val routingDataSourceFactory = androidx.media3.datasource.DataSource.Factory {
		object : androidx.media3.datasource.DataSource {
			var delegate: androidx.media3.datasource.DataSource? = null
			val listeners = mutableListOf<androidx.media3.datasource.TransferListener>()

			override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
				listeners.add(transferListener)
			}

			override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
				val factory = if (dataSpec.uri.scheme?.startsWith("smb") == true) {
					cacheDataSourceFactory
				} else {
					customDataSourceFactory
				}
				val source = factory.createDataSource()
				listeners.forEach { source.addTransferListener(it) }
				delegate = source
				return source.open(dataSpec)
			}

			override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
				delegate!!.read(buffer, offset, length)

			override fun getUri(): android.net.Uri? = delegate?.uri

			override fun close() {
				delegate?.close()
				delegate = null
			}
		}
	}
	val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
		.setDataSourceFactory(routingDataSourceFactory)
	return androidx.media3.exoplayer.ExoPlayer.Builder(context)
		.setMediaSourceFactory(mediaSourceFactory)
		.setAudioAttributes(audioAttributes, false)
		.setHandleAudioBecomingNoisy(true)
		.setSkipSilenceEnabled(SettingsManager.isSkipSilenceEnabled(context))
		.build().apply {
			trackSelectionParameters = trackSelectionParameters.buildUpon()
				.setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_VIDEO, true)
				.build()
		}
}

@Composable
fun AudioPlayerView(
	playlist: Playlist,
	modifier: Modifier = Modifier,
	canModifyPlaylist: Boolean,
	onProgressUpdate: (Int, Float) -> Unit,
	onPlayStateChanged: (Boolean) -> Unit,
	onHide: () -> Unit,
	onRemove: () -> Unit,
	onUpdatePlaylist: (Playlist) -> Unit
) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val player1 = remember { createFukexPlayer(context) }
	val player2 = remember { createFukexPlayer(context) }
	val players = remember { listOf(player1, player2) }
	var activePlayerIndex by remember { mutableIntStateOf(0) }
	val exoPlayer = players[activePlayerIndex]
	var currentIndex by remember { mutableIntStateOf(playlist.lastIndex) }
	var isPlaying by remember { mutableStateOf(false) }
	var progress by remember { mutableFloatStateOf(0f) }
	var currentTime by remember { mutableLongStateOf(0L) }
	var totalTime by remember { mutableLongStateOf(0L) }
	var isInitialPlayback by remember { mutableStateOf(true) }
	var isSeeking by remember { mutableStateOf(false) }
	var showMoreOptions by remember { mutableStateOf(false) }
	var showSearchDialog by remember { mutableStateOf(false) }
	var showInfoDialog by remember { mutableStateOf(false) }
	var searchQuery by remember { mutableStateOf("") }
	var playlistSize by remember { mutableLongStateOf(0L) }
	var unmeasuredTracks by remember { mutableIntStateOf(0) }
	var amplifierEnabled by remember { mutableStateOf(SettingsManager.isAmplifierEnabled(context)) }
	var amplifierLevel by remember { mutableIntStateOf(SettingsManager.getAmplifierLevel(context)) }
	var loudnessEnhancer by remember { mutableStateOf<android.media.audiofx.LoudnessEnhancer?>(null) }
	
	var playbackSpeed by remember { mutableFloatStateOf(SettingsManager.getPlaybackSpeed(context)) }
	var playbackPitch by remember { mutableFloatStateOf(SettingsManager.getPlaybackPitch(context)) }
	var bassBoostLevel by remember { mutableIntStateOf(SettingsManager.getBassBoost(context)) }
	var replayGainEnabled by remember { mutableStateOf(SettingsManager.isReplayGainEnabled(context)) }
	var showPlaybackEffectsDialog by remember { mutableStateOf(false) }
	var bassBoostEffect by remember { mutableStateOf<android.media.audiofx.BassBoost?>(null) }
	val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_STOP) {
				if (!SettingsManager.isBackgroundPlaybackEnabled(context)) {
					exoPlayer.pause()
				}
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose {
			lifecycleOwner.lifecycle.removeObserver(observer)
		}
	}
	DisposableEffect(context) {
		val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
			if (key == "amplifier_enabled") {
				amplifierEnabled = SettingsManager.isAmplifierEnabled(context)
			} else if (key == "amplifier_level") {
				amplifierLevel = SettingsManager.getAmplifierLevel(context)
			} else if (key == "skip_silence") {
				val skip = SettingsManager.isSkipSilenceEnabled(context)
				players.forEach { it.skipSilenceEnabled = skip }
			} else if (key == "replay_gain_enabled") {
				replayGainEnabled = SettingsManager.isReplayGainEnabled(context)
			}
		}
		SettingsManager.registerChangeListener(context, listener)
		onDispose {
			SettingsManager.unregisterChangeListener(context, listener)
		}
	}

	fun updateAmplifier(sessionId: Int) {
		try {
			loudnessEnhancer?.release()
			if (amplifierEnabled && sessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET && sessionId != 0) {
				loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(sessionId).apply {
					setTargetGain(amplifierLevel)
					enabled = true
				}
			}
		}
	}
	fun updateBassBoost(sessionId: Int) {
		try {
			bassBoostEffect?.release()
			if (bassBoostLevel > 0 && sessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET && sessionId != 0) {
				bassBoostEffect = android.media.audiofx.BassBoost(0, sessionId).apply {
					enabled = true
					setStrength(bassBoostLevel.toShort())
				}
			} else {
				bassBoostEffect = null
			}
		} catch (e: Exception) {
			e.printStackTrace()
			bassBoostEffect = null
		}
	}
	LaunchedEffect(players) {
		players.forEach { player ->
			val listener = object : androidx.media3.common.Player.Listener {
				override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
					if (!SettingsManager.isReplayGainEnabled(context)) {
						player.volume = 1.0f
						return
					}
					var gainDb = 0f
					var found = false
					for (group in tracks.groups) {
						for (i in 0 until group.length) {
							val metadata = group.getTrackFormat(i).metadata
							if (metadata != null) {
								for (j in 0 until metadata.length()) {
									val entry = metadata.get(j)
									val className = entry.javaClass.simpleName
									if (className == "TextInformationFrame") {
										try {
											val id = entry.javaClass.getField("id").get(entry) as String
											val description = entry.javaClass.getField("description").get(entry) as String
											val value = entry.javaClass.getField("value").get(entry) as String
											if (id.lowercase().contains("txxx") && description.lowercase().contains("replaygain_track_gain")) {
												val parsed = value.replace("dB", "").trim().toFloatOrNull()
												if (parsed != null) { gainDb = parsed; found = true }
											}
										} catch (e: Exception) {}
									} else if (className == "VorbisComment") {
										try {
											val keyStr = entry.javaClass.getField("key").get(entry) as String
											val value = entry.javaClass.getField("value").get(entry) as String
											if (keyStr.equals("REPLAYGAIN_TRACK_GAIN", ignoreCase = true)) {
												val parsed = value.replace("dB", "").trim().toFloatOrNull()
												if (parsed != null) { gainDb = parsed; found = true }
											}
										} catch (e: Exception) {}
									}
								}
							}
						}
					}
					val volume = if (found) Math.pow(10.0, gainDb / 20.0).toFloat().coerceIn(0.1f, 3.0f) else 1.0f
					player.volume = volume
				}
			}
			player.addListener(listener)
		}
	}
	LaunchedEffect(amplifierEnabled, amplifierLevel) {
		updateAmplifier()
	}
	LaunchedEffect(bassBoostLevel) {
		updateBassBoost(exoPlayer.audioSessionId)
	}
	LaunchedEffect(playbackSpeed, playbackPitch) {
		val params = androidx.media3.common.PlaybackParameters(playbackSpeed, playbackPitch)
		players.forEach { it.playbackParameters = params }
	}
	LaunchedEffect(showInfoDialog) {
		if (showInfoDialog) {
			val result = withContext(Dispatchers.IO) {
				var totalSize = 0L
				var unmeasured = 0
				playlist.uris.forEach { uri ->
					val size = when {
						uri.scheme == "file" -> uri.path?.let { java.io.File(it).length() } ?: 0L
						uri.scheme?.startsWith("smb") == true -> 0L
						else -> try {
							context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
						} catch (e: Exception) {
							0L
						}
					}
					if (size > 0L) totalSize += size else unmeasured++
				}
				totalSize to unmeasured
			}
			playlistSize = result.first
			unmeasuredTracks = result.second
		}
	}
	val volumeAnimators = remember { mutableMapOf<androidx.media3.exoplayer.ExoPlayer, kotlinx.coroutines.Job>() }
	var duckFactor by remember { mutableFloatStateOf(1f) }
	val pauseRef = remember { mutableStateOf({}) }
	val resumeRef = remember { mutableStateOf({}) }
	val audioFocus = remember {
		AudioFocusController(
			context,
			onPause = { pauseRef.value() },
			onResume = { resumeRef.value() },
			onDuck = { ducking -> duckFactor = if (ducking) 0.25f else 1f }
		)
	}

	fun setVolume(player: androidx.media3.exoplayer.ExoPlayer, level: Float) {
		player.volume = level * duckFactor
	}
	LaunchedEffect(duckFactor) {
		players.forEach { player ->
			if (player.volume > 0f) player.volume = duckFactor
		}
	}

	fun startVolumeAnimation(player: androidx.media3.exoplayer.ExoPlayer, from: Float, to: Float, durationMs: Long, onEnd: () -> Unit = {}) {
		volumeAnimators[player]?.cancel()
		if (durationMs <= 0) {
			setVolume(player, to)
			onEnd()
			return
		}
		val job = scope.launch(Dispatchers.Main) {
			val startTime = System.currentTimeMillis()
			while (true) {
				val elapsed = System.currentTimeMillis() - startTime
				if (elapsed >= durationMs) {
					setVolume(player, to)
					onEnd()
					break
				}
				val fraction = elapsed.toFloat() / durationMs.toFloat()
				setVolume(player, from + (to - from) * fraction)
				kotlinx.coroutines.delay(16)
			}
		}
		volumeAnimators[player] = job
	}

	fun playIndex(index: Int, play: Boolean = true) {
		isInitialPlayback = false
		if (index in playlist.uris.indices) {
			if (play && !audioFocus.requestFocus()) return
			val fadeOutManual = SettingsManager.getFadeOutManual(context).toLong()
			val fadeInManual = SettingsManager.getFadeInManual(context).toLong()
			val startPlayback = { p: androidx.media3.exoplayer.ExoPlayer ->
				p.seekTo(index, 0L)
				if (p.playbackState == androidx.media3.common.Player.STATE_IDLE) {
					p.prepare()
				}
				if (play) {
					p.playWhenReady = true
					startVolumeAnimation(p, 0f, 1f, fadeInManual)
				} else {
					setVolume(p, 1f)
				}
			}
			if (fadeOutManual > 0 && isPlaying) {
				val previousPlayer = exoPlayer
				activePlayerIndex = (activePlayerIndex + 1) % 2
				val newPlayer = players[activePlayerIndex]
				startVolumeAnimation(previousPlayer, previousPlayer.volume, 0f, fadeOutManual) {
					previousPlayer.pause()
				}
				startPlayback(newPlayer)
			} else {
				startPlayback(exoPlayer)
			}
		}
	}
	LaunchedEffect(playlist.uris) {
		val items = playlist.uris.map { androidx.media3.common.MediaItem.fromUri(it) }
		players.forEach { player ->
			if (items.isNotEmpty()) {
				if (player.mediaItemCount == 0) {
					player.setMediaItems(items)
					player.prepare()
					if (currentIndex in items.indices) {
						player.seekTo(currentIndex, 0L)
					}
				} else {
					val currentIdx = player.currentMediaItemIndex
					val currentPos = player.currentPosition
					player.setMediaItems(items, currentIdx, currentPos)
					if (player.playbackState == androidx.media3.common.Player.STATE_IDLE) {
						player.prepare()
					}
				}
			} else {
				player.clearMediaItems()
			}
		}
	}

	fun pausePlayback() {
		val fadeOutPause = SettingsManager.getFadeOutPause(context).toLong()
		if (fadeOutPause > 0 && isPlaying) {
			startVolumeAnimation(exoPlayer, exoPlayer.volume, 0f, fadeOutPause) {
				exoPlayer.pause()
			}
		} else {
			exoPlayer.pause()
		}
	}

	fun startPlayback() {
		if (!audioFocus.requestFocus()) return
		if (exoPlayer.playbackState == androidx.media3.common.Player.STATE_IDLE) {
			exoPlayer.prepare()
		}
		val fadeInPause = SettingsManager.getFadeInPause(context).toLong()
		if (fadeInPause > 0) {
			exoPlayer.play()
			startVolumeAnimation(exoPlayer, 0f, 1f, fadeInPause)
		} else {
			setVolume(exoPlayer, 1f)
			exoPlayer.play()
		}
	}
	SideEffect {
		pauseRef.value = { pausePlayback() }
		resumeRef.value = { startPlayback() }
	}

	fun seekToTime(pos: Long) {
		val fadeOutSeek = SettingsManager.getFadeOutSeek(context).toLong()
		val fadeInSeek = SettingsManager.getFadeInSeek(context).toLong()
		if (fadeOutSeek > 0 && isPlaying) {
			val previousPlayer = exoPlayer
			activePlayerIndex = (activePlayerIndex + 1) % 2
			val newPlayer = players[activePlayerIndex]
			startVolumeAnimation(previousPlayer, previousPlayer.volume, 0f, fadeOutSeek) {
				previousPlayer.pause()
			}
			newPlayer.seekTo(currentIndex, pos)
			newPlayer.playWhenReady = true
			startVolumeAnimation(newPlayer, 0f, 1f, fadeInSeek)
		} else {
			exoPlayer.seekTo(pos)
			if (isPlaying && fadeInSeek > 0) {
				startVolumeAnimation(exoPlayer, 0f, 1f, fadeInSeek)
			}
		}
	}
	val currentPlayIndex = rememberUpdatedState({ index: Int, play: Boolean -> playIndex(index, play) })
	val currentCurrentIndex = rememberUpdatedState(currentIndex)
	val currentPlaylistUrisSize = rememberUpdatedState(playlist.uris.size)
	val mediaSession = remember {
		MediaSessionCompat(context, "FukeXSession").apply {
			val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
				setClass(context, androidx.media.session.MediaButtonReceiver::class.java)
			}
			val pendingIntent = PendingIntent.getBroadcast(
				context, 0, mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE
			)
			setMediaButtonReceiver(pendingIntent)
			isActive = true
		}
	}
	DisposableEffect(mediaSession) {
		PlaybackService.activeMediaSession = mediaSession
		mediaSession.setCallback(object : MediaSessionCompat.Callback() {
			override fun onPlay() { startPlayback() }

			override fun onPause() { pausePlayback() }

			override fun onSkipToNext() {
				val size = currentPlaylistUrisSize.value
				val idx = currentCurrentIndex.value
				if (idx < size - 1) {
					currentPlayIndex.value.invoke(idx + 1, true)
				}
			}

			override fun onSkipToPrevious() {
				val idx = currentCurrentIndex.value
				if (idx > 0) {
					currentPlayIndex.value.invoke(idx - 1, true)
				}
			}
		})
		onDispose {
			mediaSession.release()
			PlaybackService.activeMediaSession = null
			val stopIntent = Intent(context, PlaybackService::class.java).apply {
				action = PlaybackService.ACTION_STOP
			}
			context.startService(stopIntent)
		}
	}
	LaunchedEffect(isPlaying, currentIndex, playlist.uris) {
		val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
		val playbackState = PlaybackStateCompat.Builder()
			.setActions(
				PlaybackStateCompat.ACTION_PLAY or 
				PlaybackStateCompat.ACTION_PAUSE or 
				PlaybackStateCompat.ACTION_PLAY_PAUSE or
				PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
				PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
			).setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
			.build()
		mediaSession.setPlaybackState(playbackState)
		val trackName = if (currentIndex in playlist.uris.indices) trackTitle(playlist.uris[currentIndex], currentIndex) else "Unknown"
		val metadata = MediaMetadataCompat.Builder()
			.putString(MediaMetadataCompat.METADATA_KEY_TITLE, trackName)
			.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, playlist.name)
			.build()
		mediaSession.setMetadata(metadata)
		if (isPlaying || PlaybackService.activeMediaSession != null) {
			val intent = Intent(context, PlaybackService::class.java).apply {
				putExtra(PlaybackService.EXTRA_IS_PLAYING, isPlaying)
				putExtra(PlaybackService.EXTRA_TITLE, trackName)
				putExtra(PlaybackService.EXTRA_AUTHOR, playlist.name)
			}
			ContextCompat.startForegroundService(context, intent)
		}
	}
	LaunchedEffect(isPlaying) {
		if (isPlaying && isInitialPlayback && playlist.lastPosition > 0f) {
			val dur = exoPlayer.duration
			if (dur > 0) {
				exoPlayer.seekTo((playlist.lastPosition * dur).toLong())
			}
			isInitialPlayback = false
		}
		onPlayStateChanged(isPlaying)
	}
	LaunchedEffect(currentIndex, isPlaying) {
		var isFadingOut = false
		val fadeOutAuto = SettingsManager.getFadeOutAuto(context).toLong()
		val fadeInAuto = SettingsManager.getFadeInAuto(context).toLong()
		// Tighter polling only pays off when we have to catch the crossfade window.
		val tick = if (fadeOutAuto > 0) 50L else 250L
		while (isPlaying) {
			val pos = exoPlayer.currentPosition
			val dur = exoPlayer.duration
			totalTime = if (dur > 0) dur else 0L
			if (!isSeeking) {
				currentTime = pos
				progress = if (dur > 0) pos.toFloat() / dur.toFloat() else 0f
			}
			if (fadeOutAuto > 0 && dur > 0 && (dur - pos) <= fadeOutAuto && !isFadingOut) {
				isFadingOut = true
				val nextIndex = currentIndex + 1
				if (nextIndex < playlist.uris.size) {
					val previousPlayer = exoPlayer
					activePlayerIndex = (activePlayerIndex + 1) % 2
					val newPlayer = players[activePlayerIndex]
					newPlayer.seekTo(nextIndex, 0L)
					newPlayer.playWhenReady = true
					startVolumeAnimation(newPlayer, 0f, 1f, fadeInAuto)
					startVolumeAnimation(previousPlayer, previousPlayer.volume, 0f, fadeOutAuto) {
						previousPlayer.pause()
					}
				}
			}
			kotlinx.coroutines.delay(tick)
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
		val listeners = players.map { player ->

			object : androidx.media3.common.Player.Listener {
				override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
					if (player == players[activePlayerIndex]) {
						isPlaying = isPlayingChanged
					}
				}

				override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
					if (player == players[activePlayerIndex]) {
						val newIndex = player.currentMediaItemIndex
						if (currentIndex != newIndex) {
							currentIndex = newIndex
						}
						if (reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
							val fadeInAutoDuration = SettingsManager.getFadeInAuto(context).toLong()
							if (fadeInAutoDuration > 0) {
								startVolumeAnimation(player, 0f, 1f, fadeInAutoDuration)
							} else {
								player.volume = 1f
							}
						}
					}
				}

				override fun onAudioSessionIdChanged(audioSessionId: Int) {
					updateAmplifier()
				}

				override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
					val cause = error.cause?.message ?: "Unknown cause"
					Log.e(TAG, "Playback failed: ${error.errorCodeName}", error)
					if (player == players[activePlayerIndex]) {
						if (SettingsManager.isSkipUnavailableTracksEnabled(context)) {
							android.widget.Toast.makeText(context, "Skipping unavailable track...", android.widget.Toast.LENGTH_SHORT).show()
							val size = playlist.uris.size
							if (currentIndex < size - 1) {
								playIndex(currentIndex + 1)
							} else {
								isPlaying = false
							}
						} else {
							android.widget.Toast.makeText(context, "Playback error: ${error.errorCodeName} - ${error.message}\nCause: $cause", android.widget.Toast.LENGTH_LONG).show()
							isPlaying = false
						}
					}
				}
			}
		}
		players.forEachIndexed { i, p -> p.addListener(listeners[i]) }
		onDispose {
			audioFocus.abandonFocus()
			loudnessEnhancers.forEachIndexed { i, enhancer ->
				enhancer?.release()
				loudnessEnhancers[i] = null
			}
			players.forEach { it.release() }
		}
	}
	Column(modifier = modifier) {
		LazyColumn(modifier = Modifier.weight(1f)) {
			items(playlist.uris.size) { index ->
				val uri = playlist.uris[index]
				val isSelected = index == currentIndex
				ListItem(
					headlineContent = {
						Text(
							trackTitle(uri, index),
							color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
						)
					},
					leadingContent = {
						if (isSelected) {
							Icon(Icons.Default.PlayArrow, contentDescription = "Playing", tint = MaterialTheme.colorScheme.primary)
						} else {
							Icon(Icons.Default.AudioFile, contentDescription = null)
						}
					},
					modifier = Modifier
						.clickable {
							playIndex(index)
						}
						.semantics {
							val actions = mutableListOf<CustomAccessibilityAction>()
							if (index > 0) {
								actions.add(CustomAccessibilityAction("Move Up") {
									val newUris = playlist.uris.toMutableList()
									java.util.Collections.swap(newUris, index, index - 1)
									onUpdatePlaylist(playlist.copy(uris = newUris))
									if (currentIndex == index) currentIndex = index - 1
									else if (currentIndex == index - 1) currentIndex = index
									true
								})
							}
							if (index < playlist.uris.size - 1) {
								actions.add(CustomAccessibilityAction("Move Down") {
									val newUris = playlist.uris.toMutableList()
									java.util.Collections.swap(newUris, index, index + 1)
									onUpdatePlaylist(playlist.copy(uris = newUris))
									if (currentIndex == index) currentIndex = index + 1
									else if (currentIndex == index + 1) currentIndex = index
									true
								})
							}
							actions.add(CustomAccessibilityAction("Remove") {
								val newUris = playlist.uris.toMutableList()
								newUris.removeAt(index)
								onUpdatePlaylist(playlist.copy(uris = newUris))
								if (currentIndex == index) {
									if (newUris.isEmpty()) {
										exoPlayer.stop()
										exoPlayer.clearMediaItems()
										currentIndex = -1
									} else {
										val nextIndex = if (index < newUris.size) index else newUris.lastIndex
										playIndex(nextIndex)
									}
								} else if (currentIndex > index) {
									currentIndex--
								}
								true
							})
							customActions = actions
						}
				)
			}
		}
		Surface(shadowElevation = 8.dp) {
			Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
				Text(
					text = "Now Playing: ${playlist.name} - Track ${currentIndex + 1} of ${playlist.uris.size}",
					style = MaterialTheme.typography.labelMedium,
					modifier = Modifier.padding(bottom = 8.dp)
				)
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.clearAndSetSemantics {
							contentDescription = "Track Progress"
							setProgress { targetValue ->
								val targetMs = (targetValue * totalTime).toLong()
								if (targetMs >= 0) seekToTime(targetMs)
								true
							}
							progressBarRangeInfo = ProgressBarRangeInfo(
								current = progress,
								range = 0f..1f
							)
							stateDescription = "${formatTime(currentTime)} of ${formatTime(totalTime)}"
						}
				) {
					Slider(
						value = progress,
						onValueChange = {
							isSeeking = true
							progress = it
							currentTime = (it * totalTime).toLong()
						},
						onValueChangeFinished = {
							isSeeking = false
							val newPos = (progress * exoPlayer.duration).toLong()
							if (newPos >= 0) seekToTime(newPos)
						},
						modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}
					)
					Row(
						modifier = Modifier.fillMaxWidth(), 
						horizontalArrangement = Arrangement.SpaceBetween
					) {
						Text(formatTime(currentTime))
						Text(formatTime(totalTime))
					}
				}
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.SpaceEvenly,
					verticalAlignment = Alignment.CenterVertically
				) {
					IconButton(
						onClick = { if (currentIndex > 0) { currentIndex--; playIndex(currentIndex) } },
						enabled = currentIndex > 0
					) {
						Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
					}
					IconButton(onClick = { if (isPlaying) pausePlayback() else startPlayback() }) {
						Icon(
							if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 
							contentDescription = if (isPlaying) "Pause" else "Play"
						)
					}
					IconButton(
						onClick = { if (currentIndex < playlist.uris.size - 1) { currentIndex++; playIndex(currentIndex) } },
						enabled = currentIndex < playlist.uris.size - 1
					) {
						Icon(Icons.Default.SkipNext, contentDescription = "Next")
					}
					Box {
						IconButton(
							onClick = { showMoreOptions = true },
							modifier = Modifier.semantics {
								contentDescription = "More Options"
								val actions = mutableListOf(
									CustomAccessibilityAction("Search within playlist") {
										showSearchDialog = true
										true
									},
									CustomAccessibilityAction("Playlist info") {
										showInfoDialog = true
										true
									},
									CustomAccessibilityAction(if (amplifierEnabled) "Disable Amplifier" else "Enable Amplifier (Boost ${amplifierLevel / 100}dB)") {
										amplifierEnabled = !amplifierEnabled
										SettingsManager.setAmplifierEnabled(context, amplifierEnabled)
										true
									}
								)
								if (canModifyPlaylist) {
									actions.add(
										CustomAccessibilityAction(if (playlist.isHidden) "Unlock" else "Hide") {
											onHide()
											true
										}
									)
									actions.add(
										CustomAccessibilityAction("Remove") {
											onRemove()
											true
										}
									)
								}
								customActions = actions
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
								text = { Text(if (amplifierEnabled) "Disable Amplifier" else "Enable Amplifier (Boost ${amplifierLevel / 100}dB)") },
								onClick = { 
									amplifierEnabled = !amplifierEnabled
									SettingsManager.setAmplifierEnabled(context, amplifierEnabled)
									showMoreOptions = false 
								},
								leadingIcon = { Icon(if (amplifierEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, null) }
							)
							DropdownMenuItem(
								text = { Text("Playback Effects") },
								onClick = { showPlaybackEffectsDialog = true; showMoreOptions = false },
								leadingIcon = { Icon(Icons.Default.GraphicEq, null) }
							)
							if (canModifyPlaylist) {
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
		}
	}
	if (showPlaybackEffectsDialog) {
		AlertDialog(
			modifier = Modifier.semantics { paneTitle = "Playback Effects" },
			onDismissRequest = { showPlaybackEffectsDialog = false },
			title = { Text("Playback Effects") },
			text = {
				Column {
					// Playback Speed Slider
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 8.dp)
							.clearAndSetSemantics {
								contentDescription = "Playback Speed"
								setProgress { targetValue ->
									val newSpeed = targetValue.coerceIn(0.5f, 2.0f)
									playbackSpeed = newSpeed
									SettingsManager.setPlaybackSpeed(context, newSpeed)
									true
								}
								progressBarRangeInfo = ProgressBarRangeInfo(
									current = playbackSpeed,
									range = 0.5f..2.0f,
									steps = 15
								)
								stateDescription = String.format("%.2fx speed", playbackSpeed)
							}
					) {
						Text("Playback Speed: ${String.format("%.2fx", playbackSpeed)}", style = MaterialTheme.typography.bodyLarge)
						Slider(
							value = playbackSpeed,
							onValueChange = { 
								val newSpeed = (Math.round(it * 10f) / 10f).coerceIn(0.5f, 2.0f)
								playbackSpeed = newSpeed
								SettingsManager.setPlaybackSpeed(context, newSpeed)
							},
							valueRange = 0.5f..2.0f,
							steps = 15,
							modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}
						)
					}
					// Playback Pitch Slider
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 8.dp)
							.clearAndSetSemantics {
								contentDescription = "Playback Pitch"
								setProgress { targetValue ->
									val newPitch = targetValue.coerceIn(0.5f, 2.0f)
									playbackPitch = newPitch
									SettingsManager.setPlaybackPitch(context, newPitch)
									true
								}
								progressBarRangeInfo = ProgressBarRangeInfo(
									current = playbackPitch,
									range = 0.5f..2.0f,
									steps = 15
								)
								stateDescription = String.format("%.2fx pitch", playbackPitch)
							}
					) {
						Text("Playback Pitch: ${String.format("%.2fx", playbackPitch)}", style = MaterialTheme.typography.bodyLarge)
						Slider(
							value = playbackPitch,
							onValueChange = { 
								val newPitch = (Math.round(it * 10f) / 10f).coerceIn(0.5f, 2.0f)
								playbackPitch = newPitch
								SettingsManager.setPlaybackPitch(context, newPitch)
							},
							valueRange = 0.5f..2.0f,
							steps = 15,
							modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}
						)
					}
					// Bass Boost Slider
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(vertical = 8.dp)
							.clearAndSetSemantics {
								contentDescription = "Bass Boost"
								setProgress { targetValue ->
									val newBass = targetValue.toInt().coerceIn(0, 1000)
									bassBoostLevel = newBass
									SettingsManager.setBassBoost(context, newBass)
									true
								}
								progressBarRangeInfo = ProgressBarRangeInfo(
									current = bassBoostLevel.toFloat(),
									range = 0f..1000f,
									steps = 10
								)
								stateDescription = if (bassBoostLevel == 0) "Disabled" else "Level $bassBoostLevel"
							}
					) {
						Text("Bass Boost: ${if (bassBoostLevel == 0) "Disabled" else bassBoostLevel}", style = MaterialTheme.typography.bodyLarge)
						Slider(
							value = bassBoostLevel.toFloat(),
							onValueChange = { 
								val newBass = (Math.round(it / 100f) * 100).coerceIn(0, 1000)
								bassBoostLevel = newBass
								SettingsManager.setBassBoost(context, newBass)
							},
							valueRange = 0f..1000f,
							steps = 10,
							modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}
						)
					}
				}
			},
			confirmButton = {
				TextButton(onClick = { showPlaybackEffectsDialog = false }) { Text("Close") }
			}
		)
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
						.filter { trackTitle(it.second, it.first).contains(searchQuery, ignoreCase = true) }
					LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
						items(filteredTracks) { (index, uri) ->
							ListItem(
								headlineContent = { Text(trackTitle(uri, index)) },
								modifier = Modifier.clickable {
									playIndex(index)
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
					if (unmeasuredTracks > 0) {
						Text("$unmeasuredTracks track(s) could not be measured, such as tracks on an SMB share.")
					}
					Text("Locked: ${if (playlist.isHidden) "Yes" else "No"}")
				}
			},
			confirmButton = {
				TextButton(onClick = { showInfoDialog = false }) { Text("OK") }
			}
		)
	}
}

fun formatTime(ms: Long): String {
	val totalSeconds = if (ms > 0) ms / 1000 else 0
	val hours = totalSeconds / 3600
	val minutes = (totalSeconds % 3600) / 60
	val seconds = totalSeconds % 60
	return if (hours > 0) {
		"%d:%02d:%02d".format(hours, minutes, seconds)
	} else {
		"%02d:%02d".format(minutes, seconds)
	}
}

fun trackTitle(uri: Uri, index: Int): String {
	val name = uri.lastPathSegment?.let { Uri.decode(it) }?.takeIf { it.isNotBlank() }
	return name?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Track ${index + 1}"
}

fun formatSize(size: Long): String {
	if (size <= 0) return "0 B"
	val units = arrayOf("B", "KB", "MB", "GB", "TB")
	val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
	return "%.2f %s".format(size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
