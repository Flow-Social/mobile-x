package me.floow.shared.chats.model

data class ChatThreadHeaderModel(
	val peerUserId: String,
	val selfUserId: String? = null,
	val title: String,
	val avatarUrl: String? = null,
	val subtitle: String? = null,
	val isSavedMessages: Boolean = false,
)
