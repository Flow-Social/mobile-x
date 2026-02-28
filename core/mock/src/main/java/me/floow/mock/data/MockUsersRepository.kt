package me.floow.mock.data

import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.UsersRepository
import me.floow.domain.models.UserPreview
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.RawValueObjectCreate
import me.floow.mock.interfaces.MockStorage

class MockUsersRepository(
	private val mockStorage: MockStorage,
) : UsersRepository {
	@OptIn(RawValueObjectCreate::class)
	override suspend fun searchUsers(query: String): GetDataResponse<List<UserPreview>> {
		val normalizedQuery = query.trim().lowercase()
		if (normalizedQuery.isBlank()) return GetDataResponse.Success(emptyList())

		val baseUsers = listOf(
			UserPreview(
				id = "1",
				name = ProfileName.createRaw("Alice"),
				username = ProfileUsername.createRaw("alice"),
				avatarUrl = "https://robohash.org/alice",
			),
			UserPreview(
				id = "2",
				name = ProfileName.createRaw("Bob"),
				username = ProfileUsername.createRaw("bob"),
				avatarUrl = "https://robohash.org/bob",
			),
			UserPreview(
				id = "3",
				name = ProfileName.createRaw("Charlie"),
				username = ProfileUsername.createRaw("charlie"),
				avatarUrl = "https://robohash.org/charlie",
			),
		)

		mockStorage.saveString("users_last_query", query)

		val filtered = baseUsers.filter { user ->
			val name = user.name?.value?.lowercase().orEmpty()
			val username = user.username?.value?.lowercase().orEmpty()
			name.contains(normalizedQuery) || username.contains(normalizedQuery)
		}

		return GetDataResponse.Success(filtered)
	}
}

