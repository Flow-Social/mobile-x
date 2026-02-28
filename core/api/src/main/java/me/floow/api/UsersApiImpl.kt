package me.floow.api

import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.*
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import me.floow.api.util.ApiConfig
import me.floow.api.util.HttpClientProvider
import me.floow.api.util.JsonSerializer
import me.floow.api.util.extensions.addAuthTokenHeader
import me.floow.api.util.extensions.logFailureResponse
import me.floow.api.util.extensions.logKtorRequest
import me.floow.api.util.safeApiCall
import me.floow.domain.api.UsersApi
import me.floow.domain.api.models.SearchUsersResponse
import me.floow.domain.api.models.UserSearchItem
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.utils.Logger

@Serializable
private data class SearchUserResponseItem(
	val id: String,
	val username: String,
	val name: String,
	val avatar: String = "",
)

class UsersApiImpl(
	private val config: ApiConfig,
	private val logger: Logger,
	private val authenticationManager: AuthenticationManager,
	httpClientProvider: HttpClientProvider,
) : UsersApi {
	private val httpClient = httpClientProvider.getClientWithoutHttpCache()

	override suspend fun searchUsers(query: String, limit: Int, offset: Int): SearchUsersResponse {
		return safeApiCall(errorResponse = SearchUsersResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall SearchUsersResponse.Error

			val response = httpClient.get("${config.apiUrl}/users/search") {
				addAuthTokenHeader(authToken)
				parameter("q", query)
				parameter("limit", limit)
				parameter("offset", offset)
			}

			logger.logKtorRequest("UsersApiImpl searchUsers", response.call.request)

			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("UsersApiImpl searchUsers", response.status, bodyText)
				return@safeApiCall SearchUsersResponse.Error
			}

			val parsed = JsonSerializer.decodeFromString<List<SearchUserResponseItem>>(bodyText)
			val users = parsed.map { item ->
				UserSearchItem(
					id = item.id,
					username = item.username,
					name = item.name,
					avatarUrl = item.avatar.ifBlank { null },
				)
			}

			return@safeApiCall SearchUsersResponse.Success(users = users)
		}
	}
}
