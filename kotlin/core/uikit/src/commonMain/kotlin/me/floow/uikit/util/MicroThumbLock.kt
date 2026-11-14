package me.floow.uikit.util

expect class MicroThumbLock()

expect inline fun <T> MicroThumbLock.withLock(action: () -> T): T
