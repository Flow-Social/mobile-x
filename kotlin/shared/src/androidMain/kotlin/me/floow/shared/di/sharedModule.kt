package me.floow.shared.di

import me.floow.shared.chats.data.AndroidChatPresenceContract
import me.floow.shared.chats.data.AndroidChatRealtimeContract
import me.floow.shared.chats.data.AndroidChatThreadRepository
import me.floow.shared.chats.data.AndroidChatsListRepository
import me.floow.shared.chats.data.AndroidRepliesRepository
import me.floow.shared.chats.uilogic.ChatsListRepository
import me.floow.shared.chats.uilogic.direct.ChatPresenceContract
import me.floow.shared.chats.uilogic.direct.ChatRealtimeContract
import me.floow.shared.chats.uilogic.direct.ChatThreadRepository
import me.floow.shared.chats.uilogic.replies.RepliesRepository
import me.floow.shared.login.auth.AndroidAuthRepository
import me.floow.shared.login.createprofile.AndroidCreateProfileRepository
import me.floow.shared.login.uilogic.AuthRepository
import me.floow.shared.login.uilogic.createprofile.CreateProfileRepository
import me.floow.shared.profile.image.AndroidLocalImageFileReader
import me.floow.shared.profile.image.LocalImageFileReader
import me.floow.shared.profile.uilogic.AndroidProfileRepository
import me.floow.shared.profile.uilogic.ProfileRepository
import me.floow.shared.profile.uilogic.compose.AndroidPostComposerRepository
import me.floow.shared.profile.uilogic.compose.PostComposerRepository
import me.floow.shared.profile.uilogic.edit.AndroidProfileEditorRepository
import me.floow.shared.profile.uilogic.edit.ProfileEditorRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

import me.floow.shared.chats.uilogic.ConversationActivityStore

val sharedModule = module {
	factoryOf(::AndroidAuthRepository) bind AuthRepository::class
	factoryOf(::AndroidCreateProfileRepository) bind CreateProfileRepository::class

	single<LocalImageFileReader> { AndroidLocalImageFileReader(androidContext()) }
	single<ProfileRepository> {
		AndroidProfileRepository(
			profileRepository = get(),
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

	single<ChatsListRepository> {
		AndroidChatsListRepository(
			chatsRepository = get(),
			notificationsRealtimeRepository = get(),
			presenceRepository = get(),
			authenticationManager = get(),
			activityStore = getOrNull() ?: ConversationActivityStore(),
		)
	}
	single<ChatThreadRepository> { AndroidChatThreadRepository(get(), get(), get(), get(), get(), get()) }
	single<ChatRealtimeContract> { AndroidChatRealtimeContract(get(), get(), get()) }
	single<ChatPresenceContract> { AndroidChatPresenceContract(get()) }
	single<RepliesRepository> { AndroidRepliesRepository(get(), get(), get()) }
}
