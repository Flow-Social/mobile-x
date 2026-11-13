package me.floow.shared.chats.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberChatMediaPermissionRequester(
    onGranted: (ChatMediaPermissionType) -> Unit,
): (ChatMediaPermissionType) -> Boolean {
    return remember {
        { true }
    }
}
