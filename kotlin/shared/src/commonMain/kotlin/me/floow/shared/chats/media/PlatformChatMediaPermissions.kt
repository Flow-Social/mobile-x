package me.floow.shared.chats.media

import androidx.compose.runtime.Composable

enum class ChatMediaPermissionType {
    VOICE,
    VIDEO
}

@Composable
expect fun rememberChatMediaPermissionRequester(
    onGranted: (ChatMediaPermissionType) -> Unit = {},
): (ChatMediaPermissionType) -> Boolean
