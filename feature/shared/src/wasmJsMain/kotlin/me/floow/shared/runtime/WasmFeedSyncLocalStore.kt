package me.floow.shared.runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.floow.domain.cache.FeedSyncCommand
import me.floow.domain.cache.FeedSyncCommandType
import me.floow.domain.cache.FeedSyncLocalStore
import me.floow.shared.login.auth.flowAuthReadLocalStorage
import me.floow.shared.login.auth.flowAuthWriteLocalStorage

class WasmFeedSyncLocalStore : FeedSyncLocalStore {
    private companion object {
        private const val STORAGE_KEY = "flow.feed.sync.queue"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val commands = mutableListOf<FeedSyncCommand>()
    private var nextId = 1L

    init {
        restoreState()
    }

    override suspend fun enqueueSwipe(userId: String, postId: String, isLiked: Boolean) {
        commands += FeedSyncCommand(
            id = nextId++,
            userId = userId,
            type = FeedSyncCommandType.SWIPE,
            postId = postId,
            isLiked = isLiked,
            attempts = 0,
            nextAttemptAt = 0L,
        )
        persistState()
    }

    override suspend fun enqueueUndo(userId: String, postId: String) {
        commands += FeedSyncCommand(
            id = nextId++,
            userId = userId,
            type = FeedSyncCommandType.UNDO,
            postId = postId,
            isLiked = null,
            attempts = 0,
            nextAttemptAt = 0L,
        )
        persistState()
    }

    override suspend fun peekNext(userId: String, nowMs: Long): FeedSyncCommand? {
        return commands
            .filter { it.userId == userId && it.nextAttemptAt <= nowMs }
            .minByOrNull(FeedSyncCommand::id)
    }

    override suspend fun nextAttemptAt(userId: String): Long? {
        return commands
            .filter { it.userId == userId }
            .minOfOrNull(FeedSyncCommand::nextAttemptAt)
    }

    override suspend fun remove(id: Long) {
        commands.removeAll { it.id == id }
        persistState()
    }

    override suspend fun incrementAttempts(id: Long, nextAttemptAt: Long) {
        val index = commands.indexOfFirst { it.id == id }
        if (index < 0) return
        val command = commands[index]
        commands[index] = command.copy(
            attempts = command.attempts + 1,
            nextAttemptAt = nextAttemptAt,
        )
        persistState()
    }

    override suspend fun pendingCount(userId: String): Int {
        return commands.count { it.userId == userId }
    }

    override suspend fun clearUser(userId: String) {
        commands.removeAll { it.userId == userId }
        persistState()
    }

    private fun restoreState() {
        val rawState = flowAuthReadLocalStorage(STORAGE_KEY)?.trim()?.takeIf(String::isNotEmpty) ?: return
        val restored = runCatching { json.decodeFromString<WasmFeedSyncQueueState>(rawState) }.getOrNull() ?: return
        commands.clear()
        commands += restored.commands.map(WasmFeedSyncCommandDto::toDomain)
        val maxKnownId = commands.maxOfOrNull(FeedSyncCommand::id) ?: 0L
        nextId = maxOf(restored.nextId, maxKnownId + 1L)
    }

    private fun persistState() {
        val state = WasmFeedSyncQueueState(
            nextId = nextId,
            commands = commands.map(WasmFeedSyncCommandDto.Companion::fromDomain),
        )
        flowAuthWriteLocalStorage(STORAGE_KEY, json.encodeToString(state))
    }
}

@Serializable
private data class WasmFeedSyncQueueState(
    val nextId: Long = 1L,
    val commands: List<WasmFeedSyncCommandDto> = emptyList(),
)

@Serializable
private data class WasmFeedSyncCommandDto(
    val id: Long,
    val userId: String,
    val type: String,
    val postId: String,
    val isLiked: Boolean? = null,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0L,
) {
    fun toDomain(): FeedSyncCommand {
        return FeedSyncCommand(
            id = id,
            userId = userId,
            type = if (type == FeedSyncCommandType.UNDO.name) FeedSyncCommandType.UNDO else FeedSyncCommandType.SWIPE,
            postId = postId,
            isLiked = isLiked,
            attempts = attempts,
            nextAttemptAt = nextAttemptAt,
        )
    }

    companion object {
        fun fromDomain(command: FeedSyncCommand): WasmFeedSyncCommandDto {
            return WasmFeedSyncCommandDto(
                id = command.id,
                userId = command.userId,
                type = command.type.name,
                postId = command.postId,
                isLiked = command.isLiked,
                attempts = command.attempts,
                nextAttemptAt = command.nextAttemptAt,
            )
        }
    }
}
