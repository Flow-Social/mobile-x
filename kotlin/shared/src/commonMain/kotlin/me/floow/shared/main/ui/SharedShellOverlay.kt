package me.floow.shared.main.ui

import me.floow.comments.CommentsRouteInitialData
import me.floow.shared.chats.model.ChatOpenMode
import me.floow.shared.chats.model.RepliesNavigationTarget
import me.floow.shared.chats.uilogic.direct.DirectChatInitialRequest
import me.floow.shared.post.ui.PostScreenModel
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.shared.profile.uilogic.edit.EditProfileOverlayData

sealed interface SharedShellOverlay {
    data object CreatePost : SharedShellOverlay
    data class EditPost(val post: ProfilePostItem) : SharedShellOverlay
    data class EditProfile(val data: EditProfileOverlayData) : SharedShellOverlay
    data class ViewPost(val model: PostScreenModel) : SharedShellOverlay
    data class ViewComments(val initialData: CommentsRouteInitialData) : SharedShellOverlay
    data class ViewProfile(val userId: String) : SharedShellOverlay
    data class ViewProfilePost(val userId: String, val postId: String) : SharedShellOverlay
    data object SearchUsers : SharedShellOverlay
    data class RepliesInbox(
        val openMode: ChatOpenMode = ChatOpenMode.FROM_LAST_SEEN,
        val anchorSeq: Long? = null,
    ) : SharedShellOverlay
    data class DirectChat(val request: DirectChatInitialRequest) : SharedShellOverlay
}
