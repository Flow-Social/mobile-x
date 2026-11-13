package me.floow.app

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import me.floow.app.di.flowModules
import me.floow.app.notifications.DirectMessagesReadSyncScheduler
import me.floow.app.push.ChatNotificationLifecycleObserver
import me.floow.app.push.ForegroundVisibleChatStore
import me.floow.app.push.PushNotificationChannels
import me.floow.app.push.PushServiceController
import me.floow.app.push.PushTokenSyncScheduler
import me.floow.app.presence.PresenceLifecycleObserver
import me.floow.app.notifications.NotificationsReadSyncScheduler
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.data.repos.DirectMessagesOutgoingRetryScheduler
import me.floow.domain.data.repos.PresenceRepository
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.floow.app.rescue.RescueSyncManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class FlowApplication : Application() {
	override fun onCreate() {
		super.onCreate()

		me.floow.database.AndroidDatabaseContext.set(this)
		me.floow.shared.auth.AndroidTokenContext.set(this)
		me.floow.api.util.AndroidApiContext.set(this)
		if (GlobalContext.getKoinApplicationOrNull() == null) {
			startKoin {
				androidContext(this@FlowApplication)
				modules(flowModules())
			}
		}
		val koin = GlobalContext.getKoinApplicationOrNull()?.koin
		ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
			override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
				me.floow.domain.lifecycle.AppLifecycleBridge.onAppForeground()
			}
			override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
				me.floow.domain.lifecycle.AppLifecycleBridge.onAppBackground()
			}
		})
		koin?.getOrNull<PresenceRepository>()?.let { presenceRepository: PresenceRepository ->
			ProcessLifecycleOwner.get().lifecycle.addObserver(PresenceLifecycleObserver(presenceRepository))
		}
		koin?.getOrNull<ForegroundVisibleChatStore>()?.let { foregroundVisibleChatStore ->
			ProcessLifecycleOwner.get().lifecycle.addObserver(
				ChatNotificationLifecycleObserver(foregroundVisibleChatStore)
			)
		}

		val imageLoader = ImageLoader.Builder(this)
			.components {
				add(OkHttpNetworkFetcherFactory())
			}
			.crossfade(false)
			.memoryCache {
				MemoryCache.Builder()
					.maxSizePercent(this, 0.25)
					.build()
			}
			.diskCache {
				DiskCache.Builder()
					.directory(cacheDir.resolve("flow_image_cache"))
					.maxSizeBytes(256L * 1024L * 1024L)
					.build()
			}
			.build()

		SingletonImageLoader.setSafe { imageLoader }
		PushNotificationChannels.ensureCreated(this)
		PushServiceController.start(this)
		PushTokenSyncScheduler.ensurePeriodic(this)
		PushTokenSyncScheduler.enqueueNow(this)
		NotificationsReadSyncScheduler.ensurePeriodic(this, channel = "replies")
		DirectMessagesReadSyncScheduler.ensurePeriodic(this)
		val authManager = koin?.getOrNull<AuthenticationManager>()
		if (authManager?.isSignedIn() == true) {
			koin.getOrNull<DirectMessagesOutgoingRetryScheduler>()?.enqueue()
		}

		CoroutineScope(Dispatchers.IO).launch {
			koin?.getOrNull<RescueSyncManager>()?.syncIfNeeded()
		}
	}
}
