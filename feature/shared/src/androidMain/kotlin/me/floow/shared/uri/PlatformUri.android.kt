package me.floow.shared.uri

import android.net.Uri

actual class PlatformUri(val value: Uri)

actual fun String.toUriOrNull(): PlatformUri? {
    return PlatformUri(Uri.parse(this))
}
