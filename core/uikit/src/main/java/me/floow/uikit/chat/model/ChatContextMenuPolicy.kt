package me.floow.uikit.chat.model

fun ChatMessage.resolveDefaultContextMenuActions(
	allowEditAndDelete: Boolean = true
): List<ChatContextMenuAction> {
	val isOwnMessage = this is PrimaryOutMessage || this is ReplyOutMessage
	return buildList {
		add(ChatContextMenuAction.Reply)
		if (this@resolveDefaultContextMenuActions.isPinned) {
			add(ChatContextMenuAction.Unpin)
		} else {
			add(ChatContextMenuAction.Pin)
		}
		add(ChatContextMenuAction.CopyText)
		if (allowEditAndDelete && isOwnMessage) {
			add(ChatContextMenuAction.Edit)
			add(ChatContextMenuAction.Delete)
		}
	}
}
