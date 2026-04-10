package me.floow.chats.di

import me.floow.chats.uilogic.chats.AndroidChatsListRepository
import me.floow.chats.uilogic.chat.AndroidChatPresenceContract
import me.floow.chats.uilogic.chat.AndroidChatRealtimeContract
import me.floow.chats.uilogic.chat.AndroidChatThreadRepository
import me.floow.chats.uilogic.replies.AndroidRepliesRepository
import me.floow.shared.chats.uilogic.ChatsListRepository
import me.floow.shared.chats.uilogic.direct.ChatPresenceContract
import me.floow.shared.chats.uilogic.direct.ChatRealtimeContract
import me.floow.shared.chats.uilogic.direct.ChatThreadRepository
import me.floow.shared.chats.uilogic.replies.RepliesRepository
import org.koin.dsl.module

val chatsModule = module {
	single<ChatsListRepository> { AndroidChatsListRepository(get(), get(), get(), get(), get()) }
	single<ChatThreadRepository> { AndroidChatThreadRepository(get(), get(), get()) }
	single<ChatRealtimeContract> { AndroidChatRealtimeContract(get(), get(), get()) }
	single<ChatPresenceContract> { AndroidChatPresenceContract(get()) }
	single<RepliesRepository> { AndroidRepliesRepository(get(), get()) }
}
