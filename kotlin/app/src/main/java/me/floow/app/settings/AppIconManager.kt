package me.floow.app.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

enum class AppIcon(val aliasClass: String) {
    DEFAULT("me.floow.app.MainActivityDefault"),
    BLUE("me.floow.app.MainActivityDark"),
    BLACK("me.floow.app.MainActivityBlack"),
}

object AppIconManager {

    private const val PREF_NAME = "app_icon_prefs"
    private const val PREF_KEY = "active_icon"

    fun getActiveIcon(context: Context): AppIcon {
        val raw = context
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(PREF_KEY, AppIcon.DEFAULT.name) ?: AppIcon.DEFAULT.name
        return runCatching { AppIcon.valueOf(raw) }.getOrDefault(AppIcon.DEFAULT)
    }

    fun setAppIcon(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        AppIcon.entries.forEach { entry ->
            val component = ComponentName(context.packageName, entry.aliasClass)
            val state = if (entry == icon)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            try {
                pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            } catch (_: Exception) {}
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_KEY, icon.name).apply()
    }
}
