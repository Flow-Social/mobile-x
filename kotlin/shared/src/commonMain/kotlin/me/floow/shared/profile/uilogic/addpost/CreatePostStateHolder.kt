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
import me.floow.domain.utils.currentTimeMillis
import me.floow.shared.profile.image.LocalImageFileReader
import me.floow.shared.profile.image.LocalImageReadError
import me.floow.shared.profile.image.LocalImageReadResult
import me.floow.shared.profile.ui.addpost.CreatePostCategoryItem
import me.floow.shared.profile.ui.addpost.CreatePostImageItem
import me.floow.shared.profile.ui.addpost.CreatePostUiState
import me.floow.shared.profile.uilogic.ProfileImageVariant
import me.floow.shared.profile.uilogic.ProfilePost
import me.floow.shared.profile.uilogic.ProfilePostOptimisticState
import me.floow.shared.profile.uilogic.ProfilePostPendingMutationType
import me.floow.shared.profile.uilogic.ProfilePostSyncState
import me.floow.shared.profile.uilogic.compose.PlatformImagePicker
import me.floow.shared.profile.uilogic.compose.PlatformPickedImage
import me.floow.shared.profile.uilogic.compose.PostComposerImageInput
import me.floow.shared.profile.uilogic.compose.PostComposerRepository

class CreatePostStateHolder(
    private val repository: PostComposerRepository,
    private val imagePicker: PlatformImagePicker,
    private val imageFileReader: LocalImageFileReader,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    sealed interface Event {
        data class Created(val post: ProfilePost) : Event
        data class ShowMessage(val message: String) : Event
    }

    private val pickedImagesById = linkedMapOf<String, String>()
    private val _state = MutableStateFlow(emptyCreatePostState())
    val state: StateFlow<CreatePostUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
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
                            canPublish = current.selectedImages.isNotEmpty() && !selectedCode.isNullOrBlank(),
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(isCategoriesLoading = false, isCategoriesError = true) }
                }
        }
    }

    fun pickImages() {
        val availableSlots = (_state.value.maxImages - pickedImagesById.size).coerceAtLeast(0)
        if (availableSlots == 0) return
        scope.launch {
            val picked = imagePicker.pickPostImages(availableSlots)
            if (picked.isEmpty()) return@launch
            addPickedImages(picked)
        }
    }

    fun addPickedImages(picked: List<PlatformPickedImage>) {
        if (picked.isEmpty()) return
        picked.forEach { image ->
            pickedImagesById[image.id] = image.id
        }
        _state.update { current ->
            val currentIds = current.selectedImages.mapTo(hashSetOf(), CreatePostImageItem::id)
            val appended = picked
                .filterNot { it.id in currentIds }
                .map { image ->
                    CreatePostImageItem(
                        id = image.id,
                        uri = image.previewUri,
                    )
                }
                .take(current.maxImages - current.selectedImages.size)
            current.copy(
                selectedImages = current.selectedImages + appended,
                errorMessage = null,
                canPublish = (current.selectedImages + appended).isNotEmpty() &&
                    !current.selectedCategoryCode.isNullOrBlank() &&
                    !current.isCategoriesLoading,
            )
        }
    }

    fun removeImage(imageId: String) {
        pickedImagesById.remove(imageId)
        _state.update { current ->
            val updated = current.selectedImages.filterNot { it.id == imageId }
            current.copy(
                selectedImages = updated,
                errorMessage = null,
                canPublish = updated.isNotEmpty() && !current.selectedCategoryCode.isNullOrBlank() && !current.isCategoriesLoading,
            )
        }
    }

    fun reorderImages(orderedIds: List<String>) {
        _state.update { current ->
            if (orderedIds.size != current.selectedImages.size) return@update current
            val byId = current.selectedImages.associateBy(CreatePostImageItem::id)
            val reordered = orderedIds.mapNotNull(byId::get)
            if (reordered.size != current.selectedImages.size) return@update current
            current.copy(selectedImages = reordered)
        }
    }

    fun updateDescription(value: String) {
        _state.update { it.copy(description = value, errorMessage = null) }
    }

    fun updateCategory(code: String) {
        _state.update { current ->
            current.copy(
                selectedCategoryCode = code,
                canPublish = current.selectedImages.isNotEmpty() && code.isNotBlank() && !current.isCategoriesLoading,
            )
        }
    }

    fun publish() {
        val snapshot = _state.value
        if (!snapshot.canPublish || snapshot.selectedCategoryCode.isNullOrBlank()) return

        val now = currentTimeMillis()
        val localPostId = "pending_create_${now}_${(1000..9999).random()}"
        val selectedUris = snapshot.selectedImages.map { it.uri }
        val categoryCode = snapshot.selectedCategoryCode
        val description = snapshot.description.trim().ifBlank { null }

        val optimisticPost = ProfilePost(
            id = localPostId,
            description = description,
            imageVariants = selectedUris.map { uri ->
                ProfileImageVariant(thumbUrl = uri, fullUrl = uri)
            },
            imageUrls = selectedUris,
            category = categoryCode,
            createdAtMillis = now,
            optimisticState = ProfilePostOptimisticState.PENDING,
            syncState = ProfilePostSyncState.PENDING,
            pendingMutationType = ProfilePostPendingMutationType.CREATE,
            isOptimistic = true,
            localPostId = localPostId,
            likesCount = 0,
            commentsCount = 0,
            commentersPreview = emptyList(),
        )

        val selectedImagesCopy = snapshot.selectedImages.toList()
        val pickedMapCopy = selectedImagesCopy.mapNotNull { img ->
            val picked = pickedImagesById[img.id] ?: return@mapNotNull null
            img.id to picked
        }

        // Instant UI reset and sheet dismissal
        _state.value = emptyCreatePostState()
        pickedImagesById.clear()
        reloadCategories()
        _events.tryEmit(Event.Created(optimisticPost))

        // Background worker handles file reading and queueing
        scope.launch(Dispatchers.Default) {
            try {
                val localFiles = pickedMapCopy.mapNotNull { (_, picked) ->
                    when (val result = imageFileReader.readPost(picked)) {
                        is LocalImageReadResult.Success -> result.file
                        is LocalImageReadResult.Failure -> null
                    }
                }
                if (localFiles.isNotEmpty()) {
                    repository.createPost(
                        description = description,
                        images = localFiles.map { file -> PostComposerImageInput(localFile = file) },
                        categoryCode = categoryCode,
                        localPostId = localPostId,
                    )
                }
            } catch (_: Throwable) {
            }
        }
    }

    private suspend fun failPublishing(message: String) {
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
}

private fun emptyCreatePostState(): CreatePostUiState {
    return CreatePostUiState(
        description = "",
        selectedImages = emptyList(),
        categories = emptyList(),
        selectedCategoryCode = null,
        isCategoriesLoading = false,
        isCategoriesError = false,
        isPublishing = false,
        publishingStage = null,
        uploadProgress = 0,
        uploadTotal = 0,
        errorMessage = null,
        canPublish = false,
    )
}
