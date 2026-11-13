package me.floow.database.localstore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.floow.database.dao.PendingPostMutationDao
import me.floow.database.dbo.PendingPostMutationEntity
import me.floow.domain.cache.PendingPostMutation
import me.floow.domain.cache.PendingPostMutationState
import me.floow.domain.cache.PendingPostMutationType
import me.floow.domain.cache.PendingPostsLocalStore

class PendingPostsLocalStoreImpl(
	private val dao: PendingPostMutationDao,
) : PendingPostsLocalStore {
	private companion object {
		const val DEFAULT_DUE_LIMIT = 20
	}

	override fun observeAllPendingForSelf(): Flow<List<PendingPostMutation>> {
		return dao.observeAllPendingForSelf().map { entities -> entities.map { it.toDomain() } }
	}

	override suspend fun getDueCommands(nowMs: Long, limit: Int): List<PendingPostMutation> {
		return dao.getDueCommands(nowMs, limit.takeIf { it > 0 } ?: DEFAULT_DUE_LIMIT).map { it.toDomain() }
	}

	override suspend fun getByCommandId(commandId: String): PendingPostMutation? {
		return dao.getByCommandId(commandId)?.toDomain()
	}

	override suspend fun getByLocalPostId(localPostId: String): PendingPostMutation? {
		return dao.getByLocalPostId(localPostId)?.toDomain()
	}

	override suspend fun getByRemotePostId(remotePostId: String): PendingPostMutation? {
		return dao.getByRemotePostId(remotePostId)?.toDomain()
	}

	override suspend fun upsert(command: PendingPostMutation) {
		dao.upsert(command.toEntity())
	}

	override suspend fun markState(commandId: String, state: PendingPostMutationState, updatedAt: Long) {
		dao.markState(commandId, state.name, updatedAt)
	}

	override suspend fun markFailed(commandId: String, error: String?, retryCount: Int, nextRetryAt: Long, updatedAt: Long) {
		dao.markFailed(commandId, error, retryCount, nextRetryAt, updatedAt)
	}

	override suspend fun updateUploadedPayload(commandId: String, imagePayloadJson: String, updatedAt: Long) {
		dao.updateUploadedPayload(commandId, imagePayloadJson, updatedAt)
	}

	override suspend fun delete(commandId: String) {
		dao.delete(commandId)
	}

	override suspend fun deleteByLocalPostId(localPostId: String) {
		dao.deleteByLocalPostId(localPostId)
	}

	override suspend fun retry(commandId: String, nextRetryAt: Long, updatedAt: Long): Boolean {
		return dao.retry(commandId, nextRetryAt, updatedAt) > 0
	}

	override suspend fun retryByLocalPostId(localPostId: String, nextRetryAt: Long, updatedAt: Long): Boolean {
		return dao.retryByLocalPostId(localPostId, nextRetryAt, updatedAt) > 0
	}

	override suspend fun cleanupExpiredFailed(cutoffMs: Long) {
		dao.cleanupExpiredFailed(cutoffMs)
	}
}

private fun PendingPostMutationEntity.toDomain(): PendingPostMutation {
	return PendingPostMutation(
		commandId = commandId,
		type = runCatching { PendingPostMutationType.valueOf(type) }.getOrDefault(PendingPostMutationType.CREATE),
		localPostId = localPostId,
		remotePostId = remotePostId,
		clientMutationId = clientMutationId,
		state = runCatching { PendingPostMutationState.valueOf(state) }.getOrDefault(PendingPostMutationState.PENDING),
		description = description,
		category = category,
		imagePayloadJson = imagePayloadJson,
		stagedMediaJson = stagedMediaJson,
		previousPostSnapshotJson = previousPostSnapshotJson,
		lastError = lastError,
		retryCount = retryCount,
		nextRetryAt = nextRetryAt,
		createdAt = createdAt,
		updatedAt = updatedAt,
	)
}

private fun PendingPostMutation.toEntity(): PendingPostMutationEntity {
	return PendingPostMutationEntity(
		commandId = commandId,
		type = type.name,
		localPostId = localPostId,
		remotePostId = remotePostId,
		clientMutationId = clientMutationId,
		state = state.name,
		description = description,
		category = category,
		imagePayloadJson = imagePayloadJson,
		stagedMediaJson = stagedMediaJson,
		previousPostSnapshotJson = previousPostSnapshotJson,
		lastError = lastError,
		retryCount = retryCount,
		nextRetryAt = nextRetryAt,
		createdAt = createdAt,
		updatedAt = updatedAt,
	)
}
