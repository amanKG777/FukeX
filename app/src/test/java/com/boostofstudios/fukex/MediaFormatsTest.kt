package com.boostofstudios.fukex
import com.boostofstudios.fukex.data.isMediaFile
import com.boostofstudios.fukex.data.isMediaUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MediaFormatsTest {
	@Test
	fun acceptsSupportedExtensions() {
		assertTrue(File("song.mp3").isMediaFile())
		assertTrue(File("song.FLAC").isMediaFile())
		assertTrue("Track%2001.opus".isMediaUrl())
	}

	@Test
	fun rejectsEverythingElse() {
		assertFalse(File("notes.txt").isMediaFile())
		assertFalse(File("noextension").isMediaFile())
		assertFalse("folder/".isMediaUrl())
	}
}
