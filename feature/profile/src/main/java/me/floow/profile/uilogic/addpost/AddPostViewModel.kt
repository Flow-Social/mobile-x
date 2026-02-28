package me.floow.profile.uilogic.addpost

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.domain.cache.PostsCacheKeys
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.api.models.CreatePostData
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.CategoryCatalogRepository
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.UploadImageData
import me.floow.domain.data.repos.UploadsRepository
import me.floow.domain.models.CategoryCatalogItem

data class AddPostVmState(
	val imageUrls: List<String> = listOf(""),
	val localImageUris: List<String> = emptyList(),
	val description: String = "",
	val categories: List<CategoryCatalogItem> = emptyList(),
	val selectedCategoryCode: String? = null,
	val isCategoriesLoading: Boolean = false,
	val isCategoriesError: Boolean = false,
	val isPublishing: Boolean = false,
	val publishingStage: String? = null,
	val uploadProgress: Int = 0,
	val uploadTotal: Int = 0,
	val errorMessage: String? = null,
) {
	val selectedUrlCount: Int
		get() = imageUrls.count { it.isNotBlank() }

	val isError: Boolean
		get() = !errorMessage.isNullOrBlank()

	fun totalSelectedImagesCount(allowManualUrls: Boolean): Int {
		return localImageUris.size + if (allowManualUrls) selectedUrlCount else 0
	}

	fun canPublish(allowManualUrls: Boolean): Boolean {
		return !isPublishing &&
			!isCategoriesLoading &&
			!selectedCategoryCode.isNullOrBlank() &&
			totalSelectedImagesCount(allowManualUrls) in 1..MAX_IMAGES
	}

	companion object {
		const val MAX_IMAGES = 4
	}
}

class AddPostViewModel(
	private val postsRepository: PostsRepository,
	private val categoryCatalogRepository: CategoryCatalogRepository,
	private val uploadsRepository: UploadsRepository,
	private val localImageFileReader: LocalImageFileReader,
	private val postsLocalStore: PostsLocalStore,
) : ViewModel() {
	private val _state = MutableStateFlow(AddPostVmState())
	val state: StateFlow<AddPostVmState> = _state

	private var categoriesJob: Job? = null

	init {
		loadCategories(force = false)
	}

	fun updateImageUrl(index: Int, value: String) {
		_state.update { state ->
			if (index !in state.imageUrls.indices) return@update state
			val updated = state.imageUrls.toMutableList()
			updated[index] = value
			state.copy(imageUrls = updated, errorMessage = null)
		}
	}

	fun addImageField() {
		_state.update { state ->
			if (state.imageUrls.size >= AddPostVmState.MAX_IMAGES || (state.selectedUrlCount + state.localImageUris.size) >= AddPostVmState.MAX_IMAGES) {
				return@update state
			}
			state.copy(imageUrls = state.imageUrls + "")
		}
	}

	fun removeImageField(index: Int) {
		_state.update { state ->
			if (state.imageUrls.size <= 1 || index !in state.imageUrls.indices) return@update state
			val updated = state.imageUrls.toMutableList()
			updated.removeAt(index)
			state.copy(imageUrls = updated, errorMessage = null)
		}
	}

	fun addLocalImageUris(uris: List<String>) {
		_state.update { state ->
			val normalized = uris
				.map { it.trim() }
				.filter { it.isNotBlank() }
			if (normalized.isEmpty()) {
				return@update state
			}

			val availableSlots = (AddPostVmState.MAX_IMAGES - state.selectedUrlCount).coerceAtLeast(0)
			val merged = (state.localImageUris + normalized)
				.distinct()
				.take(availableSlots)

			state.copy(localImageUris = merged, errorMessage = null)
		}
	}

	fun removeLocalImage(index: Int) {
		_state.update { state ->
			if (index !in state.localImageUris.indices) {
				return@update state
			}
			val updated = state.localImageUris.toMutableList()
			updated.removeAt(index)
			state.copy(localImageUris = updated, errorMessage = null)
		}
	}

	fun removeLocalImageByUri(uri: String) {
		_state.update { state ->
			if (uri.isBlank()) {
				return@update state
			}
			val targetIndex = state.localImageUris.indexOf(uri)
			if (targetIndex < 0) {
				return@update state
			}
			val updated = state.localImageUris.toMutableList()
			updated.removeAt(targetIndex)
			state.copy(localImageUris = updated, errorMessage = null)
		}
	}

	fun moveLocalImage(fromIndex: Int, toIndex: Int) {
		_state.update { state ->
			val reordered = reorderItems(
				items = state.localImageUris,
				fromIndex = fromIndex,
				toIndex = toIndex
			)
			if (reordered === state.localImageUris) {
				return@update state
			}
			state.copy(localImageUris = reordered, errorMessage = null)
		}
	}

	fun setLocalImageOrder(orderedUris: List<String>) {
		_state.update { state ->
			if (orderedUris.isEmpty() || orderedUris.size != state.localImageUris.size) {
				return@update state
			}
			if (orderedUris.toSet() != state.localImageUris.toSet()) {
				return@update state
			}
			if (orderedUris == state.localImageUris) {
				return@update state
			}
			state.copy(localImageUris = orderedUris, errorMessage = null)
		}
	}

	fun updateDescription(value: String) {
		_state.update { it.copy(description = value, errorMessage = null) }
	}

	fun updateCategory(value: String) {
		_state.update { it.copy(selectedCategoryCode = value, errorMessage = null) }
	}

	fun retryLoadCategories() {
		loadCategories(force = true)
	}

	fun publish(allowManualUrls: Boolean, onSuccess: () -> Unit) {
		val snapshot = _state.value
		if (snapshot.isPublishing) return

		val cleanedManualUrls = if (allowManualUrls) {
			snapshot.imageUrls
				.map { it.trim() }
				.filter { it.isNotBlank() }
				.take(AddPostVmState.MAX_IMAGES)
		} else {
			emptyList()
		}

		if (cleanedManualUrls.size + snapshot.localImageUris.size > AddPostVmState.MAX_IMAGES) {
			_state.update { it.copy(errorMessage = "Можно добавить не более 4 изображений") }
			return
		}
		if (cleanedManualUrls.isEmpty() && snapshot.localImageUris.isEmpty()) {
			_state.update { it.copy(errorMessage = "Добавь хотя бы одно изображение") }
			return
		}

		val selectedCategoryCode = snapshot.selectedCategoryCode
		if (selectedCategoryCode.isNullOrBlank()) {
			_state.update { it.copy(errorMessage = "Выбери категорию") }
			return
		}

		viewModelScope.launch {
			_state.update {
				it.copy(
					isPublishing = true,
					publishingStage = "Подготовка файлов...",
					uploadProgress = 0,
					uploadTotal = snapshot.localImageUris.size,
					errorMessage = null
				)
			}

			val uploadedUrls = when {
				snapshot.localImageUris.isEmpty() -> emptyList()
				else -> {
					val localFiles = coroutineScope {
						snapshot.localImageUris
							.map { uri -> async { localImageFileReader.read(uri) } }
							.awaitAll()
					}
					val uploadData = localFiles.mapNotNull { file ->
						file?.let {
							UploadImageData(
								fileName = it.fileName,
								contentType = it.contentType,
								sizeBytes = it.sizeBytes,
								bytes = it.bytes
							)
						}
					}
					if (uploadData.size != snapshot.localImageUris.size) {
						_state.update {
							it.copy(
								isPublishing = false,
								publishingStage = null,
								uploadProgress = 0,
								uploadTotal = 0,
								errorMessage = "Некоторые файлы не поддерживаются. Используй изображения из галереи"
							)
						}
						return@launch
					}

					_state.update { it.copy(publishingStage = "Загрузка изображений...") }

					when (val uploadResult = uploadsRepository.uploadImages(
						data = uploadData,
						kind = "post",
						onProgress = { uploaded, total ->
							_state.update {
								it.copy(
									publishingStage = "Загрузка изображений... $uploaded/$total",
									uploadProgress = uploaded,
									uploadTotal = total
								)
							}
						}
					)) {
						is GetDataResponse.Success -> uploadResult.data
						is GetDataResponse.Error -> {
							_state.update {
								it.copy(
									isPublishing = false,
									publishingStage = null,
									uploadProgress = 0,
									uploadTotal = 0,
									errorMessage = "Не удалось загрузить изображения. Проверь интернет и повтори попытку"
								)
							}
							return@launch
						}
					}
				}
			}

			val finalImageUrls = (cleanedManualUrls + uploadedUrls).take(AddPostVmState.MAX_IMAGES)
			if (finalImageUrls.isEmpty()) {
				_state.update {
					it.copy(
						isPublishing = false,
						publishingStage = null,
						uploadProgress = 0,
						uploadTotal = 0,
						errorMessage = "Добавь хотя бы одно изображение"
					)
				}
				return@launch
			}

			_state.update { it.copy(publishingStage = "Публикация поста...") }
			val result = postsRepository.createPost(
				CreatePostData(
					imageUrls = finalImageUrls,
					description = snapshot.description.trim().ifBlank { null },
					category = selectedCategoryCode,
				)
			)

			when (result) {
				is UpdateDataResponse.Success -> {
					_state.update {
						it.copy(
							isPublishing = false,
							publishingStage = null,
							uploadProgress = 0,
							uploadTotal = 0,
							errorMessage = null
						)
					}
					onSuccess()
					viewModelScope.launch {
						syncPostsCacheAfterPublish()
					}
				}

				is UpdateDataResponse.Failure -> {
					loadCategories(force = true)
					_state.update {
						it.copy(
							isPublishing = false,
							publishingStage = null,
							uploadProgress = 0,
							uploadTotal = 0,
							errorMessage = "Не удалось опубликовать пост. Попробуй ещё раз"
						)
					}
				}
			}
		}
	}

	private suspend fun syncPostsCacheAfterPublish() {
		val refreshedPosts = when (val response = postsRepository.getUserPosts(
			userId = PostsCacheKeys.SELF_USER_ID,
			forceNetwork = true
		)) {
			is GetDataResponse.Success -> response.data
			is GetDataResponse.Error -> return
		}

		val now = System.currentTimeMillis()
		postsLocalStore.replacePosts(
			userId = PostsCacheKeys.SELF_USER_ID,
			posts = refreshedPosts,
			updatedAt = now
		)

		// Some routes can resolve self as a concrete id; keep that cache key in sync too.
		val selfAuthorId = refreshedPosts.firstOrNull()?.author?.id
		if (!selfAuthorId.isNullOrBlank() && selfAuthorId != PostsCacheKeys.SELF_USER_ID) {
			postsLocalStore.replacePosts(
				userId = selfAuthorId,
				posts = refreshedPosts,
				updatedAt = now
			)
		}
	}

	private fun loadCategories(force: Boolean) {
		categoriesJob?.cancel()
		categoriesJob = viewModelScope.launch {
			_state.update { it.copy(isCategoriesLoading = true, isCategoriesError = false) }

			when (val result = if (force) {
				categoryCatalogRepository.refresh(force = true)
			} else {
				categoryCatalogRepository.getOrRefresh()
			}) {
				is GetDataResponse.Success -> {
					val categories = result.data
					_state.update { current ->
						val selected = current.selectedCategoryCode
						val nextSelected = selected?.takeIf { code ->
							categories.any { it.code == code }
						}

						current.copy(
							categories = categories,
							selectedCategoryCode = nextSelected,
							isCategoriesLoading = false,
							isCategoriesError = categories.isEmpty(),
							errorMessage = null,
						)
					}
				}

				is GetDataResponse.Error -> {
					_state.update {
						it.copy(
							isCategoriesLoading = false,
							isCategoriesError = true,
						)
					}
				}
			}
		}
	}
}

internal fun <T> reorderItems(
	items: List<T>,
	fromIndex: Int,
	toIndex: Int,
): List<T> {
	if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) {
		return items
	}
	val mutable = items.toMutableList()
	val movedItem = mutable.removeAt(fromIndex)
	mutable.add(toIndex, movedItem)
	return mutable
}
