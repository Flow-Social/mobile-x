package me.floow.uikit.util

actual class MicroThumbLock {
    @PublishedApi
    internal val lock = Any()
}

actual inline fun <T> MicroThumbLock.withLock(action: () -> T): T {
    return synchronized(lock) {
        action()
    }
}
