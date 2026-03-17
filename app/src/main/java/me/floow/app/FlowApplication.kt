package me.floow.app

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import me.floow.app.di.flowModules
import me.floow.app.notifications.DirectMessagesReadSyncScheduler
import me.floow.app.push.ChatNotificationLifecycleObserver
import me.floow.app.push.ForegroundVisibleChatStore
import me.floow.app.push.PushNotificationChannels
import me.floow.app.push.PushServiceController
import me.floow.app.push.PushTokenSyncScheduler
import me.floow.app.presence.PresenceLifecycleObserver
import me.floow.app.notifications.NotificationsReadSyncScheduler
import me.floow.domain.data.repos.PresenceRepository
import androidx.lifecycle.ProcessLifecycleOwner
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class FlowApplication : Application() {
	override fun onCreate() {
		super.onCreate()

		if (GlobalContext.getKoinApplicationOrNull() == null) {
			startKoin {
				androidContext(this@FlowApplication)
				modules(flowModules())
			}
		}
		val koin = GlobalContext.getKoinApplicationOrNull()?.koin
		koin?.getOrNull<PresenceRepository>()?.let { presenceRepository: PresenceRepository ->
			ProcessLifecycleOwner.get().lifecycle.addObserver(PresenceLifecycleObserver(presenceRepository))
		}
		koin?.getOrNull<ForegroundVisibleChatStore>()?.let { foregroundVisibleChatStore ->
			ProcessLifecycleOwner.get().lifecycle.addObserver(
				ChatNotificationLifecycleObserver(foregroundVisibleChatStore)
			)
		}

		val imageLoader = ImageLoader.Builder(this)
			.memoryCache {
				MemoryCache.Builder(this)
					.maxSizePercent(0.25)
					.build()
			}
			.diskCache {
				DiskCache.Builder()
					.directory(cacheDir.resolve("flow_image_cache"))
					.maxSizeBytes(256L * 1024L * 1024L)
					.build()
			}
			.crossfade(false)
			.build()

		Coil.setImageLoader(imageLoader)
		PushNotificationChannels.ensureCreated(this)
		PushServiceController.start(this)
		PushTokenSyncScheduler.ensurePeriodic(this)
		PushTokenSyncScheduler.enqueueNow(this)
		NotificationsReadSyncScheduler.ensurePeriodic(this, channel = "replies")
		DirectMessagesReadSyncScheduler.ensurePeriodic(this)
	}
}
