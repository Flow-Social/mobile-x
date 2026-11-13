package me.floow.shared.profile.ui.settings

import platform.Foundation.NSUserDefaults

actual object PlatformThemeBridge {
    private const val KEY_THEME_MODE = "flow_theme_mode"

    actual fun getTheme(): AppThemeMode {
        val value = NSUserDefaults.standardUserDefaults.stringForKey(KEY_THEME_MODE)
        return when (value) {
            "LIGHT" -> AppThemeMode.LIGHT
            "DARK" -> AppThemeMode.DARK
            else -> AppThemeMode.SYSTEM
        }
    }

    actual fun setTheme(mode: AppThemeMode) {
        NSUserDefaults.standardUserDefaults.setObject(mode.name, forKey = KEY_THEME_MODE)
    }
}
