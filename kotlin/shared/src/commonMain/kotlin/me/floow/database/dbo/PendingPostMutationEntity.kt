package me.floow.database.dbo

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
	tableName = "pending_post_mutations",
	indices = [
		Index(value = ["state", "nextRetryAt", "createdAt"]),
		Index(value = ["remotePostId"]),
		Index(value = ["localPostId"]),
		Index(value = ["clientMutationId"], unique = true)
	]
)
data class PendingPostMutationEntity(
	@PrimaryKey
	val commandId: String,
	val type: String,
	val localPostId: String,
	val remotePostId: String?,
	val clientMutationId: String,
	val state: String,
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
