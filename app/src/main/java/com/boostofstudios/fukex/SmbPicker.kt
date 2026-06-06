package com.boostofstudios.fukex
import android.net.Uri
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
import com.boostofstudios.fukex.data.SmbConfig
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun String.isMediaUrl(): Boolean {
	val ext = this.substringAfterLast('.', "").lowercase()
	return ext in listOf("mp3", "mp4", "flac", "wav", "m4a", "aac", "ogg", "mkv", "webm", "opus", "alac")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmbPickerScreen(
	onFilesSelected: (List<Uri>) -> Unit,
	onCancel: () -> Unit
) {
	var smbUrl by remember { mutableStateOf("smb://") }
	var username by remember { mutableStateOf("") }
	var password by remember { mutableStateOf("") }
	var showLogin by remember { mutableStateOf(true) }
	var currentUrl by remember { mutableStateOf("") }

	data class SmbItemInfo(val name: String, val cleanUrl: String, val authUrl: String, val isDirectory: Boolean)
	var files by remember { mutableStateOf<List<SmbItemInfo>>(emptyList()) }
	var isScanning by remember { mutableStateOf(false) }
	var errorMsg by remember { mutableStateOf("") }
	var smbContext by remember { mutableStateOf<jcifs.CIFSContext?>(null) }
	var currentFileContext by remember { mutableStateOf<jcifs.CIFSContext?>(null) }
	LaunchedEffect(Unit) {
		withContext(Dispatchers.IO) {
			smbContext = SmbConfig.baseContext
		}
	}
	LaunchedEffect(currentUrl, smbContext) {
		if (currentUrl.isNotEmpty() && smbContext != null) {
			isScanning = true
			errorMsg = ""
			withContext(Dispatchers.IO) {
				try {
					var processedUrl = currentUrl
					if (!processedUrl.endsWith("/")) {
						processedUrl += "/"
					}
					val urlToLoad = if (username.isNotEmpty()) {
						val cleanUrl = processedUrl.replace("smb://", "")
						val encUser = java.net.URLEncoder.encode(username, "UTF-8").replace("+", "%20")
						val encPass = java.net.URLEncoder.encode(password, "UTF-8").replace("+", "%20")
						"smb://${encUser}:${encPass}@${cleanUrl}"
					} else {
						processedUrl
					}
					// Parse the URL to explicitly extract and use NtlmPasswordAuthenticator
					val parsedUri = android.net.Uri.parse(urlToLoad)
					val userInfo = parsedUri.userInfo
					val ctx = smbContext ?: return@withContext
					val fileContext = if (userInfo != null) {
						val parts = userInfo.split(":", limit = 2)
						val decodedUser = java.net.URLDecoder.decode(parts[0], "UTF-8")
						val decodedPass = if (parts.size > 1) java.net.URLDecoder.decode(parts[1], "UTF-8") else ""
						val domain = if (decodedUser.contains(";")) decodedUser.substringBefore(";") else null
						val actualUser = if (decodedUser.contains(";")) decodedUser.substringAfter(";") else decodedUser
						val auth = jcifs.smb.NtlmPasswordAuthenticator(domain, actualUser, decodedPass)
						ctx.withCredentials(auth)
					} else {
						ctx
					}
					currentFileContext = fileContext
					val smbDir = SmbFile(urlToLoad, fileContext)
					val listed = smbDir.listFiles()
					if (listed != null) {
						// We process isDirectory and isHidden on the IO thread to avoid NetworkOnMainThreadException
						val fileInfos = listed.filter { !it.isHidden }.map { 
							val childCleanUrl = if (currentUrl.endsWith("/")) currentUrl + it.name else currentUrl + "/" + it.name
							val childAuthUrl = if (urlToLoad.endsWith("/")) urlToLoad + it.name else urlToLoad + "/" + it.name
							SmbItemInfo(it.name, childCleanUrl, childAuthUrl, it.isDirectory)
						}
						files = fileInfos.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
					} else {
						files = emptyList()
					}
				} catch (e: Exception) {
					e.printStackTrace()
					errorMsg = "Failed to access SMB: ${e.message}"
					files = emptyList()
				} finally {
					isScanning = false
				}
			}
		}
	}
	BackHandler {
		if (showLogin) {
			onCancel()
		} else {
			if (currentUrl == smbUrl) {
				showLogin = true
			} else {
				val parts = currentUrl.trimEnd('/').split("/")
				if (parts.size <= 3) { // smb://host
					showLogin = true
				} else {
					currentUrl = parts.dropLast(1).joinToString("/") + "/"
				}
			}
		}
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
				TextField(value = smbUrl, onValueChange = { smbUrl = it }, label = { Text("SMB URL (e.g. smb://192.168.1.100/share/)") }, modifier = Modifier.fillMaxWidth())
				Spacer(Modifier.height(8.dp))
				TextField(value = username, onValueChange = { username = it }, label = { Text("Username (Optional)") }, modifier = Modifier.fillMaxWidth())
				Spacer(Modifier.height(8.dp))
				TextField(value = password, onValueChange = { password = it }, label = { Text("Password (Optional)") }, modifier = Modifier.fillMaxWidth())
				Spacer(Modifier.height(16.dp))
				Button(onClick = {
					if (!smbUrl.endsWith("/")) smbUrl += "/"
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
					title = { Text(currentUrl.substringAfterLast("/", currentUrl)) },
					navigationIcon = {
						IconButton(onClick = {
							if (currentUrl == smbUrl) {
								showLogin = true
							} else {
								val parts = currentUrl.trimEnd('/').split("/")
								if (parts.size <= 3) showLogin = true
								else currentUrl = parts.dropLast(1).joinToString("/") + "/"
							}
						}) { Icon(Icons.Default.Folder, contentDescription = "Go up") }
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
							headlineContent = { Text(file.name.trimEnd('/')) },
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
										currentUrl = file.cleanUrl
									} else if (isMedia) {
										// We need to return the URL with credentials so ExoPlayer can access it
										onFilesSelected(listOf(Uri.parse(file.authUrl)))
									}
								}
								.semantics {
									if (isDir) {
										customActions = listOf(
											CustomAccessibilityAction("Add entire folder") {
												isScanning = true
												Thread {
													val mediaFiles = mutableListOf<Uri>()

													fun scan(dirUrl: String, dirAuthUrl: String) {
														try {
															val ctx = currentFileContext ?: smbContext
															if (ctx == null) return
															SmbFile(dirAuthUrl, ctx).listFiles()?.forEach { child ->
																val childClean = if (dirUrl.endsWith("/")) dirUrl + child.name else dirUrl + "/" + child.name
																val childAuth = if (dirAuthUrl.endsWith("/")) dirAuthUrl + child.name else dirAuthUrl + "/" + child.name
																if (child.isDirectory) {
																	scan(childClean, childAuth)
																} else if (child.name.isMediaUrl()) {
																	mediaFiles.add(Uri.parse(childAuth))
																}
															}
														} catch (e: Exception) { e.printStackTrace() }
													}
													scan(file.cleanUrl, file.authUrl)
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
}
