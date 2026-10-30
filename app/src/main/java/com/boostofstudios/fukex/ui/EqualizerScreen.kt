package com.boostofstudios.fukex.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.boostofstudios.fukex.SettingsToggleItem
import com.boostofstudios.fukex.data.AudioRoute
import com.boostofstudios.fukex.data.EqConfig
import com.boostofstudios.fukex.data.EqualizerPresets
import com.boostofstudios.fukex.data.EqualizerStore
import com.boostofstudios.fukex.playback.PlaybackEngine
import kotlin.math.roundToInt

private fun decibels(value: Float): String {
	val rounded = value.roundToInt()
	return when {
		rounded > 0 -> "+$rounded decibels"
		rounded < 0 -> "$rounded decibels"
		else -> "0 decibels"
	}
}

@Composable
private fun Picker(label: String, current: String, options: List<String>, onPick: (String) -> Unit) {
	var expanded by remember { mutableStateOf(false) }
	Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
		OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
			Text("$label: $current")
			Icon(FxIcons.ArrowDropDown, null)
		}
		DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
			options.forEach { option ->
				DropdownMenuItem(text = { Text(option) }, onClick = { onPick(option); expanded = false })
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(engine: PlaybackEngine, onBack: () -> Unit) {
	androidx.activity.compose.BackHandler(onBack = onBack)
	val context = LocalContext.current
	val connectedRoute = remember { engine.currentRoute }
	var route by remember { mutableStateOf(connectedRoute) }
	var config by remember { mutableStateOf(EqualizerStore.load(context, connectedRoute)) }

	fun update(newConfig: EqConfig) {
		config = newConfig
		engine.setEqualizer(route, newConfig)
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text("Equalizer", modifier = Modifier.semantics { heading() }) },
				navigationIcon = {
					IconButton(onClick = onBack) {
						Icon(FxIcons.ArrowBack, contentDescription = "Back")
					}
				}
			)
		}
	) { padding ->
		LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
			item {
				Text(
					"Each output keeps its own settings. Connected now: ${connectedRoute.label}.",
					style = MaterialTheme.typography.bodyMedium,
					modifier = Modifier.padding(16.dp)
				)
				Picker(
					label = "Editing",
					current = route.label + if (route == connectedRoute) " (connected)" else "",
					options = AudioRoute.entries.map { it.label + if (it == connectedRoute) " (connected)" else "" }
				) { picked ->
					val newRoute = AudioRoute.entries.first { picked.startsWith(it.label) }
					route = newRoute
					config = EqualizerStore.load(context, newRoute)
				}
				SettingsToggleItem(
					title = "Enable Equalizer",
					subtitle = "10-band equalizer for ${route.label}",
					icon = FxIcons.Equalizer,
					checked = config.enabled,
					onCheckedChange = { update(config.copy(enabled = it)) }
				)
				Picker(
					label = "Preset",
					current = config.preset,
					options = EqualizerPresets.presets.keys.toList()
				) { name ->
					val gains = EqualizerPresets.presets[name] ?: return@Picker
					update(config.copy(preset = name, gainsDb = gains, enabled = true))
				}
			}
			item {
				Column(modifier = Modifier.padding(horizontal = 16.dp)) {
					AccessibleSlider(
						label = "Preamp",
						value = config.preampDb,
						range = -EqualizerPresets.MAX_GAIN_DB..EqualizerPresets.MAX_GAIN_DB,
						steps = 23,
						stateText = decibels(config.preampDb)
					) { update(config.copy(preampDb = it.roundToInt().toFloat())) }
					EqualizerPresets.BAND_FREQUENCIES.forEachIndexed { band, frequency ->
						val gain = config.gainsDb[band]
						AccessibleSlider(
							label = EqualizerPresets.label(frequency),
							value = gain,
							range = -EqualizerPresets.MAX_GAIN_DB..EqualizerPresets.MAX_GAIN_DB,
							steps = 23,
							stateText = decibels(gain)
						) { value ->
							val gains = config.gainsDb.toMutableList().apply { this[band] = value.roundToInt().toFloat() }
							update(config.copy(gainsDb = gains, preset = EqualizerPresets.CUSTOM))
						}
					}
				}
			}
			item {
				TextButton(
					onClick = { update(EqConfig(enabled = config.enabled)) },
					modifier = Modifier.padding(16.dp)
				) { Text("Reset to flat") }
			}
		}
	}
}
