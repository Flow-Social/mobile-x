package me.floow.shared.main.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.chats_icon
import flow.feature.shared.generated.resources.feed_icon
import flow.feature.shared.generated.resources.feed_icon_active
import flow.feature.shared.generated.resources.feed_bottom_nav_back_label
import flow.feature.shared.generated.resources.profile_icon
import flow.feature.shared.generated.resources.undo_icon
import kotlinx.coroutines.launch
import me.floow.chatssearch.ui.SearchUsersStrings
import me.floow.chatssearch.ui.SharedSearchUsersRoute
import me.floow.chatssearch.uilogic.SearchUsersStateHolder
import me.floow.comments.CommentsRouteInitialData
import me.floow.comments.SharedCommentsRoute
import me.floow.comments.uilogic.CommentsStateHolder
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.models.CommentId
import me.floow.domain.models.Post
import me.floow.domain.models.PostImageVariant
import me.floow.domain.models.resolveReplyTargetCommentCandidates
import me.floow.domain.models.resolvedImageVariants
import me.floow.domain.models.viewerImageUrls
import me.floow.shared.chats.model.ChatListItemVisualType
import me.floow.shared.chats.model.ChatOpenMode
import me.floow.shared.chats.model.RepliesActorTarget
import me.floow.shared.chats.model.RepliesNavigationTarget
import me.floow.shared.chats.model.RepliesThreadItemModel
import me.floow.shared.chats.ui.SharedChatsRoute
import me.floow.shared.chats.ui.SharedDirectChatRoute
import me.floow.shared.chats.ui.SharedRepliesRoute
import me.floow.shared.chats.uilogic.ChatsListStateHolder
import me.floow.shared.chats.uilogic.direct.ChatPresenceContract
import me.floow.shared.chats.uilogic.direct.ChatRealtimeContract
import me.floow.shared.chats.uilogic.direct.DirectChatInitialRequest
import me.floow.shared.chats.uilogic.direct.DirectChatStateHolder
import me.floow.shared.chats.uilogic.replies.RepliesStateHolder
import me.floow.shared.feed.uilogic.SharedFeedScreenState
import me.floow.shared.feed.uilogic.SharedFeedStateHolder
import me.floow.shared.feed.ui.SharedFeedRoute
import me.floow.shared.profile.ui.addpost.CreatePostOverlayScreen
import me.floow.shared.profile.ui.edit.EditProfileScreen
import me.floow.shared.profile.uilogic.addpost.CreatePostStateHolder
import me.floow.shared.profile.uilogic.addpost.EditPostStateHolder
import me.floow.shared.profile.uilogic.ProfileMessageTarget
import me.floow.shared.profile.uilogic.edit.EditProfileOverlayData
import me.floow.shared.profile.uilogic.edit.EditProfileStateHolder
import me.floow.shared.profile.uilogic.ProfileStateHolder
import me.floow.shared.profile.uilogic.ProfileScreenState
import me.floow.shared.profile.uilogic.findPost
import me.floow.shared.profile.uilogic.toPostScreenModel
import me.floow.shared.post.ui.PostScreen
import me.floow.shared.post.ui.PostScreenModel
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.uikit.components.media.viewer2.HostedFullscreenImageViewer
import me.floow.uikit.components.media.viewer2.SharedFullscreenImageViewer
import me.floow.uikit.components.media.transfer.PostMediaTransferStore
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private enum class MainTab(
    val title: String,
    val icon: DrawableResource,
) {
    Feed(title = "Лента", icon = Res.drawable.feed_icon),
    Chats(title = "Чаты", icon = Res.drawable.chats_icon),
    Profile(title = "Профиль", icon = Res.drawable.profile_icon),
}

private enum class SharedShellDiscardDialog {
    CreatePost,
    EditPost,
}



@Composable
fun MainFlowShell(
    profileStateHolder: ProfileStateHolder,
    chatsListStateHolder: ChatsListStateHolder,
    repliesStateHolderProvider: () -> RepliesStateHolder,
    feedStateHolder: SharedFeedStateHolder,
    directMessagesReadCursorStore: me.floow.domain.data.repos.DirectMessagesReadCursorStore,
    directChatStateHolderProvider: () -> DirectChatStateHolder,
    chatRealtimeContract: ChatRealtimeContract? = null,
    chatPresenceContract: ChatPresenceContract? = null,
    createPostStateHolderProvider: () -> CreatePostStateHolder,
    editPostStateHolderProvider: (ProfilePostItem) -> EditPostStateHolder,
    editProfileStateHolderProvider: (EditProfileOverlayData) -> EditProfileStateHolder,
    searchUsersStateHolderProvider: () -> SearchUsersStateHolder,
    commentsStateHolderProvider: () -> CommentsStateHolder,
    postsRepository: PostsRepository,
    postMediaTransferStore: PostMediaTransferStore,
    onCopyText: (String) -> Unit = {},
    buildProfileShareUrl: (String) -> String = { it },
    buildPostShareUrl: (String, String?) -> String = { postId, username ->
        val safeUsername = username.orEmpty()
        "$safeUsername/$postId"
    },
    profileStateHolderProvider: (String?) -> ProfileStateHolder = { profileStateHolder },
    modifier: Modifier = Modifier,
    initialTab: String = MainTab.Feed.name,
) {
    val feedState by feedStateHolder.state.collectAsState()
    val feedCanUndo = (feedState as? SharedFeedScreenState.Success)?.canUndo == true
    var selectedTab by rememberSaveable {
        mutableStateOf(MainTab.entries.firstOrNull { it.name == initialTab } ?: MainTab.Feed)
    }
    val overlayStack = remember { mutableStateListOf<SharedShellOverlay>() }
    val activeOverlay = overlayStack.lastOrNull()
    val pushOverlay: (SharedShellOverlay) -> Unit = { screen ->
        overlayStack.add(screen)
    }
    val mainScope = rememberCoroutineScope()
    
    val popOverlay: () -> Unit = {
        if (overlayStack.isNotEmpty()) {
            overlayStack.removeLast()
        }
    }
    var hostedFullscreenViewer by remember { mutableStateOf<HostedFullscreenImageViewer?>(null) }
    var pendingDiscardDialog by remember { mutableStateOf<SharedShellDiscardDialog?>(null) }
    val showBottomBar = activeOverlay == null
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val postCacheById = remember { mutableMapOf<String, Post>() }
    val showMessage: (String) -> Unit = { message ->
        scope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }
    val openCommentsOverlay: (CommentsRouteInitialData) -> Unit = { initialData ->
        pushOverlay(SharedShellOverlay.ViewComments(initialData))
    }
    val openProfileOverlay: (String) -> Unit = { userId ->
        pushOverlay(SharedShellOverlay.ViewProfile(userId))
    }
    val openProfilePostOverlay: (String, String) -> Unit = { userId, postId ->
        pushOverlay(SharedShellOverlay.ViewProfilePost(userId = userId, postId = postId))
    }
    val openPostOverlay: (PostScreenModel) -> Unit = { model ->
        pushOverlay(SharedShellOverlay.ViewPost(model))
    }
    val openDirectChatRequest: (DirectChatInitialRequest?) -> Unit = { request ->
        if (request == null) {
            showMessage("Не удалось открыть диалог")
        } else {
            pushOverlay(SharedShellOverlay.DirectChat(request))
        }
    }
    val openDirectChatTarget: (ProfileMessageTarget) -> Unit = { target ->
        openDirectChatRequest(target.toDirectChatInitialRequest())
    }
    val shareProfileLink: (String) -> Unit = { slug ->
        val url = buildProfileShareUrl(slug)
        me.floow.shared.platform.systemShareText(url)
        showMessage("Ссылка на профиль скопирована")
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        MainTab.entries.forEach { tab ->
                            val isSelected = selectedTab == tab
                            val useFeedUndoUi = tab == MainTab.Feed && isSelected && feedCanUndo
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    if (useFeedUndoUi) {
                                        feedStateHolder.undoLastSwipe()
                                    } else {
                                        selectedTab = tab
                                    }
                                },
                                icon = {
                                    val icon = when {
                                        useFeedUndoUi -> Res.drawable.undo_icon
                                        tab == MainTab.Feed && isSelected -> Res.drawable.feed_icon_active
                                        else -> tab.icon
                                    }
                                    Icon(painter = painterResource(icon), contentDescription = tab.title)
                                },
                                label = {
                                    Text(
                                        if (useFeedUndoUi) {
                                            stringResource(Res.string.feed_bottom_nav_back_label)
                                        } else {
                                            tab.title
                                        }
                                    )
                                }
                            )
                        }
                    }
                } else {
                    // Keep scaffold content padding stable while overlays cover the shell.
                    NavigationBar(containerColor = Color.Transparent) {}
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Crossfade(
                    targetState = selectedTab,
                    modifier = Modifier.fillMaxSize(),
                    label = "main_flow_tab"
                ) { tab ->
                    LaunchedEffect(tab) {
                        if (tab == MainTab.Profile) {
                            profileStateHolder.loadIfNeeded()
                        }
                    }
                    when (tab) {
                        MainTab.Feed -> SharedFeedRoute(
                            stateHolder = feedStateHolder,
                            onPostCreateClick = {
                                pushOverlay(SharedShellOverlay.CreatePost)
                            },
                            onProfileClick = openProfileOverlay,
                            onCommentsClick = { post ->
                                val selfUserId = profileStateHolder.currentSelfUserIdOrNull()
                                openCommentsOverlay(
                                    post.toCommentsRouteInitialData(
                                        isSelf = post.author.id == selfUserId || post.author.id == "me",
                                        mediaTransferToken = null,
                                        initialTargetCommentId = null,
                                        fallbackTargetCommentId = null,
                                    )
                                )
                            },
                            onSharePost = { post ->
                                val url = buildPostShareUrl(post.id, post.author.username?.value)
                                me.floow.shared.platform.systemShareText(url)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Ссылка на пост скопирована")
                                }
                            },
                            onEditPost = { post ->
                                pushOverlay(SharedShellOverlay.EditPost(post))
                            },
                            modifier = Modifier.fillMaxSize(),
                            onPresentFullscreenViewer = { hostedFullscreenViewer = it },
                        )
                        MainTab.Chats -> SharedChatsRoute(
                            stateHolder = chatsListStateHolder,
                            onSearchClick = {
                                pushOverlay(SharedShellOverlay.SearchUsers)
                            },
                            onChatClick = { chat ->
                                mainScope.launch {
                                    val newOverlay = if (chat.visualType == ChatListItemVisualType.RepliesInbox) {
                                        SharedShellOverlay.RepliesInbox(
                                            openMode = ChatOpenMode.FROM_LAST_SEEN,
                                            anchorSeq = null,
                                        )
                                    } else {
                                        chat.toDirectChatInitialRequestOrNull(directMessagesReadCursorStore)?.let(SharedShellOverlay::DirectChat)
                                            ?: run {
                                                showMessage("Не удалось открыть диалог")
                                                null
                                            }
                                    }
                                    if (newOverlay != null) {
                                        pushOverlay(newOverlay)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        MainTab.Profile -> ProfileScreen(
                            stateHolder = profileStateHolder,
                            onAddPostButtonClick = {
                                pushOverlay(SharedShellOverlay.CreatePost)
                            },
                            onMessageButtonClick = openDirectChatTarget,
                            onShareProfileClick = shareProfileLink,
                            onProfileEditClick = { data ->
                                pushOverlay(SharedShellOverlay.EditProfile(data))
                            },
                            onEditPost = { post ->
                                pushOverlay(SharedShellOverlay.EditPost(post))
                            },
                            onOpenPost = openPostOverlay,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        when (val overlay = activeOverlay) {
            SharedShellOverlay.CreatePost -> {
                val holder = remember(createPostStateHolderProvider) {
                    createPostStateHolderProvider()
                }
                val uiState by holder.state.collectAsState()
                LaunchedEffect(holder) {
                    holder.events.collect { event ->
                        when (event) {
                            is CreatePostStateHolder.Event.Created -> {
                                profileStateHolder.insertCreatedPost(event.post)
                                popOverlay()
                            }
                                is CreatePostStateHolder.Event.ShowMessage -> {
                                showMessage(event.message)
                                }
                            }
                        }
                }
                CreatePostOverlayScreen(
                    uiState = uiState,
                    onCloseClick = {
                        if (uiState.hasDraft) {
                            pendingDiscardDialog = SharedShellDiscardDialog.CreatePost
                        } else {
                            popOverlay()
                        }
                    },
                    onPickImagesClick = holder::pickImages,
                    onRemoveImageClick = holder::removeImage,
                    onCommitImageOrder = holder::reorderImages,
                    onDescriptionChange = holder::updateDescription,
                    onCategoryChange = holder::updateCategory,
                    onRetryCategoriesClick = holder::reloadCategories,
                    onPublishClick = holder::publish,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is SharedShellOverlay.EditPost -> {
                val holder = remember(overlay.post, editPostStateHolderProvider) {
                    editPostStateHolderProvider(overlay.post)
                }
                val uiState by holder.state.collectAsState()
                LaunchedEffect(holder) {
                    holder.events.collect { event ->
                        when (event) {
                            is EditPostStateHolder.Event.Saved -> {
                                profileStateHolder.replaceEditedPost(event.post)
                                feedStateHolder.applyPostEdit(
                                    postId = event.post.id,
                                    description = event.post.description,
                                    imageUrls = event.post.imageUrls,
                                )
                                popOverlay()
                            }
                            is EditPostStateHolder.Event.ShowMessage -> {
                                showMessage(event.message)
                            }
                        }
                    }
                }
                CreatePostOverlayScreen(
                    uiState = uiState,
                    onCloseClick = {
                        if (uiState.canPublish) {
                            pendingDiscardDialog = SharedShellDiscardDialog.EditPost
                        } else {
                            popOverlay()
                        }
                    },
                    onPickImagesClick = {},
                    onRemoveImageClick = holder::removeImage,
                    onCommitImageOrder = holder::reorderImages,
                    onDescriptionChange = holder::updateDescription,
                    onCategoryChange = {},
                    onRetryCategoriesClick = {},
                    onPublishClick = holder::save,
                    onImageCardClick = holder::replaceImage,
                    showCategorySelector = false,
                    allowAddImageCard = false,
                    blockAutoFocus = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is SharedShellOverlay.EditProfile -> {
                val holder = remember(overlay.data, editProfileStateHolderProvider) {
                    editProfileStateHolderProvider(overlay.data)
                }
                val uiState by holder.state.collectAsState()
                LaunchedEffect(holder) {
                    holder.events.collect { event ->
                        when (event) {
                            is EditProfileStateHolder.Event.Saved -> {
                                profileStateHolder.updateProfileHeader(event.profile)
                                popOverlay()
                            }
                            is EditProfileStateHolder.Event.ShowMessage -> {
                                showMessage(event.message)
                            }
                        }
                    }
                }
                EditProfileScreen(
                    state = uiState,
                    onBackClick = popOverlay,
                    onDoneClick = holder::save,
                    onAvatarPickerClick = holder::pickAvatar,
                    onBackgroundPickerClick = holder::pickBackground,
                    onNameChange = holder::updateName,
                    onUsernameChange = holder::updateUsername,
                    onBiographyChange = holder::updateBio,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is SharedShellOverlay.ViewPost -> {
                PostScreen(
                    model = overlay.model,
                    onBackClick = popOverlay,
                    onProfileClick = {
                        val userId = overlay.model.authorUsername
                            ?.takeIf(String::isNotBlank)
                            ?: overlay.model.authorId.takeIf(String::isNotBlank)
                        if (userId != null) {
                            openProfileOverlay(userId)
                        } else {
                            showMessage("Не удалось открыть профиль автора")
                        }
                    },
                    onCommentsClick = {
                        val mediaTransferToken = overlay.model.mediaTransferSnapshot
                            ?.let(postMediaTransferStore::save)
                        scope.launch {
                            val initialData = when (val result = postsRepository.getPostById(overlay.model.postId)) {
                                is GetDataResponse.Success -> result.data.toCommentsRouteInitialData(
                                    isSelf = overlay.model.isSelf,
                                    mediaTransferToken = mediaTransferToken,
                                    initialTargetCommentId = null,
                                    fallbackTargetCommentId = null,
                                )
                                is GetDataResponse.Error -> overlay.model.toCommentsRouteInitialData(mediaTransferToken)
                            }
                            openCommentsOverlay(initialData)
                        }
                    },
                    onShareClick = {
                        val url = buildPostShareUrl(overlay.model.postId, overlay.model.authorUsername)
                        me.floow.shared.platform.systemShareText(url)
                        scope.launch {
                            snackbarHostState.showSnackbar("Ссылка на пост скопирована")
                        }
                    },
                    onEditPost = { snapshot ->
                        val matchingPost = (profileStateHolder.state.value as? ProfileScreenState.Success)
                            ?.findPost(overlay.model.postId)
                        if (matchingPost != null) {
                            pushOverlay(SharedShellOverlay.EditPost(matchingPost))
                        } else {
                            showMessage("Не удалось открыть редактирование поста")
                        }
                    },
                    onDeletePost = {
                        scope.launch {
                            profileStateHolder.deletePost(overlay.model.postId)
                                .onSuccess {
                                    popOverlay()
                                    showMessage("Пост удален")
                                }
                                .onFailure {
                                    showMessage("Не удалось удалить пост")
                                }
                        }
                    },
                    onProfileTagClick = {
                        openProfileOverlay(it)
                    },
                    onPostLinkClick = { username, postId ->
                        openProfilePostOverlay(username, postId)
                    },
                    onPresentFullscreenViewer = { hostedFullscreenViewer = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            SharedShellOverlay.SearchUsers -> {
                val holder = remember(searchUsersStateHolderProvider) {
                    searchUsersStateHolderProvider()
                }
                SharedSearchUsersRoute(
                    onBackClick = popOverlay,
                    onUserPick = { userId ->
                        pushOverlay(SharedShellOverlay.ViewProfile(userId))
                    },
                    component = holder,
                    strings = SearchUsersStrings(
                        searchFieldPlaceholder = "Поиск",
                        recentSearchesTitle = "Недавние поиски",
                        globalSearchTitle = "Глобальный поиск",
                        showMoreLabel = "Показать еще",
                        showLessLabel = "Показать меньше",
                        messageSearchTitle = "Поиск по сообщениям",
                        onlineLabel = "В сети",
                        offlineLabel = "Не в сети",
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is SharedShellOverlay.RepliesInbox -> {
                val holder = remember(
                    overlay.openMode,
                    overlay.anchorSeq,
                    repliesStateHolderProvider,
                ) {
                    repliesStateHolderProvider()
                }
                SharedRepliesRoute(
                    stateHolder = holder,
                    onBackClick = popOverlay,
                    onOpenThread = { target ->
                        scope.launch {
                            val targetCommentIds = target.resolveCommentTargetCandidates()
                            val targetCommentId = targetCommentIds.firstOrNull()
                            val fallbackTargetCommentId = targetCommentIds.getOrNull(1)
                            if (target.postId.isBlank()) {
                                showMessage("Не удалось открыть комментарий")
                                return@launch
                            }
                            val cachedPost = postCacheById[target.postId]
                            if (cachedPost != null) {
                                openCommentsOverlay(
                                    cachedPost.toCommentsRouteInitialData(
                                        isSelf = cachedPost.author.id == profileStateHolder.currentSelfUserIdOrNull(),
                                        mediaTransferToken = null,
                                        initialTargetCommentId = targetCommentId,
                                        fallbackTargetCommentId = fallbackTargetCommentId,
                                    )
                                )
                                return@launch
                            }
                            when (val postResult = postsRepository.getPostById(target.postId)) {
                                is GetDataResponse.Success -> {
                                    val post = postResult.data
                                    if (post.id.isNotBlank()) {
                                        postCacheById[post.id] = post
                                    }
                                    openCommentsOverlay(
                                        post.toCommentsRouteInitialData(
                                            isSelf = post.author.id == profileStateHolder.currentSelfUserIdOrNull(),
                                            mediaTransferToken = null,
                                            initialTargetCommentId = targetCommentId,
                                            fallbackTargetCommentId = fallbackTargetCommentId,
                                        )
                                    )
                                }

                                is GetDataResponse.Error -> {
                                    showMessage("Не удалось открыть комментарий")
                                }
                            }
                        }
                    },
                    onOpenActorProfile = { target ->
                        openProfileOverlay(target.userId)
                    },
                    onShowMessage = showMessage,
                    onSeeAll = chatsListStateHolder::refresh,
                    openMode = overlay.openMode,
                    anchorSeq = overlay.anchorSeq,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is SharedShellOverlay.ViewComments -> {
                val holder = remember(
                    overlay.initialData.postId,
                    overlay.initialData.initialTargetCommentId,
                    overlay.initialData.fallbackTargetCommentId,
                ) {
                    commentsStateHolderProvider()
                }
                DisposableEffect(holder) {
                    onDispose {
                        holder.dispose()
                    }
                }
                SharedCommentsRoute(
                    initialData = overlay.initialData,
                    onBackClick = popOverlay,
                    onAuthorClick = openProfileOverlay,
                    onCopyText = onCopyText,
                    component = holder,
                    mediaTransferStore = postMediaTransferStore,
                    commentsTitle = "Комментарии",
                    commentsPhotoSubtitle = "Фото",
                    onPresentFullscreenViewer = { hostedFullscreenViewer = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is SharedShellOverlay.ViewProfile -> {
                val holder = remember(overlay.userId) {
                    profileStateHolderProvider(overlay.userId)
                }
                SharedProfileOverlayRoute(
                    stateHolder = holder,
                    onBackClick = popOverlay,
                    onOpenDirectChat = openDirectChatTarget,
                    onShareProfile = shareProfileLink,
                    onOpenPost = openPostOverlay,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is SharedShellOverlay.ViewProfilePost -> {
                val holder = remember(overlay.userId) {
                    profileStateHolderProvider(overlay.userId)
                }
                SharedProfileOverlayRoute(
                    stateHolder = holder,
                    linkedPostId = overlay.postId,
                    onLinkedPostMissing = {
                        openProfileOverlay(overlay.userId)
                        showMessage("Не удалось открыть пост по ссылке")
                    },
                    onBackClick = popOverlay,
                    onOpenDirectChat = openDirectChatTarget,
                    onShareProfile = shareProfileLink,
                    onOpenPost = openPostOverlay,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is SharedShellOverlay.DirectChat -> {
                val holder = remember(overlay.request, directChatStateHolderProvider) {
                    directChatStateHolderProvider()
                }
                SharedDirectChatRoute(
                    stateHolder = holder,
                    initialRequest = overlay.request,
                    onBackClick = popOverlay,
                    onShowMessage = showMessage,
                    onCopyText = onCopyText,
                    onHeaderClick = if (overlay.request.isSavedMessages || overlay.request.peerUserId.isBlank()) {
                        null
                    } else {
                        { openProfileOverlay(overlay.request.peerUserId) }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            null -> Unit
        }

        when (pendingDiscardDialog) {
            SharedShellDiscardDialog.CreatePost -> {
                AlertDialog(
                    onDismissRequest = { pendingDiscardDialog = null },
                    title = { Text("Удалить черновик?") },
                    text = { Text("Есть несохраненные изменения. Если выйти, черновик будет удален.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                popOverlay()
                                pendingDiscardDialog = null
                            }
                        ) {
                            Text("Удалить")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDiscardDialog = null }) {
                            Text("Отмена")
                        }
                    }
                )
            }

            SharedShellDiscardDialog.EditPost -> {
                AlertDialog(
                    onDismissRequest = { pendingDiscardDialog = null },
                    title = { Text("Отменить изменения?") },
                    text = { Text("Есть несохраненные изменения. Если выйти, они будут потеряны.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                popOverlay()
                                pendingDiscardDialog = null
                            }
                        ) {
                            Text("Выйти")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDiscardDialog = null }) {
                            Text("Отмена")
                        }
                    }
                )
            }

            null -> Unit
        }

        hostedFullscreenViewer?.let { viewer ->
            viewer.modelProvider()?.let { model ->
                SharedFullscreenImageViewer(
                    model = model,
                    state = viewer.state,
                    onAction = viewer.onAction,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private suspend fun me.floow.shared.chats.model.ChatListItemModel.toDirectChatInitialRequestOrNull(
    store: me.floow.domain.data.repos.DirectMessagesReadCursorStore
): DirectChatInitialRequest? {
    val resolvedConversationId = conversationId?.takeIf { it > 0L }
    val isSavedMessagesChat = visualType == ChatListItemVisualType.SavedMessages
    if (!isSavedMessagesChat && resolvedConversationId == null) return null
    
    val hasStoredOpenAnchor = resolvedConversationId?.let { id ->
        store.getOpenViewportSnapshot(id).anchorMessageId?.let { anchorId -> anchorId > 0L } ?: false
    } ?: false
    
    val openMode = when {
        hasStoredOpenAnchor -> ChatOpenMode.FROM_LAST_SEEN
        unreadCount > 0 -> ChatOpenMode.FROM_UNREAD
        else -> ChatOpenMode.FROM_LAST_SEEN
    }
    
    return DirectChatInitialRequest(
        peerUserId = peerUserId.orEmpty(),
        peerDisplayName = title,
        peerAvatarUrl = avatarUrl,
        conversationId = resolvedConversationId,
        isSavedMessages = isSavedMessagesChat,
        allowCreateFromPeerUserId = false,
        openMode = openMode,
    )
}

private fun ProfileMessageTarget.toDirectChatInitialRequest(): DirectChatInitialRequest {
    return DirectChatInitialRequest(
        peerUserId = userId,
        peerDisplayName = displayName,
        peerAvatarUrl = avatarUrl,
        conversationId = null,
        isSavedMessages = false,
        allowCreateFromPeerUserId = true,
    )
}

@Composable
private fun SharedProfileOverlayRoute(
    stateHolder: ProfileStateHolder,
    onBackClick: () -> Unit,
    onOpenDirectChat: (ProfileMessageTarget) -> Unit,
    onShareProfile: (String) -> Unit,
    onOpenPost: (PostScreenModel) -> Unit,
    linkedPostId: String? = null,
    onLinkedPostMissing: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val profileState by stateHolder.state.collectAsState()
    LaunchedEffect(profileState, linkedPostId) {
        val pendingPostId = linkedPostId ?: return@LaunchedEffect
        val successState = profileState as? ProfileScreenState.Success ?: return@LaunchedEffect
        val model = successState.toPostScreenModel(
            postId = pendingPostId,
            sourceSnapshot = null,
        )
        if (model != null) {
            onOpenPost(model)
        } else {
            onLinkedPostMissing?.invoke()
        }
    }
    ProfileScreen(
        stateHolder = stateHolder,
        onBackClick = onBackClick,
        onMessageButtonClick = onOpenDirectChat,
        onShareProfileClick = onShareProfile,
        onOpenPost = onOpenPost,
        modifier = modifier,
    )
}

private fun PostScreenModel.toCommentsRouteInitialData(
    mediaTransferToken: String?,
): CommentsRouteInitialData {
    return CommentsRouteInitialData(
        postId = postId,
        postAuthorId = authorId,
        postAuthorName = authorName.orEmpty(),
        postAuthorAvatarUrl = authorAvatarUrl,
        postAuthorUsername = authorUsername,
        postImageUrls = imageUrls,
        postImageVariants = emptyList<PostImageVariant>(),
        postDescription = description,
        postCreatedAt = 0L,
        postLikesCount = likesCount,
        postIsSelf = isSelf,
        mediaTransferToken = mediaTransferToken,
    )
}

private fun RepliesNavigationTarget.resolveCommentTargetCandidates(): List<CommentId> {
    return resolveReplyTargetCommentCandidates(
        commentId = commentId,
        replyToCommentId = replyToCommentId,
        threadId = threadId,
    )
}

private fun Post.toCommentsRouteInitialData(
    isSelf: Boolean,
    mediaTransferToken: String?,
    initialTargetCommentId: CommentId?,
    fallbackTargetCommentId: CommentId?,
): CommentsRouteInitialData {
    return CommentsRouteInitialData(
        postId = id,
        postAuthorId = author.id,
        postAuthorName = author.name?.value ?: author.username?.value.orEmpty(),
        postAuthorAvatarUrl = author.avatarUrl,
        postAuthorUsername = author.username?.value,
        postImageUrls = content.viewerImageUrls(),
        postImageVariants = content.resolvedImageVariants(),
        postDescription = content.description,
        postCreatedAt = createdAt,
        postLikesCount = likesCount,
        postIsSelf = isSelf,
        mediaTransferToken = mediaTransferToken,
        initialTargetCommentId = initialTargetCommentId,
        fallbackTargetCommentId = fallbackTargetCommentId,
    )
}

private fun ProfileStateHolder.currentSelfUserIdOrNull(): String? {
    val state = state.value as? ProfileScreenState.Success ?: return null
    return state.id.takeIf { state.isSelf }
}
