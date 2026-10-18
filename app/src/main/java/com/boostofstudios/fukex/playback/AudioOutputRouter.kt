package com.boostofstudios.fukex.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.boostofstudios.fukex.data.AudioRoute

// Tells the engine which kind of output is connected, so it can switch EQ setups.
// Android sends media to the most recently connected headset, so headsets win over the speaker.
class AudioOutputRouter(context: Context, private val onRouteChanged: (AudioRoute) -> Unit) {
	private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
	var currentRoute: AudioRoute = detect()
		private set

	private val callback = object : AudioDeviceCallback() {
		override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
		override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
	}

	fun start() {
		audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
	}

	fun stop() {
		audioManager.unregisterAudioDeviceCallback(callback)
	}

	private fun refresh() {
		val route = detect()
		if (route != currentRoute) {
			currentRoute = route
			onRouteChanged(route)
		}
	}

	private fun detect(): AudioRoute {
		val types = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.toSet()
		val bluetooth = mutableSetOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			bluetooth += AudioDeviceInfo.TYPE_BLE_HEADSET
			bluetooth += AudioDeviceInfo.TYPE_BLE_SPEAKER
		}
		val usb = setOf(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE)
		val wired = setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET)
		return when {
			types.any { it in bluetooth } -> AudioRoute.BLUETOOTH
			types.any { it in usb } -> AudioRoute.USB
			types.any { it in wired } -> AudioRoute.WIRED
			else -> AudioRoute.SPEAKER
		}
	}
}
