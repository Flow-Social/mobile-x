package me.floow.domain.api

import me.floow.domain.api.models.SearchUsersResponse

interface UsersApi {
	suspend fun searchUsers(
		query: String,
		limit: Int = 20,
		offset: Int = 0,
	): SearchUsersResponse
}

