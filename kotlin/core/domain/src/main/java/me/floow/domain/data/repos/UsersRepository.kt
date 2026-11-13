package me.floow.domain.data.repos

import me.floow.domain.data.GetDataResponse
import me.floow.domain.models.UserPreview

interface UsersRepository {
	suspend fun searchUsers(query: String): GetDataResponse<List<UserPreview>>
}

