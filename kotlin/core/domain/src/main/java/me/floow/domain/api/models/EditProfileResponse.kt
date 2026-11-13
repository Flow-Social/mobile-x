package me.floow.domain.api.models

enum class EditProfileResponseStatus {
	SUCCESS,
	ERROR,
	USERNAME_ALREADY_EXISTS
}

data class EditProfileResponse(
	val status: EditProfileResponseStatus,
)
