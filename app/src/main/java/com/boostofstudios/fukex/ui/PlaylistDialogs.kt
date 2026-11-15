package com.boostofstudios.fukex.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.boostofstudios.fukex.data.AuthType
import com.boostofstudios.fukex.data.Playlist
import com.boostofstudios.fukex.data.PlaylistSecurity

@Composable
fun ExitDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Exit FukeX") },
		text = { Text("Do you really wanna quit?") },
		confirmButton = { Button(onClick = onConfirm) { Text("Yes") } },
		dismissButton = { TextButton(onClick = onDismiss) { Text("No") } }
	)
}

@Composable
fun StorageRationaleDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
	AlertDialog(
		modifier = Modifier.semantics { paneTitle = "Storage access needed" },
		onDismissRequest = onDismiss,
		title = { Text("Storage access needed") },
		text = { Text("FukeX uses its own file picker so it works properly with TalkBack. To browse your music, Android needs to grant it access to all files. You can skip this and still play from an SMB share.") },
		confirmButton = { Button(onClick = onOpenSettings) { Text("Open settings") } },
		dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } }
	)
}

@Composable
fun PlaylistActionsDialog(
	playlist: Playlist,
	canModify: Boolean,
	onMoveLeft: () -> Unit,
	onMoveRight: () -> Unit,
	onToggleHidden: () -> Unit,
	onExport: () -> Unit,
	onRemove: () -> Unit,
	onDismiss: () -> Unit
) {
	AlertDialog(
		modifier = Modifier.semantics { paneTitle = "Actions: ${playlist.name}" },
		onDismissRequest = onDismiss,
		title = { Text("Actions: ${playlist.name}") },
		text = {
			Column {
				if (canModify) {
					ListItem(
						headlineContent = { Text("Move Left") },
						leadingContent = { Icon(FxIcons.ArrowBack, null) },
						modifier = Modifier.clickable(onClick = onMoveLeft)
					)
					ListItem(
						headlineContent = { Text("Move Right") },
						leadingContent = { Icon(FxIcons.ArrowForward, null) },
						modifier = Modifier.clickable(onClick = onMoveRight)
					)
					ListItem(
						headlineContent = { Text(if (playlist.isHidden) "Unlock Playlist" else "Hide Playlist") },
						leadingContent = { Icon(if (playlist.isHidden) FxIcons.LockOpen else FxIcons.Lock, null) },
						modifier = Modifier.clickable(onClick = onToggleHidden)
					)
				}
				ListItem(
					headlineContent = { Text("Export Playlist") },
					leadingContent = { Icon(FxIcons.FileUpload, null) },
					modifier = Modifier.clickable(onClick = onExport)
				)
				if (canModify) {
					ListItem(
						headlineContent = { Text("Remove") },
						leadingContent = { Icon(FxIcons.Delete, null) },
						modifier = Modifier.clickable(onClick = onRemove)
					)
				}
			}
		},
		confirmButton = {}
	)
}

@Composable
fun LockedPlaylistPickerDialog(lockedPlaylists: List<Playlist>, onPick: (Playlist) -> Unit, onDismiss: () -> Unit) {
	AlertDialog(
		modifier = Modifier.semantics { paneTitle = "Select Playlist to View" },
		onDismissRequest = onDismiss,
		title = { Text("Select Playlist to View") },
		text = {
			Column {
				lockedPlaylists.forEach { playlist ->
					ListItem(
						headlineContent = { Text(playlist.name) },
						modifier = Modifier.clickable { onPick(playlist) }
					)
				}
			}
		},
		confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
	)
}

@Composable
fun PinSetupDialog(onConfirm: (secret: String, authType: AuthType) -> Unit, onDismiss: () -> Unit) {
	var expanded by remember { mutableStateOf(false) }
	var authType by remember { mutableStateOf(AuthType.PIN) }
	var input by remember { mutableStateOf("") }
	AlertDialog(
		modifier = Modifier.semantics { paneTitle = "Set PIN/Password to hide playlist" },
		onDismissRequest = onDismiss,
		title = { Text("Set PIN/Password to hide playlist") },
		text = {
			Column {
				Text("Enter a PIN or Password to hide this playlist. Note: If you forget it, the playlist cannot be recovered.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
				Spacer(Modifier.height(8.dp))
				androidx.compose.foundation.layout.Box {
					OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
						Text("Type: ${authType.name}")
					}
					DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
						DropdownMenuItem(text = { Text("PIN") }, onClick = { authType = AuthType.PIN; expanded = false })
						DropdownMenuItem(text = { Text("Password") }, onClick = { authType = AuthType.PASSWORD; expanded = false })
					}
				}
				Spacer(Modifier.height(8.dp))
				TextField(
					value = input,
					onValueChange = { input = it },
					placeholder = { Text(if (authType == AuthType.PIN) "4-6 digit PIN" else "Password") },
					keyboardOptions = KeyboardOptions(
						keyboardType = if (authType == AuthType.PIN) KeyboardType.Number else KeyboardType.Password
					),
					modifier = Modifier.fillMaxWidth()
				)
			}
		},
		confirmButton = {
			Button(onClick = { if (input.length >= 4) onConfirm(input, authType) }) { Text("Hide") }
		}
	)
}

// Shared by "view a locked playlist" and "permanently unlock"; calls onVerified only for the right PIN.
@Composable
fun PlaylistSecretDialog(
	playlist: Playlist,
	title: String,
	message: String?,
	confirmLabel: String,
	onVerified: () -> Unit,
	onDismiss: () -> Unit
) {
	var input by remember { mutableStateOf("") }
	var error by remember { mutableStateOf("") }
	AlertDialog(
		modifier = Modifier.semantics { paneTitle = title },
		onDismissRequest = onDismiss,
		title = { Text(title) },
		text = {
			Column {
				if (error.isNotEmpty()) {
					Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
					Spacer(Modifier.height(4.dp))
				}
				if (message != null) {
					Text(message)
					Spacer(Modifier.height(8.dp))
				}
				TextField(
					value = input,
					onValueChange = { input = it; error = "" },
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
				if (PlaylistSecurity.verify(input, playlist)) onVerified() else error = "Incorrect ${playlist.authType.name}"
			}) { Text(confirmLabel) }
		}
	)
}

@Composable
fun NamePlaylistDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
	var name by remember { mutableStateOf("") }
	AlertDialog(
		modifier = Modifier.semantics { paneTitle = "Name your playlist" },
		onDismissRequest = onDismiss,
		title = { Text("Name your playlist") },
		text = {
			TextField(
				value = name,
				onValueChange = { name = it },
				placeholder = { Text("Playlist Name") },
				singleLine = true,
				modifier = Modifier.fillMaxWidth()
			)
		},
		confirmButton = { Button(onClick = { if (name.isNotBlank()) onCreate(name) }) { Text("Create") } },
		dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
	)
}

@Composable
fun AddMediaDialog(
	onLocal: () -> Unit,
	onLibrary: () -> Unit,
	onSmb: () -> Unit,
	onYoutube: () -> Unit,
	onDismiss: () -> Unit
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text("Add Media") },
		text = {
			Column {
				Text("Where would you like to add media from?", modifier = Modifier.padding(bottom = 16.dp))
				Button(onClick = onLibrary, modifier = Modifier.fillMaxWidth()) { Text("Music Library") }
				Button(onClick = onLocal, modifier = Modifier.fillMaxWidth()) { Text("Local Device") }
				Button(onClick = onSmb, modifier = Modifier.fillMaxWidth()) { Text("SMB Share") }
				Button(onClick = onYoutube, modifier = Modifier.fillMaxWidth()) { Text("YouTube Search") }
			}
		},
		confirmButton = {},
		dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
	)
}

@Composable
fun AddToPlaylistDialog(playlists: List<Playlist>, onPick: (Playlist) -> Unit, onDismiss: () -> Unit) {
	AlertDialog(
		modifier = Modifier.semantics { paneTitle = "Add to playlist" },
		onDismissRequest = onDismiss,
		title = { Text("Add to playlist") },
		text = {
			Column {
				playlists.forEach { playlist ->
					ListItem(
						headlineContent = { Text(playlist.name) },
						supportingContent = { Text("${playlist.uris.size} tracks") },
						modifier = Modifier.clickable { onPick(playlist) }
					)
				}
			}
		},
		confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
	)
}
