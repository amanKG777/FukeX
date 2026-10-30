package com.boostofstudios.fukex.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.boostofstudios.fukex.data.CatalogEntry
import com.boostofstudios.fukex.data.IconPackException
import com.boostofstudios.fukex.data.IconPackManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val DEFAULT_PACK_ID = "visual-icons"

sealed interface IconPackInstallState {
	data object Idle : IconPackInstallState
	data object Working : IconPackInstallState
	data class Done(val updated: Boolean) : IconPackInstallState
	data class UpToDate(val name: String) : IconPackInstallState
	data class Failed(val reason: String) : IconPackInstallState
}

// Shared by the first-run question and the settings section, so both behave the same.
class IconPackInstaller(private val context: Context, private val scope: CoroutineScope) {
	var state: IconPackInstallState by mutableStateOf<IconPackInstallState>(IconPackInstallState.Idle)
		private set
	private var job: Job? = null

	// Downloads the pack, or, when onlyIfNewer is set, only replaces an installed pack with a newer version.
	fun start(onlyIfNewer: Boolean = false) {
		if (state == IconPackInstallState.Working) return
		state = IconPackInstallState.Working
		job = scope.launch {
			state = try {
				val catalog = IconPackManager.fetchCatalog()
				val entry: CatalogEntry = catalog.firstOrNull { it.id == DEFAULT_PACK_ID } ?: catalog.firstOrNull()
					?: throw IconPackException("No icon packs are available right now")
				val installed = IconPackManager.installed.value
				if (onlyIfNewer && installed != null && installed.data.id == entry.id && installed.data.version >= entry.version) {
					IconPackInstallState.UpToDate(installed.data.name)
				} else {
					IconPackManager.install(context.applicationContext, entry)
					IconPackInstallState.Done(updated = installed != null)
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: IconPackException) {
				IconPackInstallState.Failed(e.message ?: "Something went wrong")
			} catch (e: Exception) {
				IconPackInstallState.Failed("Something went wrong")
			}
		}
	}

	fun cancel() {
		job?.cancel()
		state = IconPackInstallState.Idle
	}

	fun reset() {
		state = IconPackInstallState.Idle
	}
}

@Composable
fun rememberIconPackInstaller(): IconPackInstaller {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	return remember { IconPackInstaller(context, scope) }
}

// Asked once, the first time the app opens: the icon pack is optional and never bundled.
@Composable
fun IconPackPrompt(onFinished: () -> Unit) {
	val installer = rememberIconPackInstaller()
	val state = installer.state
	when (state) {
		IconPackInstallState.Idle -> AlertDialog(
			onDismissRequest = onFinished,
			icon = FxIcons.decoration("section_icons")?.let { { androidx.compose.material3.Icon(it, null) } },
			title = { Text("Download visual icons?") },
			text = {
				Text(
					"FukeX can add a set of clean, colour-matched icons to every screen, menu and dialog. " +
						"It is a small optional download from GitHub, and it is not built into the app. " +
						"You can add, switch off or remove it any time in Settings."
				)
			},
			confirmButton = { TextButton(onClick = { installer.start() }) { Text("Download") } },
			dismissButton = { TextButton(onClick = onFinished) { Text("Not now") } }
		)
		IconPackInstallState.Working -> AlertDialog(
			onDismissRequest = {},
			title = { Text("Downloading icons") },
			text = {
				Row(verticalAlignment = Alignment.CenterVertically) {
					CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
					Spacer(Modifier.width(16.dp))
					Text("Downloading from GitHub...", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
				}
			},
			confirmButton = {},
			dismissButton = { TextButton(onClick = { installer.cancel() }) { Text("Cancel") } }
		)
		is IconPackInstallState.Failed -> AlertDialog(
			onDismissRequest = onFinished,
			title = { Text("Couldn't download icons") },
			text = { Text(state.reason, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) },
			confirmButton = { TextButton(onClick = { installer.start() }) { Text("Try again") } },
			dismissButton = { TextButton(onClick = onFinished) { Text("Close") } }
		)
		else -> AlertDialog(
			onDismissRequest = onFinished,
			title = { Text("Visual icons are on") },
			text = { Text("Your icons have changed. You can switch them off or delete them in Settings, under Visual Icons.", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) },
			confirmButton = { TextButton(onClick = onFinished) { Text("Done") } }
		)
	}
}

// The Settings section: download, switch on or off, update and delete the pack.
@Composable
fun IconPackSettingsItems(onDeleteRequested: () -> Unit) {
	val installer = rememberIconPackInstaller()
	val context = LocalContext.current
	val installed by IconPackManager.installed.collectAsState()
	val active by IconPackManager.active.collectAsState()
	val pack = installed
	val state = installer.state

	val status = when (state) {
		IconPackInstallState.Working -> "Working..."
		is IconPackInstallState.Failed -> state.reason
		is IconPackInstallState.UpToDate -> "${state.name} is already the latest version"
		is IconPackInstallState.Done -> if (state.updated) "Updated" else "Downloaded and switched on"
		IconPackInstallState.Idle -> null
	}

	if (pack == null) {
		ListItem(
			headlineContent = { Text("Download Visual Icons") },
			supportingContent = { Text(status ?: "Optional icon pack from GitHub that restyles every screen, menu and dialog", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) },
			leadingContent = { androidx.compose.material3.Icon(FxIcons.CloudDownload, null) },
			modifier = Modifier.clickable(enabled = state != IconPackInstallState.Working) { installer.start() }
		)
	} else {
		com.boostofstudios.fukex.SettingsToggleItem(
			title = "Use Visual Icons",
			subtitle = "${pack.data.name}, version ${pack.data.version}",
			icon = FxIcons.Category,
			checked = active != null,
			onCheckedChange = { IconPackManager.setEnabled(context, it) }
		)
		ListItem(
			headlineContent = { Text("Check for icon updates") },
			supportingContent = { Text(status ?: "Look for a newer version on GitHub", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) },
			leadingContent = { androidx.compose.material3.Icon(FxIcons.FileDownload, null) },
			modifier = Modifier.clickable(enabled = state != IconPackInstallState.Working) { installer.start(onlyIfNewer = true) }
		)
		ListItem(
			headlineContent = { Text("Delete icon pack") },
			supportingContent = { Text("Go back to the built-in icons and free the space") },
			leadingContent = { androidx.compose.material3.Icon(FxIcons.Delete, null) },
			modifier = Modifier.clickable { onDeleteRequested() }
		)
	}
}
