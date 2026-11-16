package com.boostofstudios.fukex
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.boostofstudios.fukex.data.SettingsManager
import com.boostofstudios.fukex.playback.PlaybackEngine
import com.boostofstudios.fukex.ui.MainScreen
import com.boostofstudios.fukex.ui.theme.FukeXTheme

class MainActivity : FragmentActivity() {
	companion object {
		// The assistant lives here; background triggers check this before broadcasting
		// Counted, so a second instance being destroyed doesn't clear it for the first
		private val aliveCount = java.util.concurrent.atomic.AtomicInteger(0)
		val isAlive: Boolean get() = aliveCount.get() > 0
		private const val REQUEST_RECORD_AUDIO = 1
	}

	private var voiceAssistantManager: VoiceAssistantManager? = null
	private var sensorManager: android.hardware.SensorManager? = null
	private var shakeDetector: ShakeDetector? = null
	private val volumeHoldDetector by lazy {
		VolumeDownHoldDetector(this) { voiceAssistantManager?.startListening() }
	}

	private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
		when (key) {
			"voice_assistant_enabled" -> {
				updateForegroundShakeDetector()
				if (SettingsManager.isVoiceAssistantEnabled(this)) requestMicPermissionIfNeeded()
			}
			SettingsManager.KEY_VOICE_SHAKE_ENABLED -> updateForegroundShakeDetector()
			SettingsManager.KEY_VOICE_SHAKE_SENSITIVITY -> shakeDetector?.thresholdG =
				SettingsManager.shakeThresholdFor(SettingsManager.getVoiceShakeSensitivity(this))
			"voice_assistant_tts_engine" -> voiceAssistantManager?.reloadTtsEngine()
		}
	}

	private val voiceAssistantReceiver = object : android.content.BroadcastReceiver() {
		override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
			if (intent.action == PlaybackService.ACTION_VOICE_ASSISTANT) {
				voiceAssistantManager?.startListening()
			}
		}
	}

	private fun requestMicPermissionIfNeeded() {
		if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
			androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
		}
	}

	private fun updateForegroundShakeDetector() {
		val sensitivity = SettingsManager.getVoiceShakeSensitivity(this)
		val wanted = SettingsManager.isVoiceAssistantEnabled(this) &&
			SettingsManager.isVoiceShakeEnabled(this) &&
			lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
		if (wanted) {
			if (sensorManager == null) {
				sensorManager = getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager
			}
			val detector = shakeDetector ?: ShakeDetector(SettingsManager.shakeThresholdFor(sensitivity)) {
				// Buzz and beep come from startListening, so a shake seen by both this
				// detector and the background service still gives one acknowledgement
				voiceAssistantManager?.startListening()
			}.also { shakeDetector = it }
			detector.thresholdG = SettingsManager.shakeThresholdFor(sensitivity)
			val accelerometer = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
			accelerometer?.let {
				// Don't stack duplicate listeners when called repeatedly
				sensorManager?.unregisterListener(detector)
				sensorManager?.registerListener(detector, it, android.hardware.SensorManager.SENSOR_DELAY_GAME)
			}
		} else {
			shakeDetector?.let { sensorManager?.unregisterListener(it) }
		}
	}

	// Hold Volume Down to talk to the assistant; a short press still lowers the volume.
	// In the background, VolumeKeyAccessibilityService does the same job (and when it
	// handles a key, the key never reaches us).
	override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
		if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN &&
			SettingsManager.isVoiceAssistantEnabled(this) && !isInCall(this) &&
			volumeHoldDetector.onKeyEvent(event)
		) return true
		return super.onKeyDown(keyCode, event)
	}

	override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
		// Always pass releases through the detector so a press it swallowed gets finished
		if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && volumeHoldDetector.onKeyEvent(event)) return true
		return super.onKeyUp(keyCode, event)
	}

	override fun onResume() {
		super.onResume()
		updateForegroundShakeDetector()
	}

	override fun onPause() {
		super.onPause()
		shakeDetector?.let { sensorManager?.unregisterListener(it) }
		volumeHoldDetector.reset()
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
		aliveCount.incrementAndGet()

		// Only ask for the microphone when the assistant is actually switched on
		if (SettingsManager.isVoiceAssistantEnabled(this)) requestMicPermissionIfNeeded()

		SettingsManager.registerChangeListener(this, prefsListener)

		val commandHandler = VoiceCommandHandler(this) { voiceAssistantManager }
		voiceAssistantManager = VoiceAssistantManager(this) { command -> commandHandler.handle(command) }

		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
			registerReceiver(voiceAssistantReceiver, android.content.IntentFilter(PlaybackService.ACTION_VOICE_ASSISTANT), RECEIVER_NOT_EXPORTED)
		} else {
			@Suppress("UnspecifiedRegisterReceiverFlag")
			registerReceiver(voiceAssistantReceiver, android.content.IntentFilter(PlaybackService.ACTION_VOICE_ASSISTANT))
		}

		handleVoiceIntent(intent)

		setContent {
			val iconPack by com.boostofstudios.fukex.data.IconPackManager.active.collectAsState()
			androidx.compose.runtime.CompositionLocalProvider(com.boostofstudios.fukex.ui.LocalIconPack provides iconPack) {
				FukeXTheme {
					Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
						MainScreen(modifier = Modifier.padding(innerPadding))
					}
				}
			}
		}
	}

	override fun onNewIntent(intent: android.content.Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		handleVoiceIntent(intent)
	}

	// The background volume-key shortcut relaunches us with this extra when we'd been destroyed
	private fun handleVoiceIntent(intent: android.content.Intent?) {
		if (intent?.getBooleanExtra(VoiceAssistantTrigger.EXTRA_START_VOICE, false) == true) {
			intent.removeExtra(VoiceAssistantTrigger.EXTRA_START_VOICE)
			if (SettingsManager.isVoiceAssistantEnabled(this)) voiceAssistantManager?.startListening()
		}
	}

	override fun onDestroy() {
		aliveCount.decrementAndGet()
		super.onDestroy()
		SettingsManager.unregisterChangeListener(this, prefsListener)
		shakeDetector?.let { sensorManager?.unregisterListener(it) }
		volumeHoldDetector.reset()
		unregisterReceiver(voiceAssistantReceiver)
		voiceAssistantManager?.destroy()
		voiceAssistantManager = null
	}
}
