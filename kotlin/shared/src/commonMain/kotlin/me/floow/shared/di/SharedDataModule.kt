package me.floow.shared.di

import me.floow.data.repos.BumpRepositoryImpl
import me.floow.data.repos.CategoryCatalogRepositoryImpl
import me.floow.data.repos.ChatsRepositoryImpl
import me.floow.data.repos.CommentsRepositoryImpl
import me.floow.data.repos.FeedRepositoryImpl
import me.floow.data.repos.NotificationsRealtimeRepositoryImpl
import me.floow.data.repos.NotificationsRepositoryImpl
import me.floow.data.repos.PostsRepositoryImpl
import me.floow.data.repos.PresenceRepositoryImpl
import me.floow.data.repos.ProfileRepositoryImpl
import me.floow.data.repos.PushTokensRepositoryImpl
import me.floow.data.repos.UploadsRepositoryImpl
import me.floow.data.repos.UserProfileRepositoryImpl
import me.floow.data.repos.UsersRepositoryImpl
import me.floow.domain.data.repos.BumpRepository
import me.floow.domain.data.repos.CategoryCatalogRepository
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.data.repos.CommentsRepository
import me.floow.domain.data.repos.DirectMessagesOutgoingRetryScheduler
import me.floow.domain.data.repos.FeedRepository
import me.floow.domain.data.repos.NotificationsRealtimeRepository
import me.floow.domain.data.repos.NotificationsRepository
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.PresenceRepository
import me.floow.domain.data.repos.PresenceSessionStore
import me.floow.domain.data.repos.ProfileRepository
import me.floow.domain.data.repos.PushTokensRepository
import me.floow.domain.data.repos.UploadsRepository
import me.floow.domain.data.repos.UserProfileRepository
import me.floow.domain.data.repos.UsersRepository
import me.floow.shared.push.PushTokenRegistrar
import me.floow.shared.push.PushTokenRegistrarImpl
import org.koin.dsl.module

class CommonDirectMessagesOutgoingRetryScheduler : DirectMessagesOutgoingRetryScheduler {
    override fun enqueue(conversationId: Long) {}
}

val sharedDataModule = module {
    single<PresenceSessionStore> { me.floow.shared.presence.createPresenceSessionStore() }
    single<DirectMessagesOutgoingRetryScheduler> { CommonDirectMessagesOutgoingRetryScheduler() }
    single<BumpRepository> { BumpRepositoryImpl(get()) }
    single<ProfileRepository> { ProfileRepositoryImpl(get(), get()) }
    single<UserProfileRepository> { UserProfileRepositoryImpl(get()) }
    single<CategoryCatalogRepository> { CategoryCatalogRepositoryImpl(get(), get()) }
    single<FeedRepository> { FeedRepositoryImpl(get(), get()) }
    single<ChatsRepository> { ChatsRepositoryImpl(get(), get(), get(), get(), get(), getOrNull()) }
    single<UsersRepository> { UsersRepositoryImpl(get(), get()) }
    single<PostsRepository> { PostsRepositoryImpl(get(), get()) }
    single<CommentsRepository> { CommentsRepositoryImpl(get(), get(), get()) }
    single<NotificationsRepository> { NotificationsRepositoryImpl(get(), get()) }
    single<NotificationsRealtimeRepository> { NotificationsRealtimeRepositoryImpl(get(), get(), get(), get(), get()) }
    single<PresenceRepository> { PresenceRepositoryImpl(get(), get(), get(), get(), get()) }
    single<PushTokensRepository> { PushTokensRepositoryImpl(get(), get()) }
    single<PushTokenRegistrar> { PushTokenRegistrarImpl(get(), get()) }
    single<UploadsRepository> { UploadsRepositoryImpl(get(), get()) }
}
