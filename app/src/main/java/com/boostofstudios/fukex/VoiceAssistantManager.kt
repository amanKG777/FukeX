package com.boostofstudios.fukex

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceAssistantManager(
    private val context: Context,
    private val onCommand: (String, String?) -> Unit
) {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

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
                playBeep()
                speak("Sorry, I didn't catch that.")
            }
            override fun onResults(results: Bundle?) {
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

    private fun parseCommand(text: String) {
        val lowerText = text.lowercase(Locale.getDefault())

        val isAmp = ampKeywords.any { lowerText.contains(it) }
        if (isAmp) {
            if (increaseKeywords.any { lowerText.contains(it) }) {
                onCommand("amplifier_increase", null)
                return
            } else if (decreaseKeywords.any { lowerText.contains(it) }) {
                onCommand("amplifier_decrease", null)
                return
            } else if (onKeywords.any { lowerText.contains(it) }) {
                onCommand("amplifier_on", null)
                return
            } else if (offKeywords.any { lowerText.contains(it) }) {
                onCommand("amplifier_off", null)
                return
            }
        }

        if (docKeywords.any { lowerText.contains(it) }) {
            onCommand("open_docs", null)
            return
        }

        if (pauseKeywords.any { lowerText.contains(it) }) {
            onCommand("pause", null)
            return
        }

        if (resumeKeywords.any { lowerText.contains(it) }) {
            onCommand("resume", null)
            return
        }

        if (nextKeywords.any { lowerText.contains(it) }) {
            onCommand("next", null)
            return
        }

        if (prevKeywords.any { lowerText.contains(it) }) {
            onCommand("previous", null)
            return
        }

        val playMatch = playKeywords.find { lowerText.contains(it) }
        if (playMatch != null) {
            val query = lowerText.replace(playMatch, "").trim()
            onCommand("play", query.ifEmpty { null })
            return
        }
    }

    private fun playBeep() {
        android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100).startTone(android.media.ToneGenerator.TONE_PROP_BEEP)
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}
