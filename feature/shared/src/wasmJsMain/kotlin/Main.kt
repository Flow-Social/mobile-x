import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import me.floow.api.CommentsApiImpl
import me.floow.api.CategoriesApiImpl
import me.floow.api.CommentsRealtimeApiImpl
import me.floow.api.FeedApiImpl
import me.floow.api.PostsApiImpl
import me.floow.api.UsersApiImpl
import me.floow.api.util.ApiConfig
import me.floow.api.util.HttpClientProvider
import me.floow.chatssearch.uilogic.SearchUsersStateHolder
import me.floow.comments.uilogic.CommentsStateHolder
import me.floow.data.repos.CategoryCatalogRepositoryImpl
import me.floow.data.repos.CommentsRepositoryImpl
import me.floow.data.repos.FeedRepositoryImpl
import me.floow.data.repos.PostsRepositoryImpl
import me.floow.data.repos.UserProfileRepositoryImpl
import me.floow.data.repos.UsersRepositoryImpl
import me.floow.domain.api.CommentsApi
import me.floow.domain.api.CategoriesApi
import me.floow.domain.api.CommentsRealtimeApi
import me.floow.domain.api.FeedApi
import me.floow.domain.api.PostsApi
import me.floow.domain.api.UsersApi
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.cache.FeedSyncLocalStore
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.data.repos.CategoryCatalogRepository
import me.floow.domain.data.repos.DirectMessagesReadCursorStore
import me.floow.domain.data.repos.CommentsReadCursorStore
import me.floow.domain.data.repos.FeedRepository
import me.floow.domain.data.repos.CommentsRepository
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.UserProfileRepository
import me.floow.domain.data.repos.UsersRepository
import me.floow.domain.utils.Logger
import me.floow.shared.login.auth.WasmAuthRepository
import me.floow.shared.login.createprofile.WasmCreateProfileRepository
import me.floow.shared.chats.uilogic.ChatsListRepository
import me.floow.shared.chats.uilogic.ChatsListStateHolder
import me.floow.shared.chats.uilogic.WasmChatsListRepository
import me.floow.shared.chats.uilogic.direct.ChatThreadRepository
import me.floow.shared.chats.uilogic.direct.ChatPresenceContract
import me.floow.shared.chats.uilogic.direct.ChatRealtimeTransport
import me.floow.shared.chats.uilogic.direct.ChatRealtimeContract
import me.floow.shared.chats.uilogic.direct.ChatRealtimeSessionManager
import me.floow.shared.chats.uilogic.direct.WasmChatThreadRepository
import me.floow.shared.chats.uilogic.direct.PresenceRealtimeSessionManager
import me.floow.shared.chats.uilogic.direct.PresenceRealtimeTransport
import me.floow.shared.chats.uilogic.direct.RealtimeAuthProvider
import me.floow.shared.chats.uilogic.direct.DirectChatStateHolder
import me.floow.shared.chats.uilogic.direct.WasmChatRealtimeTransport
import me.floow.shared.chats.uilogic.direct.WasmPresenceRealtimeTransport
import me.floow.shared.chats.uilogic.direct.WasmRealtimeAuthProvider
import me.floow.shared.chats.uilogic.replies.RepliesRepository
import me.floow.shared.chats.uilogic.replies.RepliesStateHolder
import me.floow.shared.chats.uilogic.replies.WasmRepliesRepository
import me.floow.shared.di.sharedCommonModule
import me.floow.shared.login.uilogic.AuthRepository
import me.floow.shared.login.uilogic.LoginState
import me.floow.shared.login.uilogic.LoginViewModel
import me.floow.shared.login.uilogic.createprofile.CreateProfileRepository
import me.floow.shared.login.uilogic.createprofile.CreateProfileStateHolder
import me.floow.shared.login.ui.createprofile.CreateProfileRoute
import me.floow.shared.login.ui.login.LoginScreen
import me.floow.shared.main.ui.MainFlowShell
import me.floow.shared.profile.auth.WasmProfileRepository
import me.floow.shared.profile.auth.flowWasmCopyText
import me.floow.shared.profile.uilogic.compose.PostComposerRepository
import me.floow.shared.profile.uilogic.compose.WasmPostComposerRepository
import me.floow.shared.profile.image.LocalImageFileReader
import me.floow.shared.profile.image.WasmLocalImageFileReader
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.shared.profile.uilogic.edit.ProfileEditorRepository
import me.floow.shared.profile.uilogic.edit.EditProfileOverlayData
import me.floow.shared.profile.uilogic.edit.EditProfileStateHolder
import me.floow.shared.profile.uilogic.edit.WasmProfileEditorRepository
import me.floow.shared.profile.uilogic.addpost.CreatePostStateHolder
import me.floow.shared.profile.uilogic.addpost.EditPostStateHolder
import me.floow.shared.profile.uilogic.compose.PlatformImagePicker
import me.floow.shared.profile.uilogic.compose.WasmPlatformImagePicker
import me.floow.shared.profile.uilogic.ProfileRepository
import me.floow.shared.profile.uilogic.ProfileStateHolder
import me.floow.shared.runtime.WasmAuthenticationManager
import me.floow.shared.runtime.WasmCommentsReadCursorStore
import me.floow.shared.runtime.WasmDirectMessagesReadCursorStore
import me.floow.shared.runtime.WasmFeedSyncLocalStore
import me.floow.shared.runtime.WasmPostsLocalStore
import me.floow.shared.runtime.WasmProfileLocalStore
import me.floow.shared.runtime.WasmLogger
import me.floow.shared.runtime.WasmPostMediaTransferStore
import me.floow.uikit.components.media.transfer.PostMediaTransferStore
import me.floow.uikit.theme.FlowTheme
import me.floow.uikit.theme.rememberWasmEmojiFontReady
import org.w3c.dom.events.Event
import org.koin.core.parameter.parametersOf
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    if (runCatching { GlobalContext.get() }.getOrNull() == null) {
        startKoin {
            modules(
                module {
                    single { ApiConfig(apiUrl = "https://45.66.228.158.nip.io/api/v1") }
                    single<HttpClientProvider> { HttpClientProvider() }
                    single<Logger> { WasmLogger() }
                    single<AuthenticationManager> { WasmAuthenticationManager() }
                    single<CommentsReadCursorStore> { WasmCommentsReadCursorStore() }
                    single<DirectMessagesReadCursorStore> { WasmDirectMessagesReadCursorStore() }
                    single<PostMediaTransferStore> { WasmPostMediaTransferStore() }
                    single { WasmAuthRepository(get()) } bind AuthRepository::class
                    single { WasmCreateProfileRepository(get()) } bind CreateProfileRepository::class
                    single { WasmProfileRepository() } bind ProfileRepository::class
                    single<CommentsApi> { CommentsApiImpl(get(), get(), get(), get()) }
                    single<CommentsRealtimeApi> { CommentsRealtimeApiImpl(get(), get(), get(), get()) }
                    single<CategoriesApi> { CategoriesApiImpl(get(), get(), get(), get()) }
                    single<FeedApi> { FeedApiImpl(get(), get(), get(), get()) }
                    single<PostsApi> { PostsApiImpl(get(), get(), get(), get()) }
                    single<UsersApi> { UsersApiImpl(get(), get(), get(), get()) }
                    single<CommentsRepository> { CommentsRepositoryImpl(get(), get(), get()) }
                    single<CategoryCatalogRepository> { CategoryCatalogRepositoryImpl(get(), get()) }
                    single<UserProfileRepository> { UserProfileRepositoryImpl(get()) }
                    single<FeedRepository> { FeedRepositoryImpl(get(), get()) }
                    single<PostsRepository> { PostsRepositoryImpl(get(), get()) }
                    single<PostsLocalStore> { WasmPostsLocalStore() }
                    single<ProfileLocalStore> { WasmProfileLocalStore() }
                    single<FeedSyncLocalStore> { WasmFeedSyncLocalStore() }
                    single<UsersRepository> { UsersRepositoryImpl(get(), get()) }
                    single<ChatsListRepository> { WasmChatsListRepository(get()) }
                    single<ChatThreadRepository> { WasmChatThreadRepository(get()) }
                    single<RealtimeAuthProvider> { WasmRealtimeAuthProvider() }
                    single { WasmChatRealtimeTransport() } bind ChatRealtimeTransport::class
                    single { WasmPresenceRealtimeTransport() } bind PresenceRealtimeTransport::class
                    single<ChatRealtimeContract> { ChatRealtimeSessionManager(get(), get()) }
                    single<ChatPresenceContract> { PresenceRealtimeSessionManager(get(), get()) }
                    single<RepliesRepository> { WasmRepliesRepository() }
                    single<LocalImageFileReader> { WasmLocalImageFileReader() }
                    single<PlatformImagePicker> { WasmPlatformImagePicker() }
                    single<PostComposerRepository> { WasmPostComposerRepository() }
                    single<ProfileEditorRepository> { WasmProfileEditorRepository() }
                },
                sharedCommonModule,
            )
        }
    }

    ComposeViewport(
        viewportContainerId = "compose-target"
    ) {
        val loginViewModel = remember {
            GlobalContext.get().get<LoginViewModel>()
        }
        val authenticationManager = remember {
            GlobalContext.get().get<AuthenticationManager>()
        }
        val createProfileRepository = remember {
            GlobalContext.get().get<CreateProfileRepository>()
        }
        val profileStateHolder = remember {
            GlobalContext.get().get<ProfileStateHolder> { parametersOf(null) }
        }
        val chatsListStateHolder = remember {
            GlobalContext.get().get<ChatsListStateHolder>()
        }
        val chatRealtimeContract = remember {
            GlobalContext.get().get<ChatRealtimeContract>()
        }
        val chatPresenceContract = remember {
            GlobalContext.get().get<ChatPresenceContract>()
        }
        val feedStateHolder = remember {
            GlobalContext.get().get<me.floow.shared.feed.uilogic.SharedFeedStateHolder>()
        }
        val directMessagesReadCursorStore = remember {
            GlobalContext.get().get<DirectMessagesReadCursorStore>()
        }
        val repliesStateHolderProvider = remember {
            { GlobalContext.get().get<RepliesStateHolder>() }
        }
        val directChatStateHolderProvider = remember(chatRealtimeContract, chatPresenceContract) {
            {
                GlobalContext.get().get<DirectChatStateHolder> {
                    parametersOf(chatRealtimeContract, chatPresenceContract)
                }
            }
        }
        val profileStateHolderProvider = remember {
            { userId: String? -> GlobalContext.get().get<ProfileStateHolder> { parametersOf(userId) } }
        }
        val createPostStateHolderProvider = remember {
            {
                GlobalContext.get().get<CreatePostStateHolder> {
                    parametersOf(GlobalContext.get().get<PlatformImagePicker>())
                }
            }
        }
        val editPostStateHolderProvider = remember {
            { initialPost: ProfilePostItem ->
                GlobalContext.get().get<EditPostStateHolder> {
                    parametersOf(initialPost, GlobalContext.get().get<PlatformImagePicker>())
                }
            }
        }
        val editProfileStateHolderProvider = remember {
            { initialData: EditProfileOverlayData ->
                GlobalContext.get().get<EditProfileStateHolder> {
                    parametersOf(initialData, GlobalContext.get().get<PlatformImagePicker>())
                }
            }
        }
        val searchUsersStateHolderProvider = remember {
            { GlobalContext.get().get<SearchUsersStateHolder>() }
        }
        val commentsStateHolderProvider = remember {
            { GlobalContext.get().get<CommentsStateHolder>() }
        }
        val postsRepository = remember {
            GlobalContext.get().get<PostsRepository>()
        }
        val postMediaTransferStore = remember {
            GlobalContext.get().get<PostMediaTransferStore>()
        }
        val buildProfileShareUrl = remember {
            { slug: String -> "${window.location.origin}/profile/$slug" }
        }
        val buildPostShareUrl = remember {
            { postId: String, username: String? -> "${window.location.origin}/${username.orEmpty()}/$postId" }
        }
        val state by loginViewModel.state.collectAsState()

        LaunchedEffect(Unit) {
            loginViewModel.resumeAuthorization()
        }

        val darkTheme = rememberWasmHostDarkTheme()
        val emojiFontReady = rememberWasmEmojiFontReady()

        FlowTheme(darkTheme = darkTheme) {
            if (!emojiFontReady) {
                Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    CircularProgressIndicator()
                }
            } else when (state) {
                LoginState.Authenticated -> MainFlowShell(
                    profileStateHolder = profileStateHolder,
                    chatsListStateHolder = chatsListStateHolder,
                    repliesStateHolderProvider = repliesStateHolderProvider,
                    feedStateHolder = feedStateHolder,
                    directMessagesReadCursorStore = directMessagesReadCursorStore,
                    directChatStateHolderProvider = directChatStateHolderProvider,
                    chatRealtimeContract = chatRealtimeContract,
                    chatPresenceContract = chatPresenceContract,
                    createPostStateHolderProvider = createPostStateHolderProvider,
                    editPostStateHolderProvider = editPostStateHolderProvider,
                    editProfileStateHolderProvider = editProfileStateHolderProvider,
                    searchUsersStateHolderProvider = searchUsersStateHolderProvider,
                    commentsStateHolderProvider = commentsStateHolderProvider,
                    postsRepository = postsRepository,
                    postMediaTransferStore = postMediaTransferStore,
                    onCopyText = { text ->
                        flowWasmCopyText(
                            text = text,
                            onSuccess = {},
                            onError = {},
                        )
                    },
                    buildProfileShareUrl = buildProfileShareUrl,
                    buildPostShareUrl = buildPostShareUrl,
                    profileStateHolderProvider = profileStateHolderProvider,
                )
                LoginState.PendingRegistration -> {
                    val createProfileStateHolder = remember(authenticationManager) {
                        CreateProfileStateHolder(
                            profileRepository = createProfileRepository,
                            initialData = authenticationManager.getPendingRegistrationInitialDataOrNull(),
                        )
                    }
                    CreateProfileRoute(
                        stateHolder = createProfileStateHolder,
                        onDone = loginViewModel::resumeAuthorization,
                    )
                }
                is LoginState.Error,
                LoginState.Idle,
                LoginState.Loading -> {
                    LoginScreen(
                        isLoading = state is LoginState.Loading,
                        onLoginClick = loginViewModel::signInWithGoogle
                    )
                }
            }
        }
    }
}

private enum class WasmHostThemePreference {
    Light,
    Dark,
    System,
}

@Composable
private fun rememberWasmHostDarkTheme(): Boolean {
    val systemDarkTheme = isSystemInDarkTheme()
    var darkTheme by remember(systemDarkTheme) {
        mutableStateOf(readWasmHostDarkTheme(systemDarkTheme))
    }

    DisposableEffect(systemDarkTheme) {
        val syncTheme = {
            darkTheme = readWasmHostDarkTheme(systemDarkTheme)
        }
        val listener: (Event) -> Unit = { _: Event ->
            syncTheme()
        }
        val mediaQuery = window.matchMedia("(prefers-color-scheme: dark)")

        syncTheme()
        window.addEventListener("flow-theme-change", listener)
        mediaQuery.addEventListener("change", listener)

        onDispose {
            window.removeEventListener("flow-theme-change", listener)
            mediaQuery.removeEventListener("change", listener)
        }
    }

    return darkTheme
}

private fun readWasmHostDarkTheme(systemDarkTheme: Boolean): Boolean {
    val preference = when (window.localStorage.getItem("flow_theme")) {
        "dark" -> WasmHostThemePreference.Dark
        "light" -> WasmHostThemePreference.Light
        else -> WasmHostThemePreference.System
    }
    val resolvedTheme = document.documentElement
        ?.getAttribute("data-theme")
        ?.lowercase()

    return when (preference) {
        WasmHostThemePreference.Dark -> true
        WasmHostThemePreference.Light -> false
        WasmHostThemePreference.System -> when (resolvedTheme) {
            "dark" -> true
            "light" -> false
            else -> systemDarkTheme
        }
    }
}
