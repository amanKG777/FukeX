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

object SettingsManager {
    private const val PREFS_NAME = "fukex_settings"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_BACKGROUND_PLAYBACK = "background_playback"
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
}
