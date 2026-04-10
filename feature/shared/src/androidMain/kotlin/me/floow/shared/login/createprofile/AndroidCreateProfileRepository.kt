package me.floow.shared.login.createprofile

import me.floow.domain.api.models.EditProfileData
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.ProfileRepository as DomainProfileRepository
import me.floow.shared.login.uilogic.createprofile.CreateProfileRepository
import me.floow.shared.login.uilogic.createprofile.CreateProfileSubmissionResult

class AndroidCreateProfileRepository(
    private val profileRepository: DomainProfileRepository,
) : CreateProfileRepository {
    override suspend fun submitProfile(data: EditProfileData): CreateProfileSubmissionResult {
        return when (profileRepository.edit(data)) {
            is UpdateDataResponse.Success -> CreateProfileSubmissionResult.Success
            is UpdateDataResponse.Failure -> CreateProfileSubmissionResult.Failure
        }
    }
}
