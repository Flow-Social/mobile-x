package me.floow.chats.uilogic.shared

import android.net.Uri

internal fun String?.toSafeUriOrNull(): Uri? {
	val value = this?.trim().orEmpty()
	if (value.isEmpty()) return null
	return runCatching { Uri.parse(value) }.getOrNull()
}
