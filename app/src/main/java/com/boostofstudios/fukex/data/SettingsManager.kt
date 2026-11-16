package com.boostofstudios.fukex.data
import android.content.Context
import android.content.SharedPreferences
enum class LockTimeout(val minutes: Int, val label: String) {
    IMMEDIATE(0, "Immediate"),
    ONE_MINUTE(1, "1 Minute"),
    TWO_MINUTES(2, "2 Minutes"),
    FIVE_MINUTES(5, "5 Minutes"),
    SCREEN_LOCK(-1, "When Screen Locks")
}

enum class VoiceSpeechEvent(
    val key: String,
    val title: String,
    val speechDescription: String
) {
    LISTENING(
        "voice_speak_listening",
        "When listening",
        "Speak 'Listening' prompt (uncheck to beep only)"
    ),
    SEARCHING(
        "voice_speak_searching",
        "When searching for a video or song",
        "Speak 'Searching YouTube for...' (uncheck to beep only)"
    ),
    PLAYING(
        "voice_speak_playing",
        "When playing a video or song",
        "Announce title when playback starts (uncheck to beep only)"
    ),
    RESULTS(
        "voice_speak_results",
        "When switching results or versions",
        "Announce result number and title (uncheck to beep only)"
    ),
    CONTROLS(
        "voice_speak_controls",
        "When adjusting controls",
        "Confirm seek, volume, and amplifier (uncheck to beep only)"
    ),
    ERRORS(
        "voice_speak_errors",
        "When errors or unrecognised speech occur",
        "Speak error messages (uncheck to beep only)"
    )
}

object SettingsManager {
    private const val PREFS_NAME = "fukex_settings"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_BACKGROUND_PLAYBACK = "background_playback"
    private const val KEY_VOICE_ASSISTANT_ENABLED = "voice_assistant_enabled"
    private const val KEY_VOICE_ASSISTANT_BACKGROUND = "voice_assistant_background"
    private const val KEY_VOICE_ASSISTANT_TTS_ENGINE = "voice_assistant_tts_engine"
    private const val KEY_LOCK_TIMEOUT = "lock_timeout"
    private const val KEY_AMPLIFIER_LEVEL = "amplifier_level"
    private const val KEY_AMPLIFIER_ENABLED = "amplifier_enabled"
    private const val KEY_EXIT_PROMPT = "exit_prompt"
    private const val KEY_FADE_IN_SEEK = "fade_in_seek"
    private const val KEY_FADE_OUT_SEEK = "fade_out_seek"
    private const val KEY_FADE_IN_PAUSE = "fade_in_pause"
    private const val KEY_FADE_OUT_PAUSE = "fade_out_pause"
    private const val KEY_FADE_IN_MANUAL = "fade_in_manual"
    private const val KEY_FADE_OUT_MANUAL = "fade_out_manual"
    private const val KEY_FADE_IN_AUTO = "fade_in_auto"
    private const val KEY_FADE_OUT_AUTO = "fade_out_auto"
    private const val KEY_SKIP_SILENCE = "skip_silence"
    private const val KEY_SKIP_UNAVAILABLE = "skip_unavailable"
    private const val KEY_PLAYBACK_SPEED = "playback_speed"
    private const val KEY_PLAYBACK_PITCH = "playback_pitch"
    private const val KEY_BASS_BOOST = "bass_boost"
    private const val KEY_REPLAY_GAIN_ENABLED = "replay_gain_enabled"

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun registerChangeListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        getPrefs(context).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterChangeListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        getPrefs(context).unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun isExitPromptEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_EXIT_PROMPT, false)
    }

    fun setExitPromptEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_EXIT_PROMPT, enabled).apply()
    }

    fun isSkipSilenceEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SKIP_SILENCE, false)
    }

    fun setSkipSilenceEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SKIP_SILENCE, enabled).apply()
    }

    fun isSkipUnavailableTracksEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SKIP_UNAVAILABLE, false)
    }

    fun setSkipUnavailableTracksEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SKIP_UNAVAILABLE, enabled).apply()
    }

    fun getAmplifierLevel(context: Context): Int {
        return getPrefs(context).getInt(KEY_AMPLIFIER_LEVEL, 0)
    }

    fun setAmplifierLevel(context: Context, level: Int) {
        getPrefs(context).edit().putInt(KEY_AMPLIFIER_LEVEL, level).apply()
    }

    fun isAmplifierEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AMPLIFIER_ENABLED, false)
    }

    fun setAmplifierEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AMPLIFIER_ENABLED, enabled).apply()
    }

    fun isBiometricEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isBackgroundPlaybackEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BACKGROUND_PLAYBACK, true)
    }

    fun setBackgroundPlaybackEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BACKGROUND_PLAYBACK, enabled).apply()
    }

    fun getLockTimeout(context: Context): LockTimeout {
        val name = getPrefs(context).getString(KEY_LOCK_TIMEOUT, LockTimeout.IMMEDIATE.name)
        return try { LockTimeout.valueOf(name!!) } catch (e: Exception) { LockTimeout.IMMEDIATE }
    }

    fun setLockTimeout(context: Context, timeout: LockTimeout) {
        getPrefs(context).edit().putString(KEY_LOCK_TIMEOUT, timeout.name).apply()
    }

    fun getFadeInSeek(context: Context): Int = getPrefs(context).getInt(KEY_FADE_IN_SEEK, 0)

    fun setFadeInSeek(context: Context, ms: Int) = getPrefs(context).edit().putInt(KEY_FADE_IN_SEEK, ms).apply()

    fun getFadeOutSeek(context: Context): Int = getPrefs(context).getInt(KEY_FADE_OUT_SEEK, 0)

    fun setFadeOutSeek(context: Context, ms: Int) = getPrefs(context).edit().putInt(KEY_FADE_OUT_SEEK, ms).apply()

    fun getFadeInPause(context: Context): Int = getPrefs(context).getInt(KEY_FADE_IN_PAUSE, 0)

    fun setFadeInPause(context: Context, ms: Int) = getPrefs(context).edit().putInt(KEY_FADE_IN_PAUSE, ms).apply()

    fun getFadeOutPause(context: Context): Int = getPrefs(context).getInt(KEY_FADE_OUT_PAUSE, 0)

    fun setFadeOutPause(context: Context, ms: Int) = getPrefs(context).edit().putInt(KEY_FADE_OUT_PAUSE, ms).apply()

    fun getFadeInManual(context: Context): Int = getPrefs(context).getInt(KEY_FADE_IN_MANUAL, 0)

    fun setFadeInManual(context: Context, ms: Int) = getPrefs(context).edit().putInt(KEY_FADE_IN_MANUAL, ms).apply()

    fun getFadeOutManual(context: Context): Int = getPrefs(context).getInt(KEY_FADE_OUT_MANUAL, 0)

    fun setFadeOutManual(context: Context, ms: Int) = getPrefs(context).edit().putInt(KEY_FADE_OUT_MANUAL, ms).apply()

    fun getFadeInAuto(context: Context): Int = getPrefs(context).getInt(KEY_FADE_IN_AUTO, 0)

    fun setFadeInAuto(context: Context, ms: Int) = getPrefs(context).edit().putInt(KEY_FADE_IN_AUTO, ms).apply()

    fun getFadeOutAuto(context: Context): Int = getPrefs(context).getInt(KEY_FADE_OUT_AUTO, 0)

    fun setFadeOutAuto(context: Context, ms: Int) = getPrefs(context).edit().putInt(KEY_FADE_OUT_AUTO, ms).apply()

    fun getPlaybackSpeed(context: Context): Float = getPrefs(context).getFloat(KEY_PLAYBACK_SPEED, 1.0f)

    fun setPlaybackSpeed(context: Context, speed: Float) = getPrefs(context).edit().putFloat(KEY_PLAYBACK_SPEED, speed).apply()

    fun getPlaybackPitch(context: Context): Float = getPrefs(context).getFloat(KEY_PLAYBACK_PITCH, 1.0f)

    fun setPlaybackPitch(context: Context, pitch: Float) = getPrefs(context).edit().putFloat(KEY_PLAYBACK_PITCH, pitch).apply()

    fun getBassBoost(context: Context): Int = getPrefs(context).getInt(KEY_BASS_BOOST, 0)

    fun setBassBoost(context: Context, level: Int) = getPrefs(context).edit().putInt(KEY_BASS_BOOST, level).apply()

    fun isReplayGainEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_REPLAY_GAIN_ENABLED, false)
    }

    fun setReplayGainEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_REPLAY_GAIN_ENABLED, enabled).apply()
    }

    fun isVoiceAssistantEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VOICE_ASSISTANT_ENABLED, false)
    }

    fun setVoiceAssistantEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VOICE_ASSISTANT_ENABLED, enabled).apply()
    }

    fun isVoiceAssistantBackgroundEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_VOICE_ASSISTANT_BACKGROUND, false)
    }

    fun setVoiceAssistantBackgroundEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_VOICE_ASSISTANT_BACKGROUND, enabled).apply()
    }

    // Holding Volume Down is the main trigger; shaking is an optional extra
    fun isVoiceShakeEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_VOICE_SHAKE_ENABLED, false)

    fun setVoiceShakeEnabled(context: Context, enabled: Boolean) =
        getPrefs(context).edit().putBoolean(KEY_VOICE_SHAKE_ENABLED, enabled).apply()

    // 1 (needs a hard shake) .. 5 (a light shake is enough)
    fun getVoiceShakeSensitivity(context: Context): Int =
        getPrefs(context).getInt(KEY_VOICE_SHAKE_SENSITIVITY, DEFAULT_SHAKE_SENSITIVITY)
            .coerceIn(MIN_SHAKE_SENSITIVITY, MAX_SHAKE_SENSITIVITY)

    fun setVoiceShakeSensitivity(context: Context, level: Int) =
        getPrefs(context).edit()
            .putInt(KEY_VOICE_SHAKE_SENSITIVITY, level.coerceIn(MIN_SHAKE_SENSITIVITY, MAX_SHAKE_SENSITIVITY))
            .apply()

    // Acceleration (in g) a shake has to exceed at each sensitivity level
    fun shakeThresholdFor(level: Int): Float = when (level.coerceIn(MIN_SHAKE_SENSITIVITY, MAX_SHAKE_SENSITIVITY)) {
        1 -> 2.6f
        2 -> 2.1f
        3 -> 1.75f
        4 -> 1.5f
        else -> 1.3f
    }

    fun shakeSensitivityLabel(level: Int): String = when (level.coerceIn(MIN_SHAKE_SENSITIVITY, MAX_SHAKE_SENSITIVITY)) {
        1 -> "Very low"
        2 -> "Low"
        3 -> "Medium"
        4 -> "High"
        else -> "Very high"
    }

    const val KEY_VOICE_SHAKE_ENABLED = "voice_assistant_shake"
    const val KEY_VOICE_SHAKE_SENSITIVITY = "voice_assistant_shake_sensitivity"
    const val MIN_SHAKE_SENSITIVITY = 1
    const val MAX_SHAKE_SENSITIVITY = 5
    private const val DEFAULT_SHAKE_SENSITIVITY = 3

    fun getVoiceAssistantTtsEngine(context: Context): String? {
        return getPrefs(context).getString(KEY_VOICE_ASSISTANT_TTS_ENGINE, null)
    }

    fun setVoiceAssistantTtsEngine(context: Context, enginePackage: String?) {
        getPrefs(context).edit().putString(KEY_VOICE_ASSISTANT_TTS_ENGINE, enginePackage).apply()
    }

    fun isVoiceSpeechEventEnabled(context: Context, event: VoiceSpeechEvent): Boolean {
        return getPrefs(context).getBoolean(event.key, true)
    }

    fun setVoiceSpeechEventEnabled(context: Context, event: VoiceSpeechEvent, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(event.key, enabled).apply()
    }

    // Off by default: TalkBack only reads lyric lines when the user opts in
    fun isLyricsAnnounceEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_LYRICS_ANNOUNCE, false)

    fun setLyricsAnnounceEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_LYRICS_ANNOUNCE, enabled).apply()

    fun isLyricsOnlineEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_LYRICS_ONLINE, true)

    fun setLyricsOnlineEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_LYRICS_ONLINE, enabled).apply()

    fun getLastPlaylistId(context: Context): String? = getPrefs(context).getString(KEY_LAST_PLAYLIST_ID, null)

    fun setLastPlaylistId(context: Context, id: String?) = getPrefs(context).edit().putString(KEY_LAST_PLAYLIST_ID, id).apply()

    private const val KEY_LYRICS_ANNOUNCE = "lyrics_announce"
    private const val KEY_LYRICS_ONLINE = "lyrics_online"
    private const val KEY_LAST_PLAYLIST_ID = "last_playlist_id"

    fun isIconPackEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_ICON_PACK_ENABLED, true)

    fun setIconPackEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_ICON_PACK_ENABLED, enabled).apply()

    // Whether the one-time "download the visual icons pack?" question has been answered
    fun isIconPackPromptDone(context: Context): Boolean = getPrefs(context).getBoolean(KEY_ICON_PACK_PROMPT_DONE, false)

    fun setIconPackPromptDone(context: Context, done: Boolean) = getPrefs(context).edit().putBoolean(KEY_ICON_PACK_PROMPT_DONE, done).apply()

    private const val KEY_ICON_PACK_ENABLED = "icon_pack_enabled"
    private const val KEY_ICON_PACK_PROMPT_DONE = "icon_pack_prompt_done"
}
