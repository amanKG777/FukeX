package com.boostofstudios.fukex.playback

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

// Sends lyric lines to TalkBack as announcements. Only used when the user turned it on in Settings.
object LyricsAnnouncer {
	fun isTalkBackRunning(context: Context): Boolean {
		val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
		return manager.isEnabled && manager.isTouchExplorationEnabled
	}

	fun announce(context: Context, text: String) {
		val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return
		if (!manager.isEnabled || !manager.isTouchExplorationEnabled) return
		@Suppress("DEPRECATION")
		val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			AccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
		} else {
			AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
		}
		event.text.add(text)
		event.className = LyricsAnnouncer::class.java.name
		event.packageName = context.packageName
		try {
			manager.sendAccessibilityEvent(event)
		} catch (e: IllegalStateException) {
			// Accessibility was switched off between the check and the send
		}
	}
}
