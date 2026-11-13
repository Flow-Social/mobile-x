package me.floow.shared.profile.ui.settings

import android.content.Context
import org.koin.core.context.GlobalContext

actual object PlatformThemeBridge {
    private const val PREFS_NAME = "flow_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    actual fun getTheme(): AppThemeMode {
        val context = GlobalContext.getOrNull()?.getOrNull<Context>() ?: return AppThemeMode.SYSTEM
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return when (prefs.getString(KEY_THEME_MODE, null)) {
            "LIGHT" -> AppThemeMode.LIGHT
            "DARK" -> AppThemeMode.DARK
            else -> AppThemeMode.SYSTEM
        }
    }

    actual fun setTheme(mode: AppThemeMode) {
        val context = GlobalContext.getOrNull()?.getOrNull<Context>() ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }
}
