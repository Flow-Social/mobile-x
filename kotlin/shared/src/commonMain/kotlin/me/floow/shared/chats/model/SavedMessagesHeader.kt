package me.floow.shared.chats.model

fun ChatThreadHeaderModel.withSavedMessagesSemantics(
	titleOverride: String? = null,
): ChatThreadHeaderModel {
	if (!isSavedMessages) return this
	return copy(
		title = titleOverride ?: title,
		avatarUrl = null,
		subtitle = null,
		isSavedMessages = true,
	)
}
