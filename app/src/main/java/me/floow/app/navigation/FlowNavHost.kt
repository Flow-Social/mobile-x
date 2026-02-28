package me.floow.app.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.blur
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import coil.memory.MemoryCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.floow.app.deeplink.DeepLinkDispatcher
import me.floow.app.navigation.bottomNavigationItems
import me.floow.app.ui.components.FlowBottomBar
import me.floow.app.ui.components.MainScreenScaffold
import me.floow.chats.ChatRoute
import me.floow.chats.ChatRouteInitialData
import me.floow.chats.ChatsRoute
import me.floow.chatssearch.ui.SearchUsersRoute
import me.floow.comments.CommentsRoute
import me.floow.comments.CommentsRouteInitialData
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.models.PostContent
import me.floow.domain.models.PostImageVariant
import me.floow.domain.models.previewImageUrls
import me.floow.domain.models.resolvedImageVariants
import me.floow.domain.models.viewerImageUrls
import me.floow.feed.ui.FeedRoute
import me.floow.feed.uilogic.FeedScreenState
import me.floow.feed.uilogic.FeedViewModel
import me.floow.login.ui.createprofile.CreateProfileRoute
import me.floow.login.ui.login.LoginRoute
import me.floow.post.ui.PostDeepLinkRoute
import me.floow.post.ui.PostRoute
import me.floow.profile.ui.addpost.CreatePostOverlayRoute
import me.floow.profile.ui.addpost.EditPostOverlayRoute
import me.floow.profile.ui.edit.EditProfileRoute
import me.floow.profile.ui.edit.EditProfileRouteInitialData
import me.floow.profile.ui.profile.ProfileRoute
import me.floow.profile.uilogic.addpost.AddPostViewModel
import me.floow.profile.uilogic.addpost.EditPostViewModel
import me.floow.profile.uilogic.profile.ProfileScreenState
import me.floow.profile.uilogic.profile.ProfileScreenViewModel
import me.floow.uikit.components.media.transfer.PostMediaSourceOwner
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.components.media.transfer.PostMediaTransferStore
import me.floow.uikit.util.SwipeBackOverlay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

private sealed interface OverlayScreen {
	data class OverlayChat(
		val interlocutorId: String,
		val interlocutorName: String,
		val interlocutorAvatarUri: String?
	) : OverlayScreen
	data class OverlayComments(
		val postId: String,
		val postAuthorId: String,
		val postAuthorName: String,
		val postAuthorAvatarUrl: String?,
		val postAuthorUsername: String?,
		val postImageUrls: List<String>,
		val postImageVariants: List<PostImageVariant>,
		val postDescription: String?,
		val postCreatedAt: Long,
		val postCategory: String,
		val postLikesCount: Int,
		val postIsSelf: Boolean,
		val mediaTransferToken: String? = null
	) : OverlayScreen

	data class OverlayPostDeepLink(
		val postId: String,
		val username: String
	) : OverlayScreen

	data class OverlayPost(
		val postId: String,
		val imageUrls: List<String>,
		val imageVariants: List<PostImageVariant>,
		val mediaTransferToken: String? = null,
		val description: String?,
		val authorId: String,
		val authorName: String?,
		val authorUsername: String?,
		val authorAvatarUrl: String?,
		val category: String,
		val createdAt: Long,
		val likesCount: Int,
		val commentsCount: Int,
		val commentersPreview: List<String>,
		val isSelf: Boolean
	) : OverlayScreen

	data class OverlayProfile(
		val userId: String
	) : OverlayScreen

	data class OverlaySearchUsers(
		val source: String = "default"
	) : OverlayScreen

	data class OverlayEditProfile(
		val name: String,
		val username: String,
		val description: String,
		val avatarUrl: String? = null,
		val backgroundUrl: String? = null,
	) : OverlayScreen

	data class OverlayEditPost(
		val postId: String,
		val initialDescription: String?,
		val initialImageUrls: List<String>,
		val mediaTransferToken: String? = null,
		val sourcePostOverlayId: Long? = null,
	) : OverlayScreen

	object OverlayCreatePost : OverlayScreen
}

private data class OverlayEntry(
	val id: Long,
	val screen: OverlayScreen
)

private data class PostContentOverride(
	val description: String?,
	val imageUrls: List<String>,
)

@Composable
fun FlowNavHost(
	navController: NavHostController,
	startDestination: NavigationRoute,
	modifier: Modifier = Modifier,
) {
	val context = LocalContext.current
	val authenticationManager: AuthenticationManager = koinInject()
	val deepLinkDispatcher: DeepLinkDispatcher = koinInject()
	val mediaTransferStore: PostMediaTransferStore = koinInject()
	val usernameToIdCache: me.floow.domain.cache.UsernameToIdCache = koinInject()
	val authState by authenticationManager.authenticationStateFlow.collectAsState()
	val isSignedIn = authState.let { authenticationManager.isSignedIn() }
	val navBackStackEntry by navController.currentBackStackEntryAsState()
	val currentDestination = navBackStackEntry?.destination
	val isProfileScreen = currentDestination?.hierarchy?.any { destination ->
		destination.hasRoute(SelfProfileScreen::class) || destination.hasRoute(ProfileScreen::class)
	} ?: false

	val overlayStack = remember { mutableStateListOf<OverlayEntry>() }
	var overlayIdCounter by remember { mutableStateOf(0L) }
	var overlayProgress by remember { mutableStateOf(0f) }
	val enteredIds = remember { mutableStateMapOf<Long, Boolean>() }
	val overlayParallaxEnabled = remember { mutableStateMapOf<Long, Boolean>() }
	val createPostHasDraft = remember { mutableStateMapOf<Long, Boolean>() }
	val editPostHasChanges = remember { mutableStateMapOf<Long, Boolean>() }
	var activeFeedViewModel by remember { mutableStateOf<FeedViewModel?>(null) }
	var createPostDiscardDialogOverlayId by remember { mutableStateOf<Long?>(null) }
	var editPostDiscardDialogOverlayId by remember { mutableStateOf<Long?>(null) }
	val snackbarHostState = remember { SnackbarHostState() }
	val snackbarScope = rememberCoroutineScope()
	val previewCacheWindow = remember { ArrayDeque<Pair<String, List<String>>>() }
	val editedPostOverrides = remember { mutableStateMapOf<String, PostContentOverride>() }

	val deepLinkIntent by deepLinkDispatcher.intentFlow.collectAsState()

	LaunchedEffect(deepLinkIntent, isSignedIn) {
		val intent = deepLinkIntent ?: return@LaunchedEffect
		if (isSignedIn) {
			navController.handleDeepLink(intent)
		}
		deepLinkDispatcher.clear()
	}

	fun shareText(text: String) {
		val sendIntent = Intent().apply {
			setAction(Intent.ACTION_SEND)
			putExtra(Intent.EXTRA_TEXT, text)
			setType("text/plain")
		}

		val shareIntent = Intent.createChooser(sendIntent, null)
		context.startActivity(shareIntent)
	}

	fun evictImageUrlsFromMemory(urls: List<String>) {
		val cache = Coil.imageLoader(context).memoryCache ?: return
		urls.filter { it.isNotBlank() }.forEach { raw ->
			cache.remove(MemoryCache.Key(raw))
		}
	}

	fun pushOverlay(screen: OverlayScreen) {
		overlayIdCounter += 1
		val newId = overlayIdCounter
		overlayStack.add(OverlayEntry(id = newId, screen = screen))
		// For transparent Profile loading we disable parallax until content is loaded (Success).
		overlayParallaxEnabled[newId] = screen !is OverlayScreen.OverlayProfile &&
			screen !is OverlayScreen.OverlayCreatePost &&
			screen !is OverlayScreen.OverlayEditPost
		// New overlay starts offscreen to the right (progress=1) and animates in to 0.
		// Keeping this in sync prevents the underlay from "jumping" on the first frame.
		overlayProgress = 1f
	}

	fun buildPostMediaSnapshot(
		post: me.floow.domain.models.Post,
		owner: PostMediaSourceOwner
	): PostMediaSourceSnapshot {
		return PostMediaSourceSnapshot(
			postId = post.id,
			owner = owner,
			selectedIndex = 0,
			urls = post.content.viewerImageUrls(),
			previewUrls = post.content.previewImageUrls()
		)
	}

	fun openCommentsOverlay(
		post: me.floow.domain.models.Post,
		isSelf: Boolean,
		sourceSnapshot: PostMediaSourceSnapshot? = null
	) {
		post.author.username?.value?.let { username ->
			if (username.isNotBlank() && post.author.id.isNotBlank()) {
				usernameToIdCache.put(username, post.author.id)
			}
		}
		val authorName = post.author.name?.value ?: post.author.username?.value.orEmpty()
		val effectiveSourceSnapshot = sourceSnapshot?.takeIf { it.postId == post.id }
			?: buildPostMediaSnapshot(post = post, owner = PostMediaSourceOwner.FEED)
		val mediaTransferToken = mediaTransferStore.save(effectiveSourceSnapshot)
		pushOverlay(
			OverlayScreen.OverlayComments(
				postId = post.id,
				postAuthorId = post.author.id,
				postAuthorName = authorName,
				postAuthorAvatarUrl = post.author.avatarUrl,
				postAuthorUsername = post.author.username?.value,
				postImageUrls = post.content.viewerImageUrls(),
				postImageVariants = post.content.resolvedImageVariants(),
				postDescription = post.content.description,
				postCreatedAt = post.createdAt,
				postCategory = post.category,
				postLikesCount = post.likesCount,
				postIsSelf = isSelf,
				mediaTransferToken = mediaTransferToken
			)
		)
	}

	fun openPostOverlay(
		post: me.floow.domain.models.Post,
		isSelf: Boolean,
		sourceSnapshot: PostMediaSourceSnapshot? = null
	) {
		post.author.username?.value?.let { username ->
			if (username.isNotBlank() && post.author.id.isNotBlank()) {
				usernameToIdCache.put(username, post.author.id)
			}
		}
		val previewImageUrls = post.content.previewImageUrls()
		val detailImageUrls = post.content.viewerImageUrls()
		val imageVariants = post.content.resolvedImageVariants()
		val effectiveSourceSnapshot = sourceSnapshot?.takeIf { it.postId == post.id }
			?: buildPostMediaSnapshot(post = post, owner = PostMediaSourceOwner.FEED)
		val mediaTransferToken = mediaTransferStore.save(effectiveSourceSnapshot)

		previewCacheWindow.removeAll { it.first == post.id }
		previewCacheWindow.addLast(post.id to previewImageUrls)
		while (previewCacheWindow.size > 3) {
			val removed = previewCacheWindow.removeFirst()
			evictImageUrlsFromMemory(removed.second)
		}

		pushOverlay(
			OverlayScreen.OverlayPost(
				postId = post.id,
				imageUrls = detailImageUrls,
				imageVariants = imageVariants,
				mediaTransferToken = mediaTransferToken,
				description = post.content.description,
				authorId = post.author.id,
				authorName = post.author.name?.value,
				authorUsername = post.author.username?.value,
				authorAvatarUrl = post.author.avatarUrl,
				category = post.category,
				createdAt = post.createdAt,
				likesCount = post.likesCount,
				commentsCount = post.commentsCount,
				commentersPreview = post.commentersPreview,
				isSelf = isSelf
			)
		)
	}

	fun openEditPostOverlay(
		postId: String,
		description: String?,
		imageUrls: List<String>,
		sourceSnapshot: PostMediaSourceSnapshot? = null,
		sourcePostOverlayId: Long? = null,
	) {
		val normalizedImageUrls = imageUrls
			.map { it.trim() }
			.filter { it.isNotBlank() }
		if (normalizedImageUrls.isEmpty()) {
			Toast.makeText(context, "Для редактирования нужен минимум 1 медиа-файл", Toast.LENGTH_SHORT).show()
			return
		}
		val effectiveSourceSnapshot = sourceSnapshot?.takeIf { it.postId == postId }
			?: PostMediaSourceSnapshot(
				postId = postId,
				owner = PostMediaSourceOwner.POST,
				selectedIndex = 0,
				urls = normalizedImageUrls,
				previewUrls = normalizedImageUrls,
			)
		val mediaTransferToken = mediaTransferStore.save(effectiveSourceSnapshot)
		pushOverlay(
			OverlayScreen.OverlayEditPost(
				postId = postId,
				initialDescription = description,
				initialImageUrls = normalizedImageUrls,
				mediaTransferToken = mediaTransferToken,
				sourcePostOverlayId = sourcePostOverlayId,
			)
		)
	}

	fun openEditPostOverlay(
		post: me.floow.domain.models.Post,
		sourceSnapshot: PostMediaSourceSnapshot? = null,
		sourcePostOverlayId: Long? = null
	) {
		val imageUrls = post.content.viewerImageUrls().ifEmpty { post.content.imageUrls }
		if (imageUrls.isEmpty()) {
			Toast.makeText(context, "Для редактирования нужен минимум 1 медиа-файл", Toast.LENGTH_SHORT).show()
			return
		}
		val effectiveSourceSnapshot = sourceSnapshot?.takeIf { it.postId == post.id }
			?: buildPostMediaSnapshot(
				post = post,
				owner = PostMediaSourceOwner.FEED
			)
		openEditPostOverlay(
			postId = post.id,
			description = post.content.description,
			imageUrls = imageUrls,
			sourceSnapshot = effectiveSourceSnapshot,
			sourcePostOverlayId = sourcePostOverlayId,
		)
	}

	fun applyEditedPostOverride(postId: String, description: String?, imageUrls: List<String>) {
		val normalizedImageUrls = imageUrls
			.map { it.trim() }
			.filter { it.isNotBlank() }
		if (postId.isBlank() || normalizedImageUrls.isEmpty()) return
		editedPostOverrides[postId] = PostContentOverride(
			description = description,
			imageUrls = normalizedImageUrls,
		)
	}

	fun resolvePostContentOverride(
		postId: String,
		defaultDescription: String?,
		defaultImageUrls: List<String>,
	): PostContentOverride {
		val normalizedDefaultImageUrls = defaultImageUrls
			.map { it.trim() }
			.filter { it.isNotBlank() }
		val override = editedPostOverrides[postId]
		return PostContentOverride(
			description = override?.description ?: defaultDescription,
			imageUrls = override?.imageUrls?.takeIf { it.isNotEmpty() } ?: normalizedDefaultImageUrls
		)
	}

	fun popOverlay() {
		if (overlayStack.isNotEmpty()) {
			val removed = overlayStack.removeAt(overlayStack.lastIndex)
			enteredIds.remove(removed.id)
			overlayParallaxEnabled.remove(removed.id)
			createPostHasDraft.remove(removed.id)
			editPostHasChanges.remove(removed.id)
			if (createPostDiscardDialogOverlayId == removed.id) {
				createPostDiscardDialogOverlayId = null
			}
			if (editPostDiscardDialogOverlayId == removed.id) {
				editPostDiscardDialogOverlayId = null
			}
		}
		overlayProgress = 0f
	}

	fun overlayKey(entry: OverlayEntry): String {
		val overlay = entry.screen
		return when (overlay) {
			is OverlayScreen.OverlayProfile -> "overlay:${entry.id}:profile:${overlay.userId}"
			is OverlayScreen.OverlayChat -> "overlay:${entry.id}:chat:${overlay.interlocutorId}"
			is OverlayScreen.OverlayComments -> "overlay:${entry.id}:comments:${overlay.postId}"
			is OverlayScreen.OverlayPostDeepLink -> "overlay:${entry.id}:post-deeplink:${overlay.postId}"
			is OverlayScreen.OverlaySearchUsers -> "overlay:${entry.id}:search"
			is OverlayScreen.OverlayEditProfile -> "overlay:${entry.id}:editProfile:${overlay.name}:${overlay.username}"
			is OverlayScreen.OverlayCreatePost -> "overlay:${entry.id}:createPost"
			is OverlayScreen.OverlayEditPost -> "overlay:${entry.id}:editPost:${overlay.postId}"
			is OverlayScreen.OverlayPost -> "overlay:${entry.id}:post:${overlay.postId}"
		}
	}

	LaunchedEffect(isProfileScreen) {
		// no-op: system bar style handled elsewhere
	}

	@Composable
	fun OverlayContent(overlay: OverlayScreen, overlayId: Long, onClose: () -> Unit) {
		when (overlay) {
			is OverlayScreen.OverlayProfile -> {
				val logger: me.floow.domain.utils.Logger = koinInject()
				val profileRepository: me.floow.domain.data.repos.ProfileRepository = koinInject()
				val postsRepository: me.floow.domain.data.repos.PostsRepository = koinInject()
				val usersRepository: me.floow.domain.data.repos.UsersRepository = koinInject()
				val profileLocalStore: me.floow.domain.cache.ProfileLocalStore = koinInject()
				val postsLocalStore: me.floow.domain.cache.PostsLocalStore = koinInject()
					val factory = remember(overlay.userId) {
						object : ViewModelProvider.Factory {
							@Suppress("UNCHECKED_CAST")
							override fun <T : ViewModel> create(
								modelClass: Class<T>,
								extras: CreationExtras
							): T {
								val handle = extras.createSavedStateHandle().apply {
									set("userId", overlay.userId)
								}
									return ProfileScreenViewModel(
										logger = logger,
										profileRepository = profileRepository,
										postsRepository = postsRepository,
										usersRepository = usersRepository,
										profileLocalStore = profileLocalStore,
										postsLocalStore = postsLocalStore,
										usernameToIdCache = usernameToIdCache,
										savedStateHandle = handle
									) as T
								}
							}
						}

					val profileVm: ProfileScreenViewModel =
						viewModel(factory = factory, key = "profile-${overlay.userId}")

				LaunchedEffect(overlayId, overlay.userId) {
					profileVm.state
						.map { it is ProfileScreenState.Success }
						.distinctUntilChanged()
						.collect { isSuccess ->
							overlayParallaxEnabled[overlayId] = isSuccess
						}
				}
				DisposableEffect(overlayId) {
					onDispose {
						overlayParallaxEnabled.remove(overlayId)
					}
				}

					ProfileRoute(
						goToProfileEditScreen = { _, _, _, _, _ -> },
						goToAddPostScreen = {},
						onPostClick = { post, sourceSnapshot ->
							openPostOverlay(post = post, isSelf = false, sourceSnapshot = sourceSnapshot)
						},
										onEditPost = { post, sourceSnapshot ->
											openEditPostOverlay(
												post = post,
												sourceSnapshot = sourceSnapshot,
											)
										},
						goToChatScreen = { userId, name, avatarUrl ->
							pushOverlay(
								OverlayScreen.OverlayChat(
								interlocutorId = userId,
								interlocutorName = name,
								interlocutorAvatarUri = avatarUrl
							)
						)
					},
					shareProfile = { url ->
						shareText(url)
					},
					sharePost = { url ->
						shareText(url)
					},
					onBackClick = onClose,
					viewModel = profileVm,
					modifier = Modifier.fillMaxSize()
				)
			}

			is OverlayScreen.OverlaySearchUsers -> {
				SearchUsersRoute(
					onBackClick = onClose,
					onUserPick = { userId ->
						pushOverlay(OverlayScreen.OverlayProfile(userId = userId))
					},
					vm = koinViewModel(key = "overlay-${overlayId}-search-users"),
					modifier = Modifier.fillMaxSize()
				)
			}

			is OverlayScreen.OverlayEditProfile -> {
				EditProfileRoute(
					initialData = EditProfileRouteInitialData(
						name = overlay.name,
						username = overlay.username,
						description = overlay.description,
						avatarUrl = overlay.avatarUrl,
						backgroundUrl = overlay.backgroundUrl,
					),
					onBackClick = onClose,
					onDoneClick = onClose,
					vm = koinViewModel(key = "overlay-${overlayId}-edit-profile"),
					modifier = Modifier.fillMaxSize()
				)
			}

			is OverlayScreen.OverlayCreatePost -> {
				val createPostVm: AddPostViewModel = koinViewModel(key = "overlay-${overlayId}-create-post")
				val createPostState by createPostVm.state.collectAsState()

				LaunchedEffect(overlayId, createPostState.description, createPostState.localImageUris) {
					createPostHasDraft[overlayId] = createPostState.description.isNotBlank() ||
						createPostState.localImageUris.isNotEmpty()
				}
				DisposableEffect(overlayId) {
					onDispose {
						createPostHasDraft.remove(overlayId)
					}
				}

				CreatePostOverlayRoute(
					onBackClick = {
						if (createPostHasDraft[overlayId] == true) {
							createPostDiscardDialogOverlayId = overlayId
						} else {
							onClose()
						}
					},
					onPublished = {
						navController.currentBackStackEntry
							?.savedStateHandle
							?.set("refresh_posts", true)
						onClose()
					},
					isMockBuild = me.floow.app.BuildConfig.USE_MOCK_DATA,
					viewModel = createPostVm,
					modifier = Modifier.fillMaxSize()
				)
			}

			is OverlayScreen.OverlayEditPost -> {
				val editPostVm: EditPostViewModel = koinViewModel(key = "overlay-${overlayId}-edit-post-${overlay.postId}")
				val editPostState by editPostVm.state.collectAsState()

				LaunchedEffect(overlayId, editPostState.hasChanges) {
					editPostHasChanges[overlayId] = editPostState.hasChanges
				}
				DisposableEffect(overlayId) {
					onDispose {
						editPostHasChanges.remove(overlayId)
					}
				}

				EditPostOverlayRoute(
					postId = overlay.postId,
					initialDescription = overlay.initialDescription,
					initialImageUrls = overlay.initialImageUrls,
					mediaTransferToken = overlay.mediaTransferToken,
					mediaTransferStore = mediaTransferStore,
					onBackClick = {
						if (editPostHasChanges[overlayId] == true) {
							editPostDiscardDialogOverlayId = overlayId
						} else {
							onClose()
						}
					},
					onOptimistic = { result ->
						applyEditedPostOverride(
							postId = result.postId,
							description = result.description,
							imageUrls = result.imageUrls,
						)
						activeFeedViewModel?.applyPostEdit(
							postId = result.postId,
							description = result.description,
							imageUrls = result.imageUrls,
						)
						val sourceOverlayId = overlay.sourcePostOverlayId
						if (sourceOverlayId != null) {
							val sourceIndex = overlayStack.indexOfFirst { entry ->
								entry.id == sourceOverlayId && entry.screen is OverlayScreen.OverlayPost
							}
							if (sourceIndex >= 0) {
								val sourceScreen = overlayStack[sourceIndex].screen as OverlayScreen.OverlayPost
								val updatedDescription = result.description
								val updatedImageUrls = result.imageUrls
								val updatedVariants = PostContent(
									imageUrls = updatedImageUrls,
									description = updatedDescription,
									imageVariants = emptyList(),
								).resolvedImageVariants()
								overlayStack[sourceIndex] = overlayStack[sourceIndex].copy(
									screen = sourceScreen.copy(
										description = updatedDescription,
										imageUrls = updatedImageUrls,
										imageVariants = updatedVariants,
									)
								)
							}
						}
						if (overlayStack.lastOrNull()?.id == overlayId) {
							onClose()
						}
					},
					onSaved = { result ->
						applyEditedPostOverride(
							postId = result.postId,
							description = result.description,
							imageUrls = result.imageUrls,
						)
						navController.currentBackStackEntry
							?.savedStateHandle
							?.set("refresh_posts", true)
						activeFeedViewModel?.applyPostEdit(
							postId = result.postId,
							description = result.description,
							imageUrls = result.imageUrls,
						)
						val sourceOverlayId = overlay.sourcePostOverlayId
						if (sourceOverlayId != null) {
							val sourceIndex = overlayStack.indexOfFirst { entry ->
								entry.id == sourceOverlayId && entry.screen is OverlayScreen.OverlayPost
							}
							if (sourceIndex >= 0) {
								val sourceScreen = overlayStack[sourceIndex].screen as OverlayScreen.OverlayPost
								val updatedDescription = result.description
								val updatedImageUrls = result.imageUrls
								val updatedVariants = PostContent(
									imageUrls = updatedImageUrls,
									description = updatedDescription,
									imageVariants = emptyList(),
								).resolvedImageVariants()
								overlayStack[sourceIndex] = overlayStack[sourceIndex].copy(
									screen = sourceScreen.copy(
										description = updatedDescription,
										imageUrls = updatedImageUrls,
										imageVariants = updatedVariants,
									)
								)
							}
						}
					},
					onSaveFailed = { rollback ->
						applyEditedPostOverride(
							postId = rollback.postId,
							description = rollback.description,
							imageUrls = rollback.imageUrls,
						)
						activeFeedViewModel?.applyPostEdit(
							postId = rollback.postId,
							description = rollback.description,
							imageUrls = rollback.imageUrls,
						)
						val sourceOverlayId = overlay.sourcePostOverlayId
						if (sourceOverlayId != null) {
							val sourceIndex = overlayStack.indexOfFirst { entry ->
								entry.id == sourceOverlayId && entry.screen is OverlayScreen.OverlayPost
							}
							if (sourceIndex >= 0) {
								val sourceScreen = overlayStack[sourceIndex].screen as OverlayScreen.OverlayPost
								val restoredVariants = PostContent(
									imageUrls = rollback.imageUrls,
									description = rollback.description,
									imageVariants = emptyList(),
								).resolvedImageVariants()
								overlayStack[sourceIndex] = overlayStack[sourceIndex].copy(
									screen = sourceScreen.copy(
										description = rollback.description,
										imageUrls = rollback.imageUrls,
										imageVariants = restoredVariants,
									)
								)
							}
						}
						snackbarScope.launch {
							snackbarHostState.showSnackbar("Не удалось сохранить изменения")
						}
					},
					viewModel = editPostVm,
					modifier = Modifier.fillMaxSize(),
				)
			}

			is OverlayScreen.OverlayPostDeepLink -> {
				val resolvedPostContent = resolvePostContentOverride(
					postId = overlay.postId,
					defaultDescription = null,
					defaultImageUrls = emptyList(),
				)
				PostDeepLinkRoute(
					postId = overlay.postId,
					username = overlay.username,
					overrideDescription = resolvedPostContent.description,
					overrideImageUrls = resolvedPostContent.imageUrls,
					onBackClick = onClose,
					onProfileClick = { userId ->
						pushOverlay(OverlayScreen.OverlayProfile(userId = userId))
					},
					onProfileTagClick = { username ->
						pushOverlay(OverlayScreen.OverlayProfile(userId = username))
					},
					onPostLinkClick = { username, postId ->
						pushOverlay(OverlayScreen.OverlayPostDeepLink(postId = postId, username = username))
					},
					onCommentsClick = { post ->
						openCommentsOverlay(post = post, isSelf = post.author.id == "me")
					},
					onEditPost = { post ->
						openEditPostOverlay(post = post)
					},
					sharePost = { url ->
						shareText(url)
					},
					onPostUpdated = {
						navController.currentBackStackEntry
							?.savedStateHandle
							?.set("refresh_posts", true)
					},
					onPostDeleted = {
						editedPostOverrides.remove(overlay.postId)
						navController.currentBackStackEntry
							?.savedStateHandle
							?.set("refresh_posts", true)
						onClose()
					},
					modifier = Modifier.fillMaxSize(),
				)
			}

			is OverlayScreen.OverlayPost -> {
				val resolvedPostContent = resolvePostContentOverride(
					postId = overlay.postId,
					defaultDescription = overlay.description,
					defaultImageUrls = overlay.imageUrls,
				)
				val resolvedPostVariants = PostContent(
					imageUrls = resolvedPostContent.imageUrls,
					description = resolvedPostContent.description,
					imageVariants = emptyList(),
				).resolvedImageVariants()
				val resolvedOverlayPost = me.floow.domain.models.Post(
					id = overlay.postId,
					author = me.floow.domain.models.PostAuthor(
						id = overlay.authorId,
						name = overlay.authorName?.let { raw -> runCatching { me.floow.domain.values.ProfileName.create(raw) }.getOrNull() },
						username = overlay.authorUsername?.let { raw -> runCatching { me.floow.domain.values.ProfileUsername.create(raw) }.getOrNull() },
						avatarUrl = overlay.authorAvatarUrl
					),
					content = me.floow.domain.models.PostContent(
						imageUrls = resolvedPostContent.imageUrls,
						description = resolvedPostContent.description,
						imageVariants = resolvedPostVariants
					),
					category = overlay.category,
					createdAt = overlay.createdAt,
					likesCount = overlay.likesCount,
					commentsCount = overlay.commentsCount,
					commentersPreview = overlay.commentersPreview
				)
				PostRoute(
						postId = overlay.postId,
						imageUrls = resolvedPostContent.imageUrls,
						mediaTransferToken = overlay.mediaTransferToken,
						description = resolvedPostContent.description,
					authorId = overlay.authorId,
					authorName = overlay.authorName,
					authorUsername = overlay.authorUsername,
					authorAvatarUrl = overlay.authorAvatarUrl,
					category = overlay.category,
					createdAt = overlay.createdAt,
					likesCount = overlay.likesCount,
					commentsCount = overlay.commentsCount,
					commentersPreview = overlay.commentersPreview,
					isSelf = overlay.isSelf,
					onBackClick = onClose,
					onProfileClick = { userId ->
						pushOverlay(OverlayScreen.OverlayProfile(userId = userId))
					},
					onProfileTagClick = { username ->
						pushOverlay(OverlayScreen.OverlayProfile(userId = username))
					},
					onPostLinkClick = { username, postId ->
						pushOverlay(OverlayScreen.OverlayPostDeepLink(postId = postId, username = username))
					},
					onCommentsClick = { sourceSnapshot ->
						openCommentsOverlay(
							post = resolvedOverlayPost,
							isSelf = overlay.isSelf,
							sourceSnapshot = sourceSnapshot?.copy(owner = PostMediaSourceOwner.POST)
								?: buildPostMediaSnapshot(
									post = resolvedOverlayPost,
									owner = PostMediaSourceOwner.POST
								)
						)
					},
					sharePost = { url ->
						shareText(url)
					},
						onEditPost = { sourceSnapshot ->
							openEditPostOverlay(
								postId = overlay.postId,
								description = resolvedPostContent.description,
								imageUrls = resolvedPostContent.imageUrls,
								sourceSnapshot = sourceSnapshot,
								sourcePostOverlayId = overlayId,
							)
						},
					onPostUpdated = {
						navController.currentBackStackEntry
							?.savedStateHandle
							?.set("refresh_posts", true)
					},
						onPostDeleted = {
							editedPostOverrides.remove(overlay.postId)
							navController.currentBackStackEntry
								?.savedStateHandle
								?.set("refresh_posts", true)
						onClose()
					},
					modifier = Modifier.fillMaxSize(),
				)
			}

			is OverlayScreen.OverlayComments -> {
				CommentsRoute(
					initialData = CommentsRouteInitialData(
						postId = overlay.postId,
						postAuthorId = overlay.postAuthorId,
						postAuthorName = overlay.postAuthorName,
						postAuthorAvatarUrl = overlay.postAuthorAvatarUrl?.let { Uri.parse(it) },
						postAuthorUsername = overlay.postAuthorUsername,
						postImageUrls = overlay.postImageUrls,
						postImageVariants = overlay.postImageVariants,
						postDescription = overlay.postDescription,
						postCreatedAt = overlay.postCreatedAt,
						postCategory = overlay.postCategory,
						postLikesCount = overlay.postLikesCount,
						postIsSelf = overlay.postIsSelf,
						mediaTransferToken = overlay.mediaTransferToken
					),
					onBackClick = onClose,
					onAuthorClick = { userId ->
						pushOverlay(OverlayScreen.OverlayProfile(userId = userId))
					},
					vm = koinViewModel(key = "overlay-${overlayId}-comments-${overlay.postId}"),
					modifier = Modifier.fillMaxSize()
				)
			}

			is OverlayScreen.OverlayChat -> {
				ChatRoute(
					initialData = ChatRouteInitialData(
						chatInterlocutorId = overlay.interlocutorId,
						chatInterlocutorName = overlay.interlocutorName,
						chatInterlocutorAvatarUrl = overlay.interlocutorAvatarUri?.let { Uri.parse(it) }
					),
					onBackClick = onClose,
					onProfileClick = { userId ->
						pushOverlay(OverlayScreen.OverlayProfile(userId = userId))
					},
					vm = koinViewModel(key = "overlay-${overlayId}-chat-${overlay.interlocutorId}"),
					modifier = Modifier.fillMaxSize()
				)
			}
		}
	}

	BoxWithConstraints(modifier = modifier) {
		val density = LocalDensity.current
		val widthPx = with(density) { maxWidth.toPx() }
		val parallaxFactor = 0.12f
		val stateHolder = rememberSaveableStateHolder()

			LaunchedEffect(overlayStack.size) {
				val activeIds = overlayStack.map { it.id }.toSet()
				val toRemove = enteredIds.keys.filter { it !in activeIds }
				toRemove.forEach { enteredIds.remove(it) }
				val toRemoveParallax = overlayParallaxEnabled.keys.filter { it !in activeIds }
				toRemoveParallax.forEach { overlayParallaxEnabled.remove(it) }
				val toRemoveCreatePostDraftFlags = createPostHasDraft.keys.filter { it !in activeIds }
				toRemoveCreatePostDraftFlags.forEach { createPostHasDraft.remove(it) }
				val toRemoveEditPostFlags = editPostHasChanges.keys.filter { it !in activeIds }
				toRemoveEditPostFlags.forEach { editPostHasChanges.remove(it) }
				val pendingDiscardId = createPostDiscardDialogOverlayId
				if (pendingDiscardId != null && pendingDiscardId !in activeIds) {
					createPostDiscardDialogOverlayId = null
				}
				val pendingEditDiscardId = editPostDiscardDialogOverlayId
				if (pendingEditDiscardId != null && pendingEditDiscardId !in activeIds) {
					editPostDiscardDialogOverlayId = null
				}
			}

		val topEntry = overlayStack.lastOrNull()
		val topParallaxEnabled = topEntry?.let { overlayParallaxEnabled[it.id] } ?: true
		val effectiveParallaxFactor = if (topParallaxEnabled == true) parallaxFactor else 0f
			val shouldBlurUnderlay = topEntry?.screen is OverlayScreen.OverlayCreatePost ||
				topEntry?.screen is OverlayScreen.OverlayEditPost

		Box {
			val navHostBaseModifier =
				if (overlayStack.size == 1) {
					Modifier
						.fillMaxSize()
						.graphicsLayer {
							translationX = -widthPx * effectiveParallaxFactor * (1f - overlayProgress)
						}
				} else {
					Modifier.fillMaxSize()
				}
			val navHostModifier = if (shouldBlurUnderlay) {
				navHostBaseModifier.blur(22.dp)
			} else {
				navHostBaseModifier
			}

			Box(modifier = navHostModifier) {
				NavHost(navController = navController, startDestination = startDestination) {
				navigation<AuthDestinationsCluster>(
					startDestination = LoginScreen
				) {
					composable<RegistrationScreen> {
						Text(text = "Registration")
					}

					composable<CreateProfileScreen> {
						CreateProfileRoute(
							onDone = {
								navController.navigate(MainDestinationsCluster) {
									popUpTo<AuthDestinationsCluster> { inclusive = true }
								}
							},
							vm = koinViewModel(),
							modifier = modifier
						)
					}

					composable<EditProfileScreen> {
						val backStackEntry = navController.currentBackStackEntry
						val editProfileScreen: EditProfileScreen? =
							backStackEntry?.toRoute<EditProfileScreen>()

						EditProfileRoute(
							initialData = EditProfileRouteInitialData(
								name = editProfileScreen?.name ?: "",
								username = editProfileScreen?.username ?: "",
								description = editProfileScreen?.description ?: "",
								avatarUrl = editProfileScreen?.avatarUrl,
								backgroundUrl = editProfileScreen?.backgroundUrl,
							),
							onBackClick = {
								navController.popBackStack()
							},
							onDoneClick = {
								navController.navigate(MainDestinationsCluster) {
									popUpTo<AuthDestinationsCluster> { inclusive = true }
								}
							},
							vm = koinViewModel(),
							modifier = modifier
						)
					}

					composable<LoginScreen> {
						LoginRoute(
							onGoToHome = {
								navController.navigate(MainDestinationsCluster) {
									popUpTo<AuthDestinationsCluster> { inclusive = true }
								}
							},
							onGoToCreateProfile = {
								val initialData = authenticationManager.getPendingRegistrationInitialDataOrNull()
								navController.navigate(
									EditProfileScreen(
										name = initialData?.name.orEmpty(),
										username = initialData?.username.orEmpty(),
										description = initialData?.description.orEmpty(),
										avatarUrl = null,
										backgroundUrl = null,
									)
								)
							},
							viewModel = koinViewModel(),
							modifier = modifier
						)
					}
				}

				navigation<MainDestinationsCluster>(
					startDestination = FeedScreen
				) {
						composable<FeedScreen> {
							val feedViewModel: FeedViewModel = koinViewModel()
							val feedState by feedViewModel.state.collectAsState()
							val feedCanUndo = (feedState as? FeedScreenState.Success)?.canUndo == true
							DisposableEffect(feedViewModel) {
								activeFeedViewModel = feedViewModel
								onDispose {
									if (activeFeedViewModel === feedViewModel) {
										activeFeedViewModel = null
									}
								}
							}

						MainScreenScaffold(
							navController = navController,
							modifier = modifier,
							feedUndoEnabled = feedCanUndo,
							onFeedUndoClick = { feedViewModel.undoLastSwipe() }
						) { padding ->
								FeedRoute(
									onPostCreateClick = {
										pushOverlay(OverlayScreen.OverlayCreatePost)
									},
								onLogout = {
									navController.navigate(AuthDestinationsCluster) {
										popUpTo<MainDestinationsCluster> { inclusive = true }
									}
								},
								onProfileClick = { userId ->
									pushOverlay(OverlayScreen.OverlayProfile(userId = userId))
								},
								onProfileTagClick = { username ->
									pushOverlay(OverlayScreen.OverlayProfile(userId = username))
								},
								onPostLinkClick = { username, postId ->
									pushOverlay(OverlayScreen.OverlayPostDeepLink(postId = postId, username = username))
								},
								onCommentsClick = { post ->
									openCommentsOverlay(post = post, isSelf = post.author.id == "me")
								},
								onSharePost = { post ->
									shareText(me.floow.domain.deeplink.DeepLinkUrls.postUrl(post.id, post.author.username?.value))
								},
								onEditPost = { post ->
									openEditPostOverlay(post = post)
								},
								isMockBuild = me.floow.app.BuildConfig.USE_MOCK_DATA,
								isDebugBuild = me.floow.app.BuildConfig.DEBUG,
								modifier = Modifier.fillMaxSize(),
								viewModel = feedViewModel
							)
						}
					}

					composable<EditProfileScreen> {
						val backStackEntry = navController.currentBackStackEntry
						val editProfileScreen: EditProfileScreen? =
							backStackEntry?.toRoute<EditProfileScreen>()

						EditProfileRoute(
							initialData = EditProfileRouteInitialData(
								name = editProfileScreen?.name ?: "",
								username = editProfileScreen?.username ?: "",
								description = editProfileScreen?.description ?: "",
								avatarUrl = editProfileScreen?.avatarUrl,
								backgroundUrl = editProfileScreen?.backgroundUrl,
							),
							onBackClick = {
								navController.popBackStack()
							},
							onDoneClick = {
								navController.popBackStack()
							},
							vm = koinViewModel(),
							modifier = modifier
						)
					}

					composable<ProfileScreen>(
						deepLinks = listOf(
							navDeepLink { uriPattern = "$profileDeeplinkUri/{userId}" }
						)
					) {
								ProfileRoute(
							goToProfileEditScreen = { _, _, _, _, _ -> },
								goToAddPostScreen = {},
								onPostClick = { post, sourceSnapshot ->
									openPostOverlay(post = post, isSelf = false, sourceSnapshot = sourceSnapshot)
								},
								onEditPost = { post, sourceSnapshot ->
									openEditPostOverlay(
										post = post,
										sourceSnapshot = sourceSnapshot,
									)
								},
								goToChatScreen = { userId, name, avatarUrl ->
								pushOverlay(OverlayScreen.OverlayChat(
									interlocutorId = userId,
									interlocutorName = name,
									interlocutorAvatarUri = avatarUrl
								))
							},
							shareProfile = { url ->
								shareText(url)
							},
							sharePost = { url ->
								shareText(url)
							},
							onBackClick = { navController.popBackStack() },
							viewModel = koinViewModel(),
							modifier = Modifier.fillMaxSize()
						)
					}

					composable<PostDeepLinkScreen>(
						deepLinks = listOf(
							navDeepLink { uriPattern = "$profileDeeplinkUri/{username}/{postId}" }
						)
					) { backStackEntry ->
						val postDeepLinkScreen = backStackEntry.toRoute<PostDeepLinkScreen>()

					PostDeepLinkRoute(
						postId = postDeepLinkScreen.postId,
						username = postDeepLinkScreen.username,
						overrideDescription = editedPostOverrides[postDeepLinkScreen.postId]?.description,
						overrideImageUrls = editedPostOverrides[postDeepLinkScreen.postId]?.imageUrls ?: emptyList(),
						onBackClick = { navController.popBackStack() },
							onProfileClick = { userId ->
								navController.navigate(ProfileScreen(userId = userId))
							},
							onProfileTagClick = { username ->
								pushOverlay(OverlayScreen.OverlayProfile(userId = username))
							},
							onPostLinkClick = { username, postId ->
								pushOverlay(OverlayScreen.OverlayPostDeepLink(postId = postId, username = username))
							},
							onCommentsClick = { post ->
								openCommentsOverlay(post = post, isSelf = post.author.id == "me")
							},
							onEditPost = { post ->
								openEditPostOverlay(post = post)
							},
							sharePost = { url ->
								shareText(url)
							},
							onPostUpdated = {
								navController.previousBackStackEntry
									?.savedStateHandle
									?.set("refresh_posts", true)
							},
						onPostDeleted = {
							editedPostOverrides.remove(postDeepLinkScreen.postId)
							navController.previousBackStackEntry
								?.savedStateHandle
								?.set("refresh_posts", true)
								navController.popBackStack()
							},
							modifier = Modifier.fillMaxSize(),
						)
					}

					composable<PostScreen> { backStackEntry ->
						val postScreen = backStackEntry.toRoute<PostScreen>()
						val resolvedPostContent = resolvePostContentOverride(
							postId = postScreen.postId,
							defaultDescription = postScreen.description,
							defaultImageUrls = postScreen.imageUrls,
						)
						val resolvedPostVariants = PostContent(
							imageUrls = resolvedPostContent.imageUrls,
							description = resolvedPostContent.description,
							imageVariants = emptyList(),
						).resolvedImageVariants()
						val resolvedRoutePost = me.floow.domain.models.Post(
							id = postScreen.postId,
							author = me.floow.domain.models.PostAuthor(
								id = postScreen.authorId,
								name = postScreen.authorName?.let { raw -> runCatching { me.floow.domain.values.ProfileName.create(raw) }.getOrNull() },
								username = postScreen.authorUsername?.let { raw -> runCatching { me.floow.domain.values.ProfileUsername.create(raw) }.getOrNull() },
								avatarUrl = postScreen.authorAvatarUrl
							),
							content = me.floow.domain.models.PostContent(
								imageUrls = resolvedPostContent.imageUrls,
								description = resolvedPostContent.description,
								imageVariants = resolvedPostVariants
							),
							category = postScreen.category,
							createdAt = postScreen.createdAt,
							likesCount = postScreen.likesCount,
							commentsCount = postScreen.commentsCount,
							commentersPreview = postScreen.commentersPreview
						)

						PostRoute(
							postId = postScreen.postId,
							imageUrls = resolvedPostContent.imageUrls,
							mediaTransferToken = postScreen.mediaTransferToken,
							description = resolvedPostContent.description,
							authorId = postScreen.authorId,
							authorName = postScreen.authorName,
							authorUsername = postScreen.authorUsername,
							authorAvatarUrl = postScreen.authorAvatarUrl,
							category = postScreen.category,
							createdAt = postScreen.createdAt,
							likesCount = postScreen.likesCount,
							commentsCount = postScreen.commentsCount,
							commentersPreview = postScreen.commentersPreview,
							isSelf = postScreen.isSelf,
							onBackClick = { navController.popBackStack() },
							onProfileClick = { userId ->
								navController.navigate(ProfileScreen(userId = userId))
							},
							onProfileTagClick = { username ->
								pushOverlay(OverlayScreen.OverlayProfile(userId = username))
							},
							onPostLinkClick = { username, postId ->
								pushOverlay(OverlayScreen.OverlayPostDeepLink(postId = postId, username = username))
							},
							onCommentsClick = { sourceSnapshot ->
								openCommentsOverlay(
									post = resolvedRoutePost,
									isSelf = postScreen.isSelf,
									sourceSnapshot = sourceSnapshot?.copy(owner = PostMediaSourceOwner.POST)
										?: buildPostMediaSnapshot(
											post = resolvedRoutePost,
											owner = PostMediaSourceOwner.POST
										)
								)
							},
							sharePost = { url ->
								shareText(url)
							},
							onEditPost = { sourceSnapshot ->
								openEditPostOverlay(
									postId = postScreen.postId,
									description = resolvedPostContent.description,
									imageUrls = resolvedPostContent.imageUrls,
									sourceSnapshot = sourceSnapshot,
								)
							},
							onPostUpdated = {
								navController.previousBackStackEntry
									?.savedStateHandle
									?.set("refresh_posts", true)
							},
							onPostDeleted = {
								editedPostOverrides.remove(postScreen.postId)
								navController.previousBackStackEntry
									?.savedStateHandle
									?.set("refresh_posts", true)
								navController.popBackStack()
							},
							modifier = Modifier.fillMaxSize(),
						)
					}

					composable<SelfProfileScreen> {
						val selfBackStackEntry by navController.currentBackStackEntryAsState()
						val refreshPostsFlow = remember(selfBackStackEntry) {
							selfBackStackEntry?.savedStateHandle?.getStateFlow("refresh_posts", false)
								?: MutableStateFlow(false)
						}
						val refreshPostsSignal by refreshPostsFlow.collectAsState()

						MainScreenScaffold(navController, modifier = modifier, disableTopInset = true) { padding ->
							ProfileRoute(
								goToProfileEditScreen = { name, username, description, avatarUrl, backgroundUrl ->
									pushOverlay(OverlayScreen.OverlayEditProfile(
										name = name,
										username = username,
										description = description,
										avatarUrl = avatarUrl,
										backgroundUrl = backgroundUrl,
									))
								},
										goToAddPostScreen = {
											pushOverlay(OverlayScreen.OverlayCreatePost)
										},
									onPostClick = { post, sourceSnapshot ->
										openPostOverlay(post = post, isSelf = true, sourceSnapshot = sourceSnapshot)
									},
									onEditPost = { post, sourceSnapshot ->
										openEditPostOverlay(
											post = post,
											sourceSnapshot = sourceSnapshot,
										)
									},
									goToChatScreen = { userId, name, avatarUrl ->
										pushOverlay(OverlayScreen.OverlayChat(
										interlocutorId = userId,
										interlocutorName = name,
										interlocutorAvatarUri = avatarUrl
									))
								},
								shareProfile = { url ->
									shareText(url)
								},
								sharePost = { url ->
									shareText(url)
								},
								refreshPostsSignal = refreshPostsSignal,
								consumeRefreshPostsSignal = {
									selfBackStackEntry?.savedStateHandle?.set("refresh_posts", false)
								},
								viewModel = koinViewModel(),
								modifier = Modifier.fillMaxSize()
							)
						}
					}

					composable<ChatsScreen> {
						val chatsBackStackEntry by navController.currentBackStackEntryAsState()
						val chatsDestination = chatsBackStackEntry?.destination

						ChatsRoute(
							onChatClick = { _ ->
								// Handled internally by ChatsRoute (Master-Detail)
							},
							onSearchClick = {
								pushOverlay(OverlayScreen.OverlaySearchUsers())
							},
							onProfileClick = { userId ->
								pushOverlay(OverlayScreen.OverlayProfile(userId = userId))
							},
							isMockBuild = me.floow.app.BuildConfig.USE_MOCK_DATA,
							bottomBar = {
								FlowBottomBar(
									currentDestination = chatsDestination,
									navigationItems = bottomNavigationItems,
									onClick = {
										navController.navigate(it) {
											popUpTo(navController.graph.findStartDestination().id) {
												saveState = true
											}
											launchSingleTop = true
											restoreState = true
										}
									}
								)
							},
							vm = koinInject(),
							modifier = modifier
						)
					}

					composable<ChatScreen> {
						val backStackEntry = navController.currentBackStackEntry
						val chatScreen: ChatScreen? =
							backStackEntry?.toRoute<ChatScreen>()

						ChatRoute(
							initialData = ChatRouteInitialData(
								chatInterlocutorId = chatScreen?.interlocutorId ?: "",
								chatInterlocutorName = chatScreen?.interlocutorName ?: "",
								chatInterlocutorAvatarUrl = chatScreen?.interlocutorAvatarUri?.let { Uri.parse(it) }
							),
							onBackClick = {
								navController.popBackStack()
							},
							onProfileClick = { userId ->
								pushOverlay(OverlayScreen.OverlayProfile(userId = userId))
							},
							isMockBuild = me.floow.app.BuildConfig.USE_MOCK_DATA,
							vm = koinViewModel(),
							modifier = modifier
						)
					}

					composable<SearchUsersScreen> {
						SearchUsersRoute(
							onBackClick = {
								navController.popBackStack()
							},
							onUserPick = { userId ->
								pushOverlay(OverlayScreen.OverlayProfile(userId = userId))
							},
							vm = koinViewModel(),
							modifier = modifier
						)
					}
				}
			}
			}
		}

		val topIndex = overlayStack.lastIndex

		overlayStack.forEachIndexed { index, entry ->
			val entryKey = overlayKey(entry)
			val isTop = index == topIndex
			val isUnderlay = index == topIndex - 1
			val overlay = entry.screen
			val overlayId = entry.id

			val movableOverlayContent = remember(overlayId) {
				movableContentOf { close: () -> Unit ->
					stateHolder.SaveableStateProvider(entryKey) {
						key(entryKey) {
							OverlayContent(overlay, overlayId, onClose = close)
						}
					}
				}
			}

			val layerModifier = Modifier
				.fillMaxSize()
				.zIndex(index.toFloat())
				.then(
					if (isUnderlay) {
						Modifier.graphicsLayer {
							translationX = -widthPx * effectiveParallaxFactor * (1f - overlayProgress)
						}
					} else {
						Modifier
					}
				)

			if (isTop) {
				val animateIn = if (
					overlay is OverlayScreen.OverlayCreatePost ||
					overlay is OverlayScreen.OverlayEditPost
				) {
					false
				} else {
					enteredIds[entry.id] != true
				}
				val scrimMaxAlpha = when (overlay) {
					is OverlayScreen.OverlayCreatePost,
					is OverlayScreen.OverlayEditPost -> 0.69f
					else -> 0.25f
				}
				LaunchedEffect(entry.id) {
					enteredIds[entry.id] = true
				}
				SwipeBackOverlay(
					onClose = { popOverlay() },
					canClose = {
						val hasCreatePostDraft = overlay is OverlayScreen.OverlayCreatePost &&
							createPostHasDraft[overlayId] == true
						val hasEditPostChanges = overlay is OverlayScreen.OverlayEditPost &&
							editPostHasChanges[overlayId] == true
						when {
							hasCreatePostDraft -> {
								createPostDiscardDialogOverlayId = overlayId
								false
							}
							hasEditPostChanges -> {
								editPostDiscardDialogOverlayId = overlayId
								false
							}
							else -> true
						}
					},
					modifier = layerModifier,
					scrimMaxAlpha = scrimMaxAlpha,
					onProgressChange = { overlayProgress = it },
					entryKey = entry.id,
					animateIn = animateIn,
				) {
					movableOverlayContent { popOverlay() }
				}
			} else {
				Box(modifier = layerModifier) {
					movableOverlayContent {}
				}
			}
		}

		val pendingDiscardDialogId = createPostDiscardDialogOverlayId
		if (pendingDiscardDialogId != null) {
			AlertDialog(
				onDismissRequest = { createPostDiscardDialogOverlayId = null },
				title = { Text("Удалить черновик?") },
				text = { Text("Есть несохраненные изменения. Если выйти, черновик будет удален.") },
				confirmButton = {
					TextButton(
						onClick = {
							if (overlayStack.lastOrNull()?.id == pendingDiscardDialogId) {
								popOverlay()
							}
							createPostDiscardDialogOverlayId = null
						}
					) {
						Text("Удалить")
					}
				},
				dismissButton = {
					TextButton(onClick = { createPostDiscardDialogOverlayId = null }) {
						Text("Отмена")
					}
				}
			)
		}

		val pendingEditDiscardDialogId = editPostDiscardDialogOverlayId
		if (pendingEditDiscardDialogId != null) {
			AlertDialog(
				onDismissRequest = { editPostDiscardDialogOverlayId = null },
				title = { Text("Отменить изменения?") },
				text = { Text("Есть несохраненные изменения. Если выйти, они будут потеряны.") },
				confirmButton = {
					TextButton(
						onClick = {
							if (overlayStack.lastOrNull()?.id == pendingEditDiscardDialogId) {
								popOverlay()
							}
							editPostDiscardDialogOverlayId = null
						}
					) {
						Text("Выйти")
					}
				},
				dismissButton = {
					TextButton(onClick = { editPostDiscardDialogOverlayId = null }) {
						Text("Отмена")
					}
				}
			)
		}

		SnackbarHost(
			hostState = snackbarHostState,
			modifier = Modifier
				.align(Alignment.BottomCenter)
				.navigationBarsPadding()
				.padding(16.dp)
		)
	}
}

private fun showNotImplementedToast(context: Context) {
	Toast.makeText(context, "Фича ещё разрабатывается…", Toast.LENGTH_SHORT).show()
}
