package me.floow.domain.api.models

sealed interface SearchUsersResponse {
	data class Success(
		val users: List<UserSearchItem>
	) : SearchUsersResponse

	data object Error : SearchUsersResponse
}

data class UserSearchItem(
	val id: String,
	val username: String,
	val name: String,
	val avatarUrl: String?,
)

