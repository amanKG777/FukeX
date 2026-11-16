package com.boostofstudios.fukex

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.content.ContextCompat
import com.boostofstudios.fukex.data.SettingsManager
import com.boostofstudios.fukex.data.VoiceSpeechEvent
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow

data class TtsEngineInfo(
    val packageName: String?,
    val label: String
)

fun getInstalledTtsEngines(context: Context): List<TtsEngineInfo> {
    val list = mutableListOf(TtsEngineInfo(null, "System Default"))
    try {
        val pm = context.packageManager
        val intent = Intent("android.intent.action.TTS_SERVICE")
        val services = pm.queryIntentServices(intent, PackageManager.MATCH_ALL)
        for (resolveInfo in services) {
            val pkg = resolveInfo.serviceInfo?.packageName ?: continue
            val label = resolveInfo.serviceInfo.loadLabel(pm)?.toString() ?: pkg
            list.add(TtsEngineInfo(pkg, label))
        }
    } catch (e: Exception) {}
    return list
}

// The voice assistant plays through PlaybackEngine directly; this only asks the UI
// to show the playlist it played from, and tells the assistant which locked playlists are open.
object VoicePlayRequests {
    val focusPlaylistId = MutableStateFlow<String?>(null)
    val openDocs = MutableStateFlow(false)
    @Volatile var unlockedPlaylistIds: Set<String> = emptySet()
}

fun playFeedbackBeep() {
    try {
        val tone = android.media.ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP)
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 300)
    } catch (e: Exception) {}
}

class VoiceAssistantManager(
    private val context: Context,
    private val onCommand: (VoiceCommand) -> Unit
) {
    companion object {
        // If the recognizer never reports back, don't leave the assistant stuck "listening"
        private const val LISTEN_WATCHDOG_MS = 20_000L
        private const val MAX_ALTERNATIVES = 5
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var retryAttempt = 0
    private var focusRequest: AudioFocusRequest? = null
    private val watchdog = Runnable {
        if (isListening) {
            speechRecognizer?.cancel()
            finishListening()
            if (SettingsManager.isVoiceSpeechEventEnabled(context, VoiceSpeechEvent.ERRORS)) {
                speak("I didn't hear anything.")
            } else {
                playFeedbackBeep()
            }
        }
    }

    init {
        initTts()
    }

    fun reloadTtsEngine() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        initTts()
    }

    private fun initTts() {
        val enginePackage = SettingsManager.getVoiceAssistantTtsEngine(context)
        val onInit = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                val res = tts?.setLanguage(Locale.getDefault())
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.US
                }
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                ttsReady = true
            }
        }
        tts = if (!enginePackage.isNullOrBlank()) {
            TextToSpeech(context, onInit, enginePackage)
        } else {
            TextToSpeech(context, onInit)
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        val engine = tts
        if (engine == null || !ttsReady) {
            // Nothing will ever call back, so carry on instead of hanging
            onDone?.let { mainHandler.post(it) }
            return
        }
        val utteranceId = java.util.UUID.randomUUID().toString()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { if (id == utteranceId) onDone?.let { mainHandler.post(it) } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { if (id == utteranceId) onDone?.let { mainHandler.post(it) } }
            override fun onStop(id: String?, interrupted: Boolean) { if (id == utteranceId) onDone?.let { mainHandler.post(it) } }
        })
        val params = Bundle().apply { putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId) }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun startListening() {
        // The activity and the background service can both detect the same shake
        if (isListening) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            speak("I need microphone permission. Please allow it in the app settings.")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            speak("Speech recognition isn't available on this device.")
            return
        }
        isListening = true
        retryAttempt = 0
        mainHandler.postDelayed(watchdog, LISTEN_WATCHDOG_MS)
        // Quiet the music so it doesn't drown out the user, and so the prompt is heard
        requestDuckFocus()
        val speakListening = SettingsManager.isVoiceSpeechEventEnabled(context, VoiceSpeechEvent.LISTENING)
        if (speakListening) {
            speak("Listening") {
                if (isListening) {
                    // Short 120ms pause after TTS completes to ensure audio track release before mic opens
                    mainHandler.postDelayed({ if (isListening) listenInternal() }, 120)
                }
            }
        } else {
            playFeedbackBeep()
            mainHandler.postDelayed({ if (isListening) listenInternal() }, 120)
        }
    }

    private fun obtainRecognizer(): SpeechRecognizer {
        speechRecognizer?.let { return it }
        val recognizer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            try {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } catch (e: Exception) {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        speechRecognizer = recognizer
        return recognizer
    }

    private fun listenInternal() {
        val recognizer = obtainRecognizer()
        try {
            recognizer.cancel()
        } catch (e: Exception) {}

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // EXTRA_LANGUAGE expects a BCP-47 tag string; a Locale object is silently ignored
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_ALTERNATIVES)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { playFeedbackBeep() }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                // Auto-retry once on busy or cold-start server/client glitches
                if (retryAttempt == 0 && (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                        error == SpeechRecognizer.ERROR_SERVER ||
                        error == SpeechRecognizer.ERROR_CLIENT)) {
                    retryAttempt++
                    try { speechRecognizer?.cancel() } catch (e: Exception) {}
                    try { speechRecognizer?.destroy() } catch (e: Exception) {}
                    speechRecognizer = null
                    mainHandler.postDelayed({
                        if (isListening) {
                            listenInternal()
                        }
                    }, 300)
                    return
                }

                finishListening()
                if (SettingsManager.isVoiceSpeechEventEnabled(context, VoiceSpeechEvent.ERRORS)) {
                    speak(
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "Sorry, I didn't catch that."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't hear anything."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                                "Speech recognition needs an internet connection."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "I need microphone permission."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The speech service is busy. Try again in a moment."
                            else -> "Something went wrong with speech recognition."
                        }
                    )
                } else {
                    playFeedbackBeep()
                }
            }
            override fun onResults(results: Bundle?) {
                finishListening()
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                if (heard.isEmpty()) {
                    if (SettingsManager.isVoiceSpeechEventEnabled(context, VoiceSpeechEvent.ERRORS)) {
                        speak("Sorry, I didn't catch that.")
                    } else {
                        playFeedbackBeep()
                    }
                    return
                }
                val command = VoiceCommandParser.parse(heard)
                if (command == null) {
                    if (SettingsManager.isVoiceSpeechEventEnabled(context, VoiceSpeechEvent.ERRORS)) {
                        speak("Sorry, I didn't understand \"${heard.first()}\". Say help for what I can do.")
                    } else {
                        playFeedbackBeep()
                    }
                } else {
                    onCommand(command)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            finishListening()
            if (SettingsManager.isVoiceSpeechEventEnabled(context, VoiceSpeechEvent.ERRORS)) {
                speak("Could not start speech recognition.")
            } else {
                playFeedbackBeep()
            }
        }
    }

    private fun finishListening() {
        isListening = false
        mainHandler.removeCallbacks(watchdog)
        releaseDuckFocus()
    }

    private fun requestDuckFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener {}
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun releaseDuckFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    fun destroy() {
        mainHandler.removeCallbacks(watchdog)
        releaseDuckFocus()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        tts = null
        speechRecognizer = null
    }
}
