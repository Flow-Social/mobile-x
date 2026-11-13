package me.floow.shared.profile.ui.settings

import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.floow.domain.settings.AutoDownloadConfig
import me.floow.domain.settings.AutoDownloadPreset

internal val LogoutRed = Color(0xFFD44848)
internal val LogoutBg = Color(0x29D44848)

enum class AppThemeMode { SYSTEM, LIGHT, DARK }
enum class AppIconMode { DEFAULT, BLUE, BLACK }

object CommonMediaAutoDownloadSettings {
    private val _config = MutableStateFlow(AutoDownloadConfig())
    val config: StateFlow<AutoDownloadConfig> = _config.asStateFlow()

    fun setPreset(preset: AutoDownloadPreset) {
        val current = _config.value
        val updated = when (preset) {
            AutoDownloadPreset.LOW -> current.copy(
                cellularEnabled = false,
                wifiEnabled = true,
                roamingEnabled = false,
                preset = preset,
                maxPhotoBytes = 2L * 1024L * 1024L,
                maxVideoBytes = 5L * 1024L * 1024L,
                maxVoiceBytes = 2L * 1024L * 1024L,
            )
            AutoDownloadPreset.MEDIUM -> current.copy(
                cellularEnabled = true,
                wifiEnabled = true,
                roamingEnabled = false,
                preset = preset,
                maxPhotoBytes = 10L * 1024L * 1024L,
                maxVideoBytes = 15L * 1024L * 1024L,
                maxVoiceBytes = 5L * 1024L * 1024L,
            )
            AutoDownloadPreset.HIGH -> current.copy(
                cellularEnabled = true,
                wifiEnabled = true,
                roamingEnabled = true,
                preset = preset,
                maxPhotoBytes = 50L * 1024L * 1024L,
                maxVideoBytes = 100L * 1024L * 1024L,
                maxVoiceBytes = 20L * 1024L * 1024L,
            )
            AutoDownloadPreset.DISABLED -> current.copy(
                cellularEnabled = false,
                wifiEnabled = false,
                roamingEnabled = false,
                preset = preset,
                maxPhotoBytes = 0L,
                maxVideoBytes = 0L,
                maxVoiceBytes = 0L,
            )
        }
        _config.value = updated
    }

    fun setCellularEnabled(enabled: Boolean) {
        _config.value = _config.value.copy(cellularEnabled = enabled)
    }

    fun setWifiEnabled(enabled: Boolean) {
        _config.value = _config.value.copy(wifiEnabled = enabled)
    }

    fun setRoamingEnabled(enabled: Boolean) {
        _config.value = _config.value.copy(roamingEnabled = enabled)
    }
}

expect object PlatformMediaCacheManager {
    suspend fun getCacheSizeBytes(): Long
    suspend fun clearCache()
}

expect object PlatformThemeBridge {
    fun getTheme(): AppThemeMode
    fun setTheme(mode: AppThemeMode)
}

