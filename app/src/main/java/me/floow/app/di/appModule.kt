package me.floow.app.di

import me.floow.api.util.ApiConfig
import me.floow.app.deeplink.DeepLinkDispatcher
import me.floow.app.AppControllerImpl
import me.floow.app.LoggerImpl
import me.floow.app.di.configs.apiConfig
import me.floow.app.di.configs.googleOAuthInfoConfig
import me.floow.app.notifications.CommentsReadCursorStoreImpl
import me.floow.app.notifications.NotificationsReadCursorStoreImpl
import me.floow.app.notifications.NotificationsBadgeViewModel
import me.floow.auth.models.GoogleOAuthInfo
import me.floow.domain.app.AppController
import me.floow.domain.data.repos.CommentsReadCursorStore
import me.floow.domain.data.repos.NotificationsReadCursorStore
import me.floow.domain.utils.Logger
import me.floow.uikit.components.media.transfer.InMemoryPostMediaTransferStore
import me.floow.uikit.components.media.transfer.PostMediaTransferStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single<GoogleOAuthInfo> { googleOAuthInfoConfig }
	    single<ApiConfig> { apiConfig }
	    single<Logger> { LoggerImpl() }
	    single { DeepLinkDispatcher() }
		single<CommentsReadCursorStore> { CommentsReadCursorStoreImpl(androidContext()) }
		single<NotificationsReadCursorStore> { NotificationsReadCursorStoreImpl(androidContext()) }
	    single<PostMediaTransferStore> { InMemoryPostMediaTransferStore() }
		viewModelOf(::NotificationsBadgeViewModel)
	}

fun createAppControllerModule(activity: android.app.Activity) = module {
    single<AppController> { AppControllerImpl(activity) }
}
