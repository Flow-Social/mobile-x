package me.floow.app.di

import me.floow.app.debug.SharedPrefsMockStorage
import me.floow.chatssearch.di.usersearchModule
import me.floow.comments.di.commentsModule
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.data.repos.BumpRepository
import me.floow.domain.data.repos.CategoryCatalogRepository
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.data.repos.CommentsRepository
import me.floow.domain.data.repos.FeedRepository
import me.floow.domain.data.repos.NotificationsRealtimeRepository
import me.floow.domain.data.repos.NotificationsRepository
import me.floow.domain.data.repos.PresenceRepository
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.ProfileRepository
import me.floow.domain.data.repos.PushTokensRepository
import me.floow.domain.data.repos.UploadsRepository
import me.floow.domain.data.repos.UserProfileRepository
import me.floow.domain.data.repos.UsersRepository
import me.floow.mock.auth.MockAuthenticationManager
import me.floow.mock.data.MockBumpRepository
import me.floow.mock.data.MockCategoryCatalogRepository
import me.floow.mock.data.MockChatsRepository
import me.floow.mock.data.MockCommentsRepository
import me.floow.mock.data.MockFeedRepository
import me.floow.mock.data.MockNotificationsRealtimeRepository
import me.floow.mock.data.MockNotificationsRepository
import me.floow.mock.data.MockPresenceRepository
import me.floow.mock.data.MockPostsRepository
import me.floow.mock.data.MockProfileRepository
import me.floow.mock.data.MockPushTokensRepository
import me.floow.mock.data.MockUploadsRepository
import me.floow.mock.data.MockUserProfileRepository
import me.floow.mock.data.MockUsersRepository
import me.floow.mock.interfaces.MockStorage
import me.floow.shared.di.sharedCommonModule
import me.floow.shared.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

val mockAuthModule = module {
    single<AuthenticationManager> { MockAuthenticationManager(get()) }
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

class MockFlowModuleProvider : FlowModuleProvider {
    override fun modules(): List<Module> {
        return listOf(
            appModule,
            apiModule,
            mockAuthModule,
            databaseModule,
            mockDataModule,
            domainModule,
            sharedCommonModule,
            sharedModule,
            usersearchModule,
            commentsModule,
        )
    }
}
