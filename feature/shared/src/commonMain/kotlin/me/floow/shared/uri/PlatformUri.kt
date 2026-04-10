package me.floow.shared.uri

expect class PlatformUri

expect fun String.toUriOrNull(): PlatformUri?
