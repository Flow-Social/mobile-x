package me.floow.app.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LoginDestination

@Serializable
data object CreateProfileDestination

@Serializable
data object MainTabsDestination

@Serializable
data object FeedTabDestination

@Serializable
data object ChatsTabDestination

@Serializable
data object ProfileTabDestination

@Serializable
enum class PostPresentation {
    FullScreen,
    ProfileModal,
}

@Serializable
data class PostDestination(
    val postId: String,
    val presentation: PostPresentation = PostPresentation.ProfileModal,
)

@Serializable
data class DirectChatDestination(
    val conversationId: Long,
    val peerUserId: String = "",
    val peerDisplayName: String = "",
    val peerAvatarUrl: String? = null,
    val isSavedMessages: Boolean = false,
)

@Serializable
data class ProfileDestination(
    val userId: String = "self",
)

@Serializable
data class CommentsDestination(
    val postId: String,
)

@Serializable
data object SearchUsersDestination

@Serializable
data class EditProfileDestination(
    val userId: String = "self",
)

@Serializable
data object RepliesDestination

@Serializable
data object SettingsDestination

sealed interface FlowSubscreen {
    data object Replies : FlowSubscreen
    data class DirectChat(
        val conversationId: Long,
        val peerUserId: String = "",
        val peerDisplayName: String = "",
        val peerAvatarUrl: String? = null,
        val isSavedMessages: Boolean = false,
    ) : FlowSubscreen
    data class Profile(
        val userId: String = "self",
        val headerSeed: me.floow.shared.profile.uilogic.ProfileHeaderSeed? = null,
    ) : FlowSubscreen
    data class Comments(val postId: String) : FlowSubscreen
    data class PostDetail(
        val postId: String,
        val presentation: PostPresentation = PostPresentation.ProfileModal,
    ) : FlowSubscreen
    data object SearchUsers : FlowSubscreen
    data object Settings : FlowSubscreen
}

data class SubscreenEntry(
    val id: Long,
    val screen: FlowSubscreen,
)

