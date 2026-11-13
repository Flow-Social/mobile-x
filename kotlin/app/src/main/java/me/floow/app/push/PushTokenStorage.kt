package me.floow.app.push

import android.content.Context

object PushTokenStorage {
	private const val PREFS_NAME = "push_token_storage"
	private const val KEY_FCM_TOKEN = "fcm_token"

	fun save(context: Context, token: String) {
		val trimmed = token.trim()
		if (trimmed.isEmpty()) return
		context.applicationContext
			.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit()
			.putString(KEY_FCM_TOKEN, trimmed)
			.apply()
	}

	fun get(context: Context): String? {
		return context.applicationContext
			.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getString(KEY_FCM_TOKEN, null)
			?.trim()
			?.takeIf(String::isNotEmpty)
	}
}
