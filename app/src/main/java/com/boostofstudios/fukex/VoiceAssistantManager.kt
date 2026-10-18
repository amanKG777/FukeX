package com.boostofstudios.fukex

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow

// The voice assistant plays through PlaybackEngine directly; this only asks the UI
// to show the playlist it played from, and tells the assistant which locked playlists are open.
object VoicePlayRequests {
    val focusPlaylistId = MutableStateFlow<String?>(null)
    val openDocs = MutableStateFlow(false)
    @Volatile var unlockedPlaylistIds: Set<String> = emptySet()
}

fun playFeedbackBeep() {
    try {
        val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100)
        tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, 300)
    } catch (e: Exception) {}
}

class VoiceAssistantManager(
    private val context: Context,
    private val onCommand: (String, String?) -> Unit
) {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    // Lightweight Dictionary Mapping for Multiple Languages
    private val playKeywords = listOf("play", "chalao", "bajao", "reproducir", "pon")
    private val pauseKeywords = listOf("pause", "stop", "ruk", "roko", "pausa")
    private val resumeKeywords = listOf("resume", "continue", "wapas chalao", "reanudar")
    private val nextKeywords = listOf("next", "skip", "agla", "siguiente")
    private val prevKeywords = listOf("previous", "back", "pichla", "anterior")
    private val ampKeywords = listOf("amplifier", "amp", "boost", "awaz badhao")
    private val docKeywords = listOf("documentation", "help", "guide", "docs")
    
    private val onKeywords = listOf("on", "enable", "chalu", "encender")
    private val offKeywords = listOf("off", "disable", "band", "apagar")
    private val increaseKeywords = listOf("increase", "up", "badao", "subir")
    private val decreaseKeywords = listOf("decrease", "down", "ghatao", "bajar")

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val loc = Locale.getDefault()
                val res = tts?.setLanguage(loc)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.US
                }
            }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (onDone != null) {
            val utteranceId = java.util.UUID.randomUUID().toString()
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId) {
                        onDone()
                    }
                }
                override fun onError(utteranceId: String?) {
                    onDone()
                }
            })
            val params = Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } else {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun startListening() {
        // The activity and the background service can both detect the same shake
        if (isListening) return
        isListening = true
        speak("Listening...") {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                listenInternal()
            }
        }
    }

    private fun listenInternal() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                isListening = false
                playBeep()
                speak("Sorry, I didn't catch that.")
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                playBeep()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    parseCommand(matches[0])
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    private fun wordRegex(keyword: String) = Regex("(^|\\s)${Regex.escape(keyword)}(\\s|$)")

    private fun List<String>.matchIn(text: String): String? =
        firstOrNull { wordRegex(it).containsMatchIn(text) }

    private fun List<String>.foundIn(text: String) = matchIn(text) != null

    private fun parseCommand(text: String) {
        val lowerText = text.lowercase(Locale.getDefault()).trim()

        // "play <song>" / "<song> bajao" comes first, so song names containing
        // words like "stop", "back" or "help" don't trigger other commands.
        val playMatch = playKeywords.matchIn(lowerText)
        if (playMatch != null && !resumeKeywords.foundIn(lowerText) && (lowerText.startsWith("$playMatch ") || lowerText.endsWith(" $playMatch"))) {
            val query = lowerText.removePrefix(playMatch).removeSuffix(playMatch).trim()
            if (query.isNotEmpty() && !nextKeywords.foundIn(query) && !prevKeywords.foundIn(query)) {
                onCommand("play", query)
                return
            }
        }

        val isAmp = ampKeywords.foundIn(lowerText)
        if (isAmp) {
            if (increaseKeywords.foundIn(lowerText)) {
                onCommand("amplifier_increase", null)
                return
            } else if (decreaseKeywords.foundIn(lowerText)) {
                onCommand("amplifier_decrease", null)
                return
            } else if (onKeywords.foundIn(lowerText)) {
                onCommand("amplifier_on", null)
                return
            } else if (offKeywords.foundIn(lowerText)) {
                onCommand("amplifier_off", null)
                return
            }
        }

        if (docKeywords.foundIn(lowerText)) {
            onCommand("open_docs", null)
            return
        }

        // Checked before pause/play so "wapas chalao" isn't read as "chalao"
        if (resumeKeywords.foundIn(lowerText)) {
            onCommand("resume", null)
            return
        }

        if (pauseKeywords.foundIn(lowerText)) {
            onCommand("pause", null)
            return
        }

        if (nextKeywords.foundIn(lowerText)) {
            onCommand("next", null)
            return
        }

        if (prevKeywords.foundIn(lowerText)) {
            onCommand("previous", null)
            return
        }

        if (playMatch != null) {
            val query = lowerText.replace(wordRegex(playMatch), " ").trim()
            onCommand("play", query.ifEmpty { null })
            return
        }

        speak("Sorry, I didn't understand that.")
    }

    private fun playBeep() = playFeedbackBeep()

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
