package com.boostofstudios.fukex

import com.boostofstudios.fukex.playback.PlayQueue
import com.boostofstudios.fukex.playback.QueueItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayQueueTest {
	private fun keys(items: List<QueueItem>) = items.map { it.uriString }

	@Test
	fun reorderKeepsCurrentTrack() {
		val old = PlayQueue.fromPlaylist(listOf("a", "b", "c"))
		val update = PlayQueue.rebuild(old, 1, listOf("b", "a", "c"))
		assertEquals(listOf("b", "a", "c"), keys(update.items))
		assertEquals(0, update.currentIndex)
	}

	@Test
	fun pendingExtrasSurviveAnEdit() {
		val old = PlayQueue.insertNext(PlayQueue.fromPlaylist(listOf("a", "b", "c")), 0, QueueItem("x", null))
		val update = PlayQueue.rebuild(old, 0, listOf("a", "b", "c", "d"))
		assertEquals(listOf("a", "x", "b", "c", "d"), keys(update.items))
		assertEquals(0, update.currentIndex)
	}

	@Test
	fun removingThePlayingTrackMovesToTheNextOne() {
		val old = PlayQueue.fromPlaylist(listOf("a", "b", "c"))
		val update = PlayQueue.rebuild(old, 1, listOf("a", "c"))
		assertEquals(listOf("a", "c"), keys(update.items))
		assertEquals(1, update.currentIndex)
	}

	@Test
	fun removingEverythingEmptiesTheQueue() {
		val old = PlayQueue.fromPlaylist(listOf("a"))
		val update = PlayQueue.rebuild(old, 0, emptyList())
		assertEquals(emptyList<String>(), keys(update.items))
		assertEquals(-1, update.currentIndex)
	}

	@Test
	fun duplicateTracksPickTheClosestCopy() {
		val old = PlayQueue.fromPlaylist(listOf("a", "b", "a"))
		val update = PlayQueue.rebuild(old, 2, listOf("a", "b", "a", "c"))
		assertEquals(2, update.currentIndex)
	}

	@Test
	fun addToQueueGoesAfterEarlierExtras() {
		var queue = PlayQueue.fromPlaylist(listOf("a", "b"))
		queue = PlayQueue.insertNext(queue, 0, QueueItem("x", null))
		val at = PlayQueue.appendPosition(queue, 0)
		assertEquals(2, at)
	}
}
