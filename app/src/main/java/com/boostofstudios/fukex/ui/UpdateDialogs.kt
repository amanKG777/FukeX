package com.boostofstudios.fukex.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.boostofstudios.fukex.BuildConfig
import com.boostofstudios.fukex.data.UpdateManager
import com.boostofstudios.fukex.data.UpdateState

private fun formatMegabytes(bytes: Long) = "%.1f MB".format(bytes / (1024.0 * 1024.0))

// Walks through check, download, permission and install. Lives in the main screen so it shows
// over whichever screen started the check.
@Composable
fun UpdateDialogs() {
	val context = LocalContext.current
	val state by UpdateManager.state.collectAsState()

	// Coming back from Android's "install unknown apps" page continues the install by itself
	LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { UpdateManager.onResume(context) }

	when (val s = state) {
		UpdateState.Idle -> Unit
		is UpdateState.Checking -> if (s.manual) {
			AlertDialog(
				onDismissRequest = { UpdateManager.dismiss() },
				title = { Text("Checking for updates") },
				text = { Text("Looking for a newer version of FukeX...", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) },
				confirmButton = {},
				dismissButton = { TextButton(onClick = { UpdateManager.dismiss() }) { Text("Cancel") } }
			)
		}
		is UpdateState.UpToDate -> AlertDialog(
			onDismissRequest = { UpdateManager.dismiss() },
			title = { Text("You're up to date") },
			text = { Text("FukeX ${s.version} is the latest version.") },
			confirmButton = { TextButton(onClick = { UpdateManager.dismiss() }) { Text("OK") } }
		)
		is UpdateState.Available -> AlertDialog(
			onDismissRequest = { UpdateManager.dismiss() },
			title = { Text("Update available") },
			text = {
				Column(Modifier.verticalScroll(rememberScrollState())) {
					val size = if (s.release.apkSizeBytes > 0) " (${formatMegabytes(s.release.apkSizeBytes)})" else ""
					Text("FukeX ${s.release.version} is ready$size. You have ${BuildConfig.VERSION_NAME}.")
					if (s.release.notes.isNotBlank()) {
						Spacer(Modifier.height(12.dp))
						Text("What's new", style = MaterialTheme.typography.titleSmall)
						Text(s.release.notes.take(800), style = MaterialTheme.typography.bodyMedium)
					}
					Spacer(Modifier.height(12.dp))
					Text("FukeX will download it and then ask Android to install it. Your playlists and settings are kept.")
				}
			},
			confirmButton = { Button(onClick = { UpdateManager.download(context) }) { Text("Update now") } },
			dismissButton = { TextButton(onClick = { UpdateManager.dismiss() }) { Text("Later") } }
		)
		is UpdateState.Downloading -> AlertDialog(
			onDismissRequest = {},
			title = { Text("Downloading update") },
			text = {
				Column(Modifier.fillMaxWidth()) {
					val percent = if (s.total > 0) (s.bytes * 100 / s.total).toInt().coerceIn(0, 100) else null
					Text(
						if (percent != null) "Downloading FukeX ${s.release.version}: $percent%" else "Downloading FukeX ${s.release.version}...",
						modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
					)
					Spacer(Modifier.height(12.dp))
					if (percent != null) {
						LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
					} else {
						LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
					}
				}
			},
			confirmButton = {},
			dismissButton = { TextButton(onClick = { UpdateManager.dismiss() }) { Text("Cancel") } }
		)
		is UpdateState.NeedsPermission -> AlertDialog(
			onDismissRequest = { UpdateManager.dismiss() },
			title = { Text("Allow FukeX to install updates") },
			text = {
				Text(
					"The update is downloaded and checked. Android needs your permission before FukeX can install it. " +
						"Tap Open settings, switch on \"Allow from this source\", then come back. The installer opens by itself.",
					modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
				)
			},
			confirmButton = { Button(onClick = { UpdateManager.openInstallPermissionSettings(context) }) { Text("Open settings") } },
			dismissButton = { TextButton(onClick = { UpdateManager.dismiss() }) { Text("Cancel") } }
		)
		is UpdateState.Failed -> AlertDialog(
			onDismissRequest = { UpdateManager.dismiss() },
			title = { Text("Update failed") },
			text = { Text(s.message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) },
			confirmButton = {
				if (s.release != null) TextButton(onClick = { UpdateManager.download(context) }) { Text("Try again") }
				else TextButton(onClick = { UpdateManager.dismiss() }) { Text("Close") }
			},
			dismissButton = if (s.release != null) {
				{ TextButton(onClick = { UpdateManager.dismiss() }) { Text("Close") } }
			} else null
		)
	}
}
