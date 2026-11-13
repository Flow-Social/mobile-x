package me.floow.shared.chats.media.attachment

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ChatAttachmentPickerModal(
    onDismissRequest: () -> Unit,
    onImagesSelected: (uris: List<String>, caption: String?) -> Unit,
    modifier: Modifier = Modifier,
    initialSelectedUris: List<String> = emptyList(),
    maxSelection: Int = 10,
) {
    ChatAttachmentPickerSheet(
        onDismissRequest = onDismissRequest,
        onImagesSelected = onImagesSelected,
        modifier = modifier,
        initialSelectedUris = initialSelectedUris,
        maxSelection = maxSelection,
    )
}
