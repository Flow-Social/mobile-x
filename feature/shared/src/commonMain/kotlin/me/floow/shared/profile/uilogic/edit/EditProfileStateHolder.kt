package me.floow.shared.profile.uilogic.edit

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
import me.floow.shared.profile.ui.edit.EditProfileState
import me.floow.shared.profile.uilogic.compose.PlatformImagePicker
import me.floow.shared.profile.uilogic.compose.PostComposerRepository
import me.floow.shared.profile.uilogic.compose.UploadKind
import me.floow.uikit.util.state.ValidatedField
import me.floow.uikit.util.state.ValidationErrorType

data class EditProfileOverlayData(
    val name: String,
    val username: String,
    val bio: String,
    val avatarUrl: String?,
    val backgroundUrl: String?,
)

private data class EditProfileInternalState(
    val name: ValidatedField = ValidatedField.Valid(""),
    val username: ValidatedField = ValidatedField.Valid(""),
    val bio: ValidatedField = ValidatedField.Valid(""),
    val avatarPreviewUri: String? = null,
    val avatarHandleId: String? = null,
    val avatarRemoteUrl: String? = null,
    val avatarErrorMessage: String? = null,
    val backgroundPreviewUri: String? = null,
    val backgroundHandleId: String? = null,
    val backgroundRemoteUrl: String? = null,
    val backgroundErrorMessage: String? = null,
    val isSubmitting: Boolean = false,
)

class EditProfileStateHolder(
    initialData: EditProfileOverlayData,
    private val repository: ProfileEditorRepository,
    private val imagePicker: PlatformImagePicker,
    private val imageFileReader: LocalImageFileReader,
    private val uploadsRepository: PostComposerRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    sealed interface Event {
        data class Saved(val profile: UpdatedProfileData) : Event
        data class ShowMessage(val message: String) : Event
    }

    private val original = initialData
    private val _internalState = MutableStateFlow(
        EditProfileInternalState(
            name = ValidatedField.Valid(initialData.name),
            username = ValidatedField.Valid(initialData.username),
            bio = ValidatedField.Valid(initialData.bio),
            avatarRemoteUrl = initialData.avatarUrl,
            backgroundRemoteUrl = initialData.backgroundUrl,
        )
    )
    private val _state = MutableStateFlow(_internalState.value.toUiState())
    val state: StateFlow<EditProfileState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun updateName(value: String) {
        _internalState.update { it.copy(name = validateName(value)) }
        syncState()
    }

    fun updateUsername(value: String) {
        _internalState.update { it.copy(username = validateUsername(value)) }
        syncState()
        if (value.length in 3..32 && value != original.username) {
            scope.launch {
                val available = repository.checkUsernameAvailability(value)
                if (!available) {
                    _internalState.update {
                        it.copy(
                            username = ValidatedField.Invalid(
                                value = value,
                                errorType = ValidationErrorType.UsernameAlreadyExists,
                            )
                        )
                    }
                    syncState()
                }
            }
        }
    }

    fun updateBio(value: String) {
        _internalState.update { it.copy(bio = validateBio(value)) }
        syncState()
    }

    fun pickAvatar() {
        scope.launch {
            val picked = imagePicker.pickSingleImage() ?: return@launch
            _internalState.update {
                it.copy(
                    avatarHandleId = picked.id,
                    avatarPreviewUri = picked.previewUri,
                    avatarErrorMessage = null,
                )
            }
            syncState()
        }
    }

    fun pickBackground() {
        scope.launch {
            val picked = imagePicker.pickSingleImage() ?: return@launch
            _internalState.update {
                it.copy(
                    backgroundHandleId = picked.id,
                    backgroundPreviewUri = picked.previewUri,
                    backgroundErrorMessage = null,
                )
            }
            syncState()
        }
    }

    fun save() {
        val snapshot = _internalState.value
        if (snapshot.name is ValidatedField.Invalid || snapshot.username is ValidatedField.Invalid || snapshot.bio is ValidatedField.Invalid) {
            return
        }
        scope.launch {
            _internalState.update { it.copy(isSubmitting = true) }
            syncState()

            val avatarUrl = uploadIfNeeded(
                handleId = snapshot.avatarHandleId,
                kind = UploadKind.Avatar,
                read = { imageFileReader.readAvatar(it) },
                currentUrl = snapshot.avatarRemoteUrl,
                onFailure = { message ->
                    _internalState.update { state -> state.copy(avatarErrorMessage = message, isSubmitting = false) }
                    syncState()
                }
            ) ?: return@launch

            val backgroundUrl = uploadIfNeeded(
                handleId = snapshot.backgroundHandleId,
                kind = UploadKind.Background,
                read = { imageFileReader.readBackground(it) },
                currentUrl = snapshot.backgroundRemoteUrl,
                onFailure = { message ->
                    _internalState.update { state -> state.copy(backgroundErrorMessage = message, isSubmitting = false) }
                    syncState()
                }
            ) ?: return@launch

            repository.updateProfile(
                name = snapshot.name.value.trim(),
                username = snapshot.username.value.trim(),
                bio = snapshot.bio.value.trim(),
            ).onFailure {
                failSave("Не удалось сохранить профиль")
                return@launch
            }
            if (avatarUrl != snapshot.avatarRemoteUrl && !avatarUrl.isNullOrBlank()) {
                repository.updateProfileMedia("avatar", avatarUrl).onFailure {
                    failSave("Не удалось обновить аватар")
                    return@launch
                }
            }
            if (backgroundUrl != snapshot.backgroundRemoteUrl && !backgroundUrl.isNullOrBlank()) {
                repository.updateProfileMedia("background", backgroundUrl).onFailure {
                    failSave("Не удалось обновить фон")
                    return@launch
                }
            }
            val updated = UpdatedProfileData(
                name = snapshot.name.value.trim(),
                username = snapshot.username.value.trim(),
                bio = snapshot.bio.value.trim(),
                avatarUrl = avatarUrl,
                backgroundUrl = backgroundUrl,
            )
            _internalState.update { state ->
                state.copy(
                    isSubmitting = false,
                    avatarRemoteUrl = avatarUrl,
                    backgroundRemoteUrl = backgroundUrl,
                    avatarHandleId = null,
                    backgroundHandleId = null,
                )
            }
            syncState()
            _events.emit(Event.Saved(updated))
        }
    }

    private suspend fun uploadIfNeeded(
        handleId: String?,
        kind: UploadKind,
        read: suspend (String) -> LocalImageReadResult,
        currentUrl: String?,
        onFailure: suspend (String) -> Unit,
    ): String? {
        if (handleId == null) return currentUrl
        val file = when (val result = read(handleId)) {
            is LocalImageReadResult.Success -> result.file
            is LocalImageReadResult.Failure -> {
                onFailure(result.error.toProfileImageMessage(kind))
                return null
            }
        }
        return uploadsRepository.uploadImages(
            files = listOf(file),
            kind = kind,
            onProgress = { _, _ -> },
        ).getOrElse {
            onFailure("Не удалось загрузить изображение")
            return null
        }.firstOrNull()
    }

    private suspend fun failSave(message: String) {
        _internalState.update { it.copy(isSubmitting = false) }
        syncState()
        _events.emit(Event.ShowMessage(message))
    }

    private fun syncState() {
        _state.value = _internalState.value.toUiState()
    }
}

private fun LocalImageReadError.toProfileImageMessage(kind: UploadKind): String {
    return when (kind) {
        UploadKind.Post -> "Не удалось прочитать выбранное изображение"
        UploadKind.Avatar -> when (this) {
            LocalImageReadError.UNSUPPORTED_TYPE -> "Поддерживаются JPG, PNG, WEBP, HEIC, HEIF и GIF"
            LocalImageReadError.FILE_TOO_LARGE -> "Файл слишком большой. Лимит для аватарки: 1 МБ"
            LocalImageReadError.CORRUPTED_IMAGE -> "Не удалось обработать изображение. Выбери другой файл"
        }
        UploadKind.Background -> when (this) {
            LocalImageReadError.UNSUPPORTED_TYPE -> "Поддерживаются JPG, PNG, WEBP, HEIC и HEIF"
            LocalImageReadError.FILE_TOO_LARGE -> "Файл слишком большой. Лимит для фона: 2 МБ"
            LocalImageReadError.CORRUPTED_IMAGE -> "Не удалось обработать изображение. Выбери другой файл"
        }
    }
}

private fun EditProfileInternalState.toUiState(): EditProfileState {
    return EditProfileState.Edit(
        name = name,
        username = username,
        bio = bio,
        avatarPreviewUri = avatarPreviewUri,
        avatarRemoteUrl = avatarRemoteUrl,
        avatarErrorMessage = avatarErrorMessage,
        backgroundPreviewUri = backgroundPreviewUri,
        backgroundRemoteUrl = backgroundRemoteUrl,
        backgroundErrorMessage = backgroundErrorMessage,
        isSubmitting = isSubmitting,
    )
}

private fun validateName(value: String): ValidatedField {
    return when {
        value.isBlank() -> ValidatedField.Invalid(value, ValidationErrorType.ShouldNotBeEmpty)
        value.length > 32 -> ValidatedField.Invalid(value, ValidationErrorType.TextTooLong)
        value.contains(Regex("[<>&\"']")) -> ValidatedField.Invalid(value, ValidationErrorType.Other)
        else -> ValidatedField.Valid(value)
    }
}

private fun validateUsername(value: String): ValidatedField {
    return when {
        value.isBlank() -> ValidatedField.Invalid(value, ValidationErrorType.ShouldNotBeEmpty)
        value.length > 32 -> ValidatedField.Invalid(value, ValidationErrorType.TextTooLong)
        value.length < 3 -> ValidatedField.Invalid(value, ValidationErrorType.Other)
        !value.matches(Regex("^[a-zA-Z0-9_]+$")) -> ValidatedField.Invalid(value, ValidationErrorType.Other)
        else -> ValidatedField.Valid(value)
    }
}

private fun validateBio(value: String): ValidatedField {
    return if (value.length > 140) {
        ValidatedField.Invalid(value, ValidationErrorType.TextTooLong)
    } else {
        ValidatedField.Valid(value)
    }
}
