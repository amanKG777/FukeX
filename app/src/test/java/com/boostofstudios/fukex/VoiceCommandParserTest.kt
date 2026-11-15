package com.boostofstudios.fukex

import com.boostofstudios.fukex.VoiceCommand.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandParserTest {
	private fun parse(text: String) = VoiceCommandParser.parse(text)

	@Test
	fun playWithASongNameKeepsTheName() {
		assertEquals(Play("shape of you"), parse("Play Shape of You"))
		assertEquals(Play("tum hi ho"), parse("tum hi ho bajao"))
	}

	@Test
	fun songNamesContainingCommandWordsStillPlay() {
		assertEquals(Play("stop the world"), parse("play stop the world"))
		assertEquals(Play("back in black"), parse("play back in black"))
		assertEquals(Play("help"), parse("play help"))
	}

	@Test
	fun barePlayResumes() {
		assertEquals(Resume, parse("play"))
		assertEquals(Resume, parse("wapas chalao"))
		assertEquals(Resume, parse("resume"))
	}

	@Test
	fun politeFillerIsDropped() {
		assertEquals(Play("lose yourself"), parse("play the song lose yourself please"))
	}

	@Test
	fun transportCommands() {
		assertEquals(Pause, parse("pause"))
		assertEquals(Pause, parse("stop"))
		assertEquals(Next, parse("next"))
		assertEquals(Next, parse("play next song"))
		assertEquals(Next, parse("skip"))
		assertEquals(Previous, parse("go back"))
		assertEquals(Previous, parse("previous track"))
		assertEquals(Restart, parse("start over"))
	}

	@Test
	fun queueCommands() {
		assertEquals(PlayNext("blinding lights"), parse("play blinding lights next"))
		assertEquals(AddToQueue("blinding lights"), parse("add blinding lights to queue"))
		assertEquals(AddToQueue("blinding lights"), parse("queue blinding lights"))
		assertNull(parse("add something"))
	}

	@Test
	fun seeking() {
		assertEquals(Seek(10), parse("fast forward"))
		assertEquals(Seek(30), parse("forward 30 seconds"))
		assertEquals(Seek(-10), parse("rewind"))
		assertEquals(Seek(-60), parse("go back 1 minute"))
		assertEquals(Seek(-20), parse("rewind twenty seconds"))
	}

	@Test
	fun volume() {
		assertEquals(VolumeUp, parse("volume up"))
		assertEquals(VolumeDown, parse("volume down"))
		assertEquals(VolumeUp, parse("louder"))
		assertEquals(VolumeDown, parse("quieter"))
	}

	@Test
	fun amplifier() {
		assertEquals(AmplifierOn, parse("turn the amplifier on"))
		assertEquals(AmplifierOff, parse("amp off"))
		assertEquals(AmplifierUp, parse("boost increase"))
		assertEquals(AmplifierDown, parse("amplifier down"))
	}

	@Test
	fun questionsAndHelp() {
		assertEquals(WhatsPlaying, parse("what's playing"))
		assertEquals(WhatsPlaying, parse("What song is this?"))
		assertEquals(Help, parse("what can I say"))
		assertEquals(OpenDocs, parse("open documentation"))
		assertEquals(OpenDocs, parse("help"))
	}

	@Test
	fun resultAndVersionCommands() {
		assertEquals(NextResult, parse("next result"))
		assertEquals(NextResult, parse("play next result"))
		assertEquals(NextResult, parse("next version"))
		assertEquals(NextResult, parse("another version"))
		assertEquals(NextResult, parse("different one"))
		assertEquals(NextResult, parse("dusra bajao"))
		assertEquals(NextResult, parse("dusra wala"))
		assertEquals(PreviousResult, parse("previous result"))
		assertEquals(PreviousResult, parse("pichla result"))
		assertEquals(SelectResult(2), parse("result 2"))
		assertEquals(SelectResult(3), parse("play option 3"))
		assertEquals(SelectResult(2), parse("second result"))
		assertEquals(SelectResult(3), parse("teesra option"))
	}

	@Test
	fun unknownSpeechIsNotACommand() {
		assertNull(parse("the weather is nice"))
		assertNull(parse(""))
	}

	@Test
	fun firstUnderstoodAlternativeWins() {
		assertEquals(Pause, VoiceCommandParser.parse(listOf("blah blah", "pause", "next")))
		assertNull(VoiceCommandParser.parse(listOf("blah", "more blah")))
	}

	@Test
	fun matcherRanksExactAboveLooseMatches() {
		val exact = VoiceMatcher.score("believer", "Believer")
		val phrase = VoiceMatcher.score("believer", "The Believer Song")
		val words = VoiceMatcher.score("song believer", "Believer (Live Song)")
		assertTrue(exact > phrase && phrase > words && words >= 0)
		assertEquals(-1, VoiceMatcher.score("thunder", "Believer"))
	}

	@Test
	fun matcherHandlesNonLatinText() {
		assertTrue(VoiceMatcher.matches("तुम ही हो", "Tum Hi Ho तुम ही हो"))
	}
}
