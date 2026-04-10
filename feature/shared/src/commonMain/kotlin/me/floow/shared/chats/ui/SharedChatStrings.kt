package me.floow.shared.chats.ui

import androidx.compose.runtime.Composable
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.chat_composer_editing
import flow.feature.shared.generated.resources.chat_composer_replying_to
import flow.feature.shared.generated.resources.chat_copy_selected
import flow.feature.shared.generated.resources.chat_copied
import flow.feature.shared.generated.resources.chat_delete_message_cancel
import flow.feature.shared.generated.resources.chat_delete_message_confirm
import flow.feature.shared.generated.resources.chat_delete_message_text
import flow.feature.shared.generated.resources.chat_delete_message_title
import flow.feature.shared.generated.resources.chat_delete_selected_messages_text
import flow.feature.shared.generated.resources.chat_delete_selected_messages_title
import flow.feature.shared.generated.resources.chat_load_failed
import flow.feature.shared.generated.resources.chat_message_deleted
import flow.feature.shared.generated.resources.chat_no_messages
import flow.feature.shared.generated.resources.chat_pinned
import flow.feature.shared.generated.resources.chat_replies_load_failed
import flow.feature.shared.generated.resources.chat_replies_no_messages
import flow.feature.shared.generated.resources.chat_replies_title
import flow.feature.shared.generated.resources.chat_saved_messages_title
import flow.feature.shared.generated.resources.chat_menu_unpin
import flow.feature.shared.generated.resources.chat_undo_action
import flow.feature.shared.generated.resources.chat_selection_close
import flow.feature.shared.generated.resources.chat_selection_delete
import flow.feature.shared.generated.resources.chat_selection_selected_count
import org.jetbrains.compose.resources.stringResource

internal data class SharedChatStrings(
    val repliesTitle: String,
    val savedMessagesTitle: String,
    val repliesLoadFailed: String,
    val repliesNoMessages: String,
    val chatLoadFailed: String,
    val chatNoMessages: String,
    val composerEditing: String,
    val selectionClose: String,
    val selectionCopy: String,
    val selectionDelete: String,
    val copied: String,
    val deleteMessageTitle: String,
    val deleteMessageText: String,
    val deleteMessageConfirm: String,
    val deleteMessageCancel: String,
    val deleteSelectedMessagesTitle: String,
    val deleteSelectedMessagesText: String,
    val messageDeleted: String,
    val pinnedLabel: String,
    val unpinContentDescription: String,
    val undoAction: String,
)

@Composable
internal fun rememberSharedChatStrings(): SharedChatStrings {
    return SharedChatStrings(
        repliesTitle = stringResource(Res.string.chat_replies_title),
        savedMessagesTitle = stringResource(Res.string.chat_saved_messages_title),
        repliesLoadFailed = stringResource(Res.string.chat_replies_load_failed),
        repliesNoMessages = stringResource(Res.string.chat_replies_no_messages),
        chatLoadFailed = stringResource(Res.string.chat_load_failed),
        chatNoMessages = stringResource(Res.string.chat_no_messages),
        composerEditing = stringResource(Res.string.chat_composer_editing),
        selectionClose = stringResource(Res.string.chat_selection_close),
        selectionCopy = stringResource(Res.string.chat_copy_selected),
        selectionDelete = stringResource(Res.string.chat_selection_delete),
        copied = stringResource(Res.string.chat_copied),
        deleteMessageTitle = stringResource(Res.string.chat_delete_message_title),
        deleteMessageText = stringResource(Res.string.chat_delete_message_text),
        deleteMessageConfirm = stringResource(Res.string.chat_delete_message_confirm),
        deleteMessageCancel = stringResource(Res.string.chat_delete_message_cancel),
        deleteSelectedMessagesTitle = stringResource(Res.string.chat_delete_selected_messages_title),
        deleteSelectedMessagesText = stringResource(Res.string.chat_delete_selected_messages_text),
        messageDeleted = stringResource(Res.string.chat_message_deleted),
        pinnedLabel = stringResource(Res.string.chat_pinned),
        unpinContentDescription = stringResource(Res.string.chat_menu_unpin),
        undoAction = stringResource(Res.string.chat_undo_action),
    )
}

@Composable
internal fun sharedSelectionCountLabel(selectedCount: Int): String {
    return stringResource(Res.string.chat_selection_selected_count, selectedCount)
}

@Composable
internal fun sharedComposerReplyingToLabel(authorName: String): String {
    return stringResource(Res.string.chat_composer_replying_to, authorName)
}
