package com.boostofstudios.fukex.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.boostofstudios.fukex.PlaybackService
import com.boostofstudios.fukex.data.AudioRoute
import com.boostofstudios.fukex.data.EqConfig
import com.boostofstudios.fukex.data.EqualizerStore
import com.boostofstudios.fukex.data.Lyrics
import com.boostofstudios.fukex.data.LyricsRepository
import com.boostofstudios.fukex.data.Playlist
import com.boostofstudios.fukex.data.PlaylistManager
import com.boostofstudios.fukex.data.SettingsManager
import com.boostofstudios.fukex.trackTitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "PlaybackEngine"

data class EngineState(
	val queue: List<QueueItem> = emptyList(),
	val currentIndex: Int = -1,
	val queueName: String = "",
	// Set when the queue came from a saved playlist, so progress can be stored against it
	val playlistId: String? = null,
	val isPlaying: Boolean = false,
	val currentTitle: String = ""
) {
	val currentItem: QueueItem? get() = queue.getOrNull(currentIndex)
}

data class PlaybackProgress(val positionMs: Long = 0L, val durationMs: Long = 0L) {
	val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

data class LyricsState(
	val forUri: String? = null,
	val loading: Boolean = false,
	val lyrics: Lyrics? = null,
	val currentLine: Int = -1
)

// Owns playback for the whole app, independent of any screen, so music keeps going when
// tabs change or the activity is closed. PlaybackService keeps the process alive around it.
// Two ExoPlayers share one audio session so crossfades and audio effects cover both.
class PlaybackEngine private constructor(private val context: Context) {
	companion object {
		@Volatile private var instance: PlaybackEngine? = null

		fun get(context: Context): PlaybackEngine =
			instance ?: synchronized(this) {
				instance ?: PlaybackEngine(context.applicationContext).also { instance = it }
			}
	}

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private val players: List<ExoPlayer> = listOf(createFukexPlayer(context), createFukexPlayer(context))
	private var activePlayerIndex = 0
	private val player: ExoPlayer get() = players[activePlayerIndex]
	private val audioSessionId: Int

	private val levels = mutableMapOf<ExoPlayer, Float>()
	private val replayGains = mutableMapOf<ExoPlayer, Float>()
	private val volumeAnimators = mutableMapOf<ExoPlayer, Job>()
	private var duckFactor = 1f

	// The inactive player only gets the new timeline when it's next used, so a fade-out isn't cut short
	private var queueVersion = 0
	private val playerVersions = mutableMapOf<ExoPlayer, Int>()

	private var pendingResumeFraction = 0f
	// Loading a timeline briefly reports item 0 before the seek lands; those transitions are ignored
	private var ignoreTransitions = false
	private var tickJob: Job? = null
	private var lyricsJob: Job? = null
	private var crossfadedFromIndex = -1
	private var lastProgressSave = 0L
	private val resumePoints = mutableMapOf<String, Pair<Int, Float>>()

	private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null
	private var bassBoost: android.media.audiofx.BassBoost? = null

	private val _state = MutableStateFlow(EngineState())
	val state: StateFlow<EngineState> = _state.asStateFlow()
	private val _progress = MutableStateFlow(PlaybackProgress())
	val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()
	private val _lyrics = MutableStateFlow(LyricsState())
	val lyrics: StateFlow<LyricsState> = _lyrics.asStateFlow()

	// True while a lyrics screen is open; lyrics are otherwise only fetched when TalkBack reading is on
	var lyricsViewerOpen: Boolean = false
		set(value) {
			field = value
			if (value) ensureLyricsLoaded()
		}

	private val audioFocus = AudioFocusController(
		context,
		onPause = { pause() },
		onResume = { play() },
		onDuck = { ducking ->
			duckFactor = if (ducking) 0.25f else 1f
			players.forEach { applyVolume(it) }
		}
	)

	private val router = AudioOutputRouter(context) { route -> LiveEqualizer.apply(EqualizerStore.load(context, route)) }
	val currentRoute: AudioRoute get() = router.currentRoute

	val session: MediaSessionCompat = MediaSessionCompat(context, "FukeXSession").apply {
		val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
			setClass(context, androidx.media.session.MediaButtonReceiver::class.java)
		}
		setMediaButtonReceiver(PendingIntent.getBroadcast(context, 0, mediaButtonIntent, PendingIntent.FLAG_IMMUTABLE))
		setSessionActivity(
			PendingIntent.getActivity(
				context, 0,
				Intent(context, com.boostofstudios.fukex.MainActivity::class.java),
				PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
			)
		)
		isActive = true
	}

	// Kept as a field: SharedPreferences only holds listeners weakly
	private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
		when (key) {
			"amplifier_enabled", "amplifier_level" -> updateAmplifier()
			"bass_boost" -> updateBassBoost()
			"skip_silence" -> {
				val skip = SettingsManager.isSkipSilenceEnabled(context)
				players.forEach { it.skipSilenceEnabled = skip }
			}
			"replay_gain_enabled" -> players.forEach { updateReplayGain(it, it.currentTracks) }
			"playback_speed", "playback_pitch" -> updatePlaybackParameters()
			"lyrics_announce" -> ensureLyricsLoaded()
		}
	}

	private val sessionCallback = object : MediaSessionCompat.Callback() {
		override fun onPlay() = play()
		override fun onPause() = pause()
		override fun onStop() = pause()
		override fun onSkipToNext() = next()
		override fun onSkipToPrevious() = previous()
		override fun onSeekTo(pos: Long) = seekTo(pos)
		override fun onSkipToQueueItem(id: Long) = playIndex(id.toInt())
		override fun onPlayFromUri(uri: Uri?, extras: Bundle?) {
			if (uri != null) playUri(uri)
		}
		override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
			if (mediaId != null) playFromMediaId(mediaId)
		}
		override fun onPlayFromSearch(query: String?, extras: Bundle?) = playFromSearch(query)
	}

	init {
		val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
		audioSessionId = audioManager.generateAudioSessionId()
		players.forEach { p ->
			p.setAudioSessionId(audioSessionId)
			levels[p] = 1f
			replayGains[p] = 1f
			p.addListener(listenerFor(p))
		}
		updatePlaybackParameters()
		updateAmplifier()
		updateBassBoost()
		SettingsManager.registerChangeListener(context, prefsListener)
		router.start()
		LiveEqualizer.apply(EqualizerStore.load(context, router.currentRoute))
		session.setCallback(sessionCallback)
		updateSessionState()
	}

	// region Public controls

	fun playPlaylist(playlist: Playlist, index: Int, resumeFraction: Float = 0f) {
		SettingsManager.setLastPlaylistId(context, playlist.id)
		val current = _state.value
		if (current.playlistId == playlist.id) {
			// Already loaded: jump within the queue so "Play next" tracks survive and nothing re-buffers
			val target = playlist.uris.getOrNull(index)?.toString()
			val queueIndex = current.queue.indexOfFirst { it.playlistIndex == index && it.uriString == target }
			if (queueIndex != -1) {
				playIndex(queueIndex)
				if (resumeFraction > 0f) pendingResumeFraction = resumeFraction
				return
			}
		}
		loadQueue(PlayQueue.fromPlaylist(playlist.uris.map { it.toString() }), index, playlist.name, playlist.id, resumeFraction)
	}

	fun playTracks(name: String, uris: List<Uri>, index: Int) {
		loadQueue(PlayQueue.fromPlaylist(uris.map { it.toString() }), index, name, null, 0f)
	}

	// Where a playlist should resume, including progress made since it was last loaded from disk
	fun resumePointFor(playlist: Playlist): Pair<Int, Float> =
		resumePoints[playlist.id] ?: (playlist.lastIndex to playlist.lastPosition)

	fun playUri(uri: Uri) {
		val key = uri.toString()
		val current = _state.value
		val existing = current.queue.indexOfFirst { it.uriString == key }
		when {
			existing != -1 -> playIndex(existing)
			current.queue.isEmpty() -> loadQueue(listOf(QueueItem(key, null)), 0, "Now Playing", null, 0f)
			else -> {
				insertIntoQueue(current.currentIndex + 1, QueueItem(key, null))
				playIndex(_state.value.currentIndex + 1)
			}
		}
	}

	fun playNext(uri: Uri) {
		val current = _state.value
		if (current.queue.isEmpty()) {
			loadQueue(listOf(QueueItem(uri.toString(), null)), 0, "Up Next", null, 0f, play = false)
		} else {
			insertIntoQueue(current.currentIndex + 1, QueueItem(uri.toString(), null))
		}
	}

	fun addToQueue(uri: Uri) {
		val current = _state.value
		if (current.queue.isEmpty()) {
			loadQueue(listOf(QueueItem(uri.toString(), null)), 0, "Up Next", null, 0f, play = false)
		} else {
			insertIntoQueue(PlayQueue.appendPosition(current.queue, current.currentIndex), QueueItem(uri.toString(), null))
		}
	}

	fun removeFromQueue(index: Int) {
		val current = _state.value
		if (index !in current.queue.indices) return
		if (index == current.currentIndex) {
			val wasPlaying = current.isPlaying
			val items = current.queue.toMutableList().apply { removeAt(index) }
			if (items.isEmpty()) {
				stop()
				return
			}
			replaceTimeline(items, index.coerceAtMost(items.lastIndex), wasPlaying)
			return
		}
		val items = current.queue.toMutableList().apply { removeAt(index) }
		queueVersion++
		if (player.mediaItemCount == current.queue.size) {
			player.removeMediaItem(index)
			playerVersions[player] = queueVersion
		}
		val newCurrent = if (index < current.currentIndex) current.currentIndex - 1 else current.currentIndex
		_state.value = current.copy(queue = items, currentIndex = newCurrent)
		publishSessionQueue()
	}

	fun play() {
		if (_state.value.queue.isEmpty()) {
			resumeLast()
			return
		}
		if (!audioFocus.requestFocus()) return
		val p = player
		if (p.playbackState == Player.STATE_IDLE) p.prepare()
		if (p.playbackState == Player.STATE_ENDED) p.seekTo(_state.value.currentIndex.coerceAtLeast(0), 0L)
		val fadeIn = SettingsManager.getFadeInPause(context).toLong()
		p.play()
		if (fadeIn > 0) startVolumeAnimation(p, 0f, 1f, fadeIn) else setLevel(p, 1f)
		ensureServiceStarted()
	}

	fun pause() {
		val p = player
		val fadeOut = SettingsManager.getFadeOutPause(context).toLong()
		if (fadeOut > 0 && p.isPlaying) {
			startVolumeAnimation(p, levels[p] ?: 1f, 0f, fadeOut) { p.pause() }
		} else {
			p.pause()
		}
	}

	fun togglePlayPause() {
		if (_state.value.isPlaying) pause() else play()
	}

	fun next() {
		val current = _state.value
		if (current.currentIndex < current.queue.lastIndex) playIndex(current.currentIndex + 1)
	}

	fun previous() {
		val current = _state.value
		if (current.currentIndex > 0) playIndex(current.currentIndex - 1)
	}

	fun stop() {
		saveProgress()
		tickJob?.cancel()
		volumeAnimators.values.forEach { it.cancel() }
		players.forEach {
			it.stop()
			it.clearMediaItems()
		}
		queueVersion++
		audioFocus.abandonFocus()
		_state.value = EngineState()
		_progress.value = PlaybackProgress()
		_lyrics.value = LyricsState()
		updateSessionState()
		updateMetadata()
		publishSessionQueue()
	}

	fun playIndex(index: Int, play: Boolean = true) {
		pendingResumeFraction = 0f
		val queue = _state.value.queue
		if (index !in queue.indices) return
		if (play && !audioFocus.requestFocus()) return
		val fadeOut = SettingsManager.getFadeOutManual(context).toLong()
		val fadeIn = SettingsManager.getFadeInManual(context).toLong()
		val start = { p: ExoPlayer ->
			ignoreTransitions = true
			syncTimeline(p)
			p.seekTo(index, 0L)
			ignoreTransitions = false
			if (p.playbackState == Player.STATE_IDLE) p.prepare()
			if (play) {
				p.playWhenReady = true
				startVolumeAnimation(p, 0f, 1f, fadeIn)
			} else {
				setLevel(p, 1f)
			}
		}
		if (fadeOut > 0 && player.isPlaying) {
			val previous = player
			activePlayerIndex = (activePlayerIndex + 1) % 2
			startVolumeAnimation(previous, levels[previous] ?: 1f, 0f, fadeOut) { previous.pause() }
		}
		start(player)
		setCurrent(index)
		if (play) ensureServiceStarted()
	}

	fun seekTo(positionMs: Long) {
		if (positionMs < 0) return
		val fadeOut = SettingsManager.getFadeOutSeek(context).toLong()
		val fadeIn = SettingsManager.getFadeInSeek(context).toLong()
		val p = player
		if (fadeOut > 0 && p.isPlaying) {
			activePlayerIndex = (activePlayerIndex + 1) % 2
			val next = player
			ignoreTransitions = true
			syncTimeline(next)
			next.seekTo(_state.value.currentIndex, positionMs)
			ignoreTransitions = false
			startVolumeAnimation(p, levels[p] ?: 1f, 0f, fadeOut) { p.pause() }
			if (next.playbackState == Player.STATE_IDLE) next.prepare()
			next.playWhenReady = true
			startVolumeAnimation(next, 0f, 1f, fadeIn)
		} else {
			p.seekTo(positionMs)
			if (p.isPlaying && fadeIn > 0) startVolumeAnimation(p, 0f, 1f, fadeIn)
		}
		_progress.value = _progress.value.copy(positionMs = positionMs)
		updateSessionState()
	}

	// Called whenever a playlist is saved, so edits to the playing playlist reach the queue
	fun onPlaylistUpdated(playlist: Playlist) {
		val current = _state.value
		if (current.playlistId != playlist.id) return
		val update = PlayQueue.rebuild(current.queue, current.currentIndex, playlist.uris.map { it.toString() })
		if (update.items.isEmpty()) {
			stop()
			return
		}
		val sameTrack = update.items.getOrNull(update.currentIndex)?.uriString == current.currentItem?.uriString
		if (!sameTrack || player.mediaItemCount != current.queue.size) {
			replaceTimeline(update.items, update.currentIndex, current.isPlaying)
			_state.value = _state.value.copy(queueName = playlist.name)
			return
		}
		// Swap everything around the playing item, so the current track isn't interrupted
		val p = player
		val oldIndex = p.currentMediaItemIndex
		val newItems = update.items.map { MediaItem.fromUri(it.uri) }
		p.replaceMediaItems(oldIndex + 1, p.mediaItemCount, newItems.drop(update.currentIndex + 1))
		p.replaceMediaItems(0, oldIndex, newItems.take(update.currentIndex))
		queueVersion++
		playerVersions[p] = queueVersion
		_state.value = current.copy(queue = update.items, currentIndex = update.currentIndex, queueName = playlist.name)
		publishSessionQueue()
	}

	fun onPlaylistRemoved(playlistId: String) {
		if (_state.value.playlistId == playlistId) stop()
		resumePoints.remove(playlistId)
	}

	fun setEqualizer(route: AudioRoute, config: EqConfig) {
		EqualizerStore.save(context, route, config)
		if (route == router.currentRoute) LiveEqualizer.apply(config)
	}

	// endregion

	// region Queue plumbing

	private fun loadQueue(items: List<QueueItem>, startIndex: Int, name: String, playlistId: String?, resumeFraction: Float, play: Boolean = true) {
		if (items.isEmpty()) return
		saveProgress()
		val index = startIndex.coerceIn(0, items.lastIndex)
		_state.value = _state.value.copy(queue = items, queueName = name, playlistId = playlistId, currentIndex = -1)
		replaceTimeline(items, index, play)
		pendingResumeFraction = resumeFraction
	}

	// Replaces the whole timeline and starts at index, crossfading if manual fades are set
	private fun replaceTimeline(items: List<QueueItem>, index: Int, play: Boolean) {
		queueVersion++
		_state.value = _state.value.copy(queue = items)
		// playIndex syncs whichever player ends up active; a crossfading one keeps the old timeline until it fades out
		playIndex(index, play)
		publishSessionQueue()
	}

	private fun insertIntoQueue(position: Int, item: QueueItem) {
		val current = _state.value
		val at = position.coerceIn(0, current.queue.size)
		val items = current.queue.take(at) + item + current.queue.drop(at)
		queueVersion++
		val p = player
		if (p.mediaItemCount == current.queue.size) {
			p.addMediaItem(at, MediaItem.fromUri(item.uri))
			playerVersions[p] = queueVersion
		}
		val newCurrent = if (at <= current.currentIndex) current.currentIndex + 1 else current.currentIndex
		_state.value = current.copy(queue = items, currentIndex = newCurrent)
		publishSessionQueue()
	}

	private fun syncTimeline(p: ExoPlayer) {
		if (playerVersions[p] == queueVersion) return
		val items = _state.value.queue.map { MediaItem.fromUri(it.uri) }
		p.setMediaItems(items)
		p.prepare()
		playerVersions[p] = queueVersion
	}

	private fun setCurrent(index: Int) {
		val current = _state.value
		if (index == current.currentIndex && current.currentTitle.isNotEmpty()) return
		val item = current.queue.getOrNull(index)
		val title = item?.let { trackTitle(it.uri, it.playlistIndex ?: index, context) } ?: ""
		_state.value = current.copy(currentIndex = index, currentTitle = title)
		crossfadedFromIndex = -1
		saveProgress()
		_lyrics.value = LyricsState(forUri = item?.uriString)
		ensureLyricsLoaded()
		updateMetadata()
		updateSessionState()
	}

	private fun resumeLast() {
		scope.launch {
			val playlists = withContext(Dispatchers.IO) { PlaylistManager.loadPlaylists(context) }
			val lastId = SettingsManager.getLastPlaylistId(context)
			val candidates = playlists.filter { !it.isHidden && it.uris.isNotEmpty() }
			val target = candidates.firstOrNull { it.id == lastId } ?: candidates.firstOrNull() ?: return@launch
			val (index, fraction) = resumePointFor(target)
			playPlaylist(target, index, fraction)
		}
	}

	private fun saveProgress() {
		val current = _state.value
		val playlistId = current.playlistId ?: return
		val playlistIndex = current.currentItem?.playlistIndex ?: return
		val fraction = progressFraction()
		resumePoints[playlistId] = playlistIndex to fraction
		scope.launch(Dispatchers.IO) {
			PlaylistManager.updatePlaylistProgress(context, playlistId, playlistIndex, fraction)
		}
	}

	private fun progressFraction(): Float {
		val duration = player.duration
		return if (duration > 0) (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
	}

	// endregion

	// region Player events

	private fun listenerFor(p: ExoPlayer) = object : Player.Listener {
		override fun onIsPlayingChanged(isPlaying: Boolean) {
			if (p !== player) return
			_state.value = _state.value.copy(isPlaying = isPlaying)
			tickJob?.cancel()
			if (isPlaying) {
				tickJob = scope.launch { tickLoop() }
			} else {
				saveProgress()
				publishProgress()
			}
			updateSessionState()
		}

		override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
			if (ignoreTransitions || p !== player || p.mediaItemCount != _state.value.queue.size) return
			setCurrent(p.currentMediaItemIndex)
			if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
				val fadeIn = SettingsManager.getFadeInAuto(context).toLong()
				if (fadeIn > 0) startVolumeAnimation(p, 0f, 1f, fadeIn) else setLevel(p, 1f)
			}
		}

		override fun onPlaybackStateChanged(playbackState: Int) {
			if (p !== player || playbackState != Player.STATE_READY) return
			if (pendingResumeFraction > 0f && p.duration > 0) {
				p.seekTo((pendingResumeFraction * p.duration).toLong())
				pendingResumeFraction = 0f
			}
			publishProgress()
			updateMetadata()
		}

		override fun onTracksChanged(tracks: Tracks) = updateReplayGain(p, tracks)

		override fun onPlayerError(error: PlaybackException) {
			Log.e(TAG, "Playback failed: ${error.errorCodeName}", error)
			if (p !== player) return
			val current = _state.value
			if (SettingsManager.isSkipUnavailableTracksEnabled(context)) {
				Toast.makeText(context, "Skipping unavailable track...", Toast.LENGTH_SHORT).show()
				if (current.currentIndex < current.queue.lastIndex) playIndex(current.currentIndex + 1)
			} else {
				val cause = error.cause?.message ?: "Unknown cause"
				Toast.makeText(context, "Playback error: ${error.errorCodeName} - ${error.message}\nCause: $cause", Toast.LENGTH_LONG).show()
			}
		}
	}

	private suspend fun tickLoop() {
		val fadeOutAuto = SettingsManager.getFadeOutAuto(context).toLong()
		val fadeInAuto = SettingsManager.getFadeInAuto(context).toLong()
		// Tighter polling only pays off when we have to catch the crossfade window
		val tick = if (fadeOutAuto > 0) 50L else 250L
		while (scope.isActive) {
			val p = player
			val position = p.currentPosition
			val duration = p.duration
			_progress.value = PlaybackProgress(position, if (duration > 0) duration else 0L)
			val current = _state.value
			if (fadeOutAuto > 0 && duration > 0 && duration - position <= fadeOutAuto && crossfadedFromIndex != current.currentIndex) {
				crossfadedFromIndex = current.currentIndex
				val nextIndex = current.currentIndex + 1
				if (nextIndex < current.queue.size) {
					activePlayerIndex = (activePlayerIndex + 1) % 2
					val incoming = player
					ignoreTransitions = true
					syncTimeline(incoming)
					incoming.seekTo(nextIndex, 0L)
					ignoreTransitions = false
					if (incoming.playbackState == Player.STATE_IDLE) incoming.prepare()
					incoming.playWhenReady = true
					startVolumeAnimation(incoming, 0f, 1f, fadeInAuto)
					startVolumeAnimation(p, levels[p] ?: 1f, 0f, fadeOutAuto) { p.pause() }
					setCurrent(nextIndex)
				}
			}
			updateLyricLine(position)
			val now = System.currentTimeMillis()
			if (now - lastProgressSave > 5000) {
				lastProgressSave = now
				saveProgress()
			}
			delay(tick)
		}
	}

	private fun publishProgress() {
		val duration = player.duration
		_progress.value = PlaybackProgress(player.currentPosition, if (duration > 0) duration else 0L)
	}

	// endregion

	// region Volume and effects

	private fun applyVolume(p: ExoPlayer) {
		p.volume = ((levels[p] ?: 1f) * duckFactor * (replayGains[p] ?: 1f)).coerceIn(0f, 1f)
	}

	private fun setLevel(p: ExoPlayer, level: Float) {
		levels[p] = level
		applyVolume(p)
	}

	private fun startVolumeAnimation(p: ExoPlayer, from: Float, to: Float, durationMs: Long, onEnd: () -> Unit = {}) {
		volumeAnimators[p]?.cancel()
		if (durationMs <= 0) {
			setLevel(p, to)
			onEnd()
			return
		}
		volumeAnimators[p] = scope.launch {
			val startTime = System.currentTimeMillis()
			while (true) {
				val elapsed = System.currentTimeMillis() - startTime
				if (elapsed >= durationMs) {
					setLevel(p, to)
					onEnd()
					break
				}
				setLevel(p, from + (to - from) * (elapsed.toFloat() / durationMs))
				delay(16)
			}
		}
	}

	private fun updateReplayGain(p: ExoPlayer, tracks: Tracks) {
		if (!SettingsManager.isReplayGainEnabled(context)) {
			replayGains[p] = 1f
			applyVolume(p)
			return
		}
		var gainDb: Float? = null
		for (group in tracks.groups) {
			for (i in 0 until group.length) {
				val metadata = group.getTrackFormat(i).metadata ?: continue
				for (j in 0 until metadata.length()) {
					val entry = metadata.get(j)
					when (entry) {
						is androidx.media3.extractor.metadata.id3.TextInformationFrame ->
							if (entry.id.contains("TXXX", ignoreCase = true) && entry.description?.contains("replaygain_track_gain", ignoreCase = true) == true) {
								gainDb = entry.values.firstOrNull()?.replace("dB", "")?.trim()?.toFloatOrNull() ?: gainDb
							}
						is androidx.media3.extractor.metadata.vorbis.VorbisComment ->
							if (entry.key.equals("REPLAYGAIN_TRACK_GAIN", ignoreCase = true)) {
								gainDb = entry.value.replace("dB", "").trim().toFloatOrNull() ?: gainDb
							}
					}
				}
			}
		}
		replayGains[p] = gainDb?.let { Math.pow(10.0, it / 20.0).toFloat().coerceIn(0.1f, 3.0f) } ?: 1f
		applyVolume(p)
	}

	private fun updatePlaybackParameters() {
		val params = PlaybackParameters(SettingsManager.getPlaybackSpeed(context), SettingsManager.getPlaybackPitch(context))
		players.forEach { it.playbackParameters = params }
	}

	private fun updateAmplifier() {
		try {
			loudnessEnhancer?.release()
			loudnessEnhancer = null
			if (SettingsManager.isAmplifierEnabled(context) && audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
				loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(audioSessionId).apply {
					setTargetGain(SettingsManager.getAmplifierLevel(context))
					enabled = true
				}
			}
		} catch (e: Exception) {
			Log.w(TAG, "Amplifier unavailable", e)
		}
	}

	private fun updateBassBoost() {
		try {
			bassBoost?.release()
			bassBoost = null
			val level = SettingsManager.getBassBoost(context)
			if (level > 0 && audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
				bassBoost = android.media.audiofx.BassBoost(0, audioSessionId).apply {
					enabled = true
					setStrength(level.toShort())
				}
			}
		} catch (e: Exception) {
			Log.w(TAG, "Bass boost unavailable", e)
		}
	}

	// endregion

	// region Lyrics

	private fun ensureLyricsLoaded() {
		val item = _state.value.currentItem ?: return
		val wanted = lyricsViewerOpen || SettingsManager.isLyricsAnnounceEnabled(context)
		val current = _lyrics.value
		if (!wanted || (current.forUri == item.uriString && (current.loading || current.lyrics != null))) return
		lyricsJob?.cancel()
		_lyrics.value = LyricsState(forUri = item.uriString, loading = true)
		val title = _state.value.currentTitle
		lyricsJob = scope.launch {
			val loaded = withContext(Dispatchers.IO) {
				try { LyricsRepository.load(context, item.uri, title) } catch (e: Exception) { null }
			}
			if (_state.value.currentItem?.uriString == item.uriString) {
				_lyrics.value = LyricsState(forUri = item.uriString, lyrics = loaded)
			}
		}
	}

	private fun updateLyricLine(positionMs: Long) {
		val current = _lyrics.value
		val lyrics = current.lyrics ?: return
		val line = lyrics.lineIndexAt(positionMs)
		if (line == current.currentLine) return
		_lyrics.value = current.copy(currentLine = line)
		if (line >= 0 && SettingsManager.isLyricsAnnounceEnabled(context)) {
			LyricsAnnouncer.announce(context, lyrics.lines[line].text)
		}
	}

	// endregion

	// region Media session

	private fun ensureServiceStarted() {
		if (PlaybackService.isRunning) return
		try {
			ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
		} catch (e: Exception) {
			// Android 12+ refuses foreground starts from the background; the media button path still works
			Log.w(TAG, "Could not start playback service", e)
		}
	}

	private fun updateSessionState() {
		val current = _state.value
		val stateCode = when {
			current.queue.isEmpty() -> PlaybackStateCompat.STATE_NONE
			current.isPlaying -> PlaybackStateCompat.STATE_PLAYING
			else -> PlaybackStateCompat.STATE_PAUSED
		}
		val speed = if (current.isPlaying) SettingsManager.getPlaybackSpeed(context) else 0f
		session.setPlaybackState(
			PlaybackStateCompat.Builder()
				.setActions(
					PlaybackStateCompat.ACTION_PLAY or
						PlaybackStateCompat.ACTION_PAUSE or
						PlaybackStateCompat.ACTION_PLAY_PAUSE or
						PlaybackStateCompat.ACTION_STOP or
						PlaybackStateCompat.ACTION_SEEK_TO or
						PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
						PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
						PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM or
						PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
						PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
						PlaybackStateCompat.ACTION_PLAY_FROM_URI
				)
				.setState(stateCode, player.currentPosition, speed)
				.setActiveQueueItemId(current.currentIndex.toLong())
				.build()
		)
	}

	private fun updateMetadata() {
		val current = _state.value
		val duration = player.duration
		session.setMetadata(
			MediaMetadataCompat.Builder()
				.putString(MediaMetadataCompat.METADATA_KEY_TITLE, current.currentTitle.ifEmpty { "FukeX" })
				.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, current.queueName)
				.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, if (duration > 0) duration else -1L)
				.build()
		)
	}

	// Titles can mean a ContentResolver query per track, so they're built off the main thread
	private fun publishSessionQueue() {
		val queue = _state.value.queue
		scope.launch {
			val items = withContext(Dispatchers.IO) {
				queue.take(500).mapIndexed { i, item ->
					val description = MediaDescriptionCompat.Builder()
						.setMediaId(item.uriString)
						.setTitle(trackTitle(item.uri, item.playlistIndex ?: i, context))
						.build()
					MediaSessionCompat.QueueItem(description, i.toLong())
				}
			}
			if (_state.value.queue == queue) {
				session.setQueue(items.ifEmpty { null })
				session.setQueueTitle(_state.value.queueName)
			}
		}
	}

	// Media IDs from PlaybackService's browse tree: "playlist/<id>/<index>"
	fun playFromMediaId(mediaId: String) {
		val parts = mediaId.split('/')
		if (parts.size != 3 || parts[0] != "playlist") return
		val index = parts[2].toIntOrNull() ?: return
		scope.launch {
			val playlist = withContext(Dispatchers.IO) { PlaylistManager.loadPlaylists(context) }
				.firstOrNull { it.id == parts[1] && !it.isHidden } ?: return@launch
			playPlaylist(playlist, index)
		}
	}

	private fun playFromSearch(query: String?) {
		if (query.isNullOrBlank()) {
			play()
			return
		}
		val needle = query.lowercase()
		scope.launch {
			val match = withContext(Dispatchers.IO) {
				val playlists = PlaylistManager.loadPlaylists(context).filter { !it.isHidden }
				playlists.firstOrNull { it.name.lowercase().contains(needle) }?.let { it to 0 }
					?: playlists.firstNotNullOfOrNull { p ->
						p.uris.indices.firstOrNull { trackTitle(p.uris[it], it, context).lowercase().contains(needle) }?.let { p to it }
					}
			} ?: return@launch
			playPlaylist(match.first, match.second)
		}
	}

	// endregion
}
