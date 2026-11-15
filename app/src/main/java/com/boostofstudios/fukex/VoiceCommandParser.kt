package com.boostofstudios.fukex

import java.util.Locale

sealed interface VoiceCommand {
	// An empty query means "just play", which resumes whatever is loaded
	data class Play(val query: String) : VoiceCommand
	data class PlayNext(val query: String) : VoiceCommand
	data class AddToQueue(val query: String) : VoiceCommand
	data object Resume : VoiceCommand
	data object Pause : VoiceCommand
	data object Next : VoiceCommand
	data object Previous : VoiceCommand
	data object Restart : VoiceCommand
	data class Seek(val deltaSeconds: Int) : VoiceCommand
	data object VolumeUp : VoiceCommand
	data object VolumeDown : VoiceCommand
	data object WhatsPlaying : VoiceCommand
	data object AmplifierOn : VoiceCommand
	data object AmplifierOff : VoiceCommand
	data object AmplifierUp : VoiceCommand
	data object AmplifierDown : VoiceCommand
	data object OpenDocs : VoiceCommand
	data object Help : VoiceCommand
	data object NextResult : VoiceCommand
	data object PreviousResult : VoiceCommand
	data class SelectResult(val index: Int) : VoiceCommand
}

// Turns what the speech recognizer heard into a command. Pure text in, command out, so it can be unit tested.
object VoiceCommandParser {
	// Lightweight dictionary mapping for multiple languages
	private val playKeywords = listOf("play", "chalao", "bajao", "reproducir", "pon")
	private val pauseKeywords = listOf("pause", "stop", "ruk", "roko", "pausa")
	private val resumeKeywords = listOf("resume", "continue", "wapas chalao", "reanudar")
	private val nextKeywords = listOf("next", "skip", "agla", "siguiente")
	private val prevKeywords = listOf("previous", "back", "pichla", "anterior")
	private val ampKeywords = listOf("amplifier", "amp", "boost", "awaz badhao")
	private val docKeywords = listOf("documentation", "help", "guide", "docs")
	private val restartKeywords = listOf("restart", "start over", "from the beginning", "replay")

	private val onKeywords = listOf("on", "enable", "chalu", "encender")
	private val offKeywords = listOf("off", "disable", "band", "apagar")
	private val increaseKeywords = listOf("increase", "up", "badao", "subir")
	private val decreaseKeywords = listOf("decrease", "down", "ghatao", "bajar")

	private val forwardKeywords = listOf("forward", "ahead", "fast forward")
	private val backwardKeywords = listOf("rewind", "backward", "backwards")
	private val louderKeywords = listOf("louder")
	private val quieterKeywords = listOf("quieter", "softer")

	private val helpPhrases = listOf("what can i say", "what can you do", "voice commands", "commands", "list commands")
	private val nowPlayingPhrases = listOf(
		"now playing", "what is playing", "whats playing", "what's playing", "what song", "which song",
		"what track", "which track", "current song", "current track", "what is this song", "whats this song"
	)

	private const val DEFAULT_SEEK_SECONDS = 10
	private val numberWords = mapOf(
		"five" to 5, "ten" to 10, "fifteen" to 15, "twenty" to 20, "thirty" to 30, "sixty" to 60
	)
	private val durationRegex = Regex("(\\d+|[a-z]+)\\s*(seconds?|secs?|minutes?|mins?)")

	private val nextResultPhrases = listOf(
		"next result", "another result", "next version", "another version", "different version",
		"different one", "another one", "try another", "not this one", "next option",
		"other version", "other one", "dusra bajao", "dusra chalao", "dusra wala",
		"dusra song", "dusra track", "dusra result", "dusri audio", "agla result", "agla version"
	)
	private val prevResultPhrases = listOf(
		"previous result", "pichla result", "pichla version", "last result", "previous version",
		"pichla wala"
	)

	private val ordinalWords = mapOf(
		"first" to 1, "1st" to 1, "pehla" to 1, "one" to 1, "1" to 1,
		"second" to 2, "2nd" to 2, "dusra" to 2, "doosra" to 2, "two" to 2, "2" to 2,
		"third" to 3, "3rd" to 3, "teesra" to 3, "three" to 3, "3" to 3,
		"fourth" to 4, "4th" to 4, "chautha" to 4, "four" to 4, "4" to 4,
		"fifth" to 5, "5th" to 5, "paanchwa" to 5, "five" to 5, "5" to 5
	)

	private fun wordRegex(keyword: String) = Regex("(^|\\s)${Regex.escape(keyword)}(\\s|$)")

	private fun List<String>.matchIn(text: String): String? =
		firstOrNull { wordRegex(it).containsMatchIn(text) }

	private fun List<String>.foundIn(text: String) = matchIn(text) != null

	// Unicode-aware so Hindi/Devanagari speech survives; apostrophes stay for "what's"
	fun normalize(text: String): String =
		text.lowercase(Locale.getDefault()).replace(Regex("[^\\p{L}\\p{N}\\p{M}\\s']"), " ").replace(Regex("\\s+"), " ").trim()

	// The recognizer returns alternatives best-first; the first one that makes sense wins.
	fun parse(candidates: List<String>): VoiceCommand? =
		candidates.firstNotNullOfOrNull { parse(it) }

	fun parse(text: String): VoiceCommand? {
		val lower = normalize(text)
		if (lower.isEmpty()) return null

		resultCommand(lower)?.let { return it }

		queueCommand(lower)?.let { return it }

		if (Regex("^(play )?next( song| track)?$").matches(lower)) return VoiceCommand.Next

		// "play <song>" / "<song> bajao" comes first, so song names containing
		// words like "stop", "back" or "help" don't trigger other commands.
		val playMatch = playKeywords.matchIn(lower)
		if (playMatch != null && !resumeKeywords.foundIn(lower) && (lower.startsWith("$playMatch ") || lower.endsWith(" $playMatch"))) {
			val query = cleanQuery(lower.removePrefix(playMatch).removeSuffix(playMatch))
			if (query.isNotEmpty() && !isSkipPhrase(query)) {
				return VoiceCommand.Play(query)
			}
		}

		if (nowPlayingPhrases.any { lower.contains(it) }) return VoiceCommand.WhatsPlaying
		if (helpPhrases.any { lower.contains(it) }) return VoiceCommand.Help

		seekCommand(lower)?.let { return it }
		if (restartKeywords.foundIn(lower)) return VoiceCommand.Restart

		if (ampKeywords.foundIn(lower)) {
			when {
				increaseKeywords.foundIn(lower) -> return VoiceCommand.AmplifierUp
				decreaseKeywords.foundIn(lower) -> return VoiceCommand.AmplifierDown
				onKeywords.foundIn(lower) -> return VoiceCommand.AmplifierOn
				offKeywords.foundIn(lower) -> return VoiceCommand.AmplifierOff
			}
		}

		if (wordRegex("volume").containsMatchIn(lower) || louderKeywords.foundIn(lower) || quieterKeywords.foundIn(lower)) {
			when {
				louderKeywords.foundIn(lower) || increaseKeywords.foundIn(lower) -> return VoiceCommand.VolumeUp
				quieterKeywords.foundIn(lower) || decreaseKeywords.foundIn(lower) -> return VoiceCommand.VolumeDown
			}
		}

		if (docKeywords.foundIn(lower)) return VoiceCommand.OpenDocs

		// Checked before pause/play so "wapas chalao" isn't read as "chalao"
		if (resumeKeywords.foundIn(lower)) return VoiceCommand.Resume
		if (pauseKeywords.foundIn(lower)) return VoiceCommand.Pause
		if (nextKeywords.foundIn(lower)) return VoiceCommand.Next
		if (prevKeywords.foundIn(lower)) return VoiceCommand.Previous

		if (playMatch != null) {
			val query = cleanQuery(lower.replace(wordRegex(playMatch), " "))
			return if (query.isEmpty()) VoiceCommand.Resume else VoiceCommand.Play(query)
		}
		return null
	}

	// "play previous" / "play next song" are navigation, but "play back in black" is a song
	private fun isSkipPhrase(query: String): Boolean {
		val bare = query.removeSuffix(" song").removeSuffix(" track")
		return bare in nextKeywords || bare in prevKeywords
	}

	private fun resultCommand(lower: String): VoiceCommand? {
		val cleaned = lower.removePrefix("play ").removePrefix("the ").trim()
		if (nextResultPhrases.any { lower.contains(it) || cleaned == it }) return VoiceCommand.NextResult
		if (prevResultPhrases.any { lower.contains(it) || cleaned == it }) return VoiceCommand.PreviousResult

		val selectRegex = Regex("^(?:play\\s+)?(?:result|option|version|item|number|no)?\\s*(\\d+|first|second|third|fourth|fifth|1st|2nd|3rd|4th|5th|one|two|three|four|five|pehla|dusra|doosra|teesra|chautha|paanchwa)\\s*(?:result|option|version|item|wala)?$")
		selectRegex.matchEntire(lower.trim())?.let { m ->
			val word = m.groupValues[1]
			val num = word.toIntOrNull() ?: ordinalWords[word]
			if (num != null && num in 1..20) return VoiceCommand.SelectResult(num)
		}
		return null
	}

	// "add <song> to queue", "queue <song>", "play <song> next"
	private fun queueCommand(lower: String): VoiceCommand? {
		Regex("^(?:add|queue)\\s+(.+?)(?:\\s+to\\s+(?:the\\s+|my\\s+)?(?:queue|up next))?$").matchEntire(lower)?.let { m ->
			val hasQueueWord = lower.startsWith("queue ") || lower.contains(" to queue") || lower.contains(" to the queue") ||
				lower.contains(" to my queue") || lower.endsWith(" up next")
			val query = cleanQuery(m.groupValues[1])
			if (hasQueueWord && query.isNotEmpty()) return VoiceCommand.AddToQueue(query)
		}
		Regex("^play\\s+(.+)\\s+next$").matchEntire(lower)?.let { m ->
			val query = cleanQuery(m.groupValues[1])
			if (query.isNotEmpty()) return VoiceCommand.PlayNext(query)
		}
		return null
	}

	private fun seekCommand(lower: String): VoiceCommand? {
		val duration = durationRegex.find(lower)
		val hasBack = wordRegex("back").containsMatchIn(lower)
		val forward = forwardKeywords.foundIn(lower)
		val backward = backwardKeywords.foundIn(lower) || (hasBack && duration != null)
		if (!forward && !backward) return null
		var seconds = DEFAULT_SEEK_SECONDS
		if (duration != null) {
			val amount = duration.groupValues[1].toIntOrNull() ?: numberWords[duration.groupValues[1]]
			if (amount != null) {
				seconds = if (duration.groupValues[2].startsWith("m")) amount * 60 else amount
			}
		}
		return VoiceCommand.Seek(if (forward && !backward) seconds else -seconds)
	}

	// Drops polite filler the recognizer picks up around a song name
	private fun cleanQuery(raw: String): String =
		raw.trim()
			.removeSuffix(" please").removeSuffix(" for me")
			.removePrefix("the song ").removePrefix("song ").removePrefix("the track ").removePrefix("track ")
			.trim()
}

// Fuzzy matching between a spoken query and titles, shared by playlist and library lookups.
object VoiceMatcher {
	private val nonWordChars = Regex("[^\\p{L}\\p{N}\\p{M}\\s]")

	private fun clean(text: String) = text.lowercase(Locale.getDefault()).replace(nonWordChars, " ").replace(Regex("\\s+"), " ").trim()

	// -1 for no match, otherwise higher is better: exact, prefix, phrase, then all words present
	fun score(query: String, text: String): Int {
		val q = clean(query)
		val t = clean(text)
		if (q.isEmpty() || t.isEmpty()) return -1
		if (t == q) return 3
		if (t.startsWith(q)) return 2
		if (t.contains(q)) return 1
		val words = q.split(' ').filter { it.length > 1 }
		return if (words.isNotEmpty() && words.all { t.contains(it) }) 0 else -1
	}

	fun matches(query: String, text: String) = score(query, text) >= 0
}
