package me.floow.shared.di

import me.floow.database.localstore.CommonPendingPostMediaStore
import me.floow.database.localstore.CommonPendingPostsSyncScheduler
import me.floow.domain.data.repos.DirectMessagesReadCursorStore
import me.floow.domain.data.repos.PendingPostMediaStore
import me.floow.domain.data.repos.PendingPostsSyncScheduler
import me.floow.domain.utils.Logger
import me.floow.shared.chats.data.AndroidChatPresenceContract
import me.floow.shared.chats.data.AndroidChatRealtimeContract
import me.floow.shared.chats.data.AndroidChatThreadRepository
import me.floow.shared.chats.data.AndroidChatsListRepository
import me.floow.shared.chats.data.AndroidRepliesRepository
import me.floow.shared.chats.data.InMemoryDirectMessagesReadCursorStore
import me.floow.shared.chats.uilogic.ChatsListRepository
import me.floow.shared.chats.uilogic.ChatsListStateHolder
import me.floow.shared.chats.uilogic.ConversationActivityStore
import me.floow.shared.chats.uilogic.direct.ChatPresenceContract
import me.floow.shared.chats.uilogic.direct.ChatRealtimeContract
import me.floow.shared.chats.uilogic.direct.ChatThreadRepository
import me.floow.shared.chats.uilogic.direct.DirectChatStateHolder
import me.floow.shared.chats.uilogic.replies.RepliesRepository
import me.floow.shared.chats.uilogic.replies.RepliesStateHolder
import me.floow.shared.chats.uilogic.replies.StaticRepliesRepository
import me.floow.shared.chats.uilogic.session.SharedChatSessionCache
import me.floow.shared.feed.uilogic.SharedFeedStateHolder
import me.floow.shared.login.createprofile.AndroidCreateProfileRepository
import me.floow.shared.login.uilogic.LoginViewModel
import me.floow.shared.login.uilogic.createprofile.CreateProfileRepository
import me.floow.shared.login.uilogic.createprofile.CreateProfileStateHolder
import me.floow.shared.profile.image.LocalImageFileReader
import me.floow.shared.profile.image.createLocalImageFileReader
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.shared.profile.uilogic.AndroidProfileRepository
import me.floow.shared.profile.uilogic.ProfileRepository
import me.floow.shared.profile.uilogic.ProfileStateHolder
import me.floow.shared.profile.uilogic.addpost.CreatePostStateHolder
import me.floow.shared.profile.uilogic.addpost.EditPostStateHolder
import me.floow.shared.profile.uilogic.compose.NoOpPlatformImagePicker
import me.floow.shared.profile.uilogic.compose.PlatformImagePicker
import me.floow.shared.profile.uilogic.edit.AndroidProfileEditorRepository
import me.floow.shared.profile.uilogic.edit.EditProfileOverlayData
import me.floow.shared.profile.uilogic.edit.EditProfileStateHolder
import me.floow.shared.profile.uilogic.edit.ProfileEditorRepository
import me.floow.comments.uilogic.CommentsStateHolder
import me.floow.shared.auth.OAuthLauncher
import me.floow.shared.auth.TokenStorage
import me.floow.shared.auth.createOAuthLauncher
import me.floow.shared.auth.createTokenStorage
import org.koin.dsl.module

val sharedCommonModule = module {
    single<TokenStorage> { createTokenStorage() }
    single<OAuthLauncher> { createOAuthLauncher() }
    single<LocalImageFileReader> { createLocalImageFileReader() }
    single<PendingPostMediaStore> { CommonPendingPostMediaStore() }
    single<PendingPostsSyncScheduler> { CommonPendingPostsSyncScheduler { getOrNull<me.floow.shared.media.outbox.MediaOutboxEngine>() } }
    single<DirectMessagesReadCursorStore> { InMemoryDirectMessagesReadCursorStore() }
    single<me.floow.domain.cache.UsernameToIdCache> { me.floow.database.sharedpref.InMemoryUsernameToIdCache() }
    single { ConversationActivityStore() }
    single<me.floow.domain.media.MediaDownloadController> { me.floow.shared.media.download.createMediaDownloadController() }

    single<ProfileRepository> {
        AndroidProfileRepository(
            profileRepository = get<me.floow.domain.data.repos.ProfileRepository>(),
            postsRepository = get(),
            presenceRepository = get(),
            profileLocalStore = get(),
            postsLocalStore = get(),
            usernameToIdCache = get(),
            pendingPostsLocalStore = get(),
            pendingPostMediaStore = get(),
            pendingPostsSyncScheduler = get(),
            usersApi = get(),
            mediaOutboxEngine = getOrNull(),
            chatsListRepository = getOrNull(),
        )
    }
    single<ProfileEditorRepository> {
        AndroidProfileEditorRepository(
            profileRepository = get<me.floow.domain.data.repos.ProfileRepository>(),
            profileLocalStore = get(),
            usernameToIdCache = get(),
            authenticationManager = get(),
            authApi = get(),
        )
    }
    single<CreateProfileRepository> {
        AndroidCreateProfileRepository(
            profileRepository = get<me.floow.domain.data.repos.ProfileRepository>(),
            authenticationManager = get(),
            authApi = get(),
        )
    }

    single<ChatsListRepository> {
        AndroidChatsListRepository(
            chatsRepository = get(),
            notificationsRealtimeRepository = get(),
            presenceRepository = get(),
            authenticationManager = get(),
            activityStore = getOrNull() ?: ConversationActivityStore(),
        )
    }
    single<ChatThreadRepository> { AndroidChatThreadRepository(get(), get(), get(), get()) }
    single<ChatRealtimeContract> { AndroidChatRealtimeContract(get(), get(), get()) }
    single<ChatPresenceContract> { AndroidChatPresenceContract(get()) }
    single<RepliesRepository> {
        runCatching { AndroidRepliesRepository(get(), get(), get()) }
            .getOrElse { StaticRepliesRepository() }
    }

    single { SharedChatSessionCache() }
    single {
        me.floow.shared.media.outbox.MediaOutboxEngine(
            directChatsLocalStore = get<me.floow.domain.cache.DirectChatsLocalStore>(),
            uploadsApi = get<me.floow.domain.api.UploadsApi>(),
            chatsApi = getOrNull<me.floow.domain.api.ChatsApi>(),
            pendingPostsLocalStore = getOrNull<me.floow.domain.cache.PendingPostsLocalStore>(),
            postsRepository = getOrNull<me.floow.domain.data.repos.PostsRepository>(),
            uploadsRepository = getOrNull<me.floow.domain.data.repos.UploadsRepository>(),
            postsLocalStore = getOrNull<me.floow.domain.cache.PostsLocalStore>(),
            pendingPostMediaStore = getOrNull<me.floow.domain.data.repos.PendingPostMediaStore>(),
        )
    }

    single<me.floow.shared.profile.image.LocalImageFileReader> { me.floow.shared.profile.image.createLocalImageFileReader() }
    single<me.floow.domain.data.repos.PendingPostMediaStore> { me.floow.database.localstore.CommonPendingPostMediaStore() }
    single<me.floow.domain.data.repos.PendingPostsSyncScheduler> { me.floow.database.localstore.CommonPendingPostsSyncScheduler { getOrNull() } }
    single<me.floow.shared.profile.uilogic.compose.PostComposerRepository> {
        me.floow.shared.profile.uilogic.compose.CommonPostComposerRepository(
            categoryCatalogRepository = get(),
            postsRepository = get(),
            uploadsRepository = get(),
            postsLocalStore = get(),
            pendingPostsLocalStore = get(),
            pendingPostsSyncScheduler = get(),
            pendingPostMediaStore = get(),
        )
    }
    single<me.floow.domain.data.repos.CommentsReadCursorStore> {
        me.floow.shared.comments.CommonCommentsReadCursorStore()
    }
    single<me.floow.shared.profile.uilogic.edit.ProfileEditorRepository> {
        me.floow.shared.profile.uilogic.edit.AndroidProfileEditorRepository(
            profileRepository = get(),
            profileLocalStore = get(),
            usernameToIdCache = get(),
            authenticationManager = get(),
            authApi = get(),
        )
    }

    factory { LoginViewModel(get()) }
    factory { CreateProfileStateHolder(get()) }
    factory { (userId: String?) -> ProfileStateHolder(get(), userId, networkMonitor = get()) }
    factory { ChatsListStateHolder(get()) }
    includes(me.floow.shared.feed.di.feedModule)
    factory { RepliesStateHolder(get()) }
    factory { params ->
        DirectChatStateHolder(
            repository = get(),
            realtimeContract = params.getOrNull(),
            presenceContract = params.getOrNull(),
            activityStore = get(),
            sessionCache = get<SharedChatSessionCache>(),
            logger = get<Logger>(),
            mediaOutboxEngine = get<me.floow.shared.media.outbox.MediaOutboxEngine>(),
            mediaDownloadController = getOrNull<me.floow.domain.media.MediaDownloadController>(),
        )
    }
    factory { (imagePicker: PlatformImagePicker) ->
        CreatePostStateHolder(get(), imagePicker, get())
    }
    factory { (initialPost: ProfilePostItem, imagePicker: PlatformImagePicker) ->
        EditPostStateHolder(initialPost, get(), imagePicker, get())
    }
    factory { params ->
        val initialData = params.values.filterIsInstance<EditProfileOverlayData>().firstOrNull()
            ?: EditProfileOverlayData(name = "", username = "", bio = "", avatarUrl = null, backgroundUrl = null)
        val imagePicker = params.values.filterIsInstance<PlatformImagePicker>().firstOrNull()
            ?: NoOpPlatformImagePicker
        EditProfileStateHolder(
            initialData = initialData,
            repository = get(),
            imagePicker = imagePicker,
            imageFileReader = get(),
            uploadsRepository = get(),
        )
    }
    factory {
        CommentsStateHolder(
            commentsRepository = get(),
            commentsReadCursorStore = get(),
            authenticationManager = get(),
            logger = get(),
        )
    }
    factory { me.floow.chatssearch.uilogic.SearchUsersStateHolder(usersRepository = get()) }
}
