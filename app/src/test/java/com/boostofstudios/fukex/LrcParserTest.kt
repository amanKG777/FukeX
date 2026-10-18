package com.boostofstudios.fukex

import com.boostofstudios.fukex.data.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
	@Test
	fun parsesTimestampsInOrder() {
		val lyrics = LrcParser.parse("[ar:Someone]\n[00:12.50]Second\n[00:01.00]First\n")!!
		assertTrue(lyrics.synced)
		assertEquals(listOf("First", "Second"), lyrics.lines.map { it.text })
		assertEquals(1000L, lyrics.lines[0].timeMs)
		assertEquals(12500L, lyrics.lines[1].timeMs)
	}

	@Test
	fun repeatedTagsProduceOneLinePerTag() {
		val lyrics = LrcParser.parse("[00:05][01:05.123]Chorus")!!
		assertEquals(listOf(5000L, 65123L), lyrics.lines.map { it.timeMs })
	}

	@Test
	fun appliesOffset() {
		val lyrics = LrcParser.parse("[offset:+500]\n[00:02.00]Line")!!
		assertEquals(1500L, lyrics.lines[0].timeMs)
	}

	@Test
	fun plainTextIsNotSynced() {
		val lyrics = LrcParser.parse("Just words\nMore words")!!
		assertFalse(lyrics.synced)
		assertEquals(-1, lyrics.lineIndexAt(10_000))
	}

	@Test
	fun emptyInputGivesNull() {
		assertNull(LrcParser.parse("[ti:Title]\n\n"))
	}

	@Test
	fun lineIndexAtFindsCurrentLine() {
		val lyrics = LrcParser.parse("[00:01.00]A\n[00:03.00]B\n[00:05.00]C")!!
		assertEquals(-1, lyrics.lineIndexAt(500))
		assertEquals(0, lyrics.lineIndexAt(1000))
		assertEquals(1, lyrics.lineIndexAt(4999))
		assertEquals(2, lyrics.lineIndexAt(60_000))
	}
}
