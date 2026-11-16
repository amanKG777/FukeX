package com.boostofstudios.fukex

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.boostofstudios.fukex.data.SettingsManager

/**
 * Turns "hold Volume Down" into an assistant trigger while keeping a short press working
 * as a normal volume-down. Down events are swallowed; if the key is released before
 * [HOLD_MS] the volume is lowered by one step, otherwise [onHold] fires once.
 */
class VolumeDownHoldDetector(
    private val context: Context,
    private val onHold: () -> Unit
) {
    companion object {
        const val HOLD_MS = 600L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pressed = false
    private var fired = false
    private val holdRunnable = Runnable {
        if (pressed && !fired) {
            fired = true
            onHold()
        }
    }

    /** Returns true when the event was consumed. Only handles KEYCODE_VOLUME_DOWN. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0 && !pressed) {
                    pressed = true
                    fired = false
                    handler.postDelayed(holdRunnable, HOLD_MS)
                }
                return true
            }
            KeyEvent.ACTION_UP -> {
                if (!pressed) return false
                pressed = false
                handler.removeCallbacks(holdRunnable)
                if (!fired && !event.isCanceled) lowerVolume()
                return true
            }
        }
        return false
    }

    fun reset() {
        handler.removeCallbacks(holdRunnable)
        pressed = false
        fired = false
    }

    private fun lowerVolume() {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audio.adjustSuggestedStreamVolume(
            AudioManager.ADJUST_LOWER,
            AudioManager.USE_DEFAULT_STREAM_TYPE,
            AudioManager.FLAG_SHOW_UI
        )
    }
}

/** Whether volume keys should be left alone (calls, VoIP). */
fun isInCall(context: Context): Boolean {
    val mode = (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.mode
    return mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION ||
        mode == AudioManager.MODE_RINGTONE
}

object VoiceAssistantTrigger {
    const val EXTRA_START_VOICE = "com.boostofstudios.fukex.EXTRA_START_VOICE"

    /**
     * Asks MainActivity (which owns the assistant) to start listening. If the activity
     * has been destroyed, try to relaunch it so the request isn't silently lost.
     */
    fun request(context: Context) {
        if (MainActivity.isAlive) {
            context.sendBroadcast(Intent(PlaybackService.ACTION_VOICE_ASSISTANT).setPackage(context.packageName))
            return
        }
        try {
            context.startActivity(Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_START_VOICE, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        } catch (e: Exception) {
            // Background activity starts can be blocked; nothing else we can do
            android.util.Log.w("VoiceAssistantTrigger", "Could not open FukeX for the assistant", e)
        }
    }
}

/**
 * Optional accessibility service: lets "hold Volume Down" wake the assistant while FukeX
 * is minimized. Apps can't see hardware keys in the background any other way.
 * It only looks at Volume Down and only when the assistant's "Work in Background" is on.
 */
class VolumeKeyAccessibilityService : AccessibilityService() {
    companion object {
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = ComponentName(context, VolumeKeyAccessibilityService::class.java)
            return enabled.split(':').any {
                ComponentName.unflattenFromString(it) == me
            }
        }
    }

    private var detector: VolumeDownHoldDetector? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        detector = VolumeDownHoldDetector(this) { VoiceAssistantTrigger.request(this) }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val d = detector ?: return false
        val active = SettingsManager.isVoiceAssistantEnabled(this) &&
            SettingsManager.isVoiceAssistantBackgroundEnabled(this) &&
            !isInCall(this)
        if (!active) {
            // Let a release through if we had swallowed its press before the setting changed
            return if (event.action == KeyEvent.ACTION_UP) d.onKeyEvent(event) else false
        }
        return d.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        detector?.reset()
    }

    override fun onDestroy() {
        detector?.reset()
        detector = null
        super.onDestroy()
    }
}
