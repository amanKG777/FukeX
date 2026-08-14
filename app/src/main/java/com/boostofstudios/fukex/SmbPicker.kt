package com.boostofstudios.fukex
import android.net.Uri
import android.util.Log
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.boostofstudios.fukex.data.SmbConfig
import com.boostofstudios.fukex.data.SmbCredentialStore
import com.boostofstudios.fukex.data.isMediaUrl
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SMB_TAG = "SmbPicker"
private const val SMB_MAX_DEPTH = 12

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmbPickerScreen(
	onFilesSelected: (List<Uri>) -> Unit,
	onCancel: () -> Unit
) {
	val appContext = LocalContext.current.applicationContext
	val scope = rememberCoroutineScope()
	var smbUrl by remember { mutableStateOf("smb://") }
	var username by remember { mutableStateOf("") }
	var password by remember { mutableStateOf("") }
	var showLogin by remember { mutableStateOf(true) }
	var currentUrl by remember { mutableStateOf("") }

	data class SmbItemInfo(val name: String, val url: String, val isDirectory: Boolean)
	var files by remember { mutableStateOf<List<SmbItemInfo>>(emptyList()) }
	var isScanning by remember { mutableStateOf(false) }
	var errorMsg by remember { mutableStateOf("") }
	val cifsContext = remember(username, password) { SmbConfig.contextFor(username, password) }
	LaunchedEffect(currentUrl, cifsContext) {
		if (currentUrl.isEmpty()) return@LaunchedEffect
		isScanning = true
		errorMsg = ""
		val result = withContext(Dispatchers.IO) {
			try {
				val dirUrl = if (currentUrl.endsWith("/")) currentUrl else "$currentUrl/"
				val listed = SmbFile(dirUrl, cifsContext).listFiles() ?: emptyArray()
				Result.success(
					listed.filter { !it.isHidden }
						.map { SmbItemInfo(it.name, dirUrl + it.name, it.isDirectory) }
						.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
				)
			} catch (e: Exception) {
				Log.e(SMB_TAG, "Could not list $currentUrl", e)
				Result.failure(e)
			}
		}
		result.onSuccess { files = it }.onFailure {
			errorMsg = "Failed to access SMB: ${it.message}"
			files = emptyList()
		}
		isScanning = false
	}

	fun goUp() {
		if (currentUrl == smbUrl) {
			showLogin = true
			return
		}
		val parts = currentUrl.trimEnd('/').split("/")
		if (parts.size <= 3) showLogin = true
		else currentUrl = parts.dropLast(1).joinToString("/") + "/"
	}
	BackHandler {
		if (showLogin) onCancel() else goUp()
	}
	if (showLogin) {
		Scaffold(
			topBar = {
				TopAppBar(
					title = { Text("Connect to SMB Share") },
					actions = { TextButton(onClick = onCancel) { Text("Cancel") } }
				)
			}
		) { padding ->
			Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
				TextField(value = smbUrl, onValueChange = { smbUrl = it }, label = { Text("SMB URL (e.g. smb://192.168.1.100/share/)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
				Spacer(Modifier.height(8.dp))
				TextField(value = username, onValueChange = { username = it }, label = { Text("Username (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
				Spacer(Modifier.height(8.dp))
				TextField(
					value = password,
					onValueChange = { password = it },
					label = { Text("Password (Optional)") },
					singleLine = true,
					keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
					modifier = Modifier.fillMaxWidth()
				)
				Spacer(Modifier.height(16.dp))
				Button(onClick = {
					if (!smbUrl.endsWith("/")) smbUrl += "/"
					SmbCredentialStore.save(appContext, Uri.parse(smbUrl), username, password)
					currentUrl = smbUrl
					showLogin = false
				}, modifier = Modifier.fillMaxWidth()) {
					Text("Connect")
				}
			}
		}
	} else {
		Scaffold(
			topBar = {
				TopAppBar(
					title = { Text(Uri.decode(currentUrl.trimEnd('/').substringAfterLast("/", currentUrl))) },
					navigationIcon = {
						IconButton(onClick = { goUp() }) { Icon(Icons.Default.Folder, contentDescription = "Go up") }
					},
					actions = { TextButton(onClick = onCancel) { Text("Cancel") } }
				)
			}
		) { padding ->
			if (isScanning) {
				Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
					CircularProgressIndicator()
				}
			} else if (errorMsg.isNotEmpty()) {
				Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
					Text(errorMsg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
				}
			} else {
				LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
					items(files) { file ->
						val isDir = file.isDirectory
						val isMedia = file.name.isMediaUrl()
						ListItem(
							headlineContent = { Text(Uri.decode(file.name.trimEnd('/'))) },
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
										currentUrl = file.url
									} else if (isMedia) {
										onFilesSelected(listOf(Uri.parse(file.url)))
									}
								}
								.semantics {
									if (isDir) {
										customActions = listOf(
											CustomAccessibilityAction("Add entire folder") {
												isScanning = true
												scope.launch {
													val mediaFiles = withContext(Dispatchers.IO) {
														val collected = mutableListOf<Uri>()
														fun scan(dirUrl: String, depth: Int) {
															if (depth > SMB_MAX_DEPTH) return
															try {
																val base = if (dirUrl.endsWith("/")) dirUrl else "$dirUrl/"
																SmbFile(base, cifsContext).listFiles()?.forEach { child ->
																	val childUrl = base + child.name
																	if (child.isDirectory) {
																		scan(childUrl, depth + 1)
																	} else if (child.name.isMediaUrl()) {
																		collected.add(Uri.parse(childUrl))
																	}
																}
															} catch (e: Exception) {
																Log.e(SMB_TAG, "Could not scan $dirUrl", e)
															}
														}
														scan(file.url, 0)
														collected
													}
													isScanning = false
													onFilesSelected(mediaFiles)
												}
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
}
