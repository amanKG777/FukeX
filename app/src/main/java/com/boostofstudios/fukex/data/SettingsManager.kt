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

    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun registerChangeListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        getPrefs(context).registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterChangeListener(context: Context, listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        getPrefs(context).unregisterOnSharedPreferenceChangeListener(listener)
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
}
