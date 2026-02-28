package me.floow.api

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
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
import me.floow.domain.api.FeedApi
import me.floow.domain.api.models.FeedItem
import me.floow.domain.api.models.GetFeedResponse
import me.floow.domain.api.models.RecordSwipeResponse
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.utils.Logger

@Serializable
private data class ApiFeedItem(
	val post: ApiPost,
	val reason: String
)

@Serializable
private data class ApiFeedResponse(
	val items: List<ApiFeedItem> = emptyList()
)

@Serializable
private data class ApiRecordSwipeRequest(
	val postId: String,
	val isLiked: Boolean
)

@Serializable
private data class ApiUndoSwipeRequest(
	val postId: String
)

@Serializable
private data class ApiRecordSwipeResponse(
	val ok: Boolean
)

class FeedApiImpl(
	private val config: ApiConfig,
	private val logger: Logger,
	private val authenticationManager: AuthenticationManager,
	httpClientProvider: HttpClientProvider
) : FeedApi {
	private companion object {
		private const val MAX_FEED_CACHE_ENTRIES = 32
	}

	private data class CachedFeedResponse(
		val etag: String,
		val items: List<FeedItem>
	)

	private val httpClient = httpClientProvider.getClientWithoutHttpCache()
	private val cacheMutex = Mutex()
	private val feedCache = LinkedHashMap<String, CachedFeedResponse>()

	override suspend fun getFeed(limit: Int): GetFeedResponse {
		return safeApiCall(errorResponse = GetFeedResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall GetFeedResponse.Error
			val cacheKey = buildFeedCacheKey(limit = limit, authToken = authToken)
			val cached = cacheMutex.withLock { feedCache[cacheKey] }

			var response = requestFeed(authToken = authToken, limit = limit, ifNoneMatch = cached?.etag)

			logger.logKtorRequest("FeedApiImpl getFeed", response.call.request)

			if (response.status == HttpStatusCode.NotModified) {
				if (cached != null) {
					return@safeApiCall GetFeedResponse.Success(items = cached.items)
				}

				// Safety fallback: if backend returns 304 but local snapshot is gone,
				// immediately re-fetch full payload.
				response = requestFeed(authToken = authToken, limit = limit, ifNoneMatch = null)
				logger.logKtorRequest("FeedApiImpl getFeed fallback", response.call.request)
			}

			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("FeedApiImpl getFeed", response.status, bodyText)
				return@safeApiCall GetFeedResponse.Error
			}

			val parsed = runCatching { JsonSerializer.decodeFromString<ApiFeedResponse>(bodyText) }
				.getOrNull()
				?: return@safeApiCall GetFeedResponse.Error

			val items = parsed.items.map(::mapFeedItem)
			val etag = response.headers[HttpHeaders.ETag]?.trim()
			if (!etag.isNullOrBlank()) {
				cacheMutex.withLock {
					feedCache[cacheKey] = CachedFeedResponse(etag = etag, items = items)
					while (feedCache.size > MAX_FEED_CACHE_ENTRIES) {
						val firstKey = feedCache.keys.firstOrNull() ?: break
						feedCache.remove(firstKey)
					}
				}
			}

			GetFeedResponse.Success(items = items)
		}
	}

	override suspend fun recordSwipe(postId: String, isLiked: Boolean): RecordSwipeResponse {
		return safeApiCall(errorResponse = RecordSwipeResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall RecordSwipeResponse.Error

			val requestBody = ApiRecordSwipeRequest(postId = postId, isLiked = isLiked)

			val response = httpClient.post("${config.apiUrl}/feed/swipe") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(JsonSerializer.encodeToString(requestBody))
			}

			logger.logKtorRequest("FeedApiImpl recordSwipe", response.call.request)

			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("FeedApiImpl recordSwipe", response.status, bodyText)
				return@safeApiCall RecordSwipeResponse.Error
			}

			val parsed = runCatching { JsonSerializer.decodeFromString<ApiRecordSwipeResponse>(bodyText) }.getOrNull()
				?: return@safeApiCall RecordSwipeResponse.Error

			if (!parsed.ok) return@safeApiCall RecordSwipeResponse.Error
			invalidateFeedCacheForAuthToken(authToken)

			RecordSwipeResponse.Success
		}
	}

	override suspend fun undoSwipe(postId: String): RecordSwipeResponse {
		return safeApiCall(errorResponse = RecordSwipeResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall RecordSwipeResponse.Error

			val requestBody = ApiUndoSwipeRequest(postId = postId)

			val response = httpClient.post("${config.apiUrl}/feed/swipe/undo") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(JsonSerializer.encodeToString(requestBody))
			}

			logger.logKtorRequest("FeedApiImpl undoSwipe", response.call.request)

			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("FeedApiImpl undoSwipe", response.status, bodyText)
				return@safeApiCall RecordSwipeResponse.Error
			}

			val parsed = runCatching { JsonSerializer.decodeFromString<ApiRecordSwipeResponse>(bodyText) }.getOrNull()
				?: return@safeApiCall RecordSwipeResponse.Error

			if (!parsed.ok) return@safeApiCall RecordSwipeResponse.Error
			invalidateFeedCacheForAuthToken(authToken)

			RecordSwipeResponse.Success
		}
	}

	private suspend fun requestFeed(
		authToken: String,
		limit: Int,
		ifNoneMatch: String?
	): HttpResponse {
		return httpClient.get("${config.apiUrl}/feed") {
			addAuthTokenHeader(authToken)
			url { parameters.append("limit", limit.toString()) }
			if (!ifNoneMatch.isNullOrBlank()) {
				header(HttpHeaders.IfNoneMatch, ifNoneMatch)
			}
		}
	}

	private fun mapFeedItem(item: ApiFeedItem): FeedItem {
		val post = item.post
		return FeedItem(
			id = post.id,
			authorId = post.author.id,
			authorName = post.author.name,
			authorUsername = post.author.username,
			authorAvatarUrl = post.author.avatarUrl?.ifBlank { null },
			imageUrls = post.content.resolveImageUrls(),
			description = post.content.description,
			category = post.category,
			createdAt = post.createdAt,
			reason = item.reason,
			likesCount = post.likesCount,
			commentsCount = post.commentsCount,
			commentersPreview = post.commentersPreview,
			imageVariants = post.content.resolveImageVariants().map { variant ->
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

	private suspend fun invalidateFeedCacheForAuthToken(authToken: String) {
		val prefix = buildFeedCacheKeyPrefix(authToken)
		cacheMutex.withLock {
			val keysToRemove = feedCache.keys.filter { key -> key.startsWith(prefix) }
			keysToRemove.forEach(feedCache::remove)
		}
	}

	private fun buildFeedCacheKey(limit: Int, authToken: String): String {
		return "${buildFeedCacheKeyPrefix(authToken)}|$limit"
	}

	private fun buildFeedCacheKeyPrefix(authToken: String): String {
		val userId = authenticationManager.getSelfUserIdOrNull()?.takeIf { it.isNotBlank() }
		return userId ?: "token:${authToken.takeLast(16)}"
	}
}
