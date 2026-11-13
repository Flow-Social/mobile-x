package me.floow.shared.presence

import me.floow.domain.data.repos.PresenceSessionStore
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID

class IosPresenceSessionStore : PresenceSessionStore {
    private companion object {
        private const val KEY_SESSION_ID = "presence_session_id"
    }

    override fun getOrCreateSessionId(): String {
        val defaults = NSUserDefaults.standardUserDefaults
        val existing = defaults.stringForKey(KEY_SESSION_ID)?.trim().orEmpty()
        if (existing.isNotEmpty()) return existing

        val generated = NSUUID().UUIDString
        defaults.setObject(generated, forKey = KEY_SESSION_ID)
        return generated
    }
}

actual fun createPresenceSessionStore(): PresenceSessionStore = IosPresenceSessionStore()
