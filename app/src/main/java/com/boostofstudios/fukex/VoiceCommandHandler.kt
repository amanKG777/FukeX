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
import com.boostofstudios.fukex.data.VoiceSpeechEvent
import com.boostofstudios.fukex.playback.PlaybackEngine

// Turns parsed voice commands into playback actions.
class VoiceCommandHandler(
	private val activity: Activity,
	private val assistant: () -> VoiceAssistantManager?
) {
	private enum class Mode { PLAY_NOW, PLAY_NEXT, ADD_TO_QUEUE }

	// A candidate track or video found from playlist, library, or YouTube
	private sealed interface Candidate {
		val displayTitle: String
		data class InPlaylist(val playlist: Playlist, val index: Int, override val displayTitle: String) : Candidate
		data class InLibrary(val label: String, val tracks: List<LibraryTrack>, val index: Int, override val displayTitle: String) : Candidate
		data class OnYoutube(val uri: Uri, val title: String, val uploader: String = "") : Candidate {
			override val displayTitle: String get() = title
		}
	}

	private data class SearchSession(
		val query: String,
		val candidates: List<Candidate>,
		var currentIndex: Int,
		val mode: Mode
	)

	private var activeSession: SearchSession? = null

	private val engine get() = PlaybackEngine.get(activity)

	private fun speak(text: String) = assistant()?.speak(text)

	private fun speakOrBeep(event: VoiceSpeechEvent, text: String, onDone: (() -> Unit)? = null) {
		if (SettingsManager.isVoiceSpeechEventEnabled(activity, event)) {
			assistant()?.speak(text, onDone)
		} else {
			playFeedbackBeep()
			onDone?.let { activity.runOnUiThread(it) }
		}
	}

	fun handle(command: VoiceCommand) {
		when (command) {
			is VoiceCommand.Play -> playQuery(command.query, Mode.PLAY_NOW)
			is VoiceCommand.PlayNext -> playQuery(command.query, Mode.PLAY_NEXT)
			is VoiceCommand.AddToQueue -> playQuery(command.query, Mode.ADD_TO_QUEUE)
			VoiceCommand.Resume -> engine.play()
			VoiceCommand.Pause -> engine.pause()
			VoiceCommand.Next -> {
				val current = engine.state.value
				val canAdvance = current.currentIndex < current.queue.lastIndex
				val session = activeSession
				if (!canAdvance && session != null && session.currentIndex + 1 < session.candidates.size) {
					cycleSessionResult(delta = 1)
				} else {
					engine.next()
				}
			}
			VoiceCommand.Previous -> {
				val current = engine.state.value
				val canGoBack = current.currentIndex > 0
				val session = activeSession
				if (!canGoBack && session != null && session.currentIndex > 0) {
					cycleSessionResult(delta = -1)
				} else {
					engine.previous()
				}
			}
			VoiceCommand.NextResult -> cycleSessionResult(delta = 1)
			VoiceCommand.PreviousResult -> cycleSessionResult(delta = -1)
			is VoiceCommand.SelectResult -> selectSessionResult(command.index)
			VoiceCommand.Restart -> engine.seekTo(0)
			is VoiceCommand.Seek -> seekBy(command.deltaSeconds)
			VoiceCommand.VolumeUp -> adjustVolume(AudioManager.ADJUST_RAISE)
			VoiceCommand.VolumeDown -> adjustVolume(AudioManager.ADJUST_LOWER)
			VoiceCommand.WhatsPlaying -> announceCurrentTrack()
			VoiceCommand.AmplifierOn -> {
				SettingsManager.setAmplifierEnabled(activity, true)
				speakOrBeep(VoiceSpeechEvent.CONTROLS, "Amplifier on")
			}
			VoiceCommand.AmplifierOff -> {
				SettingsManager.setAmplifierEnabled(activity, false)
				speakOrBeep(VoiceSpeechEvent.CONTROLS, "Amplifier off")
			}
			VoiceCommand.AmplifierUp -> {
				val level = minOf(1500, SettingsManager.getAmplifierLevel(activity) + 500)
				SettingsManager.setAmplifierLevel(activity, level)
				speakOrBeep(VoiceSpeechEvent.CONTROLS, "Amplifier increased")
			}
			VoiceCommand.AmplifierDown -> {
				val level = maxOf(0, SettingsManager.getAmplifierLevel(activity) - 500)
				SettingsManager.setAmplifierLevel(activity, level)
				speakOrBeep(VoiceSpeechEvent.CONTROLS, "Amplifier decreased")
			}
			VoiceCommand.OpenDocs -> {
				speakOrBeep(VoiceSpeechEvent.CONTROLS, "Opening documentation")
				VoicePlayRequests.openDocs.value = true
				activity.startActivity(Intent(activity, MainActivity::class.java).apply {
					flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
				})
			}
			VoiceCommand.Help -> speak(
				"You can say: play a song name, play next, add to queue, next result, previous result, or play result number. " +
					"You can also say pause, resume, next, previous, forward or rewind, volume up or down, what's playing, amplifier on or off, or open documentation."
			)
		}
	}

	private fun cycleSessionResult(delta: Int) {
		val session = activeSession
		if (session == null || session.candidates.isEmpty()) {
			speakOrBeep(VoiceSpeechEvent.ERRORS, "No active search results. Say play followed by a song name.")
			return
		}
		val targetIndex = session.currentIndex + delta
		if (targetIndex in session.candidates.indices) {
			session.currentIndex = targetIndex
			playCandidate(session.candidates[targetIndex], session.mode, session, targetIndex)
		} else if (delta > 0) {
			speakOrBeep(VoiceSpeechEvent.RESULTS, "That was the last result for ${session.query}.")
		} else {
			speakOrBeep(VoiceSpeechEvent.RESULTS, "You are already on the first result.")
		}
	}

	private fun selectSessionResult(oneBasedIndex: Int) {
		val session = activeSession
		if (session == null || session.candidates.isEmpty()) {
			speakOrBeep(VoiceSpeechEvent.ERRORS, "No active search results.")
			return
		}
		val targetIndex = oneBasedIndex - 1
		if (targetIndex in session.candidates.indices) {
			session.currentIndex = targetIndex
			playCandidate(session.candidates[targetIndex], session.mode, session, targetIndex)
		} else {
			speakOrBeep(VoiceSpeechEvent.RESULTS, "Please choose a number between 1 and ${session.candidates.size}.")
		}
	}

	private fun seekBy(deltaSeconds: Int) {
		val progress = engine.progress.value
		val target = (progress.positionMs + deltaSeconds * 1000L).coerceAtLeast(0L)
		if (progress.durationMs > 0 && target >= progress.durationMs) engine.next() else engine.seekTo(target)
		val seconds = kotlin.math.abs(deltaSeconds)
		val amount = if (seconds >= 60 && seconds % 60 == 0) "${seconds / 60} minute${if (seconds == 60) "" else "s"}" else "$seconds seconds"
		speakOrBeep(VoiceSpeechEvent.CONTROLS, if (deltaSeconds > 0) "Forward $amount" else "Back $amount")
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
			val playlistHits = findAllInPlaylists(query)
			if (playlistHits.isNotEmpty()) {
				activity.runOnUiThread {
					val session = SearchSession(query, playlistHits, 0, mode)
					activeSession = session
					playCandidate(playlistHits[0], mode, session, 0)
				}
				return@Thread
			}

			val libraryHits = findAllInLibrary(query)
			if (libraryHits.isNotEmpty()) {
				activity.runOnUiThread {
					val session = SearchSession(query, libraryHits, 0, mode)
					activeSession = session
					playCandidate(libraryHits[0], mode, session, 0)
				}
				return@Thread
			}

			activity.runOnUiThread { speakOrBeep(VoiceSpeechEvent.SEARCHING, "Searching YouTube for $query") }
			val youtubeHits = findAllOnYoutube(query)
			activity.runOnUiThread {
				if (youtubeHits.isNotEmpty()) {
					val session = SearchSession(query, youtubeHits, 0, mode)
					activeSession = session
					playCandidate(youtubeHits[0], mode, session, 0)
				} else {
					speakOrBeep(VoiceSpeechEvent.ERRORS, "Couldn't find $query.")
				}
			}
		}.start()
	}

	private fun findAllInPlaylists(query: String): List<Candidate.InPlaylist> {
		val hits = mutableListOf<Candidate.InPlaylist>()
		for (p in PlaylistManager.loadPlaylists(activity)) {
			for (i in p.uris.indices) {
				val uri = p.uris[i]
				val title = trackTitle(uri, i, activity)
				if (VoiceMatcher.matches(query, title) || VoiceMatcher.matches(query, uri.toString())) {
					hits.add(Candidate.InPlaylist(p, i, title))
				}
			}
		}
		return hits
	}

	private fun findAllInLibrary(query: String): List<Candidate.InLibrary> {
		val tracks = MediaLibrary.scan(activity)
		if (tracks.isEmpty()) return emptyList()
		val byTitle = tracks.withIndex()
			.map { it.index to VoiceMatcher.score(query, it.value.title) }
			.filter { it.second >= 0 }
			.sortedByDescending { it.second }
		if (byTitle.isNotEmpty()) {
			return byTitle.map {
				val t = tracks[it.first]
				Candidate.InLibrary(MediaLibrary.UNKNOWN, listOf(t), 0, t.title)
			}
		}

		// Check artist / album
		val fields: List<(LibraryTrack) -> String> = listOf({ it.artist }, { it.album })
		for (field in fields) {
			val names = tracks.map(field).filter { it != MediaLibrary.UNKNOWN }.distinct()
			val best = names.map { it to VoiceMatcher.score(query, it) }.filter { it.second >= 0 }.maxByOrNull { it.second }
			if (best != null) {
				val group = tracks.filter { field(it) == best.first }
				return listOf(Candidate.InLibrary(best.first, group, 0, best.first))
			}
		}
		return emptyList()
	}

	private fun findAllOnYoutube(query: String): List<Candidate.OnYoutube> = try {
		val searchExtractor = org.schabi.newpipe.extractor.ServiceList.YouTube.getSearchExtractor(query)
		searchExtractor.fetchPage()
		val videos = searchExtractor.initialPage.items
			.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
			.take(10)
		videos.map { item ->
			val videoId = item.url.substringAfterLast("v=").substringBefore("&")
			Candidate.OnYoutube(
				uri = Uri.parse("youtube://$videoId?title=${Uri.encode(item.name)}"),
				title = item.name,
				uploader = item.uploaderName.orEmpty()
			)
		}
	} catch (e: Exception) {
		emptyList()
	}

	private fun playCandidate(candidate: Candidate, mode: Mode, session: SearchSession?, index: Int) {
		val total = session?.candidates?.size ?: 1
		when (candidate) {
			is Candidate.InPlaylist -> {
				val (playlist, trackIndex, title) = candidate
				if (playlist.pinHash != null && playlist.id !in VoicePlayRequests.unlockedPlaylistIds) {
					speakOrBeep(VoiceSpeechEvent.ERRORS, "I found the song, but the playlist is encrypted. Can you unlock it so I can play?")
					return
				}
				val uri = playlist.uris[trackIndex]
				when (mode) {
					Mode.PLAY_NOW -> {
						if (total > 1) {
							val msg = if (index == 0) {
								"Playing version 1 of $total: $title from ${playlist.name}. Say next version to switch."
							} else {
								"Playing version ${index + 1} of $total: $title from ${playlist.name}."
							}
							speakOrBeep(if (index == 0) VoiceSpeechEvent.PLAYING else VoiceSpeechEvent.RESULTS, msg)
						} else {
							speakOrBeep(VoiceSpeechEvent.PLAYING, "Playing from ${playlist.name}.")
						}
						engine.playPlaylist(playlist, trackIndex)
						VoicePlayRequests.focusPlaylistId.value = playlist.id
					}
					Mode.PLAY_NEXT -> { engine.playNext(uri); speakOrBeep(VoiceSpeechEvent.PLAYING, "$title will play next.") }
					Mode.ADD_TO_QUEUE -> { engine.addToQueue(uri); speakOrBeep(VoiceSpeechEvent.PLAYING, "Added $title to the queue.") }
				}
			}
			is Candidate.InLibrary -> {
				val first = candidate.tracks[candidate.index]
				val isGroup = candidate.tracks.size > 1
				when (mode) {
					Mode.PLAY_NOW -> {
						if (total > 1) {
							val msg = if (index == 0) {
								"Playing match 1 of $total: ${first.title}. Say next version to switch."
							} else {
								"Playing match ${index + 1} of $total: ${first.title}."
							}
							speakOrBeep(if (index == 0) VoiceSpeechEvent.PLAYING else VoiceSpeechEvent.RESULTS, msg)
						} else {
							speakOrBeep(VoiceSpeechEvent.PLAYING, if (isGroup) "Playing ${candidate.label}." else "Playing ${first.title} by ${first.artist}.")
						}
						engine.playTracks(if (isGroup) candidate.label else "Library", candidate.tracks.map { it.uri }, candidate.index)
					}
					Mode.PLAY_NEXT -> {
						candidate.tracks.reversed().forEach { engine.playNext(it.uri) }
						speakOrBeep(VoiceSpeechEvent.PLAYING, if (isGroup) "${candidate.label} will play next." else "${first.title} will play next.")
					}
					Mode.ADD_TO_QUEUE -> {
						candidate.tracks.forEach { engine.addToQueue(it.uri) }
						speakOrBeep(VoiceSpeechEvent.PLAYING, if (isGroup) "Added ${candidate.label} to the queue." else "Added ${first.title} to the queue.")
					}
				}
			}
			is Candidate.OnYoutube -> when (mode) {
				Mode.PLAY_NOW -> {
					if (total > 1) {
						val msg = if (index == 0) {
							"Playing result 1 of $total: ${candidate.title}. Say next result to switch."
						} else {
							"Playing result ${index + 1} of $total: ${candidate.title}."
						}
						speakOrBeep(if (index == 0) VoiceSpeechEvent.PLAYING else VoiceSpeechEvent.RESULTS, msg)
					} else {
						speakOrBeep(VoiceSpeechEvent.PLAYING, "Playing ${candidate.title} from YouTube.")
					}
					engine.playUri(candidate.uri)
				}
				Mode.PLAY_NEXT -> { engine.playNext(candidate.uri); speakOrBeep(VoiceSpeechEvent.PLAYING, "${candidate.title} will play next.") }
				Mode.ADD_TO_QUEUE -> { engine.addToQueue(candidate.uri); speakOrBeep(VoiceSpeechEvent.PLAYING, "Added ${candidate.title} to the queue.") }
			}
		}
	}
}
