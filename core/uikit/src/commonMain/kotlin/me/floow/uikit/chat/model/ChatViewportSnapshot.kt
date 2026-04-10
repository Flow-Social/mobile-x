package me.floow.uikit.chat.model

import androidx.compose.runtime.Immutable

@Immutable
data class ChatViewportSnapshot(
	val visibleMessageIds: Set<Long>,
	val firstVisibleMessageId: Long?,
	val firstVisibleOffsetPx: Int,
	val firstVisibleItemIndex: Int,
	val firstVisibleItemScrollOffsetPx: Int,
	val visibleReadCandidateId: Long?,
	val isAtBottom: Boolean
)
