package me.floow.app.posts

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.floow.domain.api.models.CreatePostData
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.cache.PendingPostMutation
import me.floow.domain.cache.PendingPostMutationState
import me.floow.domain.cache.PendingPostMutationType
import me.floow.domain.cache.PendingPostsLocalStore
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.PendingPostImageEntry
import me.floow.domain.data.repos.PendingPostImageEntryKind
import me.floow.domain.data.repos.PendingPostImagePayload
import me.floow.domain.data.repos.PendingPostMediaStore
import me.floow.domain.data.repos.PendingPostStagedMedia
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.UploadsRepository
import me.floow.domain.models.Post
import me.floow.domain.models.PostAuthor
import me.floow.domain.models.PostContent
import me.floow.domain.utils.Logger
import org.koin.core.context.GlobalContext
import kotlin.math.min
import kotlin.math.pow

private const val SELF_USER_ID = "me"
private const val FAILED_TTL_MS = 7L * 24L * 60L * 60L * 1000L

class PendingPostsSyncWorker(
	appContext: Context,
	workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
	override suspend fun doWork(): Result {
		val koin = GlobalContext.getKoinApplicationOrNull()?.koin ?: return Result.retry()
		return try {
			PendingPostsSyncRunner.runSync(koin)
			Result.success()
		} catch (t: Throwable) {
			Result.retry()
		}
	}
}

internal object PendingPostsSyncRunner {
	private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

	suspend fun runSync(koin: org.koin.core.Koin) {
		val authManager = koin.get<AuthenticationManager>()
		if (!authManager.isSignedIn()) return

		val logger = koin.get<Logger>()
		val pendingStore = koin.get<PendingPostsLocalStore>()
		val postsRepository = koin.get<PostsRepository>()
		val uploadsRepository = koin.get<UploadsRepository>()
		val postsLocalStore = koin.get<PostsLocalStore>()
		val mediaStore = koin.get<PendingPostMediaStore>()
		val now = System.currentTimeMillis()

		try {
			pendingStore.cleanupExpiredFailed(now - FAILED_TTL_MS)
			pendingStore.getDueCommands(now).forEach { command ->
				when (command.type) {
					PendingPostMutationType.CREATE -> processCreate(command, pendingStore, postsRepository, uploadsRepository, postsLocalStore, mediaStore)
					PendingPostMutationType.UPDATE -> processUpdate(command, pendingStore, postsRepository, uploadsRepository, postsLocalStore, mediaStore)
					PendingPostMutationType.DELETE -> Unit
				}
			}
		} catch (t: Throwable) {
			logger.d("PendingPostsSyncRunner.runSync", "Sync failed: ${t.message}")
		}
	}

	private suspend fun processCreate(
		command: PendingPostMutation,
		pendingStore: PendingPostsLocalStore,
		postsRepository: PostsRepository,
		uploadsRepository: UploadsRepository,
		postsLocalStore: PostsLocalStore,
		mediaStore: PendingPostMediaStore,
	) {
		val payload = ensureRemotePayload(command, pendingStore, uploadsRepository, mediaStore)
			?: return fail(command, pendingStore, "Failed to upload staged media")
		val category = command.category ?: return fail(command, pendingStore, "Missing category")
		val now = System.currentTimeMillis()
		pendingStore.markState(command.commandId, PendingPostMutationState.SYNCING, now)
		val response = postsRepository.createPost(
			CreatePostData(
				imageUrls = payload.entries.mapNotNull(PendingPostImageEntry::remoteUrl),
				description = command.description,
				category = category,
				idempotencyKey = command.clientMutationId,
			)
		)
		when (response) {
			is UpdateDataResponse.Success -> {
				val post = response.post
				if (post != null) {
					postsLocalStore.upsertPost(
						userId = SELF_USER_ID,
						post = post,
						updatedAt = System.currentTimeMillis(),
					)
				}
				pendingStore.delete(command.commandId)
				mediaStore.deleteStaged(command.commandId)
			}
			is UpdateDataResponse.Failure -> fail(
				command = command,
				pendingStore = pendingStore,
				error = response.message ?: "Не удалось отправить пост",
			)
		}
	}

	private suspend fun processUpdate(
		command: PendingPostMutation,
		pendingStore: PendingPostsLocalStore,
		postsRepository: PostsRepository,
		uploadsRepository: UploadsRepository,
		postsLocalStore: PostsLocalStore,
		mediaStore: PendingPostMediaStore,
	) {
		val remotePostId = command.remotePostId ?: return fail(command, pendingStore, "Missing remote post id")
		val payload = ensureRemotePayload(command, pendingStore, uploadsRepository, mediaStore)
			?: return failAndRollback(command, pendingStore, postsLocalStore, "Failed to upload staged media")
		val now = System.currentTimeMillis()
		pendingStore.markState(command.commandId, PendingPostMutationState.SYNCING, now)
		val response = postsRepository.updatePost(
			postId = remotePostId,
			description = command.description,
			imageUrls = payload.entries.mapNotNull(PendingPostImageEntry::remoteUrl),
			category = command.category,
		)
		when (response) {
			is UpdateDataResponse.Success -> {
				val post = response.post
				if (post != null) {
					postsLocalStore.upsertPost(
						userId = SELF_USER_ID,
						post = post,
						updatedAt = System.currentTimeMillis(),
					)
				}
				pendingStore.delete(command.commandId)
				mediaStore.deleteStaged(command.commandId)
			}
			is UpdateDataResponse.Failure -> failAndRollback(
				command = command,
				pendingStore = pendingStore,
				postsLocalStore = postsLocalStore,
				error = response.message ?: "Не удалось сохранить изменения",
			)
		}
	}

	private suspend fun ensureRemotePayload(
		command: PendingPostMutation,
		pendingStore: PendingPostsLocalStore,
		uploadsRepository: UploadsRepository,
		mediaStore: PendingPostMediaStore,
	): PendingPostImagePayload? {
		val currentPayload = decodeImagePayload(command.imagePayloadJson)
		if (currentPayload.entries.none { it.kind == PendingPostImageEntryKind.STAGED }) {
			return currentPayload
		}
		val stagedMedia = decodeStagedMedia(command.stagedMediaJson)
		val stagedById = stagedMedia.associateBy(PendingPostStagedMedia::id)
		val uploadData = currentPayload.entries.mapNotNull { entry ->
			if (entry.kind != PendingPostImageEntryKind.STAGED) return@mapNotNull null
			val media = stagedById[entry.stagedMediaId] ?: return null
			mediaStore.readUploadData(media)
		}
		if (uploadData.size != currentPayload.entries.count { it.kind == PendingPostImageEntryKind.STAGED }) {
			return null
		}
		val now = System.currentTimeMillis()
		pendingStore.markState(command.commandId, PendingPostMutationState.UPLOADING, now)
		val uploadedUrls = when (
			val result = uploadsRepository.uploadImages(
				data = uploadData,
				kind = "post",
				onProgress = null,
			)
		) {
			is GetDataResponse.Success -> result.data
			is GetDataResponse.Error -> return null
		}
		val uploadedIterator = uploadedUrls.iterator()
		val nextPayload = PendingPostImagePayload(
			entries = currentPayload.entries.map { entry ->
				if (entry.kind == PendingPostImageEntryKind.REMOTE) {
					entry
				} else {
					PendingPostImageEntry(
						kind = PendingPostImageEntryKind.REMOTE,
						remoteUrl = uploadedIterator.next(),
					)
				}
			}
		)
		pendingStore.updateUploadedPayload(
			commandId = command.commandId,
			imagePayloadJson = json.encodeToString(PendingPostImagePayload.serializer(), nextPayload),
			updatedAt = System.currentTimeMillis(),
		)
		return nextPayload
	}

	private suspend fun failAndRollback(
		command: PendingPostMutation,
		pendingStore: PendingPostsLocalStore,
		postsLocalStore: PostsLocalStore,
		error: String,
	) {
		rollbackUpdate(command, postsLocalStore)
		if (command.type == PendingPostMutationType.UPDATE) {
			pendingStore.delete(command.commandId)
			return
		}
		fail(command, pendingStore, error)
	}

	private suspend fun rollbackUpdate(
		command: PendingPostMutation,
		postsLocalStore: PostsLocalStore,
	) {
		if (command.type != PendingPostMutationType.UPDATE) return
		val snapshot = decodePreviousSnapshot(command.previousPostSnapshotJson) ?: return
		val currentPost = command.remotePostId?.let { postsLocalStore.getPost(SELF_USER_ID, it) }
		val rollbackPost = Post(
			id = snapshot.id,
			author = currentPost?.author ?: PostAuthor(
				id = SELF_USER_ID,
				name = null,
				username = null,
				avatarUrl = null,
			),
			content = PostContent(
				imageUrls = snapshot.imageUrls,
				description = snapshot.description,
				imageVariants = emptyList(),
			),
			category = snapshot.category,
			createdAt = snapshot.createdAt,
			likesCount = snapshot.likesCount,
			commentsCount = snapshot.commentsCount,
			commentersPreview = snapshot.commentersPreview,
		)
		postsLocalStore.upsertPost(
			userId = SELF_USER_ID,
			post = rollbackPost,
			updatedAt = System.currentTimeMillis(),
		)
	}

	private suspend fun fail(command: PendingPostMutation, pendingStore: PendingPostsLocalStore, error: String) {
		val retryCount = command.retryCount + 1
		val delayMs = min(60L * 60L * 1000L, (2.0.pow(retryCount.toDouble()) * 1000L).toLong())
		val now = System.currentTimeMillis()
		pendingStore.markFailed(
			commandId = command.commandId,
			error = error,
			retryCount = retryCount,
			nextRetryAt = now + delayMs,
			updatedAt = now,
		)
	}

	private fun decodeImagePayload(payloadJson: String): PendingPostImagePayload {
		return runCatching {
			json.decodeFromString<PendingPostImagePayload>(payloadJson)
		}.getOrDefault(PendingPostImagePayload())
	}

	private fun decodeStagedMedia(payloadJson: String): List<PendingPostStagedMedia> {
		return runCatching {
			json.decodeFromString<List<PendingPostStagedMedia>>(payloadJson)
		}.getOrDefault(emptyList())
	}

	private fun decodePreviousSnapshot(payloadJson: String?): ExistingPostSnapshot? {
		if (payloadJson.isNullOrBlank()) return null
		return runCatching {
			json.decodeFromString<ExistingPostSnapshot>(payloadJson)
		}.getOrNull()
	}
}

@Serializable
private data class ExistingPostSnapshot(
	val id: String,
	val description: String?,
	val imageUrls: List<String> = emptyList(),
	val category: String,
	val createdAt: Long,
	val likesCount: Int,
	val commentsCount: Int,
	val commentersPreview: List<String> = emptyList(),
)
