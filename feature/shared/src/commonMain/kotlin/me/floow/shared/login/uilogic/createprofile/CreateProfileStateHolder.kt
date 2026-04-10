package me.floow.shared.login.uilogic.createprofile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.domain.api.models.EditProfileData
import me.floow.domain.auth.models.PendingRegistrationInitialData
import me.floow.domain.values.ProfileDescription
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.ValidationError
import me.floow.domain.values.util.ValueValidationResult
import me.floow.uikit.util.state.ValidatedField
import me.floow.uikit.util.state.ValidatedField.Companion.initialField
import me.floow.uikit.util.state.ValidationErrorType

private data class CreateProfileDraftState(
    val name: ValidatedField = initialField,
    val username: ValidatedField = initialField,
    val bio: ValidatedField = initialField,
    val isUploading: Boolean = false,
) {
    fun toUiState(): CreateProfileState {
        return if (isUploading) {
            CreateProfileState.Uploading
        } else {
            CreateProfileState.Edit(
                name = name,
                username = username,
                bio = bio,
            )
        }
    }
}

class CreateProfileStateHolder(
    private val profileRepository: CreateProfileRepository,
    initialData: PendingRegistrationInitialData? = null,
    scope: CoroutineScope? = null,
) {
    sealed interface Event {
        data object HapticFeedback : Event
        data class ShowMessage(val message: String) : Event
        data object Completed : Event
    }

    private val ownsScope = scope == null
    private val holderScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _draft = MutableStateFlow(
        CreateProfileDraftState(
            name = initialValue(initialData?.name),
            username = initialValue(initialData?.username),
            bio = initialValue(initialData?.description),
        )
    )

    val state: StateFlow<CreateProfileState> = _draft
        .map(CreateProfileDraftState::toUiState)
        .stateIn(
            holderScope,
            SharingStarted.Eagerly,
            _draft.value.toUiState(),
        )

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 16)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    fun updateName(newValue: String) {
        updateField(newValue, ProfileName::createWithValidation) { current, field ->
            current.copy(name = field)
        }
    }

    fun updateUsername(newValue: String) {
        updateField(newValue, ProfileUsername::createWithValidation) { current, field ->
            current.copy(username = field)
        }
    }

    fun updateBio(newValue: String) {
        updateField(newValue, ProfileDescription::createWithValidation) { current, field ->
            current.copy(bio = field)
        }
    }

    fun createProfile() {
        holderScope.launch {
            validateAll()
            val draft = _draft.value
            val data = draft.toValidatedProfileData()
            if (data == null) {
                _events.emit(Event.ShowMessage("Проверьте поля профиля"))
                return@launch
            }

            _draft.update { current -> current.copy(isUploading = true) }
            when (val result = profileRepository.submitProfile(data)) {
                CreateProfileSubmissionResult.Success -> {
                    _draft.update { current -> current.copy(isUploading = false) }
                    _events.emit(Event.Completed)
                }

                CreateProfileSubmissionResult.UsernameAlreadyExists -> {
                    _draft.update { current ->
                        current.copy(
                            isUploading = false,
                            username = ValidatedField.Invalid(
                                value = data.username.value,
                                errorType = ValidationErrorType.UsernameAlreadyExists,
                            ),
                        )
                    }
                    _events.emit(Event.ShowMessage("Этот юзернейм уже занят"))
                }

                CreateProfileSubmissionResult.Failure -> {
                    _draft.update { current -> current.copy(isUploading = false) }
                    _events.emit(Event.ShowMessage("Не удалось завершить регистрацию"))
                }
            }
        }
    }

    fun dispose() {
        if (ownsScope) {
            holderScope.cancel()
        }
    }

    private fun validateAll() {
        updateName(_draft.value.name.value)
        updateUsername(_draft.value.username.value)
        updateBio(_draft.value.bio.value)
    }

    private fun CreateProfileDraftState.toValidatedProfileData(): EditProfileData? {
        val nameValue = (name as? ValidatedField.Valid)?.value ?: return null
        val usernameValue = (username as? ValidatedField.Valid)?.value ?: return null
        val bioValue = (bio as? ValidatedField.Valid)?.value ?: return null
        return EditProfileData(
            name = ProfileName.create(nameValue),
            username = ProfileUsername.create(usernameValue),
            description = ProfileDescription.create(bioValue),
        )
    }

    private fun <T> updateField(
        newValue: String,
        validator: (String) -> ValueValidationResult<T>,
        transform: (CreateProfileDraftState, ValidatedField) -> CreateProfileDraftState,
    ) {
        when (val validationResult = validator(newValue)) {
            is ValueValidationResult.Valid -> {
                _draft.update { current ->
                    transform(current, ValidatedField.Valid(value = newValue))
                }
            }

            is ValueValidationResult.Invalid -> {
                holderScope.launch {
                    _events.emit(Event.HapticFeedback)
                }
                if (validationResult.error == ValidationError.TooLarge) return
                _draft.update { current ->
                    transform(current, ValidatedField.Invalid(value = newValue))
                }
            }

            else -> Unit
        }
    }

    private companion object {
        fun initialValue(value: String?): ValidatedField {
            val normalized = value?.trim().orEmpty()
            return if (normalized.isBlank()) initialField else ValidatedField.Valid(normalized)
        }
    }
}
