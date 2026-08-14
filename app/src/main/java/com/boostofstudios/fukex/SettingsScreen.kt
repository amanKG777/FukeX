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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
	androidx.activity.compose.BackHandler(onBack = onBack)
	val context = LocalContext.current
	val activity = remember(context) { context.findActivity() }
	var isBiometricEnabled by remember { mutableStateOf(SettingsManager.isBiometricEnabled(context)) }
	var isBackgroundPlaybackEnabled by remember { mutableStateOf(SettingsManager.isBackgroundPlaybackEnabled(context)) }
	var isSkipSilenceEnabled by remember { mutableStateOf(SettingsManager.isSkipSilenceEnabled(context)) }
	var isSkipUnavailableEnabled by remember { mutableStateOf(SettingsManager.isSkipUnavailableTracksEnabled(context)) }
	var isExitPromptEnabled by remember { mutableStateOf(SettingsManager.isExitPromptEnabled(context)) }
	var lockTimeout by remember { mutableStateOf(SettingsManager.getLockTimeout(context)) }
	var showTimeoutDialog by remember { mutableStateOf(false) }
	var showAboutScreen by remember { mutableStateOf(false) }
	var amplifierLevel by remember { mutableIntStateOf(SettingsManager.getAmplifierLevel(context)) }
	var showAmplifierWarning by remember { mutableStateOf(false) }
	var pendingAmplifierLevel by remember { mutableIntStateOf(0) }
	var selectedFadeEvent by remember { mutableStateOf("Manual Track Change") }
	val fadeEvents = listOf("Seek", "Pause/Play", "Manual Track Change", "Automatic Track Change")
	var fadeMenuExpanded by remember { mutableStateOf(false) }
	if (showAboutScreen) {
		AboutScreen(onBack = { showAboutScreen = false })
		return
	}

	fun authenticateAndEnableBiometrics(enabled: Boolean) {
		if (!enabled) {
			SettingsManager.setBiometricEnabled(context, false)
			isBiometricEnabled = false
			return
		}
		val biometricManager = BiometricManager.from(context)
		val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
		if (biometricManager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
			android.widget.Toast.makeText(context, "Biometric authentication is not available or not set up on this device.", android.widget.Toast.LENGTH_LONG).show()
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
				SettingsToggleItem(
					title = "Exit Prompt",
					subtitle = "Show confirmation dialog when closing app",
					icon = Icons.Default.Info,
					checked = isExitPromptEnabled,
					onCheckedChange = { 
						SettingsManager.setExitPromptEnabled(context, it)
						isExitPromptEnabled = it 
					}
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
				SettingsToggleItem(
					title = "Skip Silence",
					subtitle = "Speed through quiet or silent parts (not recommended for music)",
					icon = Icons.Default.FastForward,
					checked = isSkipSilenceEnabled,
					onCheckedChange = { 
						SettingsManager.setSkipSilenceEnabled(context, it)
						isSkipSilenceEnabled = it 
					}
				)
				SettingsToggleItem(
					title = "Skip Unavailable Tracks",
					subtitle = "Automatically skip to the next track if a file cannot be played",
					icon = Icons.Default.SkipNext,
					checked = isSkipUnavailableEnabled,
					onCheckedChange = { 
						SettingsManager.setSkipUnavailableTracksEnabled(context, it)
						isSkipUnavailableEnabled = it 
					}
				)
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 16.dp, vertical = 8.dp)
						.clearAndSetSemantics {
							contentDescription = "Amplifier Boost"
							setProgress { targetValue ->
								val newLevel = (targetValue.toInt() * 500)
								if (newLevel > 1500 && newLevel > amplifierLevel) {
									pendingAmplifierLevel = newLevel
									showAmplifierWarning = true
								} else {
									amplifierLevel = newLevel
									SettingsManager.setAmplifierLevel(context, newLevel)
								}
								true
							}
							progressBarRangeInfo = ProgressBarRangeInfo(
								current = (amplifierLevel / 500).toFloat(),
								range = 0f..5f,
								steps = 4
							)
							stateDescription = "${amplifierLevel / 100} decibels"
						}
				) {
					Row(verticalAlignment = Alignment.CenterVertically) {
						Icon(Icons.AutoMirrored.Filled.VolumeUp, null, modifier = Modifier.padding(end = 16.dp))
						Column {
							Text("Amplifier Boost: ${amplifierLevel / 100} dB", style = MaterialTheme.typography.bodyLarge)
							Text("Increase volume beyond standard limits.", style = MaterialTheme.typography.bodyMedium)
						}
					}
					Slider(
						value = (amplifierLevel / 500).toFloat(),
						onValueChange = { 
							val newLevel = (it.toInt() * 500)
							if (newLevel > 1500 && newLevel > amplifierLevel) {
								pendingAmplifierLevel = newLevel
								showAmplifierWarning = true
							} else {
								amplifierLevel = newLevel
								SettingsManager.setAmplifierLevel(context, newLevel)
							}
						},
						valueRange = 0f..5f,
						steps = 4,
						modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}
					)
				}
			}
			item {
				SettingsHeader("Fading (Gapless Playback)")
				Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
					OutlinedButton(onClick = { fadeMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
						Text("Event: $selectedFadeEvent")
						Icon(Icons.Default.ArrowDropDown, null)
					}
					DropdownMenu(
						expanded = fadeMenuExpanded,
						onDismissRequest = { fadeMenuExpanded = false }
					) {
						fadeEvents.forEach { event ->
							DropdownMenuItem(
								text = { Text(event) },
								onClick = {
									selectedFadeEvent = event
									fadeMenuExpanded = false
								}
							)
						}
					}
				}
				var fadeInValue by remember(selectedFadeEvent) { 
					mutableIntStateOf(
						when (selectedFadeEvent) {
							"Seek" -> SettingsManager.getFadeInSeek(context)
							"Pause/Play" -> SettingsManager.getFadeInPause(context)
							"Manual Track Change" -> SettingsManager.getFadeInManual(context)
							else -> SettingsManager.getFadeInAuto(context)
						}
					)
				}
				var fadeOutValue by remember(selectedFadeEvent) { 
					mutableIntStateOf(
						when (selectedFadeEvent) {
							"Seek" -> SettingsManager.getFadeOutSeek(context)
							"Pause/Play" -> SettingsManager.getFadeOutPause(context)
							"Manual Track Change" -> SettingsManager.getFadeOutManual(context)
							else -> SettingsManager.getFadeOutAuto(context)
						}
					)
				}
				FadeSliderItem(
					title = "Fade In",
					msValue = fadeInValue,
					onValueChange = {
						fadeInValue = it
						when (selectedFadeEvent) {
							"Seek" -> SettingsManager.setFadeInSeek(context, it)
							"Pause/Play" -> SettingsManager.setFadeInPause(context, it)
							"Manual Track Change" -> SettingsManager.setFadeInManual(context, it)
							"Automatic Track Change" -> SettingsManager.setFadeInAuto(context, it)
						}
					}
				)
				FadeSliderItem(
					title = "Fade Out",
					msValue = fadeOutValue,
					onValueChange = {
						fadeOutValue = it
						when (selectedFadeEvent) {
							"Seek" -> SettingsManager.setFadeOutSeek(context, it)
							"Pause/Play" -> SettingsManager.setFadeOutPause(context, it)
							"Manual Track Change" -> SettingsManager.setFadeOutManual(context, it)
							"Automatic Track Change" -> SettingsManager.setFadeOutAuto(context, it)
						}
					}
				)
			}
			item {
				SettingsHeader("About")
				ListItem(
					headlineContent = { Text("About FukeX") },
					supportingContent = { Text("Version ${BuildConfig.VERSION_NAME}, Copyright info, and license") },
					leadingContent = { Icon(Icons.Default.Info, null) },
					modifier = Modifier.clickable { showAboutScreen = true }
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
	if (showAmplifierWarning) {
		AlertDialog(
			onDismissRequest = { showAmplifierWarning = false },
			title = { Text("Speaker Damage Warning") },
			text = {
				Text("Increasing the amplifier boost above 15dB may damage your speakers. By continuing, you agree that you are responsible for any damage, not the product.")
			},
			confirmButton = {
				TextButton(onClick = { 
					amplifierLevel = pendingAmplifierLevel
					SettingsManager.setAmplifierLevel(context, pendingAmplifierLevel)
					showAmplifierWarning = false 
				}) { Text("OK") }
			},
			dismissButton = {
				TextButton(onClick = { showAmplifierWarning = false }) { Text("Cancel") }
			}
		)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
	androidx.activity.compose.BackHandler(onBack = onBack)
	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("About FukeX") },
				navigationIcon = {
					IconButton(onClick = onBack) {
						Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to settings")
					}
				}
			)
		}
	) { padding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(padding)
				.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			Text("FukeX version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineMedium)
			HorizontalDivider()
			Text(
				"Copyright (C) This project is copyrighted under the BoostOf Studios Creation Copyright 2026",
				style = MaterialTheme.typography.bodyLarge
			)
			Text(
				"This project is licensed under the MIT License.",
				style = MaterialTheme.typography.bodyMedium
			)
		}
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

@Composable
fun FadeSliderItem(title: String, msValue: Int, onValueChange: (Int) -> Unit) {
	Column(
		modifier = Modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp, vertical = 8.dp)
			.clearAndSetSemantics {
				contentDescription = title
				setProgress { targetValue ->
					onValueChange((Math.round(targetValue / 10f) * 10))
					true
				}
				progressBarRangeInfo = ProgressBarRangeInfo(
					current = msValue.toFloat(),
					range = 0f..10000f,
					steps = 1000
				)
				stateDescription = if (msValue == 0) "Disabled" else "$msValue milliseconds"
			}
	) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Icon(Icons.Default.GraphicEq, null, modifier = Modifier.padding(end = 16.dp))
			Column {
				Text(title, style = MaterialTheme.typography.bodyLarge)
				Text(if (msValue == 0) "Disabled" else "$msValue ms", style = MaterialTheme.typography.bodyMedium)
			}
		}
		Slider(
			value = msValue.toFloat(),
			onValueChange = { onValueChange((Math.round(it / 10f) * 10)) },
			valueRange = 0f..10000f,
			modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}
		)
	}
}
