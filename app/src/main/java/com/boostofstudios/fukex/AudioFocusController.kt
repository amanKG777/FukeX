package com.boostofstudios.fukex
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

// FukeX crossfades between two ExoPlayers, so focus is held once for the app rather than per player.
class AudioFocusController(
	context: Context,
	private val onPause: () -> Unit,
	private val onResume: () -> Unit,
	private val onDuck: (Boolean) -> Unit
) {
	private val audioManager =
		context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
	private var pausedByLoss = false

	private val listener = AudioManager.OnAudioFocusChangeListener { change ->
		when (change) {
			AudioManager.AUDIOFOCUS_LOSS -> {
				pausedByLoss = false
				onPause()
			}
			AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
				pausedByLoss = true
				onPause()
			}
			AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onDuck(true)
			AudioManager.AUDIOFOCUS_GAIN -> {
				onDuck(false)
				if (pausedByLoss) {
					pausedByLoss = false
					onResume()
				}
			}
		}
	}

	private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
		.setAudioAttributes(
			AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_MEDIA)
				.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
				.build()
		)
		.setWillPauseWhenDucked(false)
		.setOnAudioFocusChangeListener(listener)
		.build()

	fun requestFocus(): Boolean =
		audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

	fun abandonFocus() {
		pausedByLoss = false
		audioManager.abandonAudioFocusRequest(request)
	}
}
