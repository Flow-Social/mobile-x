package me.floow.shared.di

import me.floow.api.AuthApiImpl
import me.floow.api.BumpApiImpl
import me.floow.api.CategoriesApiImpl
import me.floow.api.ChatsApiImpl
import me.floow.api.ChatsRealtimeApiImpl
import me.floow.api.CommentsApiImpl
import me.floow.api.CommentsRealtimeApiImpl
import me.floow.api.FeedApiImpl
import me.floow.api.NotificationsApiImpl
import me.floow.api.NotificationsRealtimeApiImpl
import me.floow.api.PostsApiImpl
import me.floow.api.PresenceApiImpl
import me.floow.api.PresenceRealtimeApiImpl
import me.floow.api.ProfileApiImpl
import me.floow.api.PushApiImpl
import me.floow.api.PushTokensApiImpl
import me.floow.api.UploadsApiImpl
import me.floow.api.UsersApiImpl
import me.floow.api.util.ApiConfig
import me.floow.api.util.HttpClientProvider
import me.floow.domain.api.AuthApi
import me.floow.domain.api.BumpApi
import me.floow.domain.api.CategoriesApi
import me.floow.domain.api.ChatsApi
import me.floow.domain.api.ChatsRealtimeApi
import me.floow.domain.api.CommentsApi
import me.floow.domain.api.CommentsRealtimeApi
import me.floow.domain.api.FeedApi
import me.floow.domain.api.NotificationsApi
import me.floow.domain.api.NotificationsRealtimeApi
import me.floow.domain.api.PostsApi
import me.floow.domain.api.PresenceApi
import me.floow.domain.api.PresenceRealtimeApi
import me.floow.domain.api.ProfileApi
import me.floow.domain.api.PushApi
import me.floow.domain.api.PushTokensApi
import me.floow.domain.api.UploadsApi
import me.floow.domain.api.UsersApi
import org.koin.dsl.module

val sharedApiModule = module {
    single<HttpClientProvider> {
        val tokenStorage = getOrNull<me.floow.shared.auth.TokenStorage>()
        HttpClientProvider {
            refreshToken = {
                // Returns false if refresh token is not supported/fails
                false
            }
            onRefreshFailed = {
                if (tokenStorage?.authState?.value is me.floow.shared.auth.AuthTokenState.Authenticated) {
                    tokenStorage.clearTokens()
                }
            }
        }
    }
    single<ApiConfig> {
        ApiConfig(
            apiUrl = me.floow.shared.config.AppConfig.BASE_URL,
        )
    }
    factory<BumpApi> { BumpApiImpl(get(), get(), get(), get()) }
    factory<ProfileApi> { ProfileApiImpl(get(), get(), get(), get()) }
    factory<AuthApi> { AuthApiImpl(get(), get(), get()) }
    factory<UsersApi> { UsersApiImpl(get(), get(), get(), get()) }
    factory<PostsApi> { PostsApiImpl(get(), get(), get(), get()) }
    factory<CategoriesApi> { CategoriesApiImpl(get(), get(), get(), get()) }
    factory<CommentsApi> { CommentsApiImpl(get(), get(), get(), get()) }
    factory<CommentsRealtimeApi> { CommentsRealtimeApiImpl(get(), get(), get(), get(), getOrNull()) }
    factory<FeedApi> { FeedApiImpl(get(), get(), get(), get()) }
    factory<ChatsApi> { ChatsApiImpl(get(), get(), get(), get()) }
    single<ChatsRealtimeApi> { ChatsRealtimeApiImpl(get(), get(), get(), get(), getOrNull()) }
    factory<NotificationsApi> { NotificationsApiImpl(get(), get(), get(), get()) }
    factory<NotificationsRealtimeApi> { NotificationsRealtimeApiImpl(get(), get(), get(), get()) }
    factory<PresenceApi> { PresenceApiImpl(get(), get(), get(), get()) }
    factory<PresenceRealtimeApi> { PresenceRealtimeApiImpl(get(), get(), get(), get()) }
    factory<UploadsApi> { UploadsApiImpl(get(), get(), get(), get()) }
    factory<PushTokensApi> { PushTokensApiImpl(get(), get(), get(), get()) }
    factory<PushApi> { PushApiImpl(get(), get(), get()) }
}
