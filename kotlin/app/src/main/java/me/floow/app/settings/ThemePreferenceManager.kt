package me.floow.app.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromString(value: String?): ThemeMode = when (value) {
            "LIGHT" -> LIGHT
            "DARK" -> DARK
            else -> SYSTEM
        }
    }
}

object ThemePreferenceManager {
    private const val PREFS_NAME = "flow_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"

    private var prefs: SharedPreferences? = null
    private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun init(context: Context) {
        val appContext = context.applicationContext
        val nextPrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs === nextPrefs) {
            _themeMode.value = nextPrefs.readThemeMode()
            return
        }

        prefs?.let { currentPrefs ->
            prefsListener?.let(currentPrefs::unregisterOnSharedPreferenceChangeListener)
        }

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, key ->
            if (key == KEY_THEME_MODE) {
                _themeMode.value = changedPrefs.readThemeMode()
            }
        }
        nextPrefs.registerOnSharedPreferenceChangeListener(listener)
        prefs = nextPrefs
        prefsListener = listener
        _themeMode.value = nextPrefs.readThemeMode()
    }

    fun setTheme(mode: ThemeMode) {
        val currentPrefs = checkNotNull(prefs) {
            "ThemePreferenceManager.init(context) must be called before setTheme(mode)."
        }
        currentPrefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    private fun SharedPreferences.readThemeMode(): ThemeMode =
        ThemeMode.fromString(getString(KEY_THEME_MODE, null))
}
