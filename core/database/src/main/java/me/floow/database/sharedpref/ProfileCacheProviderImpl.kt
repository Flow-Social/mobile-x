package me.floow.database.sharedpref

import android.content.Context
import me.floow.domain.cache.ProfileCacheProvider
import me.floow.domain.models.SelfProfile
import me.floow.domain.values.ProfileDescription
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.RawValueObjectCreate

class ProfileCacheProviderImpl(
	context: Context
) : ProfileCacheProvider {
	companion object {
		private const val PROFILE_CACHE_NAME = "profile_cache"
		private const val PROFILE_ID = "profile_id"
		private const val PROFILE_NAME = "profile_name"
		private const val PROFILE_USERNAME = "profile_username"
		private const val PROFILE_BIO = "profile_bio"
		private const val PROFILE_AVATAR_URL = "profile_avatar_url"
		private const val PROFILE_BACKGROUND_URL = "profile_background_url"
		private const val PROFILE_BACKGROUND_UPDATED_AT = "profile_background_updated_at"
		private const val PROFILE_EMAIL = "profile_email"
	}

	private val sharedPreferences =
		context.getSharedPreferences(PROFILE_CACHE_NAME, Context.MODE_PRIVATE)

	@OptIn(RawValueObjectCreate::class)
	override fun getSelfProfile(): SelfProfile {
		val name = sharedPreferences.getString(PROFILE_NAME, null)
		val username = sharedPreferences.getString(PROFILE_USERNAME, null)
		val bio = sharedPreferences.getString(PROFILE_BIO, null)
		val avatarUrl = sharedPreferences.getString(PROFILE_AVATAR_URL, null)
		val backgroundUrl = sharedPreferences.getString(PROFILE_BACKGROUND_URL, null)
		val backgroundUpdatedAt = sharedPreferences.getLong(PROFILE_BACKGROUND_UPDATED_AT, 0L)

		return SelfProfile(
			name = name?.let { ProfileName.createRaw(it) },
			avatarUrl = avatarUrl,
			backgroundUrl = backgroundUrl,
			backgroundUpdatedAt = backgroundUpdatedAt.takeIf { it > 0L },
			username = username?.let { ProfileUsername.createRaw(it) },
			description = bio?.let { ProfileDescription.createRaw(it) }
		)
	}

	override fun updateSelfProfile(profile: SelfProfile) {
		sharedPreferences.edit().apply {
			putString(PROFILE_NAME, profile.name?.value)
			putString(PROFILE_USERNAME, profile.username?.value)
			putString(PROFILE_BIO, profile.description?.value)
			putString(PROFILE_AVATAR_URL, profile.avatarUrl)
			putString(PROFILE_BACKGROUND_URL, profile.backgroundUrl)
			putLong(PROFILE_BACKGROUND_UPDATED_AT, profile.backgroundUpdatedAt ?: 0L)

			apply()
		}
	}

	override fun clearSelfProfile() {
		updateSelfProfile(
			SelfProfile(
				name = null,
				description = null,
				avatarUrl = null,
				backgroundUrl = null,
				backgroundUpdatedAt = null,
				username = null
			)
		)
	}
}
