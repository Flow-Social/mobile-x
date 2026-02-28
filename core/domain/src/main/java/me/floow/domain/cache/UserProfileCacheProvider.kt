package me.floow.domain.cache

import me.floow.domain.models.UserProfile

interface UserProfileCacheProvider {
	fun getUserProfile(): UserProfile
	fun updateUserProfile(profile: UserProfile)
	fun clearUserProfile()
}