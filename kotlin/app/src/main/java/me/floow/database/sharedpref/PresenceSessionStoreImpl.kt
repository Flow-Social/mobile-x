package me.floow.database.sharedpref

import android.content.Context
import me.floow.domain.data.repos.PresenceSessionStore
import java.util.UUID

class PresenceSessionStoreImpl(
	private val context: Context
) : PresenceSessionStore {
	private companion object {
		private const val PREFS_NAME = "presence_session_store"
		private const val KEY_SESSION_ID = "presence_session_id"
	}

	override fun getOrCreateSessionId(): String {
		val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		val existing = prefs.getString(KEY_SESSION_ID, null)?.trim().orEmpty()
		if (existing.isNotEmpty()) return existing
		val generated = UUID.randomUUID().toString()
		prefs.edit().putString(KEY_SESSION_ID, generated).apply()
		return generated
	}
}
