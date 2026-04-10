package me.floow.uikit.chat.model

const val DEFAULT_CHAT_MESSAGE_MAX_LENGTH = 2256

fun String.isWithinCodePointLimit(maxCodePoints: Int): Boolean {
	if (maxCodePoints < 0) return false
	var count = 0
	var index = 0
	while (index < length) {
		if (count > maxCodePoints) return false
		val current = this[index]
		val step = if (
			current.isHighSurrogate() &&
			index + 1 < length &&
			this[index + 1].isLowSurrogate()
		) {
			2
		} else {
			1
		}
		count += 1
		index += step
	}
	return count <= maxCodePoints
}

fun String.trimToCodePointLimit(maxCodePoints: Int): String {
	if (maxCodePoints <= 0) return ""
	if (isWithinCodePointLimit(maxCodePoints)) return this
	var count = 0
	var index = 0
	while (index < length && count < maxCodePoints) {
		val current = this[index]
		val step = if (
			current.isHighSurrogate() &&
			index + 1 < length &&
			this[index + 1].isLowSurrogate()
		) {
			2
		} else {
			1
		}
		count += 1
		index += step
	}
	return substring(0, index.coerceIn(0, length))
}
