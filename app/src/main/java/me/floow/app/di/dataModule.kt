package me.floow.app.di

import me.floow.app.debug.SharedPrefsMockStorage
import me.floow.data.repos.FeedRepositoryImpl
import me.floow.data.repos.CategoryCatalogRepositoryImpl
import me.floow.data.repos.CommentsRepositoryImpl
import me.floow.data.repos.PostsRepositoryImpl
import me.floow.data.repos.ProfileRepositoryImpl
import me.floow.data.repos.UploadsRepositoryImpl
import me.floow.data.repos.UserProfileRepositoryImpl
import me.floow.data.repos.UsersRepositoryImpl
import me.floow.domain.data.repos.FeedRepository
import me.floow.domain.data.repos.CategoryCatalogRepository
import me.floow.domain.data.repos.CommentsRepository
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.ProfileRepository
import me.floow.domain.data.repos.UploadsRepository
import me.floow.domain.data.repos.UserProfileRepository
import me.floow.domain.data.repos.UsersRepository
import me.floow.mock.data.MockFeedRepository
import me.floow.mock.data.MockCategoryCatalogRepository
import me.floow.mock.data.MockCommentsRepository
import me.floow.mock.data.MockPostsRepository
import me.floow.mock.data.MockProfileRepository
import me.floow.mock.data.MockUploadsRepository
import me.floow.mock.data.MockUserProfileRepository
import me.floow.mock.data.MockUsersRepository
import me.floow.mock.interfaces.MockStorage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {
	single<ProfileRepository> { ProfileRepositoryImpl(get(), get()) }
	single<UserProfileRepository> { UserProfileRepositoryImpl(get()) }
	single<CategoryCatalogRepository> { CategoryCatalogRepositoryImpl(get(), get()) }
	single<FeedRepository> { FeedRepositoryImpl(get(), get()) }
	single<UsersRepository> { UsersRepositoryImpl(get(), get()) }
	single<PostsRepository> { PostsRepositoryImpl(get(), get()) }
	single<CommentsRepository> { CommentsRepositoryImpl(get(), get()) }
	single<UploadsRepository> { UploadsRepositoryImpl(get(), get()) }
}

val mockDataModule = module {
    single<MockStorage> { SharedPrefsMockStorage(androidContext()) }
	single<ProfileRepository> { MockProfileRepository(get()) }
	single<UserProfileRepository> { MockUserProfileRepository(get()) }
	single<CategoryCatalogRepository> { MockCategoryCatalogRepository() }
	single<FeedRepository> { MockFeedRepository(get()) }
	single<UsersRepository> { MockUsersRepository(get()) }
	single<PostsRepository> { MockPostsRepository() }
	single<CommentsRepository> { MockCommentsRepository() }
	single<UploadsRepository> { MockUploadsRepository() }
}
