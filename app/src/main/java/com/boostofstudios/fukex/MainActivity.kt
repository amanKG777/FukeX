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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import com.boostofstudios.fukex.data.Playlist
import com.boostofstudios.fukex.data.PlaylistManager
import com.boostofstudios.fukex.ui.theme.FukeXTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
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

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val newPlaylist = Playlist(
                id = UUID.randomUUID().toString(),
                name = it.lastPathSegment ?: "Single File",
                uris = listOf(it)
            )
            val newList = playlists + newPlaylist
            playlists = newList
            PlaylistManager.savePlaylists(context, newList)
            selectedTabIndex = newList.size
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
            try {
                val jsonString = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { it.readText() }
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
                    PlaylistManager.savePlaylists(context, newList)
                    selectedTabIndex = newList.size
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { exportUri ->
            showPlaylistActions?.let { playlist ->
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

    val visiblePlaylists = playlists.filter { !it.isHidden }

    Column(modifier = modifier.fillMaxSize()) {
        if (isScanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            edgePadding = 16.dp,
            modifier = Modifier.fillMaxWidth()
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
                                    PlaylistManager.savePlaylists(context, newList)
                                    true
                                } else false
                            },
                            CustomAccessibilityAction("Move Down") {
                                val idx = playlists.indexOf(playlist)
                                if (idx < playlists.size - 1) {
                                    val newList = playlists.toMutableList()
                                    java.util.Collections.swap(newList, idx, idx + 1)
                                    playlists = newList
                                    PlaylistManager.savePlaylists(context, newList)
                                    true
                                } else false
                            },
                            CustomAccessibilityAction("Hide") {
                                showPinSetupDialog = playlist
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
                                PlaylistManager.savePlaylists(context, newList)
                                selectedTabIndex = 0
                                true
                            }
                        )
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(playlist.name)
                            IconButton(onClick = { showPlaylistActions = playlist }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Actions for ${playlist.name}")
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
                        Button(
                            onClick = { showOptionsDialog = true },
                            modifier = Modifier.semantics { contentDescription = "Play" }
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Play")
                        }
                        Spacer(Modifier.width(16.dp))
                        Button(
                            onClick = { importLauncher.launch(arrayOf("application/json")) },
                            modifier = Modifier.semantics { contentDescription = "Import Playlist" }
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Import")
                        }
                    }
                    
                    if (playlists.any { it.isHidden }) {
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = { 
                            // Show first hidden playlist for simplicity in this unlock flow
                            val hidden = playlists.find { it.isHidden }
                            showPinDialog = hidden
                        }) {
                            Text("Show Hidden Playlists")
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
                            PlaylistManager.updatePlaylistProgress(context, currentPlaylist.id, index, pos)
                        },
                        onBack = { selectedTabIndex = 0 }
                    )
                }
            }
        }
    }

    // Playlist Actions Menu
    showPlaylistActions?.let { playlist ->
        AlertDialog(
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
                                PlaylistManager.savePlaylists(context, newList)
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
                                PlaylistManager.savePlaylists(context, newList)
                            }
                            showPlaylistActions = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Hide Playlist") },
                        leadingContent = { Icon(Icons.Default.Lock, null) },
                        modifier = Modifier.clickable {
                            showPinSetupDialog = playlist
                            showPlaylistActions = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Export Playlist") },
                        leadingContent = { Icon(Icons.Default.FileUpload, null) },
                        modifier = Modifier.clickable {
                            exportLauncher.launch("${playlist.name}.json")
                            // showPlaylistActions stays until launcher finishes or manually cleared
                            // but we clear it in launcher callback via showPlaylistActions reference
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Remove") },
                        leadingContent = { Icon(Icons.Default.Delete, null) },
                        modifier = Modifier.clickable {
                            val newList = playlists.filter { it.id != playlist.id }
                            playlists = newList
                            PlaylistManager.savePlaylists(context, newList)
                            selectedTabIndex = 0
                            showPlaylistActions = null
                        }
                    )
                }
            },
            confirmButton = {}
        )
    }

    // PIN Setup Dialog
    showPinSetupDialog?.let { playlist ->
        AlertDialog(
            onDismissRequest = { showPinSetupDialog = null },
            title = { Text("Set PIN to hide playlist") },
            text = {
                Column {
                    Text("Enter a PIN to hide this playlist. Note: If you forget your PIN, the playlist cannot be recovered.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = pinInput,
                        onValueChange = { pinInput = it },
                        placeholder = { Text("4-6 digit PIN") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pinInput.length >= 4) {
                        val newList = playlists.map { 
                            if (it.id == playlist.id) it.copy(isHidden = true, pin = pinInput) else it 
                        }
                        playlists = newList
                        PlaylistManager.savePlaylists(context, newList)
                        selectedTabIndex = 0
                        pinInput = ""
                        showPinSetupDialog = null
                    }
                }) { Text("Hide") }
            }
        )
    }

    // PIN Unlock Dialog
    showPinDialog?.let { playlist ->
        AlertDialog(
            onDismissRequest = { showPinDialog = null },
            title = { Text("Unlock Playlist") },
            text = {
                TextField(
                    value = pinInput,
                    onValueChange = { pinInput = it },
                    placeholder = { Text("Enter PIN") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (pinInput == playlist.pin) {
                        val newList = playlists.map { 
                            if (it.id == playlist.id) it.copy(isHidden = false) else it 
                        }
                        playlists = newList
                        PlaylistManager.savePlaylists(context, newList)
                        pinInput = ""
                        showPinDialog = null
                    }
                }) { Text("Unlock") }
            }
        )
    }

    if (showOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = false },
            title = { Text("Select what you would like to play") },
            text = {
                Column {
                    Button(
                        onClick = {
                            showOptionsDialog = false
                            folderPickerLauncher.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics { contentDescription = "Playlist" }
                    ) {
                        Text("Playlist (Folder)")
                    }
                    Button(
                        onClick = {
                            showOptionsDialog = false
                            filePickerLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics { contentDescription = "File" }
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
                            PlaylistManager.savePlaylists(context, newList)
                            selectedTabIndex = newList.size
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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val libVLC = remember { LibVLC(context, arrayListOf("-vvv")) }
    val mediaPlayer = remember { MediaPlayer(libVLC) }
    
    var currentIndex by remember { mutableIntStateOf(playlist.lastIndex) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentTime by remember { mutableLongStateOf(0L) }
    var totalTime by remember { mutableLongStateOf(0L) }
    
    var currentPFD by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var isInitialPlayback by remember { mutableStateOf(true) }

    fun playIndex(index: Int) {
        if (index in playlist.uris.indices) {
            val uri = playlist.uris[index]
            try {
                currentPFD?.close()
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    currentPFD = pfd
                    val media = Media(libVLC, pfd.fileDescriptor)
                    mediaPlayer.media = media
                    media.release()
                    mediaPlayer.play()
                    currentIndex = index
                    isPlaying = true
                } else {
                    val media = Media(libVLC, uri)
                    mediaPlayer.media = media
                    media.release()
                    mediaPlayer.play()
                    currentIndex = index
                    isPlaying = true
                }
            } catch (e: Exception) {
                val media = Media(libVLC, uri)
                mediaPlayer.media = media
                media.release()
                mediaPlayer.play()
                currentIndex = index
                isPlaying = true
            }
        }
    }

    LaunchedEffect(currentIndex) {
        playIndex(currentIndex)
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying && isInitialPlayback && playlist.lastPosition > 0f) {
            mediaPlayer.position = playlist.lastPosition
            isInitialPlayback = false
        }
    }

    // Use a timer for saving progress every 5 seconds
    LaunchedEffect(currentIndex, isPlaying) {
        while (isPlaying) {
            kotlinx.coroutines.delay(5000)
            onProgressUpdate(currentIndex, progress)
        }
    }
    
    // Also save on dispose or track change
    DisposableEffect(currentIndex) {
        onDispose {
            onProgressUpdate(currentIndex, progress)
        }
    }

    DisposableEffect(Unit) {
        val eventListener = MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.PositionChanged -> progress = event.positionChanged
                MediaPlayer.Event.TimeChanged -> currentTime = event.timeChanged
                MediaPlayer.Event.LengthChanged -> totalTime = event.lengthChanged
                MediaPlayer.Event.EndReached -> {
                    if (currentIndex < playlist.uris.size - 1) {
                        currentIndex++
                    } else {
                        onBack()
                    }
                }
                MediaPlayer.Event.Playing -> isPlaying = true
                MediaPlayer.Event.Paused -> isPlaying = false
                MediaPlayer.Event.Stopped -> isPlaying = false
            }
        }
        mediaPlayer.setEventListener(eventListener)

        onDispose {
            mediaPlayer.stop()
            mediaPlayer.release()
            currentPFD?.close()
            libVLC.release()
        }
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
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
                VLCVideoLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    mediaPlayer.attachViews(this, null, true, false)
                }
            },
            modifier = Modifier.weight(1f)
        )

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Slider(
                value = progress,
                onValueChange = { 
                    progress = it
                    mediaPlayer.position = it
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
                IconButton(onClick = {
                    if (currentIndex > 0) currentIndex--
                }, enabled = currentIndex > 0, modifier = Modifier.semantics { contentDescription = "Previous" }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                }

                IconButton(onClick = {
                    val newTime = (mediaPlayer.time - 10000).coerceAtLeast(0)
                    mediaPlayer.time = newTime
                }, modifier = Modifier.semantics { contentDescription = "Rewind" }) {
                    Icon(Icons.Default.FastRewind, contentDescription = "Rewind")
                }
                
                IconButton(onClick = {
                    if (isPlaying) mediaPlayer.pause() else mediaPlayer.play()
                }, modifier = Modifier.semantics { contentDescription = if (isPlaying) "Pause" else "Play" }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, 
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }

                IconButton(onClick = {
                    val newTime = (mediaPlayer.time + 10000).coerceAtMost(totalTime)
                    mediaPlayer.time = newTime
                }, modifier = Modifier.semantics { contentDescription = "Forward" }) {
                    Icon(Icons.Default.FastForward, contentDescription = "Forward")
                }

                IconButton(onClick = {
                    if (currentIndex < playlist.uris.size - 1) currentIndex++
                }, enabled = currentIndex < playlist.uris.size - 1, modifier = Modifier.semantics { contentDescription = "Next" }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next")
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
