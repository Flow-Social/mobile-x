package me.floow.app.di

import androidx.room.Room
import me.floow.database.AppDatabase
import me.floow.database.DatabaseMigrations
import me.floow.database.localstore.FeedSyncLocalStoreImpl
import me.floow.database.localstore.PostsLocalStoreImpl
import me.floow.database.localstore.ProfileLocalStoreImpl
import me.floow.database.sharedpref.ProfileCacheProviderImpl
import me.floow.database.sharedpref.UsernameToIdCacheImpl
import me.floow.database.sharedpref.UserProfileCacheProviderImpl
import me.floow.domain.cache.FeedSyncLocalStore
import me.floow.domain.cache.ProfileCacheProvider
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.cache.UserProfileCacheProvider
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.cache.UsernameToIdCache
import org.koin.dsl.module

val databaseModule = module {
    single<AppDatabase> {
        Room.databaseBuilder(
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
			DatabaseMigrations.MIGRATION_11_12
        )
            .build()
    }
    single { get<AppDatabase>().profileDao() }
    single { get<AppDatabase>().postsDao() }
	single { get<AppDatabase>().feedSyncCommandsDao() }

	single<ProfileCacheProvider> { ProfileCacheProviderImpl(get()) }
	single<UserProfileCacheProvider> { UserProfileCacheProviderImpl(get()) }
	single<UsernameToIdCache> { UsernameToIdCacheImpl(get()) }

    single<ProfileLocalStore> { ProfileLocalStoreImpl(get()) }
    single<PostsLocalStore> { PostsLocalStoreImpl(get()) }
	single<FeedSyncLocalStore> { FeedSyncLocalStoreImpl(get()) }
}
