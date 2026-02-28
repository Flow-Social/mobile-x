package me.floow.profile.uilogic.addpost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.domain.cache.PostsCacheKeys
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.UploadImageData
import me.floow.domain.data.repos.UploadsRepository
import me.floow.domain.models.Post
import me.floow.profile.ui.addpost.CreatePostImageItem
import me.floow.profile.ui.addpost.CreatePostUiState

data class EditPostImageDraft(
	val id: String,
	val remoteUrl: String,
	val replacementLocalUri: String? = null,
) {
	val displayUri: String
		get() = replacementLocalUri ?: remoteUrl
}

data class EditPostSaveResult(
	val postId: String,
	val description: String?,
	val imageUrls: List<String>,
)

data class EditPostVmState(
	val isInitialized: Boolean = false,
	val postId: String = "",
	val initialDescription: String? = null,
	val initialImageUrls: List<String> = emptyList(),
	val description: String = "",
	val initialImageIds: List<String> = emptyList(),
	val images: List<EditPostImageDraft> = emptyList(),
	val isSaving: Boolean = false,
	val savingStage: String? = null,
	val uploadProgress: Int = 0,
	val uploadTotal: Int = 0,
	val errorMessage: String? = null,
) {
	val hasChanges: Boolean
		get() = description != initialDescription.orEmpty() ||
			images.map { it.id } != initialImageIds ||
			images.any { it.replacementLocalUri != null }

	val canSave: Boolean
		get() = !isSaving && images.isNotEmpty() && hasChanges
}

class EditPostViewModel(
	private val postsRepository: PostsRepository,
	private val uploadsRepository: UploadsRepository,
	private val localImageFileReader: LocalImageFileReader,
	private val postsLocalStore: PostsLocalStore,
) : ViewModel() {
	private val _state = MutableStateFlow(EditPostVmState())
	val state: StateFlow<EditPostVmState> = _state

	fun initialize(postId: String, description: String?, imageUrls: List<String>) {
		_state.update { current ->
			if (current.isInitialized) {
				return@update current
			}
			val normalizedImages = imageUrls
				.map { it.trim() }
				.filter { it.isNotBlank() }
			val items = normalizedImages.mapIndexed { index, url ->
				EditPostImageDraft(
					id = "initial_$index",
					remoteUrl = url,
				)
			}
			current.copy(
				isInitialized = true,
				postId = postId,
				initialDescription = description,
				initialImageUrls = normalizedImages,
				description = description.orEmpty(),
				initialImageIds = items.map { it.id },
				images = items,
				errorMessage = null,
			)
		}
	}

	fun updateDescription(value: String) {
		_state.update { it.copy(description = value, errorMessage = null) }
	}

	fun removeImage(imageId: String) {
		_state.update { current ->
			if (current.images.size <= 1) {
				return@update current.copy(errorMessage = "В посте должно быть минимум 1 изображение")
			}
			val updated = current.images.filterNot { it.id == imageId }
			if (updated.size == current.images.size) {
				return@update current
			}
			current.copy(images = updated, errorMessage = null)
		}
	}

	fun replaceImage(imageId: String, localUri: String) {
		val normalizedUri = localUri.trim()
		if (normalizedUri.isBlank()) return

		_state.update { current ->
			val updated = current.images.map { image ->
				if (image.id == imageId) {
					image.copy(replacementLocalUri = normalizedUri)
				} else {
					image
				}
			}
			if (updated == current.images) {
				return@update current
			}
			current.copy(images = updated, errorMessage = null)
		}
	}

	fun reorderImages(orderedImageIds: List<String>) {
		_state.update { current ->
			if (orderedImageIds.size != current.images.size) {
				return@update current
			}
			if (orderedImageIds.toSet() != current.images.map { it.id }.toSet()) {
				return@update current
			}
			val byId = current.images.associateBy { it.id }
			val reordered = orderedImageIds.mapNotNull { id -> byId[id] }
			if (reordered.size != current.images.size || reordered == current.images) {
				return@update current
			}
			current.copy(images = reordered, errorMessage = null)
		}
	}

	fun save(
		onOptimistic: (EditPostSaveResult) -> Unit = {},
		onSuccess: (EditPostSaveResult) -> Unit,
		onFailure: (EditPostSaveResult) -> Unit = {},
	) {
		val snapshot = _state.value
		if (!snapshot.canSave) {
			if (snapshot.images.isEmpty()) {
				_state.update { it.copy(errorMessage = "В посте должно быть минимум 1 изображение") }
			}
			return
		}
		if (snapshot.postId.isBlank()) {
			_state.update { it.copy(errorMessage = "Не удалось определить пост для редактирования") }
			return
		}

		viewModelScope.launch {
			val optimisticDescription = snapshot.description.trim().ifBlank { null }
			val optimisticImageUrls = snapshot.images
				.map { it.displayUri.trim() }
				.filter { it.isNotBlank() }
			if (optimisticImageUrls.isEmpty()) {
				_state.update { it.copy(errorMessage = "В посте должно быть минимум 1 изображение") }
				return@launch
			}
			val optimisticResult = EditPostSaveResult(
				postId = snapshot.postId,
				description = optimisticDescription,
				imageUrls = optimisticImageUrls,
			)
			val rollbackResult = EditPostSaveResult(
				postId = snapshot.postId,
				description = snapshot.initialDescription,
				imageUrls = snapshot.initialImageUrls,
			)
			onOptimistic(optimisticResult)
			val optimisticRollback = applyOptimisticCaches(
				postId = snapshot.postId,
				description = optimisticDescription,
				imageUrls = optimisticImageUrls,
			)

			suspend fun failWithRollback(message: String) {
				rollbackOptimisticCaches(optimisticRollback)
				onFailure(rollbackResult)
				_state.update {
					it.copy(
						isSaving = false,
						savingStage = null,
						uploadProgress = 0,
						uploadTotal = 0,
						errorMessage = message,
					)
				}
			}

			_state.update {
				it.copy(
					isSaving = true,
					savingStage = "Подготовка файлов...",
					uploadProgress = 0,
					uploadTotal = snapshot.images.count { image -> image.replacementLocalUri != null },
					errorMessage = null,
				)
			}

			val replacements = snapshot.images
				.mapNotNull { image -> image.replacementLocalUri?.let { localUri -> image.id to localUri } }

			val uploadedById = if (replacements.isEmpty()) {
				emptyMap()
			} else {
				val localFiles = coroutineScope {
					replacements
						.map { (_, localUri) -> async { localImageFileReader.read(localUri) } }
						.awaitAll()
				}
				if (localFiles.any { it == null }) {
					failWithRollback("Некоторые файлы не поддерживаются. Используй изображения из галереи")
					return@launch
				}
				val uploadData = localFiles.mapNotNull { file ->
					file?.let {
						UploadImageData(
							fileName = it.fileName,
							contentType = it.contentType,
							sizeBytes = it.sizeBytes,
							bytes = it.bytes,
						)
					}
				}

				_state.update { it.copy(savingStage = "Загрузка изображений...") }
				when (val uploadResult = uploadsRepository.uploadImages(
					data = uploadData,
					kind = "post",
					onProgress = { uploaded, total ->
						_state.update {
							it.copy(
								savingStage = "Загрузка изображений... $uploaded/$total",
								uploadProgress = uploaded,
								uploadTotal = total,
							)
						}
					}
				)) {
					is GetDataResponse.Success -> {
						if (uploadResult.data.size != replacements.size) {
							failWithRollback("Не удалось обновить изображения. Попробуй ещё раз")
							return@launch
						}
						replacements.mapIndexed { index, replacement -> replacement.first to uploadResult.data[index] }.toMap()
					}
					is GetDataResponse.Error -> {
						failWithRollback("Не удалось загрузить изображения. Проверь интернет и повтори попытку")
						return@launch
					}
				}
			}

			val finalImageUrls = snapshot.images.mapNotNull { image ->
				uploadedById[image.id] ?: image.remoteUrl
			}
			if (finalImageUrls.isEmpty()) {
				failWithRollback("В посте должно быть минимум 1 изображение")
				return@launch
			}

			_state.update { it.copy(savingStage = "Сохранение изменений...") }
			val normalizedDescription = optimisticDescription
			val result = postsRepository.updatePost(
				postId = snapshot.postId,
				description = normalizedDescription,
				imageUrls = finalImageUrls,
			)

				when (result) {
					is UpdateDataResponse.Success -> {
						syncCachesAfterEdit(
							postId = snapshot.postId,
							description = normalizedDescription,
							imageUrls = finalImageUrls,
						)
						_state.update {
							it.copy(
								isSaving = false,
							savingStage = null,
							uploadProgress = 0,
							uploadTotal = 0,
							errorMessage = null,
						)
					}
					onSuccess(
						EditPostSaveResult(
							postId = snapshot.postId,
							description = normalizedDescription,
							imageUrls = finalImageUrls,
						)
					)
				}
				is UpdateDataResponse.Failure -> {
					failWithRollback("Не удалось сохранить изменения. Попробуй ещё раз")
				}
			}
		}
	}

	private data class CacheRollbackEntry(
		val userId: String,
		val posts: List<Post>,
	)

	private suspend fun applyOptimisticCaches(
		postId: String,
		description: String?,
		imageUrls: List<String>,
	): List<CacheRollbackEntry> {
		val normalizedImageUrls = imageUrls.filter { it.isNotBlank() }
		if (postId.isBlank() || normalizedImageUrls.isEmpty()) return emptyList()

		val now = System.currentTimeMillis()
		val feedCacheKeys = listOf(
			PostsCacheKeys.feedStackUserId(PostsCacheKeys.SELF_USER_ID),
			PostsCacheKeys.FEED_STACK_USER_ID,
		).distinct()

		val cachedByKey = mutableMapOf<String, List<Post>>()
		suspend fun cachedPosts(key: String): List<Post> {
			cachedByKey[key]?.let { return it }
			return postsLocalStore.getPosts(key).also { loaded ->
				cachedByKey[key] = loaded
			}
		}

		val selfPosts = cachedPosts(PostsCacheKeys.SELF_USER_ID)
		val authorIdFromSelf = selfPosts.firstOrNull { it.id == postId }?.author?.id
		var authorIdFromFeed: String? = null
		for (cacheKey in feedCacheKeys) {
			val candidateAuthorId = cachedPosts(cacheKey)
				.firstOrNull { it.id == postId }
				?.author
				?.id
			if (!candidateAuthorId.isNullOrBlank()) {
				authorIdFromFeed = candidateAuthorId
				break
			}
		}
		val authorId = (authorIdFromSelf ?: authorIdFromFeed)
			?.takeIf { it.isNotBlank() && it != PostsCacheKeys.SELF_USER_ID }

		val candidateKeys = buildList {
			add(PostsCacheKeys.SELF_USER_ID)
			addAll(feedCacheKeys)
			if (!authorId.isNullOrBlank()) {
				add(authorId)
			}
		}.distinct()

		val rollbackEntries = mutableListOf<CacheRollbackEntry>()
		candidateKeys.forEach { cacheKey ->
			val oldPosts = cachedPosts(cacheKey)
			val updatedPosts = oldPosts.replaceEditedPost(
				postId = postId,
				description = description,
				imageUrls = normalizedImageUrls,
			)
			if (updatedPosts != oldPosts) {
				rollbackEntries += CacheRollbackEntry(
					userId = cacheKey,
					posts = oldPosts,
				)
				postsLocalStore.replacePosts(
					userId = cacheKey,
					posts = updatedPosts,
					updatedAt = now,
				)
			}
		}
		return rollbackEntries
	}

	private suspend fun rollbackOptimisticCaches(entries: List<CacheRollbackEntry>) {
		if (entries.isEmpty()) return
		val now = System.currentTimeMillis()
		entries.forEach { entry ->
			postsLocalStore.replacePosts(
				userId = entry.userId,
				posts = entry.posts,
				updatedAt = now,
			)
		}
	}

	private suspend fun syncCachesAfterEdit(
		postId: String,
		description: String?,
		imageUrls: List<String>,
	) {
		val normalizedImageUrls = imageUrls.filter { it.isNotBlank() }
		if (postId.isBlank() || normalizedImageUrls.isEmpty()) return

		val now = System.currentTimeMillis()
		val mePosts = postsLocalStore.getPosts(PostsCacheKeys.SELF_USER_ID)
		val updatedMePosts = mePosts.replaceEditedPost(
			postId = postId,
			description = description,
			imageUrls = normalizedImageUrls,
		)
		if (updatedMePosts != mePosts) {
			postsLocalStore.replacePosts(
				userId = PostsCacheKeys.SELF_USER_ID,
				posts = updatedMePosts,
				updatedAt = now,
			)
		}

		val authorId = (updatedMePosts.firstOrNull { it.id == postId } ?: mePosts.firstOrNull { it.id == postId })
			?.author
			?.id
			?.takeIf { it.isNotBlank() && it != PostsCacheKeys.SELF_USER_ID }
		if (!authorId.isNullOrBlank()) {
			val authorPosts = postsLocalStore.getPosts(authorId)
			val updatedAuthorPosts = authorPosts.replaceEditedPost(
				postId = postId,
				description = description,
				imageUrls = normalizedImageUrls,
			)
			if (updatedAuthorPosts != authorPosts) {
				postsLocalStore.replacePosts(
					userId = authorId,
					posts = updatedAuthorPosts,
					updatedAt = now,
				)
			}
		}

		val feedCacheKeys = listOf(
			PostsCacheKeys.feedStackUserId(PostsCacheKeys.SELF_USER_ID),
			PostsCacheKeys.FEED_STACK_USER_ID
		).distinct()
		feedCacheKeys.forEach { feedCacheKey ->
			val feedPosts = postsLocalStore.getPosts(feedCacheKey)
			val updatedFeedPosts = feedPosts.replaceEditedPost(
				postId = postId,
				description = description,
				imageUrls = normalizedImageUrls,
			)
			if (updatedFeedPosts != feedPosts) {
				postsLocalStore.replacePosts(
					userId = feedCacheKey,
					posts = updatedFeedPosts,
					updatedAt = now,
				)
			}
		}

	}
}

private fun List<Post>.replaceEditedPost(
	postId: String,
	description: String?,
	imageUrls: List<String>,
): List<Post> {
	var changed = false
	val updated = map { post ->
		if (post.id != postId) return@map post
		changed = true
		post.copy(
			content = post.content.copy(
				description = description,
				imageUrls = imageUrls,
				imageVariants = emptyList(),
			)
		)
	}
	return if (changed) updated else this
}

internal fun EditPostVmState.toCreatePostUiState(): CreatePostUiState {
	return CreatePostUiState(
		description = description,
		selectedImages = images.map { image ->
			CreatePostImageItem(
				id = image.id,
				uri = image.displayUri,
				isLocalReplacement = image.replacementLocalUri != null,
			)
		},
		categories = emptyList(),
		selectedCategoryCode = null,
		isCategoriesLoading = false,
		isCategoriesError = false,
		isPublishing = isSaving,
		publishingStage = savingStage,
		uploadProgress = uploadProgress,
		uploadTotal = uploadTotal,
		errorMessage = errorMessage,
		canPublish = canSave,
		hasManualUrlsInDraft = false,
	)
}
