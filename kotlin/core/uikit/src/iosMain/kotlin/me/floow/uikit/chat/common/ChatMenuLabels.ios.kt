package me.floow.uikit.chat.common

import androidx.compose.runtime.Composable
import flow.core.uikit.generated.resources.Res
import flow.core.uikit.generated.resources.chat_menu_copy_text
import flow.core.uikit.generated.resources.chat_menu_delete
import flow.core.uikit.generated.resources.chat_menu_edit
import flow.core.uikit.generated.resources.chat_menu_pin
import flow.core.uikit.generated.resources.chat_menu_reply
import flow.core.uikit.generated.resources.chat_menu_retry
import flow.core.uikit.generated.resources.chat_menu_select
import flow.core.uikit.generated.resources.chat_menu_unpin
import me.floow.uikit.chat.model.ChatContextMenuAction
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun chatMenuLabel(action: ChatContextMenuAction): String = stringResource(
	when (action) {
		ChatContextMenuAction.Reply -> Res.string.chat_menu_reply
		ChatContextMenuAction.Pin -> Res.string.chat_menu_pin
		ChatContextMenuAction.Unpin -> Res.string.chat_menu_unpin
		ChatContextMenuAction.CopyText -> Res.string.chat_menu_copy_text
		ChatContextMenuAction.Edit -> Res.string.chat_menu_edit
		ChatContextMenuAction.Delete -> Res.string.chat_menu_delete
		ChatContextMenuAction.Retry -> Res.string.chat_menu_retry
		ChatContextMenuAction.Select -> Res.string.chat_menu_select
	},
)
