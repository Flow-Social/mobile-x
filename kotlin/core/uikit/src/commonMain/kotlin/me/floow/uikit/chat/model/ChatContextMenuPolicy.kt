package me.floow.uikit.chat.model

fun ChatMessage.resolveDefaultContextMenuActions(
	allowEditAndDelete: Boolean = true
): List<ChatContextMenuAction> {
	val isOwnMessage = this is PrimaryOutMessage ||
		this is ReplyOutMessage ||
		(this is ImageMessage && this.isOutgoing) ||
		(this is VideoCircleOutMessage && this.isOutgoing) ||
		(this is VoiceMessage && this.isOutgoing)
	val hasServerId = id > 0L
	val isPending = isOwnMessage && !clientMessageId.isNullOrBlank() && !hasServerId
	return buildList {
		if (!isPending) {
			add(ChatContextMenuAction.Reply)
			if (this@resolveDefaultContextMenuActions.isPinned) {
				add(ChatContextMenuAction.Unpin)
			} else {
				add(ChatContextMenuAction.Pin)
			}
		}
		add(ChatContextMenuAction.CopyText)
		if (isPending && deliveryStatus == ChatMessageDeliveryStatus.FAILED) {
			add(ChatContextMenuAction.Retry)
		}
		if (allowEditAndDelete && isOwnMessage && hasServerId) {
			add(ChatContextMenuAction.Edit)
			add(ChatContextMenuAction.Delete)
		} else if (allowEditAndDelete && isPending) {
			add(ChatContextMenuAction.Delete)
		}
	}
}
