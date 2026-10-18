package com.boostofstudios.fukex
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.boostofstudios.fukex.data.SettingsManager
import com.boostofstudios.fukex.playback.PlaybackEngine
import com.boostofstudios.fukex.ui.MainScreen
import com.boostofstudios.fukex.ui.theme.FukeXTheme

class MainActivity : FragmentActivity() {
	private var voiceAssistantManager: VoiceAssistantManager? = null
	private var sensorManager: android.hardware.SensorManager? = null
	private var shakeDetector: ShakeDetector? = null

	private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
		if (key == "voice_assistant_enabled") {
			updateForegroundShakeDetector()
		}
	}

	private val voiceAssistantReceiver = object : android.content.BroadcastReceiver() {
		override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
			if (intent.action == PlaybackService.ACTION_VOICE_ASSISTANT) {
				voiceAssistantManager?.startListening()
			}
		}
	}

	private fun updateForegroundShakeDetector() {
		if (SettingsManager.isVoiceAssistantEnabled(this)) {
			if (sensorManager == null) {
				sensorManager = getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager
				shakeDetector = ShakeDetector {
					try {
						val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
						if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
							vibrator.vibrate(android.os.VibrationEffect.createOneShot(200, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
						} else {
							@Suppress("DEPRECATION")
							vibrator.vibrate(200)
						}
					} catch (e: Exception) {}

					playFeedbackBeep()

					voiceAssistantManager?.startListening()
				}
			}
			val accelerometer = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
			accelerometer?.let {
				sensorManager?.registerListener(shakeDetector, it, android.hardware.SensorManager.SENSOR_DELAY_GAME)
			}
		} else {
			shakeDetector?.let { sensorManager?.unregisterListener(it) }
		}
	}

	override fun onResume() {
		super.onResume()
		updateForegroundShakeDetector()
	}

	override fun onPause() {
		super.onPause()
		shakeDetector?.let { sensorManager?.unregisterListener(it) }
	}

	override fun onStop() {
		super.onStop()
		// Playback lives in PlaybackEngine now, so honour the setting here rather than in the player UI
		if (!isChangingConfigurations && !SettingsManager.isBackgroundPlaybackEnabled(this)) {
			PlaybackEngine.get(this).pause()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()

		if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
			androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), 1)
		}

		SettingsManager.registerChangeListener(this, prefsListener)
		updateForegroundShakeDetector()

		val commandHandler = VoiceCommandHandler(this) { voiceAssistantManager }
		voiceAssistantManager = VoiceAssistantManager(this) { command, query -> commandHandler.handle(command, query) }

		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
			registerReceiver(voiceAssistantReceiver, android.content.IntentFilter(PlaybackService.ACTION_VOICE_ASSISTANT), RECEIVER_NOT_EXPORTED)
		} else {
			@Suppress("UnspecifiedRegisterReceiverFlag")
			registerReceiver(voiceAssistantReceiver, android.content.IntentFilter(PlaybackService.ACTION_VOICE_ASSISTANT))
		}

		setContent {
			FukeXTheme {
				Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
					MainScreen(modifier = Modifier.padding(innerPadding))
				}
			}
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		SettingsManager.unregisterChangeListener(this, prefsListener)
		unregisterReceiver(voiceAssistantReceiver)
		voiceAssistantManager?.destroy()
	}
}
