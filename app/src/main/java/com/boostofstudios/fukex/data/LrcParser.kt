package com.boostofstudios.fukex.data

data class LyricLine(val timeMs: Long, val text: String)

// synced = false means plain lyrics with no timestamps; they are shown but never announced.
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean) {
	fun lineIndexAt(positionMs: Long): Int {
		if (!synced || lines.isEmpty() || positionMs < lines[0].timeMs) return -1
		var low = 0
		var high = lines.lastIndex
		while (low < high) {
			val mid = (low + high + 1) / 2
			if (lines[mid].timeMs <= positionMs) low = mid else high = mid - 1
		}
		return low
	}
}

object LrcParser {
	private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
	private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
	private val metadataTag = Regex("""^\[[a-zA-Z]+:.*]$""")

	fun parse(text: String): Lyrics? {
		val rawLines = text.lines()
		// A positive LRC offset means the lyrics should show earlier
		val offset = rawLines.firstNotNullOfOrNull { offsetTag.find(it)?.groupValues?.get(1)?.toLongOrNull() } ?: 0L
		val synced = mutableListOf<LyricLine>()
		val plain = mutableListOf<String>()
		for (raw in rawLines) {
			val line = raw.trim()
			if (line.isEmpty()) continue
			val tags = timeTag.findAll(line).toList()
			if (tags.isEmpty()) {
				if (!metadataTag.matches(line)) plain.add(line)
				continue
			}
			val lyric = line.substring(tags.last().range.last + 1).trim()
			if (lyric.isEmpty()) continue
			for (tag in tags) {
				val minutes = tag.groupValues[1].toLong()
				val seconds = tag.groupValues[2].toLong()
				val fraction = tag.groupValues[3]
				val millis = when (fraction.length) {
					0 -> 0L
					1 -> fraction.toLong() * 100
					2 -> fraction.toLong() * 10
					else -> fraction.toLong()
				}
				val time = (minutes * 60_000 + seconds * 1000 + millis - offset).coerceAtLeast(0)
				synced.add(LyricLine(time, lyric))
			}
		}
		return when {
			synced.isNotEmpty() -> Lyrics(synced.sortedBy { it.timeMs }, synced = true)
			plain.isNotEmpty() -> Lyrics(plain.map { LyricLine(0, it) }, synced = false)
			else -> null
		}
	}
}
