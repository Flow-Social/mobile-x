package me.floow.shared.chats.model

import me.floow.uikit.components.media.FlowImageSource

data class ChatThreadHeaderModel(
	val peerUserId: String,
	val selfUserId: String? = null,
	val title: String,
	val avatarUrl: String? = null,
	val avatarThumb: String? = null,
	val subtitle: String? = null,
	val isSavedMessages: Boolean = false,
) {
	val avatarSource: FlowImageSource
		get() = FlowImageSource.of(avatarUrl, avatarThumb)
}
