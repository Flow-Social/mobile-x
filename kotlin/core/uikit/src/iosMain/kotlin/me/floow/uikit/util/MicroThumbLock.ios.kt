package me.floow.uikit.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSLock

@OptIn(ExperimentalForeignApi::class)
actual class MicroThumbLock {
    @PublishedApi
    internal val lock = NSLock()
}

@OptIn(ExperimentalForeignApi::class)
actual inline fun <T> MicroThumbLock.withLock(action: () -> T): T {
    lock.lock()
    try {
        return action()
    } finally {
        lock.unlock()
    }
}
