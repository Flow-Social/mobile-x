package me.floow.app.di

import me.floow.app.debug.SharedPrefsMockStorage
import me.floow.data.repos.BumpRepositoryImpl
import me.floow.data.repos.ChatsRepositoryImpl
import me.floow.data.repos.FeedRepositoryImpl
import me.floow.data.repos.CategoryCatalogRepositoryImpl
import me.floow.data.repos.CommentsRepositoryImpl
import me.floow.data.repos.NotificationsRepositoryImpl
import me.floow.data.repos.NotificationsRealtimeRepositoryImpl
import me.floow.data.repos.PresenceRepositoryImpl
import me.floow.data.repos.PostsRepositoryImpl
import me.floow.data.repos.ProfileRepositoryImpl
import me.floow.data.repos.PushTokensRepositoryImpl
import me.floow.data.repos.UploadsRepositoryImpl
import me.floow.data.repos.UserProfileRepositoryImpl
import me.floow.data.repos.UsersRepositoryImpl
import me.floow.domain.data.repos.FeedRepository
import me.floow.domain.data.repos.BumpRepository
import me.floow.domain.data.repos.CategoryCatalogRepository
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.data.repos.CommentsRepository
import me.floow.domain.data.repos.NotificationsRepository
import me.floow.domain.data.repos.NotificationsRealtimeRepository
import me.floow.domain.data.repos.PresenceRepository
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.ProfileRepository
import me.floow.domain.data.repos.PushTokensRepository
import me.floow.domain.data.repos.UploadsRepository
import me.floow.domain.data.repos.UserProfileRepository
import me.floow.domain.data.repos.UsersRepository
import me.floow.mock.data.MockFeedRepository
import me.floow.mock.data.MockBumpRepository
import me.floow.mock.data.MockCategoryCatalogRepository
import me.floow.mock.data.MockChatsRepository
import me.floow.mock.data.MockCommentsRepository
import me.floow.mock.data.MockNotificationsRepository
import me.floow.mock.data.MockNotificationsRealtimeRepository
import me.floow.mock.data.MockPresenceRepository
import me.floow.mock.data.MockPostsRepository
import me.floow.mock.data.MockProfileRepository
import me.floow.mock.data.MockPushTokensRepository
import me.floow.mock.data.MockUploadsRepository
import me.floow.mock.data.MockUserProfileRepository
import me.floow.mock.data.MockUsersRepository
import me.floow.mock.interfaces.MockStorage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {
	single<BumpRepository> { BumpRepositoryImpl(get()) }
	single<ProfileRepository> { ProfileRepositoryImpl(get(), get()) }
	single<UserProfileRepository> { UserProfileRepositoryImpl(get()) }
	single<CategoryCatalogRepository> { CategoryCatalogRepositoryImpl(get(), get()) }
	single<FeedRepository> { FeedRepositoryImpl(get(), get()) }
	single<ChatsRepository> { ChatsRepositoryImpl(get(), get(), get(), get(), get()) }
	single<UsersRepository> { UsersRepositoryImpl(get(), get()) }
	single<PostsRepository> { PostsRepositoryImpl(get(), get()) }
	single<CommentsRepository> { CommentsRepositoryImpl(get(), get(), get()) }
	single<NotificationsRepository> { NotificationsRepositoryImpl(get(), get()) }
	single<NotificationsRealtimeRepository> { NotificationsRealtimeRepositoryImpl(get(), get(), get(), get(), get()) }
	single<PresenceRepository> { PresenceRepositoryImpl(get(), get(), get(), get(), get()) }
	single<PushTokensRepository> { PushTokensRepositoryImpl(get(), get()) }
	single<UploadsRepository> { UploadsRepositoryImpl(get(), get()) }
}

val mockDataModule = module {
    single<MockStorage> { SharedPrefsMockStorage(androidContext()) }
	single<BumpRepository> { MockBumpRepository() }
	single<ProfileRepository> { MockProfileRepository(get()) }
	single<UserProfileRepository> { MockUserProfileRepository(get()) }
	single<CategoryCatalogRepository> { MockCategoryCatalogRepository() }
	single<FeedRepository> { MockFeedRepository(get()) }
	single<ChatsRepository> { MockChatsRepository() }
	single<UsersRepository> { MockUsersRepository(get()) }
	single<PostsRepository> { MockPostsRepository() }
	single<CommentsRepository> { MockCommentsRepository() }
	single<NotificationsRepository> { MockNotificationsRepository() }
	single<NotificationsRealtimeRepository> { MockNotificationsRealtimeRepository() }
	single<PresenceRepository> { MockPresenceRepository() }
	single<PushTokensRepository> { MockPushTokensRepository() }
	single<UploadsRepository> { MockUploadsRepository() }
}
