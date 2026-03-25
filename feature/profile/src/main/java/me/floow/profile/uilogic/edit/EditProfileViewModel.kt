package me.floow.profile.uilogic.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.domain.api.AuthApi
import me.floow.domain.api.models.CompleteGoogleRegistrationResult
import me.floow.domain.api.models.EditProfileData
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.data.FailureError
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.ProfileRepository
import me.floow.domain.data.repos.UploadImageData
import me.floow.domain.data.repos.UploadsRepository
import me.floow.domain.models.PublicProfile
import me.floow.domain.values.ProfileDescription
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.ValidationError
import me.floow.domain.values.util.ValueValidationResult
import me.floow.profile.ui.edit.EditProfileRouteInitialData
import me.floow.profile.uilogic.addpost.LocalImageFileReader
import me.floow.profile.uilogic.addpost.LocalImageReadError
import me.floow.profile.uilogic.addpost.LocalImageReadResult
import me.floow.uikit.util.state.ValidatedField
import me.floow.uikit.util.state.ValidatedField.Companion.initialField
import me.floow.uikit.util.state.ValidationErrorType

sealed interface EditProfileUiEvent {
	data object Saved : EditProfileUiEvent
	data object RollbackApplied : EditProfileUiEvent
}

data class EditProfileVmState(
	val name: ValidatedField = initialField,
	val username: ValidatedField = initialField,
	val bio: ValidatedField = initialField,
	val originalName: String = "",
	val avatarPreviewUri: String? = null,
	val avatarRemoteUrl: String? = null,
	val avatarErrorMessage: String? = null,
	val originalAvatarRemoteUrl: String? = null,
	val backgroundPreviewUri: String? = null,
	val backgroundRemoteUrl: String? = null,
	val backgroundErrorMessage: String? = null,
	val originalBackgroundRemoteUrl: String? = null,
	val isSubmitting: Boolean = false,
	val originalBio: String = "",
	val originalUsername: String = ""
) {
	fun toUiState(): EditProfileState {
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
}

private sealed interface AvatarUploadResult {
	data object NotSelected : AvatarUploadResult
	data class Success(val avatarUrl: String) : AvatarUploadResult
	data class Failure(val message: String) : AvatarUploadResult
}

private sealed interface BackgroundUploadResult {
	data object NotSelected : BackgroundUploadResult
	data class Success(val backgroundUrl: String) : BackgroundUploadResult
	data class Failure(val message: String) : BackgroundUploadResult
}

private data class EditProfileSubmission(
	val name: String,
	val username: String,
	val bio: String,
	val avatarPreviewUri: String?,
	val avatarRemoteUrl: String?,
	val backgroundPreviewUri: String?,
	val backgroundRemoteUrl: String?,
) {
	val optimisticAvatarUrl: String?
		get() = avatarPreviewUri ?: avatarRemoteUrl

	val optimisticBackgroundUrl: String?
		get() = backgroundPreviewUri ?: backgroundRemoteUrl
}

@OptIn(FlowPreview::class)
class EditProfileViewModel(
	private val profileRepository: ProfileRepository,
	private val uploadsRepository: UploadsRepository,
	private val localImageFileReader: LocalImageFileReader,
	private val authenticationManager: AuthenticationManager,
	private val authApi: AuthApi,
	private val profileLocalStore: ProfileLocalStore,
) : ViewModel() {
	private val _state = MutableStateFlow(EditProfileVmState())
	private val _usernameCheckFlow = MutableSharedFlow<String>()
	private val _hapticFeedbackFlow = MutableSharedFlow<Unit>()
	private val _uiEvents = MutableSharedFlow<EditProfileUiEvent>()

	val hapticFeedbackFlow: SharedFlow<Unit> = _hapticFeedbackFlow
	val uiEvents: SharedFlow<EditProfileUiEvent> = _uiEvents

	val state: StateFlow<EditProfileState> = _state
		.map(EditProfileVmState::toUiState)
		.stateIn(viewModelScope, SharingStarted.Eagerly, EditProfileState.Edit())

	init {
		viewModelScope.launch {
			_usernameCheckFlow
				.debounce(500)
				.distinctUntilChanged()
				.filter { it.isNotEmpty() && it != _state.value.originalUsername }
				.collect { username ->
					val isAvailable = profileRepository.checkUsername(username)
					if (!isAvailable) {
						_state.update {
							it.copy(
								username = ValidatedField.Invalid(
									value = username,
									errorType = ValidationErrorType.UsernameAlreadyExists
								)
							)
						}
					}
				}
		}
	}

	fun loadData() {
		viewModelScope.launch {
			when (val result = profileRepository.getSelfData()) {
					is GetDataResponse.Success -> _state.update {
						it.copy(
							name = ValidatedField.Valid(value = result.data.name?.value ?: ""),
							bio = ValidatedField.Valid(value = result.data.description?.value ?: ""),
							username = ValidatedField.Valid(value = result.data.username?.value ?: ""),
							originalName = result.data.name?.value ?: "",
							avatarRemoteUrl = result.data.avatarUrl,
							avatarPreviewUri = null,
							avatarErrorMessage = null,
							originalAvatarRemoteUrl = result.data.avatarUrl,
							backgroundRemoteUrl = result.data.backgroundUrl,
							backgroundPreviewUri = null,
							backgroundErrorMessage = null,
							originalBackgroundRemoteUrl = result.data.backgroundUrl,
							originalBio = result.data.description?.value ?: "",
							originalUsername = result.data.username?.value ?: ""
						)
					}

				is GetDataResponse.Error -> Unit
			}
		}
	}

	fun setAvatarFromPicker(uriString: String?) {
		val normalized = uriString?.trim()?.takeIf { it.isNotBlank() }
		_state.update {
			it.copy(
				avatarPreviewUri = normalized,
				avatarErrorMessage = null
			)
		}
	}

	fun setBackgroundFromPicker(uriString: String?) {
		val normalized = uriString?.trim()?.takeIf { it.isNotBlank() }
		_state.update {
			it.copy(
				backgroundPreviewUri = normalized,
				backgroundErrorMessage = null
			)
		}
	}

	fun updateName(newValue: String) {
		val validationResult = ProfileName.createWithValidation(newValue)

		if (validationResult is ValueValidationResult.Invalid) {
			viewModelScope.launch {
				_hapticFeedbackFlow.emit(Unit)
			}

			if (validationResult.error == ValidationError.TooLarge) return

			_state.update {
				it.copy(
					name = ValidatedField.Invalid(
						value = newValue,
					)
				)
			}
		} else {
			_state.update {
				it.copy(
					name = ValidatedField.Valid(
						value = newValue,
					)
				)
			}
		}
	}

	fun updateUsername(newValue: String) {
		val validationResult = ProfileUsername.createWithValidation(newValue)

		if (validationResult is ValueValidationResult.Invalid) {
			viewModelScope.launch {
				_hapticFeedbackFlow.emit(Unit)
			}

			if (validationResult.error == ValidationError.TooLarge) return

			_state.update {
				it.copy(
					username = ValidatedField.Invalid(
						value = newValue,
					)
				)
			}
		} else {
			_state.update {
				it.copy(
					username = ValidatedField.Valid(
						value = newValue,
					)
				)
			}
			viewModelScope.launch {
				_usernameCheckFlow.emit(newValue)
			}
		}
	}

	fun updateBiography(newValue: String) {
		val validationResult = ProfileDescription.createWithValidation(newValue)

		if (validationResult is ValueValidationResult.Invalid) {
			viewModelScope.launch {
				_hapticFeedbackFlow.emit(Unit)
			}

			if (validationResult.error == ValidationError.TooLarge) return

			_state.update {
				it.copy(
					bio = ValidatedField.Invalid(
						value = newValue,
					)
				)
			}
		} else {
			_state.update {
				it.copy(
					bio = ValidatedField.Valid(
						value = newValue,
					)
				)
			}
		}
	}

	fun updateProfile(
		optimistic: Boolean = false,
		onSuccess: () -> Unit,
		onFailure: () -> Unit
	) {
		viewModelScope.launch {
			validateAll()

			val currentState = _state.value
			val allValid = currentState.bio is ValidatedField.Valid &&
				currentState.name is ValidatedField.Valid &&
				currentState.username is ValidatedField.Valid

			if (!allValid) {
				onFailure()
				return@launch
			}

			if (!currentState.hasChanges()) {
				onSuccess()
				return@launch
			}

			val submission = currentState.toSubmission()
			val pendingToken = authenticationManager.getPendingRegistrationTokenOrNull()
			val useOptimisticFlow = optimistic && pendingToken == null
			val previousProfile = if (useOptimisticFlow) {
				upsertLocalProfile(
					submission = submission,
					avatarUrl = submission.optimisticAvatarUrl,
					backgroundUrl = submission.optimisticBackgroundUrl,
					backgroundUpdatedAt = resolveBackgroundUpdatedAt(
						backgroundUrl = submission.optimisticBackgroundUrl,
						fallbackUpdatedAt = profileLocalStore.getProfile("me")?.backgroundUpdatedAt
					)
				)
			} else {
				null
			}

			if (useOptimisticFlow) {
				onSuccess()
			} else {
				_state.update {
					it.copy(
						isSubmitting = true,
						avatarErrorMessage = null,
						backgroundErrorMessage = null
					)
				}
			}

			val avatarUploadResult = uploadAvatarIfNeeded(submission.avatarPreviewUri)
			if (avatarUploadResult is AvatarUploadResult.Failure) {
				handleOptimisticFailure(previousProfile)
				_state.update {
					it.copy(
						isSubmitting = false,
						avatarErrorMessage = avatarUploadResult.message
					)
				}
				onFailure()
				return@launch
			}

			val backgroundUploadResult = uploadBackgroundIfNeeded(submission.backgroundPreviewUri)
			if (backgroundUploadResult is BackgroundUploadResult.Failure) {
				handleOptimisticFailure(previousProfile)
				_state.update {
					it.copy(
						isSubmitting = false,
						backgroundErrorMessage = backgroundUploadResult.message
					)
				}
				onFailure()
				return@launch
			}

			val uploadedAvatarUrl = (avatarUploadResult as? AvatarUploadResult.Success)?.avatarUrl
			val uploadedBackgroundUrl = (backgroundUploadResult as? BackgroundUploadResult.Success)?.backgroundUrl
			val editData = EditProfileData(
				name = ProfileName.create(submission.name),
				username = ProfileUsername.create(submission.username),
				description = ProfileDescription.create(submission.bio)
			)

			if (pendingToken != null) {
				when (val result = authApi.completeGoogleRegistration(pendingToken, editData, uploadedAvatarUrl)) {
					is CompleteGoogleRegistrationResult.Success -> {
						result.userId?.let { authenticationManager.saveSelfUserId(it) }
						authenticationManager.writeAuthToken(result.token)
						onSuccess()
					}

					is CompleteGoogleRegistrationResult.UsernameAlreadyExists -> {
						_state.update {
							it.copy(
								username = ValidatedField.Invalid(
									value = submission.username,
									errorType = ValidationErrorType.UsernameAlreadyExists
								)
							)
						}
						onFailure()
					}

					is CompleteGoogleRegistrationResult.Failure -> {
						onFailure()
					}
				}
			} else {
				val profileUpdateResult = profileRepository.edit(data = editData)
				if (profileUpdateResult is UpdateDataResponse.Success) {
					val avatarPersistResult = if (uploadedAvatarUrl != null) {
						profileRepository.updateAvatarUrl(uploadedAvatarUrl)
					} else {
						UpdateDataResponse.Success
					}
					val backgroundPersistResult = if (uploadedBackgroundUrl != null) {
						profileRepository.updateBackgroundUrl(uploadedBackgroundUrl)
					} else {
						UpdateDataResponse.Success
					}

					if (avatarPersistResult is UpdateDataResponse.Success &&
						backgroundPersistResult is UpdateDataResponse.Success
					) {
						upsertLocalProfile(
							submission = submission,
							avatarUrl = uploadedAvatarUrl ?: submission.avatarRemoteUrl,
							backgroundUrl = uploadedBackgroundUrl ?: submission.backgroundRemoteUrl,
							backgroundUpdatedAt = resolveBackgroundUpdatedAt(
								backgroundUrl = uploadedBackgroundUrl ?: submission.backgroundRemoteUrl,
								fallbackUpdatedAt = profileLocalStore.getProfile("me")?.backgroundUpdatedAt
							)
						)
						_uiEvents.emit(EditProfileUiEvent.Saved)
						refreshProfileInBackground(previousProfile)
						if (!useOptimisticFlow) {
							onSuccess()
						}
					} else {
						handleOptimisticFailure(previousProfile)
						_state.update {
							it.copy(
								avatarErrorMessage = if (avatarPersistResult is UpdateDataResponse.Success) {
									it.avatarErrorMessage
								} else {
									"Не удалось сохранить новый аватар. Повтори попытку"
								},
								backgroundErrorMessage = if (backgroundPersistResult is UpdateDataResponse.Success) {
									it.backgroundErrorMessage
								} else {
									"Не удалось сохранить новый фон. Повтори попытку"
								}
							)
						}
						onFailure()
					}
				} else {
					handleOptimisticFailure(previousProfile)
					if (profileUpdateResult is UpdateDataResponse.Failure &&
						profileUpdateResult.failureError == FailureError.UsernameAlreadyExists
					) {
						_state.update {
							it.copy(
								username = ValidatedField.Invalid(
									value = submission.username,
									errorType = ValidationErrorType.UsernameAlreadyExists
								)
							)
						}
					}
					onFailure()
				}
			}

			_state.update {
				it.copy(isSubmitting = false)
			}
		}
	}

	private suspend fun uploadBackgroundIfNeeded(backgroundUri: String?): BackgroundUploadResult {
		val normalizedBackgroundUri = backgroundUri ?: return BackgroundUploadResult.NotSelected
		return when (val readResult = localImageFileReader.readBackground(normalizedBackgroundUri)) {
			is LocalImageReadResult.Success -> {
				val file = readResult.file
				val uploadData = UploadImageData(
					fileName = file.fileName,
					contentType = file.contentType,
					sizeBytes = file.sizeBytes,
					bytes = file.bytes
				)
				when (val uploadResult = uploadsRepository.uploadImages(listOf(uploadData), kind = "background")) {
					is GetDataResponse.Success -> {
						val url = uploadResult.data.firstOrNull()?.takeIf { it.isNotBlank() }
						if (url == null) {
							BackgroundUploadResult.Failure("Не удалось получить URL фона после загрузки")
						} else {
							BackgroundUploadResult.Success(url)
						}
					}

					is GetDataResponse.Error -> {
						BackgroundUploadResult.Failure("Не удалось загрузить фон. Проверь интернет и повтори попытку")
					}
				}
			}

			is LocalImageReadResult.Failure -> {
				BackgroundUploadResult.Failure(
					when (readResult.error) {
						LocalImageReadError.UNSUPPORTED_TYPE ->
							"Поддерживаются JPG, PNG, WEBP, HEIC и HEIF"
						LocalImageReadError.FILE_TOO_LARGE ->
							"Файл слишком большой. Лимит для фона: 2 МБ"
						LocalImageReadError.CORRUPTED_IMAGE ->
							"Не удалось обработать изображение. Выбери другой файл"
					}
				)
			}
		}
	}

	private suspend fun uploadAvatarIfNeeded(avatarUri: String?): AvatarUploadResult {
		val normalizedAvatarUri = avatarUri ?: return AvatarUploadResult.NotSelected
		return when (val readResult = localImageFileReader.readAvatar(normalizedAvatarUri)) {
			is LocalImageReadResult.Success -> {
				val file = readResult.file
				val uploadData = UploadImageData(
					fileName = file.fileName,
					contentType = file.contentType,
					sizeBytes = file.sizeBytes,
					bytes = file.bytes
				)
				when (val uploadResult = uploadsRepository.uploadImages(listOf(uploadData), kind = "avatar")) {
					is GetDataResponse.Success -> {
						val url = uploadResult.data.firstOrNull()?.takeIf { it.isNotBlank() }
						if (url == null) {
							AvatarUploadResult.Failure("Не удалось получить URL аватара после загрузки")
						} else {
							AvatarUploadResult.Success(url)
						}
					}

					is GetDataResponse.Error -> {
						AvatarUploadResult.Failure("Не удалось загрузить аватар. Проверь интернет и повтори попытку")
					}
				}
			}

			is LocalImageReadResult.Failure -> {
				AvatarUploadResult.Failure(
					when (readResult.error) {
						LocalImageReadError.UNSUPPORTED_TYPE ->
							"Поддерживаются JPG, PNG, WEBP, HEIC, HEIF и GIF"
						LocalImageReadError.FILE_TOO_LARGE ->
							"Файл слишком большой. Лимит для аватарки: 1 МБ"
						LocalImageReadError.CORRUPTED_IMAGE ->
							"Не удалось обработать изображение. Выбери другой файл"
					}
				)
			}
		}
	}

	private suspend fun upsertLocalProfile(
		submission: EditProfileSubmission,
		avatarUrl: String?,
		backgroundUrl: String?,
		backgroundUpdatedAt: Long?
	): PublicProfile? {
		val cached = profileLocalStore.observeProfile("me").firstOrNull()
		val updated = PublicProfile(
			id = "me",
			name = ProfileName.create(submission.name),
			username = ProfileUsername.create(submission.username),
			description = ProfileDescription.create(submission.bio),
			avatarUrl = avatarUrl,
			backgroundUrl = backgroundUrl,
			backgroundUpdatedAt = backgroundUpdatedAt,
			totalLikesReceived = cached?.totalLikesReceived ?: 0
		)
		profileLocalStore.upsertProfile(
			userId = "me",
			profile = updated,
			updatedAt = System.currentTimeMillis()
		)
		_state.update {
			it.copy(
				originalName = submission.name,
				originalUsername = submission.username,
				originalBio = submission.bio,
				avatarRemoteUrl = avatarUrl,
				avatarPreviewUri = null,
				originalAvatarRemoteUrl = avatarUrl,
				backgroundRemoteUrl = backgroundUrl,
				backgroundPreviewUri = null,
				originalBackgroundRemoteUrl = backgroundUrl,
			)
		}
		return cached
	}

	private suspend fun handleOptimisticFailure(previousProfile: PublicProfile?) {
		if (previousProfile == null) return
		profileLocalStore.upsertProfile(
			userId = "me",
			profile = previousProfile,
			updatedAt = System.currentTimeMillis()
		)
		_state.update { current ->
			current.copy(
				originalName = previousProfile.name?.value.orEmpty(),
				originalUsername = previousProfile.username?.value.orEmpty(),
				originalBio = previousProfile.description?.value.orEmpty(),
				avatarRemoteUrl = previousProfile.avatarUrl,
				avatarPreviewUri = null,
				originalAvatarRemoteUrl = previousProfile.avatarUrl,
				backgroundRemoteUrl = previousProfile.backgroundUrl,
				backgroundPreviewUri = null,
				originalBackgroundRemoteUrl = previousProfile.backgroundUrl,
				avatarErrorMessage = null,
				backgroundErrorMessage = null,
			)
		}
		_uiEvents.emit(EditProfileUiEvent.RollbackApplied)
	}

	private fun refreshProfileInBackground(previousProfile: PublicProfile?) {
		viewModelScope.launch {
			val selfData = profileRepository.getSelfData()
			if (selfData is GetDataResponse.Success) {
					val refreshed = PublicProfile(
						id = "me",
						name = selfData.data.name,
						username = selfData.data.username,
						description = selfData.data.description,
						avatarUrl = selfData.data.avatarUrl,
						backgroundUrl = selfData.data.backgroundUrl,
						backgroundUpdatedAt = selfData.data.backgroundUpdatedAt,
						totalLikesReceived = selfData.data.totalLikesReceived
					)
				profileLocalStore.upsertProfile(
					userId = "me",
					profile = refreshed,
					updatedAt = System.currentTimeMillis()
				)
					_state.update { current ->
						if (current.avatarPreviewUri == null && current.backgroundPreviewUri == null) {
							current.copy(
								originalName = selfData.data.name?.value.orEmpty(),
								originalUsername = selfData.data.username?.value.orEmpty(),
								originalBio = selfData.data.description?.value.orEmpty(),
								avatarRemoteUrl = selfData.data.avatarUrl,
								originalAvatarRemoteUrl = selfData.data.avatarUrl,
								backgroundRemoteUrl = selfData.data.backgroundUrl,
								originalBackgroundRemoteUrl = selfData.data.backgroundUrl,
							)
						} else {
							current
						}
					}
			} else if (previousProfile != null) {
				handleOptimisticFailure(previousProfile)
			}
		}
	}

	private fun EditProfileVmState.toSubmission(): EditProfileSubmission {
		return EditProfileSubmission(
			name = name.value,
			username = username.value,
			bio = bio.value,
			avatarPreviewUri = avatarPreviewUri,
			avatarRemoteUrl = avatarRemoteUrl,
			backgroundPreviewUri = backgroundPreviewUri,
			backgroundRemoteUrl = backgroundRemoteUrl,
		)
	}

	private fun EditProfileVmState.hasChanges(): Boolean {
		return name.value != originalName ||
			username.value != originalUsername ||
			bio.value != originalBio ||
			avatarPreviewUri != null ||
			avatarRemoteUrl != originalAvatarRemoteUrl ||
			backgroundPreviewUri != null ||
			backgroundRemoteUrl != originalBackgroundRemoteUrl
	}

	private fun resolveBackgroundUpdatedAt(backgroundUrl: String?, fallbackUpdatedAt: Long?): Long? {
		if (backgroundUrl.isNullOrBlank()) return null
		val normalized = backgroundUrl.trim()
		return if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
			System.currentTimeMillis()
		} else {
			null
		}
	}

	private fun validateAll() {
		updateName(_state.value.name.value)
		updateUsername(_state.value.username.value)
		updateBiography(_state.value.bio.value)
	}

	fun setInitialData(initialData: EditProfileRouteInitialData) {
		updateName(initialData.name)
		updateUsername(initialData.username)
		updateBiography(initialData.description)
			_state.update {
				it.copy(
					originalName = initialData.name,
					originalUsername = initialData.username,
					originalBio = initialData.description,
					avatarRemoteUrl = initialData.avatarUrl,
					avatarPreviewUri = null,
					avatarErrorMessage = null,
					originalAvatarRemoteUrl = initialData.avatarUrl,
					backgroundRemoteUrl = initialData.backgroundUrl,
					backgroundPreviewUri = null,
					backgroundErrorMessage = null,
					originalBackgroundRemoteUrl = initialData.backgroundUrl,
				)
			}
		}
	}
