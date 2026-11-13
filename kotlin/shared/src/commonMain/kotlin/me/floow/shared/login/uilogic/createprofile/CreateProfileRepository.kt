package me.floow.shared.login.uilogic.createprofile

import me.floow.domain.api.models.EditProfileData

interface CreateProfileRepository {
    suspend fun submitProfile(data: EditProfileData): CreateProfileSubmissionResult
}

sealed interface CreateProfileSubmissionResult {
    data object Success : CreateProfileSubmissionResult

    data object UsernameAlreadyExists : CreateProfileSubmissionResult

    data object Failure : CreateProfileSubmissionResult
}
