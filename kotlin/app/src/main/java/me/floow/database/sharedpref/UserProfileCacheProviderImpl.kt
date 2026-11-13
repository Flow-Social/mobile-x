package me.floow.database.sharedpref

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.floow.domain.cache.UserProfileCacheProvider
import me.floow.domain.models.UserProfile

class UserProfileCacheProviderImpl(
	context: Context
) : UserProfileCacheProvider {
	
	companion object {
		private const val PREFS_NAME = "user_profile_cache"
		private const val KEY_USER_PROFILE = "user_profile_json"
	}
	
	private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
	
	override fun getUserProfile(): UserProfile {
		val json = sharedPreferences.getString(KEY_USER_PROFILE, null)
		return if (json != null) {
			try {
				Json.decodeFromString(json)
			} catch (e: Exception) {
				UserProfile()
			}
		} else {
			UserProfile()
		}
	}
	
	override fun updateUserProfile(profile: UserProfile) {
		val json = Json.encodeToString(profile)
		sharedPreferences.edit()
			.putString(KEY_USER_PROFILE, json)
			.apply()
	}
	
	override fun clearUserProfile() {
		sharedPreferences.edit()
			.remove(KEY_USER_PROFILE)
			.apply()
	}
}