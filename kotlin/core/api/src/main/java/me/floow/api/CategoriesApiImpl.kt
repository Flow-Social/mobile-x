package me.floow.api

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.floow.api.util.ApiConfig
import me.floow.api.util.HttpClientProvider
import me.floow.api.util.JsonSerializer
import me.floow.api.util.extensions.addAuthTokenHeader
import me.floow.api.util.extensions.logFailureResponse
import me.floow.api.util.extensions.logKtorRequest
import me.floow.api.util.safeApiCall
import me.floow.domain.api.CategoriesApi
import me.floow.domain.api.models.CategoryItem
import me.floow.domain.api.models.GetCategoriesResponse
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.utils.Logger

@Serializable
private data class ApiCategoryItem(
	val code: String,
	@SerialName("is_system")
	val isSystem: Boolean = false,
	@SerialName("posts_count")
	val postsCount: Int = 0,
)

class CategoriesApiImpl(
	private val config: ApiConfig,
	private val logger: Logger,
	private val authenticationManager: AuthenticationManager,
	httpClientProvider: HttpClientProvider,
) : CategoriesApi {
	private val httpClient = httpClientProvider.getClient()

	override suspend fun getCategories(): GetCategoriesResponse {
		return safeApiCall(errorResponse = GetCategoriesResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall GetCategoriesResponse.Error

			val response = httpClient.get("${config.apiUrl}/categories") {
				addAuthTokenHeader(authToken)
			}

			logger.logKtorRequest("CategoriesApiImpl getCategories", response.call.request)

			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("CategoriesApiImpl getCategories", response.status, bodyText)
				return@safeApiCall GetCategoriesResponse.Error
			}

			val parsed = runCatching { JsonSerializer.decodeFromString<List<ApiCategoryItem>>(bodyText) }
				.getOrNull()
				?: return@safeApiCall GetCategoriesResponse.Error

			GetCategoriesResponse.Success(
				categories = parsed.map { item ->
					CategoryItem(
						code = item.code,
						isSystem = item.isSystem,
						postsCount = item.postsCount
					)
				}
			)
		}
	}
}
