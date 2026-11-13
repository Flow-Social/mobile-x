package me.floow.database.di

import me.floow.database.AppDatabase
import me.floow.database.createRoomDatabase
import me.floow.database.localstore.DirectChatsLocalStoreImpl
import me.floow.database.localstore.FeedStackSnapshotLocalStoreImpl
import me.floow.database.localstore.FeedSyncLocalStoreImpl
import me.floow.database.localstore.PendingPostsLocalStoreImpl
import me.floow.database.localstore.PostsLocalStoreImpl
import me.floow.database.localstore.ProfileLocalStoreImpl
import me.floow.database.localstore.RepliesInboxLocalStoreImpl
import me.floow.domain.cache.DirectChatsLocalStore
import me.floow.domain.cache.FeedStackSnapshotLocalStore
import me.floow.domain.cache.FeedSyncLocalStore
import me.floow.domain.cache.PendingPostsLocalStore
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.cache.RepliesInboxLocalStore
import org.koin.dsl.module

val databaseModule = module {
    single<AppDatabase> { createRoomDatabase() }

    single { get<AppDatabase>().profileDao() }
    single { get<AppDatabase>().postsDao() }
    single { get<AppDatabase>().feedStackSnapshotDao() }
    single { get<AppDatabase>().feedSyncCommandsDao() }
    single { get<AppDatabase>().repliesInboxDao() }
    single { get<AppDatabase>().directChatsDao() }
    single { get<AppDatabase>().pendingPostMutationDao() }

    single<ProfileLocalStore> { ProfileLocalStoreImpl(get()) }
    single<PostsLocalStore> { PostsLocalStoreImpl(get()) }
    single<FeedStackSnapshotLocalStore> { FeedStackSnapshotLocalStoreImpl(get()) }
    single<FeedSyncLocalStore> { FeedSyncLocalStoreImpl(get()) }
    single<PendingPostsLocalStore> { PendingPostsLocalStoreImpl(get()) }
    single<RepliesInboxLocalStore> { RepliesInboxLocalStoreImpl(get(), get()) }
    single<DirectChatsLocalStore> { DirectChatsLocalStoreImpl(get(), get()) }
}
