package me.floow.shared

import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import me.floow.app.FlowApp
import me.floow.domain.lifecycle.AppLifecycleBridge
import me.floow.shared.di.initKoin
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIViewController

@OptIn(ExperimentalAtomicApi::class)
private val isIosLifecycleObserverRegistered = AtomicInt(0)

@OptIn(ExperimentalAtomicApi::class)
private fun registerIosLifecycleObservers() {
    if (!isIosLifecycleObserverRegistered.compareAndSet(0, 1)) return

    val center = NSNotificationCenter.defaultCenter
    center.addObserverForName(
        name = UIApplicationDidBecomeActiveNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { _ ->
        AppLifecycleBridge.onAppForeground()
    }
    center.addObserverForName(
        name = UIApplicationDidEnterBackgroundNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { _ ->
        AppLifecycleBridge.onAppBackground()
    }
}

fun MainViewController(): UIViewController {
    println("==> [Flow] MainViewController called")
    try {
        initKoin()
        println("==> [Flow] initKoin OK")
        registerIosLifecycleObservers()
        println("==> [Flow] registerIosLifecycleObservers OK")
        me.floow.shared.push.flushPendingIosPushToken()
        println("==> [Flow] flushPendingIosPushToken OK")
        SingletonImageLoader.setSafe { context ->
            val cacheDir = (NSSearchPathForDirectoriesInDomains(
                NSCachesDirectory,
                NSUserDomainMask,
                true
            ).firstOrNull() as? String) ?: NSTemporaryDirectory()
            val diskCachePath = "$cacheDir/flow_image_cache".toPath()

            ImageLoader.Builder(context)
                .components {
                    add(KtorNetworkFetcherFactory())
                }
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizeBytes(64L * 1024L * 1024L)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(diskCachePath)
                        .maxSizeBytes(256L * 1024L * 1024L)
                        .build()
                }
                .build()
        }
        println("==> [Flow] SingletonImageLoader OK")
    } catch (t: Throwable) {
        println("==> [Flow CRASH in setup]: ${t.message}")
        t.printStackTrace()
    }
    return ComposeUIViewController {
        FlowApp()
    }
}
