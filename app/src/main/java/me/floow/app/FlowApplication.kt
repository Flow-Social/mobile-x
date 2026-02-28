package me.floow.app

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache

class FlowApplication : Application() {
	override fun onCreate() {
		super.onCreate()

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
	}
}
