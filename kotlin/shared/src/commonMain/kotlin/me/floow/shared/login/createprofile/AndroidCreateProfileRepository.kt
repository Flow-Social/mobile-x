package me.floow.shared.login.createprofile

import me.floow.domain.api.AuthApi
import me.floow.domain.api.models.CompleteGoogleRegistrationResult
import me.floow.domain.api.models.EditProfileData
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.ProfileRepository as DomainProfileRepository
import me.floow.shared.login.uilogic.createprofile.CreateProfileRepository
import me.floow.shared.login.uilogic.createprofile.CreateProfileSubmissionResult

class AndroidCreateProfileRepository(
    private val profileRepository: DomainProfileRepository,
    private val authenticationManager: AuthenticationManager,
    private val authApi: AuthApi,
) : CreateProfileRepository {
    override suspend fun submitProfile(data: EditProfileData): CreateProfileSubmissionResult {
        val pendingToken = authenticationManager.getPendingRegistrationTokenOrNull()
        if (pendingToken != null) {
            val result = authApi.completeGoogleRegistration(
                pendingToken = pendingToken,
                data = data,
                avatarUrl = null,
            )
            return when (result) {
                is CompleteGoogleRegistrationResult.Success -> {
                    result.userId?.takeIf(String::isNotBlank)?.let(authenticationManager::saveSelfUserId)
                    authenticationManager.writeAuthToken(result.token)
                    CreateProfileSubmissionResult.Success
                }
                is CompleteGoogleRegistrationResult.UsernameAlreadyExists -> {
                    CreateProfileSubmissionResult.UsernameAlreadyExists
                }
                is CompleteGoogleRegistrationResult.Failure -> {
                    CreateProfileSubmissionResult.Failure
                }
            }
        }

        return when (profileRepository.edit(data)) {
            is UpdateDataResponse.Success -> CreateProfileSubmissionResult.Success
            is UpdateDataResponse.Failure -> CreateProfileSubmissionResult.Failure
        }
    }
}
