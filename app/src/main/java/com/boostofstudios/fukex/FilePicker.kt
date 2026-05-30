package com.boostofstudios.fukex
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun File.isMediaFile(): Boolean {
	val ext = extension.lowercase()
	return ext in listOf("mp3", "mp4", "flac", "wav", "m4a", "aac", "ogg", "mkv", "webm", "opus", "alac")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerScreen(
	onFilesSelected: (List<Uri>) -> Unit,
	onCancel: () -> Unit
) {
	val root = Environment.getExternalStorageDirectory()
	var currentDir by remember { mutableStateOf(root) }
	var files by remember { mutableStateOf<List<File>>(emptyList()) }
	var isScanning by remember { mutableStateOf(false) }
	LaunchedEffect(currentDir) {
		withContext(Dispatchers.IO) {
			val listed = currentDir.listFiles()
			if (listed != null) {
				files = listed.filter { !it.isHidden }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
			} else {
				files = emptyList()
			}
		}
	}
	BackHandler {
		if (currentDir.absolutePath == root.absolutePath) {
			onCancel()
		} else {
			currentDir = currentDir.parentFile ?: root
		}
	}
	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(currentDir.name) },
				navigationIcon = {
					if (currentDir.absolutePath != root.absolutePath) {
						IconButton(onClick = { currentDir = currentDir.parentFile ?: root }) {
							Icon(Icons.Default.Folder, contentDescription = "Go up to parent folder")
						}
					}
				},
				actions = {
					TextButton(onClick = onCancel) {
						Text("Cancel")
					}
				}
			)
		}
	) { padding ->
		if (isScanning) {
			Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
				Column(horizontalAlignment = Alignment.CenterHorizontally) {
					CircularProgressIndicator()
					Spacer(Modifier.height(16.dp))
					Text("Scanning folder for media files...")
				}
			}
		} else {
			LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
				items(files) { file ->
					val isDir = file.isDirectory
					val isMedia = file.isMediaFile()
					ListItem(
						headlineContent = { Text(file.name) },
						leadingContent = {
							Icon(
								when {
									isDir -> Icons.Default.Folder
									isMedia -> Icons.Default.AudioFile
									else -> Icons.Default.InsertDriveFile
								},
								contentDescription = if (isDir) "Folder" else "File"
							)
						},
						modifier = Modifier
							.fillMaxWidth()
							.clickable {
								if (isDir) {
									currentDir = file
								} else if (isMedia) {
									onFilesSelected(listOf(Uri.fromFile(file)))
								}
							}
							.semantics {
								if (isDir) {
									customActions = listOf(
										CustomAccessibilityAction("Add entire folder") {
											isScanning = true
											Thread {
												val mediaFiles = file.walkTopDown()
													.filter { it.isFile && it.isMediaFile() }
													.map { Uri.fromFile(it) }
													.toList()
												android.os.Handler(android.os.Looper.getMainLooper()).post {
													onFilesSelected(mediaFiles)
												}
											}.start()
											true
										}
									)
								}
							}
					)
				}
			}
		}
	}
}
