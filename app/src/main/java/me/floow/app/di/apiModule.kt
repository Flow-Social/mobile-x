package me.floow.app.di

import me.floow.api.AuthApiImpl
import me.floow.api.CategoriesApiImpl
import me.floow.api.CommentsApiImpl
import me.floow.api.FeedApiImpl
import me.floow.api.PostsApiImpl
import me.floow.api.ProfileApiImpl
import me.floow.api.UploadsApiImpl
import me.floow.api.UsersApiImpl
import me.floow.api.util.HttpClientProvider
import me.floow.auth.GoogleOAuthImpl
import me.floow.domain.api.AuthApi
import me.floow.domain.api.CategoriesApi
import me.floow.domain.api.CommentsApi
import me.floow.domain.api.FeedApi
import me.floow.domain.api.PostsApi
import me.floow.domain.api.ProfileApi
import me.floow.domain.api.UploadsApi
import me.floow.domain.api.UsersApi
import me.floow.domain.auth.GoogleOAuth
import org.koin.dsl.module

val apiModule = module {
    single<HttpClientProvider> { HttpClientProvider() }
    factory<ProfileApi> { ProfileApiImpl(get(), get(), get(), get()) }
    factory<AuthApi> { AuthApiImpl(get(), get(), get()) }
    factory<UsersApi> { UsersApiImpl(get(), get(), get(), get()) }
    factory<PostsApi> { PostsApiImpl(get(), get(), get(), get()) }
    factory<CategoriesApi> { CategoriesApiImpl(get(), get(), get(), get()) }
	    factory<CommentsApi> { CommentsApiImpl(get(), get(), get(), get()) }
	    factory<FeedApi> { FeedApiImpl(get(), get(), get(), get()) }
	    factory<UploadsApi> { UploadsApiImpl(get(), get(), get(), get()) }
	    factory<GoogleOAuth> { GoogleOAuthImpl(get(), get()) }
	}
