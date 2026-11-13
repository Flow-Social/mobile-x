package me.floow.shared.uri

actual class PlatformUri(val value: String)

actual fun String.toUriOrNull(): PlatformUri? {
    return PlatformUri(this)
}
