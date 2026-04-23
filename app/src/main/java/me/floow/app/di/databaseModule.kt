package me.floow.app.di

import androidx.room.Room
import me.floow.database.AppDatabase
import me.floow.database.DatabaseMigrations
import me.floow.database.localstore.FeedSyncLocalStoreImpl
import me.floow.database.localstore.PostsLocalStoreImpl
import me.floow.database.localstore.ProfileLocalStoreImpl
import me.floow.database.localstore.DirectChatsLocalStoreImpl
import me.floow.database.localstore.RepliesInboxLocalStoreImpl
import me.floow.database.sharedpref.ProfileCacheProviderImpl
import me.floow.database.sharedpref.PresenceSessionStoreImpl
import me.floow.database.sharedpref.UsernameToIdCacheImpl
import me.floow.database.sharedpref.UserProfileCacheProviderImpl
import me.floow.domain.cache.FeedSyncLocalStore
import me.floow.domain.cache.ProfileCacheProvider
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.cache.DirectChatsLocalStore
import me.floow.domain.cache.RepliesInboxLocalStore
import me.floow.domain.cache.UserProfileCacheProvider
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.cache.UsernameToIdCache
import me.floow.domain.data.repos.PresenceSessionStore
import org.koin.dsl.module

val databaseModule = module {
    single<AppDatabase> {
        val builder = Room.databaseBuilder(
            get(),
            AppDatabase::class.java,
            "flowme.db"
        ).addMigrations(
            DatabaseMigrations.MIGRATION_1_2,
            DatabaseMigrations.MIGRATION_2_3,
            DatabaseMigrations.MIGRATION_3_4,
            DatabaseMigrations.MIGRATION_4_5,
            DatabaseMigrations.MIGRATION_5_6,
            DatabaseMigrations.MIGRATION_6_7,
            DatabaseMigrations.MIGRATION_7_8,
            DatabaseMigrations.MIGRATION_8_9,
            DatabaseMigrations.MIGRATION_9_10,
            DatabaseMigrations.MIGRATION_10_11,
            DatabaseMigrations.MIGRATION_11_12,
            DatabaseMigrations.MIGRATION_12_13,
            DatabaseMigrations.MIGRATION_13_14,
            DatabaseMigrations.MIGRATION_14_15,
            DatabaseMigrations.MIGRATION_15_16,
            DatabaseMigrations.MIGRATION_16_17,
            DatabaseMigrations.MIGRATION_17_18,
            DatabaseMigrations.MIGRATION_18_19,
            DatabaseMigrations.MIGRATION_19_20
        )

        builder.build()
    }
    single { get<AppDatabase>().profileDao() }
    single { get<AppDatabase>().postsDao() }
    single { get<AppDatabase>().feedSyncCommandsDao() }
    single { get<AppDatabase>().repliesInboxDao() }
    single { get<AppDatabase>().directChatsDao() }

	single<ProfileCacheProvider> { ProfileCacheProviderImpl(get()) }
	single<UserProfileCacheProvider> { UserProfileCacheProviderImpl(get()) }
	single<UsernameToIdCache> { UsernameToIdCacheImpl(get()) }
	single<PresenceSessionStore> { PresenceSessionStoreImpl(get()) }

    single<ProfileLocalStore> { ProfileLocalStoreImpl(get()) }
    single<PostsLocalStore> { PostsLocalStoreImpl(get()) }
    single<FeedSyncLocalStore> { FeedSyncLocalStoreImpl(get()) }
    single<RepliesInboxLocalStore> { RepliesInboxLocalStoreImpl(get(), get()) }
    single<DirectChatsLocalStore> { DirectChatsLocalStoreImpl(get(), get()) }
}
