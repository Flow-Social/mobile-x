package me.floow.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import me.floow.chatssearch.ui.SearchUsersStrings
import me.floow.chatssearch.ui.SharedSearchUsersRoute
import me.floow.chatssearch.uilogic.SearchUsersStateHolder
import me.floow.comments.CommentsRouteInitialData
import me.floow.comments.SharedCommentsRoute
import me.floow.comments.uilogic.CommentsStateHolder
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.SnackbarHostState
import me.floow.domain.auth.AuthTokenState
import me.floow.domain.auth.TokenStorage
import me.floow.shared.login.uilogic.LoginState
import me.floow.shared.login.uilogic.LoginViewModel
import me.floow.shared.login.ui.login.LoginScreen
import me.floow.shared.login.uilogic.createprofile.CreateProfileState
import me.floow.shared.login.uilogic.createprofile.CreateProfileStateHolder
import me.floow.shared.login.ui.createprofile.CreateProfileScreen
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.models.Post
import me.floow.domain.models.PostAuthor
import me.floow.domain.models.PostCategories
import me.floow.domain.models.PostContent
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.shared.chats.ui.SharedDirectChatRoute
import me.floow.shared.chats.ui.SharedRepliesRoute
import me.floow.shared.chats.uilogic.direct.DirectChatInitialRequest
import me.floow.shared.chats.uilogic.direct.DirectChatStateHolder
import me.floow.shared.chats.uilogic.replies.RepliesStateHolder
import me.floow.shared.main.ui.ProfileScreen
import me.floow.shared.post.ui.PostScreen
import me.floow.shared.post.ui.PostScreenModel
import me.floow.shared.post.ui.PostScreenPresentation
import me.floow.shared.profile.ui.edit.EditProfileScreen
import me.floow.shared.profile.uilogic.ProfileStateHolder
import me.floow.shared.profile.uilogic.ProfileScreenState
import me.floow.shared.profile.uilogic.toActionContext
import me.floow.shared.profile.uilogic.toEditProfileOverlayData
import me.floow.shared.profile.uilogic.edit.EditProfileOverlayData
import me.floow.shared.profile.uilogic.edit.EditProfileStateHolder
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.shared.profile.ui.settings.AppIconMode
import me.floow.shared.profile.ui.settings.AppThemeMode
import me.floow.shared.profile.ui.settings.PlatformThemeBridge
import me.floow.shared.profile.ui.settings.SettingsScreen
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.zIndex
import me.floow.uikit.components.loading.FlowLoadingIndicator
import me.floow.uikit.components.media.transfer.PostMediaTransferStore
import me.floow.uikit.components.media.viewer2.HostedFullscreenImageViewer
import me.floow.uikit.components.media.viewer2.FullscreenImageViewer
import me.floow.uikit.components.media.viewer2.ViewerPhase
import me.floow.uikit.components.media.viewer2.calculateViewerBackgroundCornerRadius
import me.floow.uikit.components.media.viewer2.calculateViewerBackgroundScale
import me.floow.uikit.util.SwipeBackOverlay
import me.floow.uikit.util.LocalSwipeBackOverlayClose
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun FlowRootNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val overlayState = rememberFlowOverlayState()
    val density = LocalDensity.current
    val tokenStorage = koinInject<TokenStorage>()
    val authState by tokenStorage.authState.collectAsState()
    val defaultProfileStateHolder = koinInject<ProfileStateHolder> {
        parametersOf(null as String?)
    }

    val initialStartDestination = remember {
        if (tokenStorage.authState.value is AuthTokenState.Authenticated) {
            MainTabsDestination
        } else {
            LoginDestination
        }
    }

    LaunchedEffect(authState) {
        if (authState is AuthTokenState.Unauthenticated) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute?.contains("LoginDestination") != true) {
                navController.navigate(LoginDestination) {
                    popUpTo(0) { inclusive = true }
                }
            }
        } else if (authState is AuthTokenState.Authenticated) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute?.contains("LoginDestination") == true) {
                navController.navigate(MainTabsDestination) {
                    popUpTo(LoginDestination) { inclusive = true }
                }
            }
        }
    }

    val overlayStack = remember { mutableStateListOf<SubscreenEntry>() }
    var overlayIdCounter by remember { mutableStateOf(0L) }
    var overlayProgress by remember { mutableFloatStateOf(0f) }
    val enteredIds = remember { mutableStateMapOf<Long, Boolean>() }
    val subscreenStateHolder = rememberSaveableStateHolder()

    fun pushOverlay(screen: FlowSubscreen) {
        overlayIdCounter += 1L
        overlayStack.add(SubscreenEntry(id = overlayIdCounter, screen = screen))
        overlayProgress = 1f
    }

    fun popOverlay() {
        if (overlayStack.isNotEmpty()) {
            val removed = overlayStack.removeAt(overlayStack.lastIndex)
            enteredIds.remove(removed.id)
        }
        overlayProgress = 0f
    }

    LaunchedEffect(overlayStack.size) {
        val activeIds = overlayStack.map { it.id }.toSet()
        val toRemove = enteredIds.keys.filter { it !in activeIds }
        toRemove.forEach { enteredIds.remove(it) }
    }

    LaunchedEffect(navController) {
        FlowDeepLinkHandler.deepLinks.collect { uriString ->
            handleFlowDeepLink(
                uriString = uriString,
                navController = navController,
                onOpenSubscreen = { pushOverlay(it) },
            )
        }
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val isSubscreenOpen = overlayStack.isNotEmpty()

    var hostedFullscreenViewer by remember { mutableStateOf<HostedFullscreenImageViewer?>(null) }
    val postCacheById = remember { mutableStateMapOf<String, Post>() }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        NavHost(
            navController = navController,
            startDestination = initialStartDestination,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = overlayProgress
                    var scale = if (overlayStack.size == 1) {
                        0.95f + (0.05f * p)
                    } else if (overlayStack.size > 1) {
                        0.95f
                    } else {
                        1f
                    }
                    if (overlayStack.size == 1) {
                        translationX = -size.width * 0.12f * (1f - p)
                    } else if (overlayStack.size > 1) {
                        translationX = -size.width * 0.12f
                    }
                    val vState = hostedFullscreenViewer?.state
                    if (vState != null && vState.phase != ViewerPhase.Closed) {
                        val densityVal = density.density
                        val dismissThresholdPx = 140f * densityVal
                        val dismissProgress = (kotlin.math.abs(vState.dismissOffsetY) / (dismissThresholdPx * 1.5f)).coerceIn(0f, 1f)
                        val bgScale = calculateViewerBackgroundScale(
                            phase = vState.phase,
                            transitionProgress = vState.transitionProgress,
                            dismissProgress = dismissProgress,
                            closeSceneStartProgress = vState.closeSceneStartProgress,
                            zoomScale = -0.05f,
                        )
                        scale *= bgScale
                        val cornerRadiusDp = calculateViewerBackgroundCornerRadius(
                            phase = vState.phase,
                            transitionProgress = vState.transitionProgress,
                            dismissProgress = dismissProgress,
                            closeSceneStartProgress = vState.closeSceneStartProgress,
                            maxCornerRadiusDp = 16f,
                        )
                        if (cornerRadiusDp > 0.5f) {
                            clip = true
                            shape = RoundedCornerShape(cornerRadiusDp.dp)
                        }
                    }
                    val overlayP = overlayState.revealProgress.coerceIn(0f, 1f)
                    if (overlayP > 0.001f) {
                        scale *= (1f - (0.09f * overlayP))
                        val maxTranslationY = with(density) { 30.dp.toPx() }
                        translationY = maxTranslationY * overlayP
                        if (overlayP > 0.01f) {
                            clip = true
                            shape = RoundedCornerShape(24.dp)
                        }
                    }
                    scaleX = scale
                    scaleY = scale
                }
                .then(
                    if (isSubscreenOpen) Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } else Modifier
                ),
        ) {
            composable<LoginDestination> {
                val loginViewModel = koinInject<LoginViewModel>()
                val loginState by loginViewModel.state.collectAsState()

                LaunchedEffect(loginState) {
                    when (loginState) {
                        is LoginState.Authenticated -> {
                            navController.navigate(MainTabsDestination) {
                                popUpTo(LoginDestination) { inclusive = true }
                            }
                        }
                        is LoginState.PendingRegistration -> {
                            navController.navigate(CreateProfileDestination) {
                                popUpTo(LoginDestination) { inclusive = false }
                            }
                        }
                        else -> Unit
                    }
                }

                LoginScreen(
                    isLoading = loginState is LoginState.Loading,
                    onLoginClick = { loginViewModel.signInWithGoogle() },
                )
            }

            composable<CreateProfileDestination> {
                val createProfileStateHolder = koinInject<CreateProfileStateHolder>()
                val createProfileState by createProfileStateHolder.state.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                LaunchedEffect(createProfileStateHolder) {
                    createProfileStateHolder.events.collect { event ->
                        when (event) {
                            is CreateProfileStateHolder.Event.Completed -> {
                                navController.navigate(MainTabsDestination) {
                                    popUpTo(LoginDestination) { inclusive = true }
                                }
                            }
                            is CreateProfileStateHolder.Event.ShowMessage -> {
                                snackbarHostState.showSnackbar(event.message)
                            }
                            else -> Unit
                        }
                    }
                }

                CreateProfileScreen(
                    state = createProfileState,
                    snackbarHostState = snackbarHostState,
                    onNameChange = createProfileStateHolder::updateName,
                    onUsernameChange = createProfileStateHolder::updateUsername,
                    onBiographyChange = createProfileStateHolder::updateBio,
                    onDoneClick = createProfileStateHolder::createProfile,
                )
            }

            composable<MainTabsDestination> {
                MainTabsScreen(
                    onNavigateToPost = { postId ->
                        pushOverlay(
                            FlowSubscreen.PostDetail(
                                postId = postId,
                                presentation = PostPresentation.FullScreen,
                            )
                        )
                    },
                    onNavigateToPostWithModel = { postModel ->
                        postCacheById[postModel.postId] = postModel.toPost()
                        overlayState.show(FlowOverlay.ViewPost(postModel))
                    },
                    onNavigateToCreatePost = {
                        overlayState.show(FlowOverlay.CreatePost)
                    },
                    onNavigateToChat = { convId, peerId, name, avatar, isSaved ->
                        pushOverlay(
                            FlowSubscreen.DirectChat(
                                conversationId = convId,
                                peerUserId = peerId,
                                peerDisplayName = name,
                                peerAvatarUrl = avatar,
                                isSavedMessages = isSaved,
                            )
                        )
                    },
                    onNavigateToProfile = { userId ->
                        pushOverlay(FlowSubscreen.Profile(userId = userId))
                    },
                    onNavigateToComments = { postId ->
                        pushOverlay(FlowSubscreen.Comments(postId = postId))
                    },
                    onNavigateToCommentsWithPost = { post ->
                        postCacheById[post.id] = post
                        pushOverlay(FlowSubscreen.Comments(postId = post.id))
                    },
                    onNavigateToSearch = {
                        pushOverlay(FlowSubscreen.SearchUsers)
                    },
                    onNavigateToEditProfileWithData = { data ->
                        overlayState.show(FlowOverlay.EditProfile(data))
                    },
                    onNavigateToEditProfile = { userId ->
                        val user = (defaultProfileStateHolder.state.value as? ProfileScreenState.Success)?.toActionContext()
                        overlayState.show(
                            FlowOverlay.EditProfile(
                                user?.toEditProfileOverlayData() ?: EditProfileOverlayData(
                                    name = "",
                                    username = if (userId != "self") userId else "",
                                    bio = "",
                                    avatarUrl = null,
                                    backgroundUrl = null,
                                )
                            )
                        )
                    },
                    onNavigateToEditPost = { postItem ->
                        overlayState.show(FlowOverlay.EditPost(postItem))
                    },
                    onNavigateToReplies = {
                        pushOverlay(FlowSubscreen.Replies)
                    },
                    onNavigateToSettings = {
                        pushOverlay(FlowSubscreen.Settings)
                    },
                    onNavigateToBump = { slug ->
                        overlayState.show(FlowOverlay.ProfileBump(slug))
                    },
                )
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val widthPx = with(density) { maxWidth.toPx() }
            val topIndex = overlayStack.lastIndex

            overlayStack.forEachIndexed { index, entry ->
                val isTop = index == topIndex
                val isUnderlay = index == topIndex - 1
                val entryKey = "subscreen_${entry.id}_${entry.screen}"

                val layerModifier = Modifier
                    .fillMaxSize()
                    .zIndex(index.toFloat() + 1f)
                    .then(
                        if (isUnderlay) {
                            Modifier.graphicsLayer {
                                translationX = -widthPx * 0.12f * (1f - overlayProgress)
                            }
                        } else if (!isTop) {
                            Modifier.graphicsLayer {
                                translationX = -widthPx * 0.12f
                            }
                        } else {
                            Modifier
                        }
                    )

                val movableOverlayContent = remember(entry.id) {
                    movableContentOf { close: () -> Unit ->
                        subscreenStateHolder.SaveableStateProvider(entryKey) {
                            key(entryKey) {
                                SubscreenContent(
                                    subscreen = entry.screen,
                                    onClose = close,
                                    pushOverlay = { pushOverlay(it) },
                                    postCacheById = postCacheById,
                                    overlayState = overlayState,
                                    defaultProfileStateHolder = defaultProfileStateHolder,
                                    onPresentFullscreenViewer = { hostedFullscreenViewer = it },
                                )
                            }
                        }
                    }
                }

                if (isTop) {
                    val animateIn = enteredIds[entry.id] != true
                    LaunchedEffect(entry.id) {
                        enteredIds[entry.id] = true
                    }
                    SwipeBackOverlay(
                        onClose = { popOverlay() },
                        onProgressChange = { overlayProgress = it },
                        entryKey = entry.id,
                        animateIn = animateIn,
                        modifier = layerModifier,
                    ) {
                        movableOverlayContent { popOverlay() }
                    }
                } else {
                    Box(modifier = layerModifier) {
                        movableOverlayContent {}
                    }
                }
            }
        }

        FlowOverlayHost(
            overlayState = overlayState,
            onNavigateToProfile = { userId ->
                pushOverlay(FlowSubscreen.Profile(userId = userId))
            },
            profileStateHolder = defaultProfileStateHolder,
            onNavigateToComments = { postId ->
                pushOverlay(FlowSubscreen.Comments(postId = postId))
            },
            onPresentFullscreenViewer = { viewer ->
                hostedFullscreenViewer = viewer
            },
            modifier = Modifier.graphicsLayer {
                val vState = hostedFullscreenViewer?.state
                if (vState != null && vState.phase != ViewerPhase.Closed) {
                    val densityVal = density.density
                    val dismissThresholdPx = 140f * densityVal
                    val dismissProgress = (kotlin.math.abs(vState.dismissOffsetY) / (dismissThresholdPx * 1.5f)).coerceIn(0f, 1f)
                    val bgScale = calculateViewerBackgroundScale(
                        phase = vState.phase,
                        transitionProgress = vState.transitionProgress,
                        dismissProgress = dismissProgress,
                        closeSceneStartProgress = vState.closeSceneStartProgress,
                        zoomScale = -0.05f,
                    )
                    scaleX = bgScale
                    scaleY = bgScale
                    val cornerRadiusDp = calculateViewerBackgroundCornerRadius(
                        phase = vState.phase,
                        transitionProgress = vState.transitionProgress,
                        dismissProgress = dismissProgress,
                        closeSceneStartProgress = vState.closeSceneStartProgress,
                        maxCornerRadiusDp = 16f,
                    )
                    if (cornerRadiusDp > 0.5f) {
                        clip = true
                        shape = RoundedCornerShape(cornerRadiusDp.dp)
                    }
                }
            },
        )

        hostedFullscreenViewer?.let { viewer ->
            viewer.modelProvider()?.let { model ->
                FullscreenImageViewer(
                    model = model,
                    state = viewer.state,
                    onAction = viewer.onAction,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(100f),
                )
            }
        }
    }
}

@Composable
private fun SubscreenContent(
    subscreen: FlowSubscreen,
    onClose: () -> Unit,
    pushOverlay: (FlowSubscreen) -> Unit,
    postCacheById: MutableMap<String, Post>,
    overlayState: FlowOverlayState,
    defaultProfileStateHolder: ProfileStateHolder,
    onPresentFullscreenViewer: (HostedFullscreenImageViewer?) -> Unit,
) {
    val overlayClose = LocalSwipeBackOverlayClose.current
    val handleBack: () -> Unit = {
        if (overlayClose != null) overlayClose() else onClose()
    }

    when (subscreen) {
        is FlowSubscreen.Replies -> {
            val repliesStateHolder = koinInject<RepliesStateHolder>()
            SharedRepliesRoute(
                stateHolder = repliesStateHolder,
                onBackClick = handleBack,
                onOpenThread = { target ->
                    pushOverlay(FlowSubscreen.Comments(postId = target.postId))
                },
                onOpenActorProfile = { target ->
                    pushOverlay(FlowSubscreen.Profile(userId = target.userId))
                },
                onShowMessage = {},
                modifier = Modifier.fillMaxSize(),
            )
        }

        is FlowSubscreen.DirectChat -> {
            val directChatStateHolder = koinInject<DirectChatStateHolder>()
            val isSaved = subscreen.isSavedMessages || subscreen.conversationId == 100L || subscreen.peerUserId == "saved" || subscreen.peerUserId == "self"
            SharedDirectChatRoute(
                stateHolder = directChatStateHolder,
                initialRequest = DirectChatInitialRequest(
                    conversationId = subscreen.conversationId.takeIf { it > 0L },
                    peerUserId = subscreen.peerUserId,
                    peerDisplayName = subscreen.peerDisplayName.ifBlank { if (isSaved) "Избранное" else "Чат" },
                    peerAvatarUrl = subscreen.peerAvatarUrl,
                    isSavedMessages = isSaved,
                    allowCreateFromPeerUserId = subscreen.conversationId <= 0L && subscreen.peerUserId.isNotBlank() && !isSaved,
                ),
                onBackClick = handleBack,
                onShowMessage = {},
                onHeaderClick = {
                    if (subscreen.peerUserId.isNotBlank()) {
                        val seed = me.floow.shared.profile.uilogic.ProfileHeaderSeed(
                            userId = subscreen.peerUserId,
                            displayName = subscreen.peerDisplayName.takeIf { it.isNotBlank() },
                            username = null,
                            avatarUrl = subscreen.peerAvatarUrl,
                        )
                        pushOverlay(FlowSubscreen.Profile(userId = subscreen.peerUserId, headerSeed = seed))
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        is FlowSubscreen.Profile -> {
            val profileRepository = koinInject<me.floow.shared.profile.uilogic.ProfileRepository>()
            subscreen.headerSeed?.let { seed ->
                profileRepository.putHeaderSeed(seed)
            }
            val profileStateHolder = koinInject<ProfileStateHolder> {
                parametersOf(subscreen.userId.takeIf { it != "self" && it != "me" })
            }
            ProfileScreen(
                stateHolder = profileStateHolder,
                onBackClick = handleBack,
                onOpenPost = { postModel ->
                    overlayState.show(FlowOverlay.ViewPost(postModel))
                },
                onEditPost = { postItem ->
                    overlayState.show(FlowOverlay.EditPost(postItem))
                },
                onProfileEditClick = { data ->
                    overlayState.show(FlowOverlay.EditProfile(data))
                },
                onMessageButtonClick = { target ->
                    pushOverlay(
                        FlowSubscreen.DirectChat(
                            conversationId = 0L,
                            peerUserId = target.userId,
                            peerDisplayName = target.displayName,
                            peerAvatarUrl = target.avatarUrl,
                        )
                    )
                },
                onSettingsClick = {
                    pushOverlay(FlowSubscreen.Settings)
                },
                onBumpClick = { slug ->
                    overlayState.show(FlowOverlay.ProfileBump(slug))
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        is FlowSubscreen.Comments -> {
            val commentsStateHolder = koinInject<CommentsStateHolder>()
            val mediaTransferStore = koinInject<PostMediaTransferStore>()
            val postsRepository = koinInject<PostsRepository>()
            val cachedPost = remember(subscreen.postId) { postCacheById[subscreen.postId] }
            var initialCommentsData by remember(subscreen.postId) {
                mutableStateOf(
                    if (cachedPost != null) {
                        CommentsRouteInitialData(
                            postId = cachedPost.id,
                            postAuthorId = cachedPost.author.id,
                            postAuthorName = cachedPost.author.name?.value ?: cachedPost.author.username?.value ?: "Flow",
                            postAuthorAvatarUrl = cachedPost.author.avatarUrl,
                            postAuthorAvatarThumb = cachedPost.author.avatarThumb,
                            postAuthorUsername = cachedPost.author.username?.value,
                            postImageUrls = cachedPost.content.imageUrls,
                            postImageVariants = cachedPost.content.imageVariants,
                            postDescription = cachedPost.content.description,
                            postCreatedAt = cachedPost.createdAt,
                            postLikesCount = cachedPost.likesCount,
                            postIsSelf = false,
                        )
                    } else {
                        CommentsRouteInitialData(
                            postId = subscreen.postId,
                            postAuthorId = "",
                            postAuthorName = "Flow",
                            postAuthorAvatarUrl = null,
                            postAuthorUsername = null,
                            postImageUrls = emptyList(),
                            postImageVariants = emptyList(),
                            postDescription = null,
                            postCreatedAt = 0L,
                            postLikesCount = 0,
                            postIsSelf = false,
                        )
                    }
                )
            }

            LaunchedEffect(subscreen.postId) {
                when (val res = postsRepository.getPostById(subscreen.postId)) {
                    is GetDataResponse.Success<Post> -> {
                        val post = res.data
                        postCacheById[post.id] = post
                        initialCommentsData = CommentsRouteInitialData(
                            postId = post.id,
                            postAuthorId = post.author.id,
                            postAuthorName = post.author.name?.value ?: post.author.username?.value ?: "Flow",
                            postAuthorAvatarUrl = post.author.avatarUrl,
                            postAuthorAvatarThumb = post.author.avatarThumb,
                            postAuthorUsername = post.author.username?.value,
                            postImageUrls = post.content.imageUrls,
                            postImageVariants = post.content.imageVariants,
                            postDescription = post.content.description,
                            postCreatedAt = post.createdAt,
                            postLikesCount = post.likesCount,
                            postIsSelf = false,
                        )
                    }
                    else -> Unit
                }
            }

            SharedCommentsRoute(
                initialData = initialCommentsData,
                onBackClick = handleBack,
                onAuthorClick = { authorId ->
                    pushOverlay(FlowSubscreen.Profile(userId = authorId))
                },
                component = commentsStateHolder,
                mediaTransferStore = mediaTransferStore,
                commentsTitle = "Комментарии",
                commentsPhotoSubtitle = "Фото",
                modifier = Modifier.fillMaxSize(),
            )
        }

        is FlowSubscreen.PostDetail -> {
            FlowPostRoute(
                postId = subscreen.postId,
                presentation = subscreen.presentation,
                cachedPost = postCacheById[subscreen.postId],
                onBackClick = handleBack,
                onProfileClick = { authorId ->
                    pushOverlay(FlowSubscreen.Profile(userId = authorId))
                },
                onCommentsClick = { postId ->
                    pushOverlay(FlowSubscreen.Comments(postId = postId))
                },
                onEditPost = { postItem ->
                    overlayState.show(FlowOverlay.EditPost(postItem))
                },
                onDeletePost = handleBack,
                onPresentFullscreenViewer = onPresentFullscreenViewer,
                onPostCached = { post ->
                    postCacheById[post.id] = post
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        is FlowSubscreen.SearchUsers -> {
            val searchUsersStateHolder = koinInject<SearchUsersStateHolder>()
            SharedSearchUsersRoute(
                onBackClick = handleBack,
                onUserPick = { userId ->
                    pushOverlay(FlowSubscreen.Profile(userId = userId))
                },
                component = searchUsersStateHolder,
                strings = SearchUsersStrings(
                    searchFieldPlaceholder = "Поиск по пользователям...",
                    recentSearchesTitle = "Недавние",
                    globalSearchTitle = "Глобальный поиск",
                    showMoreLabel = "Показать больше",
                    showLessLabel = "Скрыть",
                    messageSearchTitle = "Сообщения",
                    onlineLabel = "в сети",
                    offlineLabel = "не в сети",
                ),
                modifier = Modifier.fillMaxSize(),
            )
        }

        is FlowSubscreen.Settings -> {
            val tokenStorage = koinInject<TokenStorage>()
            val scope = rememberCoroutineScope()
            var themeMode by remember { mutableStateOf(PlatformThemeBridge.getTheme()) }
            var iconMode by remember { mutableStateOf(AppIconMode.DEFAULT) }

            SettingsScreen(
                currentTheme = themeMode,
                onThemeChange = {
                    themeMode = it
                    PlatformThemeBridge.setTheme(it)
                },
                currentIcon = iconMode,
                onIconChange = { iconMode = it },
                onLogout = {
                    scope.launch {
                        tokenStorage.clearTokens()
                    }
                },
                onBackClick = handleBack,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun Post.toPostScreenModel(): PostScreenModel {
    return PostScreenModel(
        postId = id,
        imageUrls = content.imageUrls,
        imageVariants = content.imageVariants,
        description = content.description,
        authorId = author.id,
        authorName = author.name?.value,
        authorUsername = author.username?.value,
        authorAvatarUrl = author.avatarUrl,
        authorAvatarThumb = author.avatarThumb,
        category = category,
        likesCount = likesCount,
        commentsCount = commentsCount,
        commentersPreview = commentersPreview,
        isSelf = false,
    )
}

internal fun PostScreenModel.toPost(): Post {
    return Post(
        id = postId,
        author = PostAuthor(
            id = authorId,
            name = authorName?.let { runCatching { ProfileName.create(it) }.getOrNull() },
            username = authorUsername?.let { runCatching { ProfileUsername.create(it) }.getOrNull() },
            avatarUrl = authorAvatarUrl,
            avatarThumb = authorAvatarThumb,
        ),
        content = PostContent(
            imageUrls = imageUrls,
            description = description,
            imageVariants = imageVariants,
        ),
        category = category ?: PostCategories.TECH,
        createdAt = 0L,
        likesCount = likesCount,
        commentsCount = commentsCount,
        commentersPreview = commentersPreview,
    )
}

private fun PostScreenModel.toProfilePostItem(): ProfilePostItem {
    return ProfilePostItem(
        id = postId,
        description = description,
        lqUrl = imageUrls.firstOrNull(),
        previewUrl = imageUrls.firstOrNull(),
        fullUrl = imageUrls.firstOrNull(),
        viewerUrls = imageUrls,
        previewUrls = imageUrls,
        likesCount = likesCount,
        commentsCount = commentsCount,
        commentersPreview = commentersPreview,
        category = category,
    )
}

@Composable
internal fun FlowPostRoute(
    postId: String,
    presentation: PostPresentation = PostPresentation.ProfileModal,
    cachedPost: Post? = null,
    onBackClick: () -> Unit,
    onProfileClick: (userId: String) -> Unit,
    onCommentsClick: (postId: String) -> Unit,
    onEditPost: (ProfilePostItem) -> Unit = {},
    onDeletePost: () -> Unit = onBackClick,
    onPresentFullscreenViewer: ((HostedFullscreenImageViewer?) -> Unit)? = null,
    onPostCached: ((Post) -> Unit)? = null,
    onProfileModalScrollAtTopChanged: (Boolean) -> Unit = {},
    topBarModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val postsRepository = koinInject<PostsRepository>()
    val postMediaTransferStore = koinInject<PostMediaTransferStore>()
    val scope = rememberCoroutineScope()

    var postModel by remember(postId) {
        val initial = cachedPost?.toPostScreenModel()?.copy(
            mediaTransferSnapshot = postMediaTransferStore.peek(postId)
        )
        mutableStateOf<PostScreenModel?>(initial)
    }
    var isLoading by remember(postId) { mutableStateOf(postModel == null) }
    var errorMessage by remember(postId) { mutableStateOf<String?>(null) }

    LaunchedEffect(postId) {
        if (postModel == null) {
            isLoading = true
        }
        when (val res = postsRepository.getPostById(postId)) {
            is GetDataResponse.Success<Post> -> {
                val post = res.data
                onPostCached?.invoke(post)
                postModel = post.toPostScreenModel().copy(
                    mediaTransferSnapshot = postModel?.mediaTransferSnapshot ?: postMediaTransferStore.peek(postId)
                )
                isLoading = false
            }
            is GetDataResponse.Error -> {
                if (postModel == null) {
                    errorMessage = "Не удалось загрузить пост"
                }
                isLoading = false
            }
        }
    }

    val currentModel = postModel
    when {
        currentModel != null -> {
            PostScreen(
                model = currentModel,
                onBackClick = onBackClick,
                onProfileClick = onProfileClick,
                onCommentsClick = { snapshot ->
                    snapshot?.let(postMediaTransferStore::save)
                    onPostCached?.invoke(currentModel.toPost())
                    onCommentsClick(currentModel.postId)
                },
                onShareClick = {
                    val url = "https://floow.me/${currentModel.authorUsername.orEmpty()}/${currentModel.postId}"
                    me.floow.shared.platform.systemShareText(url)
                },
                onEditPost = { onEditPost(currentModel.toProfilePostItem()) },
                onDeletePost = {
                    scope.launch {
                        postsRepository.deletePost(currentModel.postId)
                        onDeletePost()
                    }
                },
                onPresentFullscreenViewer = onPresentFullscreenViewer,
                onProfileModalScrollAtTopChanged = onProfileModalScrollAtTopChanged,
                topBarModifier = topBarModifier,
                presentation = when (presentation) {
                    PostPresentation.FullScreen -> PostScreenPresentation.FullscreenLegacy
                    PostPresentation.ProfileModal -> PostScreenPresentation.ProfileModal
                },
                modifier = modifier,
            )
        }
        isLoading -> {
            Box(
                modifier = modifier.background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                FlowLoadingIndicator()
            }
        }
        errorMessage != null -> {
            Box(
                modifier = modifier.background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
