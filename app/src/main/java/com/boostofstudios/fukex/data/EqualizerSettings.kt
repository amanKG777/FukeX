package com.boostofstudios.fukex.data

import android.content.Context

enum class AudioRoute(val label: String) {
	SPEAKER("Phone speaker"),
	WIRED("Wired headphones"),
	BLUETOOTH("Bluetooth"),
	USB("USB audio")
}

data class EqConfig(
	val enabled: Boolean = false,
	val preset: String = EqualizerPresets.FLAT,
	val gainsDb: List<Float> = List(EqualizerPresets.BAND_FREQUENCIES.size) { 0f },
	val preampDb: Float = 0f
)

object EqualizerPresets {
	val BAND_FREQUENCIES = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
	const val MAX_GAIN_DB = 12f
	const val FLAT = "Flat"
	const val CUSTOM = "Custom"

	val presets: Map<String, List<Float>> = linkedMapOf(
		FLAT to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
		"Bass Boost" to listOf(7f, 6f, 5f, 3f, 1f, 0f, 0f, 0f, 0f, 0f),
		"Treble Boost" to listOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f, 6f, 7f),
		"Vocal" to listOf(-2f, -2f, -1f, 1f, 3f, 4f, 4f, 3f, 1f, 0f),
		"Rock" to listOf(5f, 4f, 3f, 1f, -1f, -1f, 1f, 3f, 4f, 5f),
		"Pop" to listOf(-1f, 1f, 3f, 4f, 3f, 1f, -1f, -1f, 1f, 2f),
		"Jazz" to listOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f),
		"Classical" to listOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f),
		"Electronic" to listOf(5f, 4f, 1f, 0f, -2f, 1f, 1f, 2f, 4f, 5f),
		"Hip-Hop" to listOf(6f, 5f, 2f, 3f, -1f, -1f, 1f, 0f, 2f, 3f),
		"Loudness" to listOf(6f, 4f, 0f, 0f, -2f, 0f, -1f, -3f, 4f, 2f),
		"Spoken Word" to listOf(-4f, -3f, 0f, 2f, 4f, 4f, 3f, 1f, -1f, -3f)
	)

	fun label(frequency: Int): String = if (frequency >= 1000) "${frequency / 1000} kHz" else "$frequency Hz"
}

// One EQ setup per output, so headphones and the speaker can sound different.
object EqualizerStore {
	private fun key(route: AudioRoute, name: String) = "eq_${route.name.lowercase()}_$name"

	fun load(context: Context, route: AudioRoute): EqConfig {
		val prefs = SettingsManager.getPrefs(context)
		val gains = prefs.getString(key(route, "gains"), null)
			?.split(',')
			?.mapNotNull { it.toFloatOrNull() }
			?.takeIf { it.size == EqualizerPresets.BAND_FREQUENCIES.size }
		return EqConfig(
			enabled = prefs.getBoolean(key(route, "enabled"), false),
			preset = prefs.getString(key(route, "preset"), EqualizerPresets.FLAT) ?: EqualizerPresets.FLAT,
			gainsDb = gains ?: EqConfig().gainsDb,
			preampDb = prefs.getFloat(key(route, "preamp"), 0f)
		)
	}

	fun save(context: Context, route: AudioRoute, config: EqConfig) {
		SettingsManager.getPrefs(context).edit()
			.putBoolean(key(route, "enabled"), config.enabled)
			.putString(key(route, "preset"), config.preset)
			.putString(key(route, "gains"), config.gainsDb.joinToString(","))
			.putFloat(key(route, "preamp"), config.preampDb)
			.apply()
	}
}
