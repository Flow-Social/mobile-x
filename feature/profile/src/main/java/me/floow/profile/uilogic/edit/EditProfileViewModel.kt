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

data class CreateProfileVmState(
	val name: ValidatedField = initialField,
	val username: ValidatedField = initialField,
	val bio: ValidatedField = initialField,
	val avatarPreviewUri: String? = null,
	val avatarRemoteUrl: String? = null,
	val avatarErrorMessage: String? = null,
	val backgroundPreviewUri: String? = null,
	val backgroundRemoteUrl: String? = null,
	val backgroundErrorMessage: String? = null,
	val isUploading: Boolean = false,
	val originalUsername: String = ""
) {
	fun toUiState(): EditProfileState {
		return if (isUploading) {
			EditProfileState.Uploading
		} else {
			EditProfileState.Edit(
				name = name,
					username = username,
					bio = bio,
					avatarPreviewUri = avatarPreviewUri,
					avatarRemoteUrl = avatarRemoteUrl,
					avatarErrorMessage = avatarErrorMessage,
					backgroundPreviewUri = backgroundPreviewUri,
					backgroundRemoteUrl = backgroundRemoteUrl,
					backgroundErrorMessage = backgroundErrorMessage
				)
			}
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

@OptIn(FlowPreview::class)
class EditProfileViewModel(
	private val profileRepository: ProfileRepository,
	private val uploadsRepository: UploadsRepository,
	private val localImageFileReader: LocalImageFileReader,
	private val authenticationManager: AuthenticationManager,
	private val authApi: AuthApi,
	private val profileLocalStore: ProfileLocalStore,
) : ViewModel() {
	private val _state = MutableStateFlow(CreateProfileVmState())
	private val _usernameCheckFlow = MutableSharedFlow<String>()
	private val _hapticFeedbackFlow = MutableSharedFlow<Unit>()

	val hapticFeedbackFlow: SharedFlow<Unit> = _hapticFeedbackFlow

	val state: StateFlow<EditProfileState> = _state
		.map(CreateProfileVmState::toUiState)
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
							avatarRemoteUrl = result.data.avatarUrl,
							avatarPreviewUri = null,
							avatarErrorMessage = null,
							backgroundRemoteUrl = result.data.backgroundUrl,
							backgroundPreviewUri = null,
							backgroundErrorMessage = null,
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

	fun updateProfile(onSuccess: () -> Unit, onFailure: () -> Unit) {
		viewModelScope.launch {
			validateAll()

			val allValid = _state.value.bio is ValidatedField.Valid &&
				_state.value.name is ValidatedField.Valid &&
				_state.value.username is ValidatedField.Valid

			if (!allValid) {
				onFailure()
				return@launch
			}

				_state.update {
					it.copy(
						isUploading = true,
						avatarErrorMessage = null,
						backgroundErrorMessage = null
					)
				}

				val avatarUploadResult = uploadAvatarIfNeeded()
				if (avatarUploadResult is AvatarUploadResult.Failure) {
				_state.update {
					it.copy(
						isUploading = false,
						avatarErrorMessage = avatarUploadResult.message
					)
				}
				onFailure()
					return@launch
				}
				val backgroundUploadResult = uploadBackgroundIfNeeded()
				if (backgroundUploadResult is BackgroundUploadResult.Failure) {
					_state.update {
						it.copy(
							isUploading = false,
							backgroundErrorMessage = backgroundUploadResult.message
						)
					}
					onFailure()
					return@launch
				}

				val uploadedAvatarUrl = (avatarUploadResult as? AvatarUploadResult.Success)?.avatarUrl
				val uploadedBackgroundUrl = (backgroundUploadResult as? BackgroundUploadResult.Success)?.backgroundUrl
				val editData = EditProfileData(
					name = ProfileName.create(_state.value.name.value),
					username = ProfileUsername.create(_state.value.username.value),
					description = ProfileDescription.create(_state.value.bio.value)
			)

			val pendingToken = authenticationManager.getPendingRegistrationTokenOrNull()
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
									value = _state.value.username.value,
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
							applyOptimisticProfileUpdate(
								newAvatarUrl = uploadedAvatarUrl,
								newBackgroundUrl = uploadedBackgroundUrl
							)
							refreshProfileInBackground()
							onSuccess()
						} else {
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
					if (profileUpdateResult is UpdateDataResponse.Failure &&
						profileUpdateResult.failureError == FailureError.UsernameAlreadyExists
					) {
						_state.update {
							it.copy(
								username = ValidatedField.Invalid(
									value = _state.value.username.value,
									errorType = ValidationErrorType.UsernameAlreadyExists
								)
							)
						}
					}
					onFailure()
				}
			}

			_state.update {
				it.copy(
					isUploading = false
				)
			}
		}
	}

	private suspend fun uploadBackgroundIfNeeded(): BackgroundUploadResult {
		val backgroundUri = _state.value.backgroundPreviewUri ?: return BackgroundUploadResult.NotSelected
		return when (val readResult = localImageFileReader.readBackground(backgroundUri)) {
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

	private suspend fun uploadAvatarIfNeeded(): AvatarUploadResult {
		val avatarUri = _state.value.avatarPreviewUri ?: return AvatarUploadResult.NotSelected
		return when (val readResult = localImageFileReader.readAvatar(avatarUri)) {
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

	private suspend fun applyOptimisticProfileUpdate(newAvatarUrl: String?, newBackgroundUrl: String?) {
		val cached = profileLocalStore.observeProfile("me").firstOrNull()
		val updatedAvatarUrl = newAvatarUrl ?: cached?.avatarUrl
		val updatedBackgroundUrl = newBackgroundUrl ?: cached?.backgroundUrl
		val updatedBackgroundUpdatedAt = if (newBackgroundUrl != null) {
			System.currentTimeMillis()
		} else {
			cached?.backgroundUpdatedAt
		}

		val updated = PublicProfile(
			id = "me",
			name = ProfileName.create(_state.value.name.value),
			username = ProfileUsername.create(_state.value.username.value),
			description = ProfileDescription.create(_state.value.bio.value),
			avatarUrl = updatedAvatarUrl,
			backgroundUrl = updatedBackgroundUrl,
			backgroundUpdatedAt = updatedBackgroundUpdatedAt,
			totalLikesReceived = cached?.totalLikesReceived ?: 0
		)
		profileLocalStore.upsertProfile(
			userId = "me",
			profile = updated,
			updatedAt = System.currentTimeMillis()
		)
		_state.update {
				it.copy(
					originalUsername = _state.value.username.value,
					avatarRemoteUrl = updatedAvatarUrl,
					avatarPreviewUri = null,
					backgroundRemoteUrl = updatedBackgroundUrl,
					backgroundPreviewUri = null
				)
			}
	}

	private fun refreshProfileInBackground() {
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
								avatarRemoteUrl = selfData.data.avatarUrl,
								backgroundRemoteUrl = selfData.data.backgroundUrl
							)
						} else {
							current
						}
					}
			}
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
					originalUsername = initialData.username,
					avatarRemoteUrl = initialData.avatarUrl,
					avatarPreviewUri = null,
					avatarErrorMessage = null,
					backgroundRemoteUrl = initialData.backgroundUrl,
					backgroundPreviewUri = null,
					backgroundErrorMessage = null
				)
			}
		}
	}
