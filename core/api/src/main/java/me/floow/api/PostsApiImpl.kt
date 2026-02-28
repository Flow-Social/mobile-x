package me.floow.api

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import me.floow.api.dto.ApiPost
import me.floow.api.dto.resolveImageVariants
import me.floow.api.dto.resolveImageUrls
import me.floow.api.util.ApiConfig
import me.floow.api.util.HttpClientProvider
import me.floow.api.util.JsonSerializer
import me.floow.api.util.extensions.addAuthTokenHeader
import me.floow.api.util.extensions.logFailureResponse
import me.floow.api.util.extensions.logKtorRequest
import me.floow.api.util.safeApiCall
import me.floow.domain.api.PostsApi
import me.floow.domain.api.models.CreatePostData
import me.floow.domain.api.models.CreatePostResponse
import me.floow.domain.api.models.GetPostResponse
import me.floow.domain.api.models.GetUserPostsResponse
import me.floow.domain.api.models.PostItem
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.utils.Logger

@Serializable
private data class ApiPostsListResponse(
	val posts: List<ApiPost> = emptyList()
)

@Serializable
private data class CreatePostRequest(
	@SerialName("image_urls")
	val imageUrls: List<String>,
	val description: String? = null,
	val category: String
)

@Serializable
private data class UpdatePostRequest(
	@SerialName("image_urls")
	val imageUrls: List<String>,
	val description: String? = null
)

class PostsApiImpl(
	private val config: ApiConfig,
	private val logger: Logger,
	private val authenticationManager: AuthenticationManager,
	httpClientProvider: HttpClientProvider
) : PostsApi {
	private companion object {
		private const val MAX_POST_DETAILS_CACHE_ENTRIES = 128
		private const val MAX_USER_POSTS_CACHE_ENTRIES = 192
	}

	private data class CachedPostDetails(
		val etag: String,
		val post: PostItem
	)

	private data class CachedUserPostsPage(
		val etag: String,
		val posts: List<PostItem>
	)

	// Posts are frequently mutated (create/edit/delete); using HTTP cache here can
	// re-surface stale lists after mutations.
	private val httpClient = httpClientProvider.getClientWithoutHttpCache()
	private val cacheMutex = Mutex()
	private val postDetailsCache = LinkedHashMap<String, CachedPostDetails>()
	private val userPostsCache = LinkedHashMap<String, CachedUserPostsPage>()

	override suspend fun getPostById(postId: String): GetPostResponse {
		return safeApiCall(errorResponse = GetPostResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall GetPostResponse.Error
			val scopeKey = buildCacheScopeKey(authToken)
			val cacheKey = buildPostDetailsCacheKey(scopeKey = scopeKey, postId = postId)
			val cached = cacheMutex.withLock { postDetailsCache[cacheKey] }

			var response = requestPostById(
				authToken = authToken,
				postId = postId,
				ifNoneMatch = cached?.etag
			)

			if (response.status == HttpStatusCode.NotModified) {
				if (cached != null) {
					return@safeApiCall GetPostResponse.Success(post = cached.post)
				}
				response = requestPostById(
					authToken = authToken,
					postId = postId,
					ifNoneMatch = null
				)
			}

			logger.logKtorRequest("PostsApiImpl getPostById", response.call.request)

			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("PostsApiImpl getPostById", response.status, bodyText)
				return@safeApiCall GetPostResponse.Error
			}

			val post = runCatching { JsonSerializer.decodeFromString<ApiPost>(bodyText) }
				.getOrNull()
				?: return@safeApiCall GetPostResponse.Error

			val postItem = post.toPostItem()
			val etag = response.headers[HttpHeaders.ETag]?.trim()
			if (!etag.isNullOrBlank()) {
				cacheMutex.withLock {
					postDetailsCache[cacheKey] = CachedPostDetails(etag = etag, post = postItem)
					while (postDetailsCache.size > MAX_POST_DETAILS_CACHE_ENTRIES) {
						val firstKey = postDetailsCache.keys.firstOrNull() ?: break
						postDetailsCache.remove(firstKey)
					}
				}
			}

			return@safeApiCall GetPostResponse.Success(post = postItem)
		}
	}

	override suspend fun getUserPosts(
		userId: String,
		forceNetwork: Boolean,
		limit: Int,
		offset: Int
	): GetUserPostsResponse {
		return safeApiCall(errorResponse = GetUserPostsResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall GetUserPostsResponse.Error
			val normalizedLimit = limit.coerceAtLeast(1)
			val normalizedOffset = offset.coerceAtLeast(0)
			val scopeKey = buildCacheScopeKey(authToken)
			val cacheKey = buildUserPostsCacheKey(
				scopeKey = scopeKey,
				userId = userId,
				limit = normalizedLimit,
				offset = normalizedOffset
			)
			val cached = cacheMutex.withLock { userPostsCache[cacheKey] }

			var response = requestUserPosts(
				authToken = authToken,
				userId = userId,
				forceNetwork = forceNetwork,
				limit = normalizedLimit,
				offset = normalizedOffset,
				ifNoneMatch = cached?.etag
			)

			if (response.status == HttpStatusCode.NotModified) {
				if (cached != null) {
					return@safeApiCall GetUserPostsResponse.Success(posts = cached.posts)
				}
				response = requestUserPosts(
					authToken = authToken,
					userId = userId,
					forceNetwork = forceNetwork,
					limit = normalizedLimit,
					offset = normalizedOffset,
					ifNoneMatch = null
				)
			}

			logger.logKtorRequest("PostsApiImpl getUserPosts", response.call.request)

			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("PostsApiImpl getUserPosts", response.status, bodyText)
				return@safeApiCall GetUserPostsResponse.Error
			}

			val items = parsePostsList(bodyText) ?: return@safeApiCall GetUserPostsResponse.Error
			val etag = response.headers[HttpHeaders.ETag]?.trim()
			if (!etag.isNullOrBlank()) {
				cacheMutex.withLock {
					userPostsCache[cacheKey] = CachedUserPostsPage(etag = etag, posts = items)
					while (userPostsCache.size > MAX_USER_POSTS_CACHE_ENTRIES) {
						val firstKey = userPostsCache.keys.firstOrNull() ?: break
						userPostsCache.remove(firstKey)
					}
				}
			}

			return@safeApiCall GetUserPostsResponse.Success(posts = items)
		}
	}

	override suspend fun createPost(data: CreatePostData): CreatePostResponse {
		return safeApiCall(errorResponse = CreatePostResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall CreatePostResponse.Error

			val requestBody = CreatePostRequest(
				imageUrls = data.imageUrls,
				description = data.description,
				category = data.category
			)

			val response = httpClient.post("${config.apiUrl}/posts") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(JsonSerializer.encodeToString(requestBody))
			}

			logger.logKtorRequest("PostsApiImpl createPost", response.call.request)

			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("PostsApiImpl createPost", response.status, bodyText)
				return@safeApiCall CreatePostResponse.Error
			}

			val createdPost = runCatching { JsonSerializer.decodeFromString<ApiPost>(bodyText) }
				.getOrNull()
				?.toPostItem()
			invalidatePostCachesForScope(buildCacheScopeKey(authToken))

			return@safeApiCall CreatePostResponse.Success(createdPost = createdPost)
		}
	}

	override suspend fun deletePost(postId: String): Boolean {
		return safeApiCall(errorResponse = false) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall false

			val response = httpClient.delete("${config.apiUrl}/posts/$postId") {
				addAuthTokenHeader(authToken)
			}

			logger.logKtorRequest("PostsApiImpl deletePost", response.call.request)

			if (!response.status.isSuccess()) {
				val bodyText = response.bodyAsText()
				logger.logFailureResponse("PostsApiImpl deletePost", response.status, bodyText)
				return@safeApiCall false
			}
			invalidatePostCachesForScope(buildCacheScopeKey(authToken))

			return@safeApiCall true
		}
	}

	override suspend fun updatePost(postId: String, description: String?, imageUrls: List<String>?): CreatePostResponse {
		return safeApiCall(errorResponse = CreatePostResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall CreatePostResponse.Error

			val requestBody = UpdatePostRequest(
				imageUrls = imageUrls ?: emptyList(),
				description = description
			)

			val response = httpClient.put("${config.apiUrl}/posts/$postId") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(JsonSerializer.encodeToString(requestBody))
			}

			logger.logKtorRequest("PostsApiImpl updatePost", response.call.request)

			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("PostsApiImpl updatePost", response.status, bodyText)
				return@safeApiCall CreatePostResponse.Error
			}

			val createdPost = runCatching { JsonSerializer.decodeFromString<ApiPost>(bodyText) }
				.getOrNull()
				?.toPostItem()
			invalidatePostCachesForScope(buildCacheScopeKey(authToken))

			return@safeApiCall CreatePostResponse.Success(createdPost = createdPost)
		}
	}

	private suspend fun requestPostById(
		authToken: String,
		postId: String,
		ifNoneMatch: String?
	): HttpResponse {
		return httpClient.get("${config.apiUrl}/posts/$postId") {
			addAuthTokenHeader(authToken)
			if (!ifNoneMatch.isNullOrBlank()) {
				header(HttpHeaders.IfNoneMatch, ifNoneMatch)
			}
		}
	}

	private suspend fun requestUserPosts(
		authToken: String,
		userId: String,
		forceNetwork: Boolean,
		limit: Int,
		offset: Int,
		ifNoneMatch: String?
	): HttpResponse {
		return httpClient.get("${config.apiUrl}/users/$userId/posts") {
			addAuthTokenHeader(authToken)
			parameter("limit", limit)
			parameter("offset", offset)
			if (forceNetwork) {
				header(HttpHeaders.CacheControl, "no-cache")
				header(HttpHeaders.Pragma, "no-cache")
			}
			if (!ifNoneMatch.isNullOrBlank()) {
				header(HttpHeaders.IfNoneMatch, ifNoneMatch)
			}
		}
	}

	private fun parsePostsList(bodyText: String): List<PostItem>? {
		val posts: List<ApiPost> =
			runCatching { JsonSerializer.decodeFromString<List<ApiPost>>(bodyText) }.getOrNull()
				?: runCatching { JsonSerializer.decodeFromString<ApiPostsListResponse>(bodyText).posts }.getOrNull()
				?: return null
		return posts.map { post -> post.toPostItem() }
	}

	private fun buildCacheScopeKey(authToken: String): String {
		return authenticationManager
			.getSelfUserIdOrNull()
			?.takeIf { it.isNotBlank() }
			?: "token:${authToken.takeLast(16)}"
	}

	private fun buildPostDetailsCacheKey(scopeKey: String, postId: String): String {
		return "$scopeKey|post|$postId"
	}

	private fun buildUserPostsCacheKey(scopeKey: String, userId: String, limit: Int, offset: Int): String {
		return "$scopeKey|user|$userId|$limit|$offset"
	}

	private suspend fun invalidatePostCachesForScope(scopeKey: String) {
		cacheMutex.withLock {
			val prefix = "$scopeKey|"
			postDetailsCache.keys
				.filter { key -> key.startsWith(prefix) }
				.forEach(postDetailsCache::remove)
			userPostsCache.keys
				.filter { key -> key.startsWith(prefix) }
				.forEach(userPostsCache::remove)
		}
	}
}

private fun ApiPost.toPostItem(): PostItem {
	return PostItem(
		id = id,
		authorId = author.id,
		authorName = author.name,
		authorUsername = author.username,
		authorAvatarUrl = author.avatarUrl?.ifBlank { null },
		imageUrls = content.resolveImageUrls(),
		description = content.description,
		category = category,
		createdAt = createdAt,
		likesCount = likesCount,
		commentsCount = commentsCount,
		commentersPreview = commentersPreview,
		imageVariants = content.resolveImageVariants().map { variant ->
			me.floow.domain.api.models.ImageVariantItem(
				lqUrl = variant.lqUrl,
				previewUrl = variant.previewUrl,
				fullUrl = variant.fullUrl,
				width = variant.width,
				height = variant.height
			)
		}
	)
}
