package me.floow.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import me.floow.app.ui.components.MainScreenScaffold
import me.floow.app.ui.components.MainScreenScaffoldMode
import me.floow.shared.chats.model.ChatListItemVisualType
import me.floow.shared.chats.ui.SharedChatsRoute
import me.floow.shared.chats.uilogic.ChatsListStateHolder
import me.floow.shared.chats.uilogic.ChatsScreenState
import me.floow.shared.feed.ui.SharedFeedRoute
import me.floow.shared.feed.uilogic.SharedFeedScreenState
import me.floow.shared.feed.uilogic.SharedFeedStateHolder
import me.floow.shared.main.ui.ProfileScreen
import me.floow.domain.models.Post
import me.floow.shared.post.ui.PostScreenModel
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.shared.profile.uilogic.ProfileStateHolder
import me.floow.shared.profile.uilogic.edit.EditProfileOverlayData
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

enum class MainTab {
    Feed,
    Chats,
    Profile
}

@Composable
fun MainTabsScreen(
    onNavigateToPost: (postId: String) -> Unit = {},
    onNavigateToPostWithModel: ((PostScreenModel) -> Unit)? = null,
    onNavigateToCreatePost: () -> Unit = {},
    onNavigateToChat: (conversationId: Long, peerUserId: String, peerDisplayName: String, peerAvatarUrl: String?, peerAvatarThumb: String?, isSavedMessages: Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onNavigateToProfile: (userId: String) -> Unit = {},
    onNavigateToComments: (postId: String) -> Unit = {},
    onNavigateToCommentsWithPost: ((Post) -> Unit)? = null,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToEditProfile: (userId: String) -> Unit = {},
    onNavigateToEditProfileWithData: ((EditProfileOverlayData) -> Unit)? = null,
    onNavigateToEditPost: (ProfilePostItem) -> Unit = {},
    onNavigateToReplies: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    feedStateHolder: SharedFeedStateHolder = koinInject(),
    chatsListStateHolder: ChatsListStateHolder = koinInject(),
    profileStateHolder: ProfileStateHolder = koinInject { parametersOf(null as String?) }
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Feed) }
    val feedState by feedStateHolder.state.collectAsState()
    val feedCanUndo = (feedState as? SharedFeedScreenState.Success)?.canUndo == true
    val chatsState by chatsListStateHolder.state.collectAsState()
    val chatsUnreadCount = (chatsState as? ChatsScreenState.HasData)
        ?.chats
        ?.sumOf { it.unreadCount }
        ?: 0
    val isProfileTab = selectedTab == MainTab.Profile

    var feedEverSelected by remember { mutableStateOf(true) }
    var chatsEverSelected by remember { mutableStateOf(false) }
    var profileEverSelected by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            MainTab.Feed -> feedEverSelected = true
            MainTab.Chats -> chatsEverSelected = true
            MainTab.Profile -> profileEverSelected = true
        }
    }

    MainScreenScaffold(
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        shellMode = if (isProfileTab) MainScreenScaffoldMode.Immersive else MainScreenScaffoldMode.Default,
        feedUndoEnabled = feedCanUndo,
        chatsUnreadCount = chatsUnreadCount,
        onFeedUndoClick = { feedStateHolder.undoLastSwipe() },
        modifier = modifier.fillMaxSize()
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (feedEverSelected) {
                val isFeed = selectedTab == MainTab.Feed
                Box(
                    Modifier.fillMaxSize()
                        .zIndex(if (isFeed) 1f else -1f)
                        .graphicsLayer { alpha = if (isFeed) 1f else 0f }
                        .then(
                            if (isFeed) Modifier else Modifier.pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        )
                ) {
                    SharedFeedRoute(
                        stateHolder = feedStateHolder,
                        onPostCreateClick = onNavigateToCreatePost,
                        onProfileClick = onNavigateToProfile,
                        onCommentsClick = { post ->
                            onNavigateToCommentsWithPost?.invoke(post) ?: onNavigateToComments(post.id)
                        },
                        onSharePost = { post ->
                            val url = "https://floow.me/${post.author.username?.value.orEmpty()}/${post.id}"
                            me.floow.shared.platform.systemShareText(url)
                        },
                        manageLifecycle = isFeed,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (chatsEverSelected) {
                val isChats = selectedTab == MainTab.Chats
                Box(
                    Modifier.fillMaxSize()
                        .zIndex(if (isChats) 1f else -1f)
                        .graphicsLayer { alpha = if (isChats) 1f else 0f }
                        .then(
                            if (isChats) Modifier else Modifier.pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        )
                ) {
                    SharedChatsRoute(
                        stateHolder = chatsListStateHolder,
                        onSearchClick = onNavigateToSearch,
                        onChatClick = { chat ->
                            if (chat.visualType == ChatListItemVisualType.RepliesInbox) {
                                onNavigateToReplies()
                            } else {
                                val convId = chat.conversationId ?: chat.id.removePrefix("chat-").toLongOrNull() ?: 0L
                                val isSaved = chat.visualType == ChatListItemVisualType.SavedMessages || chat.id == "saved"
                                onNavigateToChat(
                                    convId,
                                    chat.peerUserId ?: chat.id,
                                    chat.title,
                                    chat.avatarUrl,
                                    chat.avatarThumb,
                                    isSaved,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (profileEverSelected) {
                val isProfile = selectedTab == MainTab.Profile
                Box(
                    Modifier.fillMaxSize()
                        .zIndex(if (isProfile) 1f else -1f)
                        .graphicsLayer { alpha = if (isProfile) 1f else 0f }
                        .then(
                            if (isProfile) Modifier else Modifier.pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        )
                ) {
                    ProfileScreen(
                        stateHolder = profileStateHolder,
                        onAddPostButtonClick = onNavigateToCreatePost,
                        onProfileEditClick = { data ->
                            if (onNavigateToEditProfileWithData != null) {
                                onNavigateToEditProfileWithData(data)
                            } else {
                                onNavigateToEditProfile(data.username.ifBlank { "self" })
                            }
                        },
                        onMessageButtonClick = { target ->
                            onNavigateToChat(0L, target.userId, target.displayName, target.avatarUrl, null, false)
                        },
                        onShareProfileClick = { slug ->
                            val url = "https://floow.me/$slug"
                            me.floow.shared.platform.systemShareText(url)
                        },
                        onOpenPost = { postModel ->
                            onNavigateToPostWithModel?.invoke(postModel) ?: onNavigateToPost(postModel.postId)
                        },
                        onEditPost = onNavigateToEditPost,
                        onSettingsClick = onNavigateToSettings,
                        onBackClick = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
