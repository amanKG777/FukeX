package com.boostofstudios.fukex
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
	@Test
	fun formatsUnderAnHourAsMinutesAndSeconds() {
		assertEquals("00:00", formatTime(0))
		assertEquals("00:09", formatTime(9_000))
		assertEquals("03:12", formatTime(192_000))
		assertEquals("59:59", formatTime(3_599_000))
	}

	@Test
	fun formatsAnHourOrMoreWithHours() {
		assertEquals("1:00:00", formatTime(3_600_000))
		assertEquals("1:30:12", formatTime(5_412_000))
		assertEquals("10:00:00", formatTime(36_000_000))
	}

	@Test
	fun treatsUnknownDurationAsZero() {
		assertEquals("00:00", formatTime(-1))
	}

	@Test
	fun formatsSizes() {
		assertEquals("0 B", formatSize(0))
		assertEquals("0 B", formatSize(-5))
		assertEquals("512.00 B", formatSize(512))
		assertEquals("1.00 KB", formatSize(1024))
		assertEquals("1.00 MB", formatSize(1024L * 1024))
		assertEquals("1.50 GB", formatSize((1.5 * 1024 * 1024 * 1024).toLong()))
	}
}
