package me.floow.feed.uilogic

import me.floow.domain.models.Post
import java.util.UUID

/**
 * Обертка над постом для UI ленты.
 * uniqueId гарантирует, что Compose правильно сбросит состояние анимации (offset, rotation),
 * даже если в ленту по какой-то причине попал тот же самый Post (с тем же ID).
 */
data class FeedItem(
    val uniqueId: String = UUID.randomUUID().toString(),
    val post: Post,
    val recommendationReason: String? = null
)
