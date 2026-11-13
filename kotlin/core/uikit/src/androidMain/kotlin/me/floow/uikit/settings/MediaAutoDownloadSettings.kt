package me.floow.uikit.settings

import android.content.Context
import android.content.SharedPreferences
import me.floow.domain.settings.AutoDownloadConfig
import me.floow.domain.settings.AutoDownloadPreset

object MediaAutoDownloadSettings {
    private const val PREFS_NAME = "flow_media_auto_download_prefs"
    private const val KEY_PRESET = "preset"
    private const val KEY_CELLULAR = "cellular"
    private const val KEY_WIFI = "wifi"
    private const val KEY_ROAMING = "roaming"

    @Volatile
    private var cachedConfig: AutoDownloadConfig? = null

    fun getConfig(context: Context): AutoDownloadConfig {
        cachedConfig?.let { return it }
        val prefs = getPrefs(context)
        val presetName = prefs.getString(KEY_PRESET, AutoDownloadPreset.MEDIUM.name)
        val preset = runCatching { AutoDownloadPreset.valueOf(presetName!!) }.getOrDefault(AutoDownloadPreset.MEDIUM)
        val config = when (preset) {
            AutoDownloadPreset.LOW -> AutoDownloadConfig(
                cellularEnabled = prefs.getBoolean(KEY_CELLULAR, false),
                wifiEnabled = prefs.getBoolean(KEY_WIFI, true),
                roamingEnabled = prefs.getBoolean(KEY_ROAMING, false),
                preset = preset,
                maxPhotoBytes = 2L * 1024L * 1024L,
                maxVideoBytes = 5L * 1024L * 1024L,
                maxVoiceBytes = 2L * 1024L * 1024L,
            )
            AutoDownloadPreset.MEDIUM -> AutoDownloadConfig(
                cellularEnabled = prefs.getBoolean(KEY_CELLULAR, true),
                wifiEnabled = prefs.getBoolean(KEY_WIFI, true),
                roamingEnabled = prefs.getBoolean(KEY_ROAMING, false),
                preset = preset,
                maxPhotoBytes = 10L * 1024L * 1024L,
                maxVideoBytes = 15L * 1024L * 1024L,
                maxVoiceBytes = 5L * 1024L * 1024L,
            )
            AutoDownloadPreset.HIGH -> AutoDownloadConfig(
                cellularEnabled = prefs.getBoolean(KEY_CELLULAR, true),
                wifiEnabled = prefs.getBoolean(KEY_WIFI, true),
                roamingEnabled = prefs.getBoolean(KEY_ROAMING, true),
                preset = preset,
                maxPhotoBytes = 50L * 1024L * 1024L,
                maxVideoBytes = 100L * 1024L * 1024L,
                maxVoiceBytes = 20L * 1024L * 1024L,
            )
            AutoDownloadPreset.DISABLED -> AutoDownloadConfig(
                cellularEnabled = false,
                wifiEnabled = false,
                roamingEnabled = false,
                preset = preset,
                maxPhotoBytes = 0L,
                maxVideoBytes = 0L,
                maxVoiceBytes = 0L,
            )
        }
        cachedConfig = config
        return config
    }

    fun setPreset(context: Context, preset: AutoDownloadPreset) {
        val prefs = getPrefs(context)
        prefs.edit().putString(KEY_PRESET, preset.name).apply()
        cachedConfig = null
    }

    fun setCellularEnabled(context: Context, enabled: Boolean) {
        val prefs = getPrefs(context)
        prefs.edit().putBoolean(KEY_CELLULAR, enabled).apply()
        cachedConfig = null
    }

    fun setWifiEnabled(context: Context, enabled: Boolean) {
        val prefs = getPrefs(context)
        prefs.edit().putBoolean(KEY_WIFI, enabled).apply()
        cachedConfig = null
    }

    fun setRoamingEnabled(context: Context, enabled: Boolean) {
        val prefs = getPrefs(context)
        prefs.edit().putBoolean(KEY_ROAMING, enabled).apply()
        cachedConfig = null
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
