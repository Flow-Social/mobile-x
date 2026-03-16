package me.floow.uikit.chat.model

const val DEFAULT_CHAT_MESSAGE_MAX_LENGTH = 2256

fun String.isWithinCodePointLimit(maxCodePoints: Int): Boolean {
	return codePointCount(0, length) <= maxCodePoints
}

fun String.trimToCodePointLimit(maxCodePoints: Int): String {
	if (maxCodePoints <= 0) return ""
	if (isWithinCodePointLimit(maxCodePoints)) return this
	return substring(0, offsetByCodePoints(0, maxCodePoints))
}
