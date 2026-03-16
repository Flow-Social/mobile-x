package me.floow.chats.uilogic.chats

import me.floow.domain.values.ProfileName
import kotlin.random.Random

fun generateRandomChats(n: Int): List<Chat> {
	val firstNames = listOf(
		"John",
		"Jane",
		"Alice",
		"Bob",
		"Charlie",
		"Demn",
		"Finsi",
		"Mixno",
		"Max",
		"Vlad",
		"Andrew",
		"Саша",
		"котик"
	)
	val messages = listOf(
		"Hello!",
		"How are you?",
		"See you later!",
		"Good morning!",
		"Have a nice day!",
		"Смотри какие пельмени!",
		"ИМБИЩЕЕЕЕ!!!",
		"На видео Threads, написанный на Compose, а не приложение фейсбука",
		"Try out the fastest crypto-to-crypto \uD83D\uDD01 Swaps in Telegram and share the \$10,000 prize fund in our contest for all users! Learn more ›"
	)

	val chats = mutableListOf<Chat>()

	for (i in 1..n) {
		val profileName = ProfileName.create(
			value = firstNames.random(),
		)
		val lastMessageText = messages.random()
		val lastMessageTimeMillis = System.currentTimeMillis() -
			Random.nextLong(0, 30L * 24 * 60 * 60 * 1000) -
			Random.nextLong(0, 24L * 60 * 60 * 1000) -
			Random.nextLong(0, 60L * 60 * 1000) -
			Random.nextLong(0, 60L * 1000)
		val isOnline = Random.nextBoolean()
		val unreadCount = if (Random.nextBoolean()) Random.nextInt(1, 10) else 0
		val chatMuted = Random.nextBoolean()
		val avatarUrl = if (Random.nextBoolean()) "https://example.com/avatar" else null
		val attachedMediaUrl =
			if (Random.nextBoolean()) "https://example.com/media" else null
		val lastSentMessageState =
			if (Random.nextBoolean()) LastSentMessageState.entries.toTypedArray().random() else null

		val chat = Chat(
			id = "mock_chat_$i",
			conversationId = null,
			type = ChatType.DIRECT,
			name = profileName,
			lastMessageText = lastMessageText,
			lastMessageTimeMillis = lastMessageTimeMillis,
			isOnline = isOnline,
			unreadCount = unreadCount,
			chatMuted = chatMuted,
			avatarUrl = avatarUrl,
			attachedMediaUrl = attachedMediaUrl,
			lastSentMessageState = lastSentMessageState
		)

		chats.add(chat)
	}

	return chats
}
