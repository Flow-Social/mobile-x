package me.floow.profile.uilogic.edit

import me.floow.uikit.util.state.ValidatedField

interface EditProfileState {
	data class Edit(
		val name: ValidatedField = ValidatedField.Valid(""),
		val username: ValidatedField = ValidatedField.Valid(""),
		val bio: ValidatedField = ValidatedField.Valid(""),
		val avatarPreviewUri: String? = null,
		val avatarRemoteUrl: String? = null,
		val avatarErrorMessage: String? = null,
		val backgroundPreviewUri: String? = null,
		val backgroundRemoteUrl: String? = null,
		val backgroundErrorMessage: String? = null,
		val isSubmitting: Boolean = false,
	) : EditProfileState
}
