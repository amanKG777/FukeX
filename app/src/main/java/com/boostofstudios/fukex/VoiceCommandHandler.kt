package com.boostofstudios.fukex

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import com.boostofstudios.fukex.data.LibraryTrack
import com.boostofstudios.fukex.data.MediaLibrary
import com.boostofstudios.fukex.data.Playlist
import com.boostofstudios.fukex.data.PlaylistManager
import com.boostofstudios.fukex.data.SettingsManager
import com.boostofstudios.fukex.playback.PlaybackEngine

// Turns parsed voice commands into playback actions.
class VoiceCommandHandler(
	private val activity: Activity,
	private val assistant: () -> VoiceAssistantManager?
) {
	private enum class Mode { PLAY_NOW, PLAY_NEXT, ADD_TO_QUEUE }

	// Where a spoken query was found: a saved playlist, the device library, or YouTube
	private sealed interface Hit {
		data class InPlaylist(val playlist: Playlist, val index: Int) : Hit
		data class InLibrary(val label: String, val tracks: List<LibraryTrack>, val index: Int) : Hit
		data class OnYoutube(val uri: Uri, val title: String) : Hit
	}

	private val engine get() = PlaybackEngine.get(activity)

	private fun speak(text: String) = assistant()?.speak(text)

	fun handle(command: VoiceCommand) {
		when (command) {
			is VoiceCommand.Play -> playQuery(command.query, Mode.PLAY_NOW)
			is VoiceCommand.PlayNext -> playQuery(command.query, Mode.PLAY_NEXT)
			is VoiceCommand.AddToQueue -> playQuery(command.query, Mode.ADD_TO_QUEUE)
			VoiceCommand.Resume -> engine.play()
			VoiceCommand.Pause -> engine.pause()
			VoiceCommand.Next -> engine.next()
			VoiceCommand.Previous -> engine.previous()
			VoiceCommand.Restart -> engine.seekTo(0)
			is VoiceCommand.Seek -> seekBy(command.deltaSeconds)
			VoiceCommand.VolumeUp -> adjustVolume(AudioManager.ADJUST_RAISE)
			VoiceCommand.VolumeDown -> adjustVolume(AudioManager.ADJUST_LOWER)
			VoiceCommand.WhatsPlaying -> announceCurrentTrack()
			VoiceCommand.AmplifierOn -> {
				SettingsManager.setAmplifierEnabled(activity, true)
				speak("Amplifier on")
			}
			VoiceCommand.AmplifierOff -> {
				SettingsManager.setAmplifierEnabled(activity, false)
				speak("Amplifier off")
			}
			VoiceCommand.AmplifierUp -> {
				val level = minOf(1500, SettingsManager.getAmplifierLevel(activity) + 500)
				SettingsManager.setAmplifierLevel(activity, level)
				speak("Amplifier increased")
			}
			VoiceCommand.AmplifierDown -> {
				val level = maxOf(0, SettingsManager.getAmplifierLevel(activity) - 500)
				SettingsManager.setAmplifierLevel(activity, level)
				speak("Amplifier decreased")
			}
			VoiceCommand.OpenDocs -> {
				speak("Opening documentation")
				VoicePlayRequests.openDocs.value = true
				activity.startActivity(Intent(activity, MainActivity::class.java).apply {
					flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
				})
			}
			VoiceCommand.Help -> speak(
				"You can say: play a song name, play next, add a song to the queue, pause, resume, next, previous, " +
					"forward or rewind, volume up or down, what's playing, amplifier on or off, or open documentation."
			)
		}
	}

	private fun seekBy(deltaSeconds: Int) {
		val progress = engine.progress.value
		val target = (progress.positionMs + deltaSeconds * 1000L).coerceAtLeast(0L)
		if (progress.durationMs > 0 && target >= progress.durationMs) engine.next() else engine.seekTo(target)
		val seconds = kotlin.math.abs(deltaSeconds)
		val amount = if (seconds >= 60 && seconds % 60 == 0) "${seconds / 60} minute${if (seconds == 60) "" else "s"}" else "$seconds seconds"
		speak(if (deltaSeconds > 0) "Forward $amount" else "Back $amount")
	}

	private fun adjustVolume(direction: Int) {
		val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
		audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
	}

	private fun announceCurrentTrack() {
		val state = engine.state.value
		if (state.currentItem == null) {
			speak("Nothing is playing.")
			return
		}
		val locked = state.playlistId?.let { id ->
			PlaylistManager.loadPlaylists(activity).firstOrNull { it.id == id }
				?.let { it.pinHash != null && it.id !in VoicePlayRequests.unlockedPlaylistIds }
		} == true
		speak(
			when {
				locked -> "A track from a locked playlist."
				state.queueName.isNotBlank() -> "${state.currentTitle}, from ${state.queueName}."
				else -> state.currentTitle
			}
		)
	}

	private fun playQuery(query: String, mode: Mode) {
		if (query.isBlank()) {
			engine.play()
			return
		}
		Thread {
			// Title lookups hit the ContentResolver per track, so keep all searching off the main thread
			val hit = findInPlaylists(query) ?: findInLibrary(query)
			if (hit != null) {
				activity.runOnUiThread { play(hit, mode) }
				return@Thread
			}
			activity.runOnUiThread { speak("Searching YouTube for $query") }
			val youtube = findOnYoutube(query)
			activity.runOnUiThread {
				if (youtube != null) play(youtube, mode) else speak("Couldn't find $query.")
			}
		}.start()
	}

	private fun findInPlaylists(query: String): Hit? {
		for (p in PlaylistManager.loadPlaylists(activity)) {
			for (i in p.uris.indices) {
				val uri = p.uris[i]
				if (VoiceMatcher.matches(query, trackTitle(uri, i, activity)) || VoiceMatcher.matches(query, uri.toString())) {
					return Hit.InPlaylist(p, i)
				}
			}
		}
		return null
	}

	private fun findInLibrary(query: String): Hit? {
		val tracks = MediaLibrary.scan(activity)
		if (tracks.isEmpty()) return null
		val byTitle = tracks.withIndex()
			.map { it.index to VoiceMatcher.score(query, it.value.title) }
			.filter { it.second >= 0 }
			.maxByOrNull { it.second }
		if (byTitle != null) return Hit.InLibrary(MediaLibrary.UNKNOWN, listOf(tracks[byTitle.first]), 0)

		// No song by that name: maybe they asked for an artist or an album
		val fields: List<(LibraryTrack) -> String> = listOf({ it.artist }, { it.album })
		for (field in fields) {
			val names = tracks.map(field).filter { it != MediaLibrary.UNKNOWN }.distinct()
			val best = names.map { it to VoiceMatcher.score(query, it) }.filter { it.second >= 0 }.maxByOrNull { it.second }
			if (best != null) return Hit.InLibrary(best.first, tracks.filter { field(it) == best.first }, 0)
		}
		return null
	}

	private fun findOnYoutube(query: String): Hit? = try {
		val searchExtractor = org.schabi.newpipe.extractor.ServiceList.YouTube.getSearchExtractor(query)
		searchExtractor.fetchPage()
		val firstVideo = searchExtractor.initialPage.items
			.firstOrNull { it is org.schabi.newpipe.extractor.stream.StreamInfoItem } as? org.schabi.newpipe.extractor.stream.StreamInfoItem
		firstVideo?.let {
			val videoId = it.url.substringAfterLast("v=").substringBefore("&")
			Hit.OnYoutube(Uri.parse("youtube://$videoId?title=${Uri.encode(it.name)}"), it.name)
		}
	} catch (e: Exception) {
		null
	}

	private fun play(hit: Hit, mode: Mode) {
		when (hit) {
			is Hit.InPlaylist -> {
				val (playlist, index) = hit
				if (playlist.pinHash != null && playlist.id !in VoicePlayRequests.unlockedPlaylistIds) {
					speak("I found the song, but the playlist is encrypted. Can you unlock it so I can play?")
					return
				}
				val uri = playlist.uris[index]
				when (mode) {
					Mode.PLAY_NOW -> {
						speak("Playing from ${playlist.name}.")
						// Play it where it lives instead of copying it into the open playlist
						engine.playPlaylist(playlist, index)
						VoicePlayRequests.focusPlaylistId.value = playlist.id
					}
					Mode.PLAY_NEXT -> { engine.playNext(uri); speak("${trackTitle(uri, index, activity)} will play next.") }
					Mode.ADD_TO_QUEUE -> { engine.addToQueue(uri); speak("Added ${trackTitle(uri, index, activity)} to the queue.") }
				}
			}
			is Hit.InLibrary -> {
				val first = hit.tracks[hit.index]
				val isGroup = hit.tracks.size > 1
				when (mode) {
					Mode.PLAY_NOW -> {
						speak(if (isGroup) "Playing ${hit.label}." else "Playing ${first.title} by ${first.artist}.")
						engine.playTracks(if (isGroup) hit.label else "Library", hit.tracks.map { it.uri }, hit.index)
					}
					Mode.PLAY_NEXT -> {
						// Reversed, so the group ends up in its original order right after the current track
						hit.tracks.reversed().forEach { engine.playNext(it.uri) }
						speak(if (isGroup) "${hit.label} will play next." else "${first.title} will play next.")
					}
					Mode.ADD_TO_QUEUE -> {
						hit.tracks.forEach { engine.addToQueue(it.uri) }
						speak(if (isGroup) "Added ${hit.label} to the queue." else "Added ${first.title} to the queue.")
					}
				}
			}
			is Hit.OnYoutube -> when (mode) {
				// Queued as "play next", so no playlist gets a permanent copy
				Mode.PLAY_NOW -> { speak("Playing ${hit.title} from YouTube."); engine.playUri(hit.uri) }
				Mode.PLAY_NEXT -> { engine.playNext(hit.uri); speak("${hit.title} will play next.") }
				Mode.ADD_TO_QUEUE -> { engine.addToQueue(hit.uri); speak("Added ${hit.title} to the queue.") }
			}
		}
	}
}
