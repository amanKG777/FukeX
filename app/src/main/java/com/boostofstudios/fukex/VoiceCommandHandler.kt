package com.boostofstudios.fukex

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.boostofstudios.fukex.data.Playlist
import com.boostofstudios.fukex.data.PlaylistManager
import com.boostofstudios.fukex.data.SettingsManager
import com.boostofstudios.fukex.playback.PlaybackEngine
import java.util.Locale

// Turns parsed voice commands into playback actions.
class VoiceCommandHandler(
	private val activity: Activity,
	private val assistant: () -> VoiceAssistantManager?
) {
	private val engine get() = PlaybackEngine.get(activity)

	private fun speak(text: String) = assistant()?.speak(text)

	fun handle(command: String, query: String?) {
		when (command) {
			"play" -> if (query.isNullOrEmpty()) engine.play() else playQuery(query)
			"next" -> engine.next()
			"previous" -> engine.previous()
			"pause" -> engine.pause()
			"resume" -> engine.play()
			"amplifier_on" -> SettingsManager.setAmplifierEnabled(activity, true)
			"amplifier_off" -> SettingsManager.setAmplifierEnabled(activity, false)
			"amplifier_increase" -> {
				val current = SettingsManager.getAmplifierLevel(activity)
				SettingsManager.setAmplifierLevel(activity, minOf(1500, current + 500))
				speak("Amplifier increased")
			}
			"amplifier_decrease" -> {
				val current = SettingsManager.getAmplifierLevel(activity)
				SettingsManager.setAmplifierLevel(activity, maxOf(0, current - 500))
				speak("Amplifier decreased")
			}
			"open_docs" -> {
				speak("Opening documentation")
				VoicePlayRequests.openDocs.value = true
				activity.startActivity(Intent(activity, MainActivity::class.java).apply {
					flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
				})
			}
		}
	}

	private fun playQuery(query: String) {
		// Unicode-aware so Hindi/Devanagari titles aren't stripped down to an empty query
		val nonWordChars = Regex("[^\\p{L}\\p{N}\\s]")
		val cleanQuery = query.lowercase(Locale.getDefault()).replace(nonWordChars, " ").trim()
		val queryWords = cleanQuery.split("\\s+".toRegex()).filter { it.length > 1 }
		Thread {
			// Title lookups hit the ContentResolver per track, so keep this off the main thread
			var found: Pair<Playlist, Int>? = null
			if (cleanQuery.isNotEmpty()) {
				search@ for (p in PlaylistManager.loadPlaylists(activity)) {
					for (i in p.uris.indices) {
						val uri = p.uris[i]
						val cleanTitle = trackTitle(uri, i, activity).lowercase(Locale.getDefault()).replace(nonWordChars, " ")
						val uriStr = uri.toString().lowercase(Locale.getDefault())
						if (cleanTitle.contains(cleanQuery) || uriStr.contains(cleanQuery) ||
							(queryWords.isNotEmpty() && queryWords.all { cleanTitle.contains(it) || uriStr.contains(it) })) {
							found = p to i
							break@search
						}
					}
				}
			}

			val match = found
			if (match != null) {
				val (playlist, index) = match
				activity.runOnUiThread {
					if (playlist.pinHash != null && playlist.id !in VoicePlayRequests.unlockedPlaylistIds) {
						speak("I found the song, but the playlist is encrypted. Can you unlock it so I can play?")
					} else {
						speak("Playing from ${playlist.name}.")
						// Play it where it lives instead of copying it into the open playlist
						engine.playPlaylist(playlist, index)
						VoicePlayRequests.focusPlaylistId.value = playlist.id
					}
				}
				return@Thread
			}

			activity.runOnUiThread { speak("Searching YouTube for $query") }
			try {
				val searchExtractor = org.schabi.newpipe.extractor.ServiceList.YouTube.getSearchExtractor(query)
				searchExtractor.fetchPage()
				val firstVideo = searchExtractor.initialPage.items
					.firstOrNull { it is org.schabi.newpipe.extractor.stream.StreamInfoItem } as? org.schabi.newpipe.extractor.stream.StreamInfoItem
				if (firstVideo != null) {
					val videoId = firstVideo.url.substringAfterLast("v=").substringBefore("&")
					val ytUri = Uri.parse("youtube://$videoId?title=${Uri.encode(firstVideo.name)}")
					activity.runOnUiThread {
						speak("Playing from YouTube.")
						// Queued as "play next", so no playlist gets a permanent copy
						engine.playUri(ytUri)
					}
				} else {
					activity.runOnUiThread { speak("Couldn't find $query on YouTube.") }
				}
			} catch (e: Exception) {
				activity.runOnUiThread { speak("Error searching YouTube.") }
			}
		}.start()
	}
}
