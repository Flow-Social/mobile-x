package me.floow.shared.login.uilogic.createprofile

import me.floow.uikit.util.state.ValidatedField
import me.floow.uikit.util.state.ValidatedField.Companion.initialField

sealed interface CreateProfileState {
    data class Edit(
        val name: ValidatedField = initialField,
        val username: ValidatedField = initialField,
        val bio: ValidatedField = initialField,
    ) : CreateProfileState

    data object Uploading : CreateProfileState
}
