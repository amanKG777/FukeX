package com.boostofstudios.fukex

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.boostofstudios.fukex.data.LockTimeout
import com.boostofstudios.fukex.data.SettingsManager

import android.content.Context
import android.content.ContextWrapper

fun Context.findActivity(): FragmentActivity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
	onBack: () -> Unit
) {
	val context = LocalContext.current
	val activity = remember(context) { context.findActivity() }
	var isBiometricEnabled by remember { mutableStateOf(SettingsManager.isBiometricEnabled(context)) }
	var isBackgroundPlaybackEnabled by remember { mutableStateOf(SettingsManager.isBackgroundPlaybackEnabled(context)) }
	var lockTimeout by remember { mutableStateOf(SettingsManager.getLockTimeout(context)) }
	var showTimeoutDialog by remember { mutableStateOf(false) }

	fun authenticateAndEnableBiometrics(enabled: Boolean) {
		if (!enabled) {
			SettingsManager.setBiometricEnabled(context, false)
			isBiometricEnabled = false
			return
		}

		val fragmentActivity = activity ?: return
		val executor = ContextCompat.getMainExecutor(context)
		val biometricPrompt = BiometricPrompt(fragmentActivity, executor,
			object : BiometricPrompt.AuthenticationCallback() {
				override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
					super.onAuthenticationSucceeded(result)
					SettingsManager.setBiometricEnabled(context, true)
					isBiometricEnabled = true
				}
			})

		val promptInfo = BiometricPrompt.PromptInfo.Builder()
			.setTitle("Biometric Authentication")
			.setSubtitle("Authenticate to enable biometric unlock")
			.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
			.build()

		biometricPrompt.authenticate(promptInfo)
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Settings", modifier = Modifier.semantics { heading() }) },
				navigationIcon = {
					IconButton(onClick = onBack) {
						Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to playlists")
					}
				}
			)
		}
	) { padding ->
		LazyColumn(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
		) {
			item {
				SettingsHeader("Security")
				SettingsToggleItem(
					title = "Biometric Unlock",
					subtitle = "Use fingerprint or face to unlock playlists",
					icon = Icons.Default.Fingerprint,
					checked = isBiometricEnabled,
					onCheckedChange = { authenticateAndEnableBiometrics(it) }
				)
				
				ListItem(
					headlineContent = { Text("Lock Timeout") },
					supportingContent = { Text("Automatically lock playlists after: ${lockTimeout.label}") },
					leadingContent = { Icon(Icons.Default.LockClock, null) },
					modifier = Modifier.clickable { showTimeoutDialog = true }
				)
			}

			item {
				SettingsHeader("Playback")
				SettingsToggleItem(
					title = "Background Playback",
					subtitle = "Keep playing when app is in background",
					icon = Icons.Default.PlayCircle,
					checked = isBackgroundPlaybackEnabled,
					onCheckedChange = { 
						SettingsManager.setBackgroundPlaybackEnabled(context, it)
						isBackgroundPlaybackEnabled = it 
					}
				)
			}

			item {
				SettingsHeader("About")
				ListItem(
					headlineContent = { Text("Version") },
					supportingContent = { Text("1.0.0") },
					leadingContent = { Icon(Icons.Default.Info, null) },
					modifier = Modifier.semantics { 
						role = Role.Button
					}
				)
			}
		}
	}

	if (showTimeoutDialog) {
		AlertDialog(
			onDismissRequest = { showTimeoutDialog = false },
			title = { Text("Select Lock Timeout") },
			text = {
				Column {
					LockTimeout.entries.forEach { timeout ->
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.clickable {
									SettingsManager.setLockTimeout(context, timeout)
									lockTimeout = timeout
									showTimeoutDialog = false
								}
								.padding(16.dp)
						) {
							RadioButton(selected = lockTimeout == timeout, onClick = null)
							Spacer(Modifier.width(8.dp))
							Text(timeout.label)
						}
					}
				}
			},
			confirmButton = {
				TextButton(onClick = { showTimeoutDialog = false }) { Text("Cancel") }
			}
		)
	}
}

@Composable
fun SettingsHeader(title: String) {
	Text(
		text = title,
		style = MaterialTheme.typography.labelLarge,
		color = MaterialTheme.colorScheme.primary,
		modifier = Modifier
			.padding(horizontal = 16.dp, vertical = 8.dp)
			.semantics { heading() }
	)
}

@Composable
fun SettingsToggleItem(
	title: String,
	subtitle: String,
	icon: androidx.compose.ui.graphics.vector.ImageVector,
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit
) {
	ListItem(
		headlineContent = { Text(title) },
		supportingContent = { Text(subtitle) },
		leadingContent = { Icon(icon, null) },
		trailingContent = {
			Switch(
				checked = checked,
				onCheckedChange = null // Managed by ListItem click for better touch target
			)
		},
		modifier = Modifier
			.clickable { onCheckedChange(!checked) }
			.semantics {
				stateDescription = if (checked) "Enabled" else "Disabled"
				role = Role.Switch
			}
	)
}
