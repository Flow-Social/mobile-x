package me.floow.app.navigation

import kotlinx.serialization.Serializable
import me.floow.app.R

sealed interface NavigationRoute

@Serializable
data object AuthDestinationsCluster : NavigationRoute

@Serializable
data object MainDestinationsCluster : NavigationRoute

@Serializable
data object RegistrationScreen : NavigationRoute

@Serializable
data object CreateProfileScreen : NavigationRoute

@Serializable
data object LoginScreen : NavigationRoute

@Serializable
data object FeedScreen : NavigationRoute

@Serializable
data class ChatScreen(
    val interlocutorId: String = "",
    val interlocutorName: String = "",
    val interlocutorAvatarUri: String? = null,
) : NavigationRoute

@Serializable
data object ChatsScreen : NavigationRoute

@Serializable
data object SearchUsersScreen : NavigationRoute

@Serializable
data class ProfileScreen(val userId: String) : NavigationRoute

@Serializable
data object SelfProfileScreen : NavigationRoute

@Serializable
data class EditProfileScreen(
    val name: String = "",
    val username: String = "",
    val description: String = "",
    val avatarUrl: String? = null,
    val backgroundUrl: String? = null,
) : NavigationRoute

@Serializable
data class PostScreen(
	val postId: String,
	val imageUrls: List<String>,
	val description: String? = null,
	val mediaTransferToken: String? = null,
	val authorId: String,
	val authorName: String? = null,
	val authorUsername: String? = null,
	val authorAvatarUrl: String? = null,
	val category: String,
	val createdAt: Long,
	val likesCount: Int = 0,
	val commentsCount: Int = 0,
	val commentersPreview: List<String> = emptyList(),
	val isSelf: Boolean = false
) : NavigationRoute

@Serializable
data class PostDeepLinkScreen(
	val postId: String,
	val username: String? = null
) : NavigationRoute

val bottomNavigationItems = listOf(
    BottomNavigationItem(
        route = FeedScreen,
        titleId = R.string.feed_bottom_nav_label,
        drawableIconId = me.floow.uikit.R.drawable.feed_icon,
    ),
    BottomNavigationItem(
        route = ChatsScreen,
        titleId = R.string.chats_bottom_nav_label,
        drawableIconId = me.floow.uikit.R.drawable.chats_icon,
    ),
    BottomNavigationItem(
        route = SelfProfileScreen,
        titleId = R.string.profile_bottom_nav_label,
        drawableIconId = me.floow.uikit.R.drawable.profile_icon,
    ),
)

val mainBottomBarNavigationDestinations = listOf(
    FeedScreen,
    ChatsScreen,
    SelfProfileScreen
)
