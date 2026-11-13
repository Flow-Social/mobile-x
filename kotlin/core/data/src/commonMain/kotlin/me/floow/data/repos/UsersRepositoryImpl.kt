package me.floow.data.repos

import me.floow.domain.api.UsersApi
import me.floow.domain.api.models.SearchUsersResponse
import me.floow.domain.data.GetDataError
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.UsersRepository
import me.floow.domain.models.UserPreview
import me.floow.domain.utils.Logger
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.RawValueObjectCreate

class UsersRepositoryImpl(
	private val logger: Logger,
	private val usersApi: UsersApi,
) : UsersRepository {
	@OptIn(RawValueObjectCreate::class)
	override suspend fun searchUsers(query: String): GetDataResponse<List<UserPreview>> {
		if (query.isBlank()) {
			return GetDataResponse.Success(emptyList())
		}

		return when (val response = usersApi.searchUsers(query.trim())) {
			is SearchUsersResponse.Success -> {
				logger.d("UsersRepositoryImpl.searchUsers", "Success response: ${response.users.size}")

				val users = response.users.map { item ->
					UserPreview(
						id = item.id,
						name = item.name.takeIf(String::isNotBlank)?.let(ProfileName::createRaw),
						username = item.username.takeIf(String::isNotBlank)?.let(ProfileUsername::createRaw),
						avatarUrl = item.avatarUrl,
					)
				}

				GetDataResponse.Success(users)
			}

			SearchUsersResponse.Error -> {
				logger.d("UsersRepositoryImpl.searchUsers", "Failure response: $response")
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}
}
