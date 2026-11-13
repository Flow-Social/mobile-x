package me.floow.app.di

import me.floow.database.AndroidDatabaseContext
import me.floow.database.AppDatabase
import me.floow.database.createRoomDatabase
import me.floow.database.di.databaseModule as sharedDatabaseModule
import me.floow.database.sharedpref.ProfileCacheProviderImpl
import me.floow.database.sharedpref.PresenceSessionStoreImpl
import me.floow.database.sharedpref.UsernameToIdCacheImpl
import me.floow.database.sharedpref.UserProfileCacheProviderImpl
import me.floow.domain.cache.ProfileCacheProvider
import me.floow.domain.cache.UserProfileCacheProvider
import me.floow.domain.cache.UsernameToIdCache
import me.floow.domain.data.repos.PresenceSessionStore
import org.koin.dsl.module

val databaseModule = module {
    includes(sharedDatabaseModule)

    single<ProfileCacheProvider> { ProfileCacheProviderImpl(get()) }
    single<UserProfileCacheProvider> { UserProfileCacheProviderImpl(get()) }
    single<UsernameToIdCache> { UsernameToIdCacheImpl(get()) }
    single<PresenceSessionStore> { PresenceSessionStoreImpl(get()) }
}
