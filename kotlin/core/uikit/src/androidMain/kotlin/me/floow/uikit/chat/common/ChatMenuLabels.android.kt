package me.floow.uikit.chat.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import me.floow.uikit.chat.components.labelResId
import me.floow.uikit.chat.model.ChatContextMenuAction

@Composable
internal actual fun chatMenuLabel(action: ChatContextMenuAction): String =
	stringResource(action.labelResId)
