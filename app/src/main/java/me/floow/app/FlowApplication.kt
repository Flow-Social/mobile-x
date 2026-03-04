package me.floow.app

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import me.floow.app.di.flowModules
import me.floow.app.push.PushNotificationChannels
import me.floow.app.push.PushTokenSyncScheduler
import me.floow.app.notifications.NotificationsReadSyncScheduler
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
		PushTokenSyncScheduler.ensurePeriodic(this)
		PushTokenSyncScheduler.enqueueNow(this)
		NotificationsReadSyncScheduler.ensurePeriodic(this, channel = "replies")
	}
}
