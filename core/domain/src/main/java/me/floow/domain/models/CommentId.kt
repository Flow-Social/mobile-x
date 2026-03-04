package me.floow.domain.models

@JvmInline
value class CommentId(val value: Long) {
	init {
		require(value > 0L) { "CommentId must be positive" }
	}
}

fun String?.toCommentIdOrNull(): CommentId? {
	val parsed = this
		?.trim()
		?.takeIf(String::isNotEmpty)
		?.toLongOrNull()
		?: return null
	if (parsed <= 0L) return null
	return CommentId(parsed)
}

