package me.floow.domain.cache

import kotlinx.coroutines.flow.Flow

enum class PendingPostMutationType {
	CREATE,
	UPDATE,
	DELETE
}

enum class PendingPostMutationState {
	PENDING,
	UPLOADING,
	SYNCING,
	FAILED,
	DONE,
	CANCELED
}

data class PendingPostMutation(
	val commandId: String,
	val type: PendingPostMutationType,
	val localPostId: String,
	val remotePostId: String?,
	val clientMutationId: String,
	val state: PendingPostMutationState,
	val description: String?,
	val category: String?,
	val imagePayloadJson: String,
	val stagedMediaJson: String,
	val previousPostSnapshotJson: String?,
	val lastError: String?,
	val retryCount: Int,
	val nextRetryAt: Long,
	val createdAt: Long,
	val updatedAt: Long,
)

interface PendingPostsLocalStore {
	fun observeAllPendingForSelf(): Flow<List<PendingPostMutation>>
	suspend fun getDueCommands(nowMs: Long, limit: Int = 20): List<PendingPostMutation>
	suspend fun getByCommandId(commandId: String): PendingPostMutation?
	suspend fun getByLocalPostId(localPostId: String): PendingPostMutation?
	suspend fun getByRemotePostId(remotePostId: String): PendingPostMutation?
	suspend fun upsert(command: PendingPostMutation)
	suspend fun markState(commandId: String, state: PendingPostMutationState, updatedAt: Long)
	suspend fun markFailed(commandId: String, error: String?, retryCount: Int, nextRetryAt: Long, updatedAt: Long)
	suspend fun updateUploadedPayload(commandId: String, imagePayloadJson: String, updatedAt: Long)
	suspend fun delete(commandId: String)
	suspend fun deleteByLocalPostId(localPostId: String)
	suspend fun retry(commandId: String, nextRetryAt: Long, updatedAt: Long): Boolean
	suspend fun retryByLocalPostId(localPostId: String, nextRetryAt: Long, updatedAt: Long): Boolean
	suspend fun cleanupExpiredFailed(cutoffMs: Long)
}
