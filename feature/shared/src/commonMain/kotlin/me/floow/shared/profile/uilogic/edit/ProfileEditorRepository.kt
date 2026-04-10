package me.floow.shared.profile.uilogic.edit

data class EditableProfileData(
    val name: String,
    val username: String,
    val bio: String,
    val avatarUrl: String?,
    val backgroundUrl: String?,
)

data class UpdatedProfileData(
    val name: String,
    val username: String,
    val bio: String,
    val avatarUrl: String?,
    val backgroundUrl: String?,
)

interface ProfileEditorRepository {
    suspend fun checkUsernameAvailability(username: String): Boolean

    suspend fun updateProfile(
        name: String,
        username: String,
        bio: String,
    ): Result<Unit>

    suspend fun updateProfileMedia(
        kind: String,
        mediaUrl: String,
    ): Result<Unit>
}
