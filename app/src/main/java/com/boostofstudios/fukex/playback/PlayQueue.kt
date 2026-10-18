package com.boostofstudios.fukex.playback

import android.net.Uri

// playlistIndex is null for tracks added with "Play next" / "Add to queue";
// those aren't part of the playlist, so they never move its saved position.
data class QueueItem(val uriString: String, val playlistIndex: Int?) {
	val uri: Uri get() = Uri.parse(uriString)
}

data class QueueUpdate(val items: List<QueueItem>, val currentIndex: Int)

object PlayQueue {
	fun fromPlaylist(uris: List<String>): List<QueueItem> = uris.mapIndexed { i, uri -> QueueItem(uri, i) }

	// Rebuilds the queue after the playing playlist was edited (reorder, add, remove),
	// keeping the current track and any queued tracks that hadn't played yet.
	fun rebuild(old: List<QueueItem>, oldIndex: Int, newPlaylistUris: List<String>): QueueUpdate {
		val base = fromPlaylist(newPlaylistUris)
		val current = old.getOrNull(oldIndex)
		val pendingExtras = old.drop(oldIndex + 1).filter { it.playlistIndex == null }
		if (current == null) return QueueUpdate(base, if (base.isEmpty()) -1 else 0)
		if (current.playlistIndex == null) {
			// A queued extra is playing: resume the playlist from the start after it
			val items = listOf(current) + pendingExtras + base
			return QueueUpdate(items, 0)
		}
		val matches = base.indices.filter { base[it].uriString == current.uriString }
		val newIndex = matches.minByOrNull { kotlin.math.abs(it - current.playlistIndex) }
		if (newIndex == null) {
			// The playing track was removed; carry on from the track that took its place
			val next = current.playlistIndex.coerceAtMost(base.lastIndex)
			if (next < 0) return QueueUpdate(pendingExtras, if (pendingExtras.isEmpty()) -1 else 0)
			val items = base.take(next) + pendingExtras + base.drop(next)
			return QueueUpdate(items, next)
		}
		val items = base.take(newIndex + 1) + pendingExtras + base.drop(newIndex + 1)
		return QueueUpdate(items, newIndex)
	}

	fun insertNext(queue: List<QueueItem>, currentIndex: Int, item: QueueItem): List<QueueItem> {
		val at = (currentIndex + 1).coerceIn(0, queue.size)
		return queue.take(at) + item + queue.drop(at)
	}

	// Where an extra track should go: after the current track and the extras already queued
	fun appendPosition(queue: List<QueueItem>, currentIndex: Int): Int {
		var at = (currentIndex + 1).coerceIn(0, queue.size)
		while (at < queue.size && queue[at].playlistIndex == null) at++
		return at
	}
}
