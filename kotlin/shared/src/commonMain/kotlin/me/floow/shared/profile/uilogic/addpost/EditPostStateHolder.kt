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
import me.floow.shared.profile.ui.addpost.CreatePostCategoryItem
import me.floow.shared.profile.ui.addpost.CreatePostImageItem
import me.floow.shared.profile.ui.addpost.CreatePostUiState
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.shared.profile.uilogic.ProfilePost
import me.floow.shared.profile.uilogic.compose.PlatformImagePicker
import me.floow.shared.profile.uilogic.compose.PlatformPickedImage
import me.floow.shared.profile.uilogic.compose.PostComposerImageInput
import me.floow.shared.profile.uilogic.compose.PostComposerRepository

private data class EditPostImageDraft(
    val id: String,
    val remoteUrl: String?,
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
        data class OptimisticApplied(val post: ProfilePost) : Event
        data class Saved(val post: ProfilePost) : Event
        data class SaveFailed(val rollbackPost: ProfilePost, val message: String) : Event
        data class ShowMessage(val message: String) : Event
    }

    private val initialDescription = initialPost.description.orEmpty()
    private val initialCategory = initialPost.category
    private var pendingRollbackPost: ProfilePost? = null
    private val imagesFlow = MutableStateFlow(initialPost.toEditDraftImages())
    private val _state = MutableStateFlow(
        initialPost.toEditPostUiState(
            images = imagesFlow.value,
            selectedCategoryCode = initialCategory,
            canPublish = false,
        )
    )
    val state: StateFlow<CreatePostUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    init {
        reloadCategories()
    }

    fun reloadCategories() {
        scope.launch {
            _state.update { it.copy(isCategoriesLoading = true, isCategoriesError = false, errorMessage = null) }
            repository.loadCategories()
                .onSuccess { categories ->
                    _state.update { current ->
                        val selectedCode = current.selectedCategoryCode
                            ?.takeIf { code -> categories.any { it.code == code } }
                            ?: initialCategory?.takeIf { code -> categories.any { it.code == code } }
                            ?: current.selectedCategoryCode
                            ?: categories.firstOrNull()?.code
                        current.copy(
                            categories = categories.map { item ->
                                CreatePostCategoryItem(
                                    code = item.code,
                                    title = item.title,
                                )
                            },
                            selectedCategoryCode = selectedCode,
                            isCategoriesLoading = false,
                            isCategoriesError = categories.isEmpty(),
                            canPublish = canSave(
                                description = current.description,
                                images = imagesFlow.value,
                                categoryCode = selectedCode,
                            ),
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(isCategoriesLoading = false, isCategoriesError = true) }
                }
        }
    }

    fun removeImage(imageId: String) {
        imagesFlow.update { current ->
            if (current.size <= 1) return@update current
            current.filterNot { it.id == imageId }
        }
        syncUiState(errorMessage = if (imagesFlow.value.isEmpty()) "В посте должно быть минимум 1 изображение" else null)
    }

    fun addRemoteImage(imageUrl: String) {
        val normalizedUrl = imageUrl.trim().takeIf(String::isNotEmpty) ?: return
        imagesFlow.update { current ->
            if (current.size >= _state.value.maxImages || current.any { it.displayUri == normalizedUrl }) {
                return@update current
            }
            current + EditPostImageDraft(
                id = normalizedUrl,
                remoteUrl = normalizedUrl,
                previewUri = normalizedUrl,
            )
        }
        syncUiState()
    }

    fun addPickedImage(picked: PlatformPickedImage) {
        val previewUri = picked.previewUri.trim().takeIf(String::isNotEmpty) ?: return
        imagesFlow.update { current ->
            if (current.size >= _state.value.maxImages || current.any { it.displayUri == previewUri }) {
                return@update current
            }
            current + EditPostImageDraft(
                id = picked.id,
                remoteUrl = null,
                previewUri = previewUri,
                replacementHandleId = picked.id,
                replacementPreviewUri = previewUri,
            )
        }
        syncUiState()
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
                canPublish = canSave(
                    description = value,
                    images = imagesFlow.value,
                    categoryCode = current.selectedCategoryCode,
                ),
            )
        }
    }

    fun updateCategory(code: String) {
        _state.update { current ->
            current.copy(
                selectedCategoryCode = code,
                errorMessage = null,
                canPublish = canSave(
                    description = current.description,
                    images = imagesFlow.value,
                    categoryCode = code,
                ),
            )
        }
    }

    fun replaceImage(imageId: String) {
        scope.launch {
            val picked = imagePicker.pickSingleImage() ?: return@launch
            replaceImageWithPicked(imageId, picked)
        }
    }

    fun replaceImageWithPicked(imageId: String, picked: PlatformPickedImage) {
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

    fun save() {
        val snapshot = _state.value
        val imageDrafts = imagesFlow.value
        if (!canSave(snapshot.description, imageDrafts)) return
        scope.launch {
            val optimisticPost = initialPost.toProfilePost(
                description = snapshot.description.trim().ifBlank { null },
                imageUrls = imageDrafts.map { it.displayUri }.filter(String::isNotBlank),
                category = snapshot.selectedCategoryCode ?: initialCategory,
            )
            pendingRollbackPost = initialPost.toProfilePost(
                description = initialPost.description,
                imageUrls = initialPost.viewerUrls,
                category = initialCategory,
            )
            _events.emit(Event.OptimisticApplied(optimisticPost))
            _state.update {
                it.copy(
                    isPublishing = true,
                    publishingStage = "Подготовка файлов...",
                    uploadProgress = 0,
                    uploadTotal = imageDrafts.count { draft -> draft.replacementHandleId != null },
                    errorMessage = null,
                )
            }

            val replacementFilesByImageId = linkedMapOf<String, me.floow.shared.profile.image.LocalImageFile>()
            val replacementDrafts = imageDrafts.filter { it.replacementHandleId != null }
            if (replacementDrafts.isNotEmpty()) {
                var readFailure: LocalImageReadError? = null
                replacementDrafts.forEach { draft ->
                    when (val result = imageFileReader.readPost(draft.replacementHandleId ?: return@forEach)) {
                        is LocalImageReadResult.Success -> replacementFilesByImageId[draft.id] = result.file
                        is LocalImageReadResult.Failure -> readFailure = result.error
                    }
                }
                if (replacementFilesByImageId.size != replacementDrafts.size) {
                    failSaving(postReadErrorMessage(readFailure))
                    return@launch
                }
            }

            _state.update { it.copy(publishingStage = "Изменения ставятся в очередь...") }
            repository.updatePost(
                postId = initialPost.id,
                description = snapshot.description.trim().ifBlank { null },
                images = imageDrafts.map { draft ->
                    val localFile = replacementFilesByImageId[draft.id]
                    if (localFile != null) {
                        PostComposerImageInput(localFile = localFile)
                    } else {
                        PostComposerImageInput(remoteUrl = checkNotNull(draft.remoteUrl))
                    }
                },
                categoryCode = snapshot.selectedCategoryCode ?: initialCategory,
            ).onSuccess { post ->
                pendingRollbackPost = null
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
            categories = _state.value.categories,
            selectedCategoryCode = _state.value.selectedCategoryCode,
            isCategoriesLoading = _state.value.isCategoriesLoading,
            isCategoriesError = _state.value.isCategoriesError,
            errorMessage = errorMessage,
            isPublishing = _state.value.isPublishing,
            publishingStage = _state.value.publishingStage,
            uploadProgress = _state.value.uploadProgress,
            uploadTotal = _state.value.uploadTotal,
            canPublish = canSave(
                description = currentDescription,
                images = imageDrafts,
                categoryCode = _state.value.selectedCategoryCode,
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
        val rollback = pendingRollbackPost
        if (rollback != null) {
            pendingRollbackPost = null
            _events.emit(Event.SaveFailed(rollback, message))
        } else {
            _events.emit(Event.ShowMessage(message))
        }
    }

    private fun postReadErrorMessage(error: LocalImageReadError?): String = when (error) {
        LocalImageReadError.UNSUPPORTED_TYPE -> "Поддерживаются только JPG, PNG и WEBP"
        LocalImageReadError.FILE_TOO_LARGE -> "Файл слишком большой. Лимит: 2 МБ"
        LocalImageReadError.CORRUPTED_IMAGE, null -> "Не удалось прочитать выбранное изображение"
    }

    private fun canSave(
        description: String,
        images: List<EditPostImageDraft>,
        categoryCode: String? = _state.value.selectedCategoryCode,
    ): Boolean {
        if (images.isEmpty()) return false
        val descriptionChanged = description != initialDescription
        val categoryChanged = categoryCode != initialCategory
        val imagesChanged = images.any { it.replacementHandleId != null } ||
            images.map(EditPostImageDraft::id) != initialPost.toEditDraftImages().map(EditPostImageDraft::id)
        return !_state.value.isPublishing && (descriptionChanged || imagesChanged || categoryChanged)
    }
}

private fun ProfilePostItem.toEditDraftImages(): List<EditPostImageDraft> {
    val imageUrls = viewerUrls.ifEmpty { listOfNotNull(fullUrl ?: previewUrl ?: lqUrl) }
    return imageUrls.map { imageUrl ->
        EditPostImageDraft(
            id = imageUrl,
            remoteUrl = imageUrl,
            previewUri = imageUrl,
        )
    }
}

private fun ProfilePostItem.toProfilePost(
    description: String?,
    imageUrls: List<String>,
    category: String?,
): ProfilePost {
    return ProfilePost(
        id = id,
        description = description,
        imageVariants = imageUrls.map { url ->
            me.floow.shared.profile.uilogic.ProfileImageVariant(
                thumbUrl = url,
                fullUrl = url,
            )
        },
        imageUrls = imageUrls,
        likesCount = likesCount,
        commentsCount = commentsCount,
        commentersPreview = commentersPreview,
        category = category,
        createdAtMillis = createdAtMillis,
    )
}

private fun ProfilePostItem.toEditPostUiState(
    images: List<EditPostImageDraft>,
    description: String = this.description.orEmpty(),
    categories: List<CreatePostCategoryItem> = emptyList(),
    selectedCategoryCode: String? = this.category,
    isCategoriesLoading: Boolean = false,
    isCategoriesError: Boolean = false,
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
        categories = categories,
        selectedCategoryCode = selectedCategoryCode,
        isCategoriesLoading = isCategoriesLoading,
        isCategoriesError = isCategoriesError,
        isPublishing = isPublishing,
        publishingStage = publishingStage,
        uploadProgress = uploadProgress,
        uploadTotal = uploadTotal,
        errorMessage = errorMessage,
        canPublish = canPublish,
        hasManualUrlsInDraft = false,
    )
}
