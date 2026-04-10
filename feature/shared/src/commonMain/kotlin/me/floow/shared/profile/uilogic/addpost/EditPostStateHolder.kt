package me.floow.shared.profile.uilogic.addpost

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.shared.profile.image.LocalImageFileReader
import me.floow.shared.profile.image.LocalImageReadError
import me.floow.shared.profile.image.LocalImageReadResult
import me.floow.shared.profile.ui.addpost.CreatePostImageItem
import me.floow.shared.profile.ui.addpost.CreatePostUiState
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.shared.profile.uilogic.ProfilePost
import me.floow.shared.profile.uilogic.compose.PlatformImagePicker
import me.floow.shared.profile.uilogic.compose.PostComposerRepository
import me.floow.shared.profile.uilogic.compose.UploadKind

private data class EditPostImageDraft(
    val id: String,
    val remoteUrl: String,
    val previewUri: String,
    val replacementHandleId: String? = null,
    val replacementPreviewUri: String? = null,
) {
    val displayUri: String
        get() = replacementPreviewUri ?: previewUri
}

class EditPostStateHolder(
    private val initialPost: ProfilePostItem,
    private val repository: PostComposerRepository,
    private val imagePicker: PlatformImagePicker,
    private val imageFileReader: LocalImageFileReader,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    sealed interface Event {
        data class Saved(val post: ProfilePost) : Event
        data class ShowMessage(val message: String) : Event
    }

    private val initialDescription = initialPost.description.orEmpty()
    private val imagesFlow = MutableStateFlow(initialPost.toEditDraftImages())
    private val _state = MutableStateFlow(
        initialPost.toEditPostUiState(
            images = imagesFlow.value,
            canPublish = false,
        )
    )
    val state: StateFlow<CreatePostUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun removeImage(imageId: String) {
        imagesFlow.update { current ->
            if (current.size <= 1) return@update current
            current.filterNot { it.id == imageId }
        }
        syncUiState(errorMessage = if (imagesFlow.value.isEmpty()) "В посте должно быть минимум 1 изображение" else null)
    }

    fun reorderImages(orderedIds: List<String>) {
        imagesFlow.update { current ->
            if (orderedIds.size != current.size) return@update current
            val byId = current.associateBy(EditPostImageDraft::id)
            val reordered = orderedIds.mapNotNull(byId::get)
            if (reordered.size != current.size) return@update current
            reordered
        }
        syncUiState()
    }

    fun updateDescription(value: String) {
        _state.update { current ->
            current.copy(
                description = value,
                errorMessage = null,
                canPublish = canSave(description = value, images = imagesFlow.value),
            )
        }
    }

    fun replaceImage(imageId: String) {
        scope.launch {
            val picked = imagePicker.pickSingleImage() ?: return@launch
            imagesFlow.update { current ->
                current.map { image ->
                    if (image.id == imageId) {
                        image.copy(
                            replacementHandleId = picked.id,
                            replacementPreviewUri = picked.previewUri,
                        )
                    } else {
                        image
                    }
                }
            }
            syncUiState()
        }
    }

    fun save() {
        val snapshot = _state.value
        val imageDrafts = imagesFlow.value
        if (!canSave(snapshot.description, imageDrafts)) return
        scope.launch {
            _state.update {
                it.copy(
                    isPublishing = true,
                    publishingStage = "Подготовка файлов...",
                    uploadProgress = 0,
                    uploadTotal = imageDrafts.count { draft -> draft.replacementHandleId != null },
                    errorMessage = null,
                )
            }

            val uploadedById = linkedMapOf<String, String>()
            val replacementDrafts = imageDrafts.filter { it.replacementHandleId != null }
            if (replacementDrafts.isNotEmpty()) {
                var readFailure: LocalImageReadError? = null
                val replacementFiles = replacementDrafts.mapNotNull { draft ->
                    when (val result = imageFileReader.readPost(draft.replacementHandleId ?: return@mapNotNull null)) {
                        is LocalImageReadResult.Success -> result.file
                        is LocalImageReadResult.Failure -> {
                            readFailure = result.error
                            null
                        }
                    }
                }
                if (replacementFiles.size != replacementDrafts.size) {
                    failSaving(postReadErrorMessage(readFailure))
                    return@launch
                }
                val uploadedUrls = repository.uploadImages(
                    files = replacementFiles,
                    kind = UploadKind.Post,
                    onProgress = { uploaded, total ->
                        _state.update {
                            it.copy(
                                publishingStage = "Загрузка изображений... $uploaded/$total",
                                uploadProgress = uploaded,
                                uploadTotal = total,
                            )
                        }
                    }
                ).getOrElse {
                    failSaving("Не удалось загрузить изображения")
                    return@launch
                }
                replacementDrafts.forEachIndexed { index, draft ->
                    uploadedById[draft.id] = uploadedUrls[index]
                }
            }

            val finalImageUrls = imageDrafts.map { draft ->
                uploadedById[draft.id] ?: draft.remoteUrl
            }
            _state.update { it.copy(publishingStage = "Сохранение изменений...") }
            repository.updatePost(
                postId = initialPost.id,
                description = snapshot.description.trim().ifBlank { null },
                imageUrls = finalImageUrls,
            ).onSuccess { post ->
                _events.emit(Event.Saved(post))
            }.onFailure {
                failSaving("Не удалось сохранить изменения")
            }
        }
    }

    private fun syncUiState(errorMessage: String? = null) {
        val imageDrafts = imagesFlow.value
        val currentDescription = _state.value.description
        _state.value = initialPost.toEditPostUiState(
            images = imageDrafts,
            description = currentDescription,
            errorMessage = errorMessage,
            isPublishing = _state.value.isPublishing,
            publishingStage = _state.value.publishingStage,
            uploadProgress = _state.value.uploadProgress,
            uploadTotal = _state.value.uploadTotal,
            canPublish = canSave(
                description = currentDescription,
                images = imageDrafts,
            ),
        )
    }

    private suspend fun failSaving(message: String) {
        _state.update {
            it.copy(
                isPublishing = false,
                publishingStage = null,
                uploadProgress = 0,
                uploadTotal = 0,
                errorMessage = message,
            )
        }
        _events.emit(Event.ShowMessage(message))
    }

    private fun postReadErrorMessage(error: LocalImageReadError?): String = when (error) {
        LocalImageReadError.UNSUPPORTED_TYPE -> "Поддерживаются только JPG, PNG и WEBP"
        LocalImageReadError.FILE_TOO_LARGE -> "Файл слишком большой. Лимит: 2 МБ"
        LocalImageReadError.CORRUPTED_IMAGE, null -> "Не удалось прочитать выбранное изображение"
    }

    private fun canSave(description: String, images: List<EditPostImageDraft>): Boolean {
        if (images.isEmpty()) return false
        val descriptionChanged = description != initialDescription
        val imagesChanged = images.any { it.replacementHandleId != null } ||
            images.map(EditPostImageDraft::id) != initialPost.toEditDraftImages().map(EditPostImageDraft::id)
        return !_state.value.isPublishing && (descriptionChanged || imagesChanged)
    }
}

private fun ProfilePostItem.toEditDraftImages(): List<EditPostImageDraft> {
    val imageUrls = viewerUrls.ifEmpty { listOfNotNull(fullUrl ?: previewUrl ?: lqUrl) }
    return imageUrls.mapIndexed { index, imageUrl ->
        EditPostImageDraft(
            id = "initial_$index",
            remoteUrl = imageUrl,
            previewUri = imageUrl,
        )
    }
}

private fun ProfilePostItem.toEditPostUiState(
    images: List<EditPostImageDraft>,
    description: String = this.description.orEmpty(),
    errorMessage: String? = null,
    isPublishing: Boolean = false,
    publishingStage: String? = null,
    uploadProgress: Int = 0,
    uploadTotal: Int = 0,
    canPublish: Boolean = images.isNotEmpty(),
): CreatePostUiState {
    return CreatePostUiState(
        description = description,
        selectedImages = images.map { image ->
            CreatePostImageItem(
                id = image.id,
                uri = image.displayUri,
                isLocalReplacement = image.replacementHandleId != null,
            )
        },
        categories = emptyList(),
        selectedCategoryCode = null,
        isCategoriesLoading = false,
        isCategoriesError = false,
        isPublishing = isPublishing,
        publishingStage = publishingStage,
        uploadProgress = uploadProgress,
        uploadTotal = uploadTotal,
        errorMessage = errorMessage,
        canPublish = canPublish,
        hasManualUrlsInDraft = false,
    )
}
