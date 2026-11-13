package me.floow.app.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.floow.database.AppDatabase
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.cache.ProfileCacheProvider
import me.floow.domain.cache.UserProfileCacheProvider

class LogoutUseCase(
    private val authenticationManager: AuthenticationManager,
    private val database: AppDatabase,
    private val profileCacheProvider: ProfileCacheProvider,
    private val userProfileCacheProvider: UserProfileCacheProvider,
) {
    suspend operator fun invoke(clearLocalCache: Boolean = true) {
        if (clearLocalCache) {
            withContext(Dispatchers.IO) {
                database.clearAllTables()
            }
        }
        profileCacheProvider.clearSelfProfile()
        userProfileCacheProvider.clearUserProfile()
        authenticationManager.clearAuth()
    }
}
