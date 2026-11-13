package me.floow.app.di

import me.floow.api.util.ApiConfig
import me.floow.app.deeplink.DeepLinkDispatcher
import me.floow.app.AppControllerImpl
import me.floow.app.LoggerImpl
import me.floow.app.di.configs.apiConfig
import me.floow.app.di.configs.googleOAuthInfoConfig
import me.floow.auth.models.GoogleOAuthInfo
import me.floow.app.notifications.CommentsReadCursorStoreImpl
import me.floow.app.notifications.DirectChatsRefreshOrchestrator
import me.floow.app.notifications.DirectChatsSyncCoordinator
import me.floow.app.notifications.DirectMessagesReadCursorStoreImpl
import me.floow.app.notifications.DirectMessagesOutgoingRetrySchedulerImpl
import me.floow.app.notifications.NotificationsReadCursorStoreImpl
import me.floow.app.notifications.NotificationsBadgeViewModel
import me.floow.app.network.AndroidNetworkMonitor
import me.floow.app.notifications.ScopedReadCursorStoreImpl
import me.floow.app.posts.PendingPostMediaStoreImpl
import me.floow.app.posts.PendingPostsSyncSchedulerImpl
import me.floow.app.push.ChatNotificationRenderer
import me.floow.app.push.ChatNotificationVisibilityGate
import me.floow.app.push.ChatPushAckSender
import me.floow.app.push.ForegroundVisibleChatStore
import me.floow.app.push.NotificationPipeline
import me.floow.api.util.HttpClientProvider
import me.floow.app.rescue.RescueSyncManager
import me.floow.domain.app.AppController
import me.floow.domain.data.repos.CommentsReadCursorStore
import me.floow.domain.data.repos.DirectMessagesReadCursorStore
import me.floow.domain.data.repos.DirectMessagesOutgoingRetryScheduler
import me.floow.domain.data.repos.NotificationsReadCursorStore
import me.floow.domain.data.repos.PendingPostMediaStore
import me.floow.domain.data.repos.PendingPostsSyncScheduler
import me.floow.domain.data.repos.ScopedReadCursorStore
import me.floow.domain.network.NetworkMonitor
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
		single<ScopedReadCursorStore> { ScopedReadCursorStoreImpl(androidContext()) }
		single<CommentsReadCursorStore> { CommentsReadCursorStoreImpl(androidContext(), get()) }
		single<NotificationsReadCursorStore> { NotificationsReadCursorStoreImpl(androidContext(), get()) }
		single<DirectMessagesReadCursorStore> { DirectMessagesReadCursorStoreImpl(androidContext(), get()) }
		single<DirectMessagesOutgoingRetryScheduler> { DirectMessagesOutgoingRetrySchedulerImpl(androidContext()) }
		single<NetworkMonitor> { AndroidNetworkMonitor(androidContext()) }
		single { ForegroundVisibleChatStore() }
		single {
			val context = androidContext()
			ChatNotificationVisibilityGate(
				foregroundVisibleChatStore = get(),
				selfUserIdProvider = {
					context.getSharedPreferences("flowme.auth", android.content.Context.MODE_PRIVATE)
						.getString("authUserId", null)
						?.trim()
						?.takeIf(String::isNotEmpty)
				}
			)
		}
		single { ChatPushAckSender(context = androidContext(), pushApi = get(), chatsRealtimeApi = get()) }
		single { ChatNotificationRenderer(context = androidContext(), visibilityGate = get(), logger = get()) }
		single { NotificationPipeline(context = androidContext(), renderer = get(), ackSender = get(), logger = get()) }
		single { DirectChatsRefreshOrchestrator(chatsRepository = get(), logger = get()) }
		single {
		val context = androidContext()
		DirectChatsSyncCoordinator(
			chatsRepository = get(),
			notificationPipeline = get(),
			refreshOrchestrator = get(),
			logger = get(),
			currentUserIdProvider = {
				context.getSharedPreferences("flowme.auth", android.content.Context.MODE_PRIVATE)
					.getString("authUserId", null)
					?.trim()
					?.takeIf(String::isNotEmpty)
			}
		)
	}
	    single<PostMediaTransferStore> { InMemoryPostMediaTransferStore() }
		single {
			val httpClientProvider: HttpClientProvider = get()
			RescueSyncManager(
				context = androidContext(),
				database = get(),
				httpClient = httpClientProvider.getClientWithoutHttpCache(),
				apiConfig = get(),
				logger = get()
			)
		}
		viewModelOf(::NotificationsBadgeViewModel)
	}

fun createAppControllerModule(activity: android.app.Activity) = module {
    single<AppController> { AppControllerImpl(activity) }
}
