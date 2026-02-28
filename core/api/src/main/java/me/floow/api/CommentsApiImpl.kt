package me.floow.api

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.floow.api.util.ApiConfig
import me.floow.api.util.HttpClientProvider
import me.floow.api.util.JsonSerializer
import me.floow.api.util.extensions.addAuthTokenHeader
import me.floow.api.util.extensions.logFailureResponse
import me.floow.api.util.extensions.logKtorRequest
import me.floow.api.util.safeApiCall
import me.floow.domain.api.CommentsApi
import me.floow.domain.api.models.CommentItem
import me.floow.domain.api.models.CommentReplyItem
import me.floow.domain.api.models.CreateCommentData
import me.floow.domain.api.models.CreateCommentResponse
import me.floow.domain.api.models.DeleteCommentResponse
import me.floow.domain.api.models.GetCommentsResponse
import me.floow.domain.api.models.UpdateCommentResponse
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.utils.Logger

@Serializable
private data class ApiCommentAuthor(
	val id: String,
	val username: String? = null,
	val name: String? = null,
	val avatar: String? = null
)

@Serializable
private data class ApiCommentReply(
	val id: String,
	val text: String,
	@SerialName("author_id") val authorId: String,
	@SerialName("author_name") val authorName: String? = null
)

@Serializable
private data class ApiComment(
	val id: String,
	@SerialName("post_id") val postId: String,
	val author: ApiCommentAuthor,
	val text: String,
	@SerialName("created_at") val createdAt: Long,
	@SerialName("updated_at") val updatedAt: Long,
	@SerialName("reply_to") val replyTo: ApiCommentReply? = null
)

@Serializable
private data class ApiCommentsListResponse(
	val items: List<ApiComment> = emptyList(),
	@SerialName("next_cursor") val nextCursor: String? = null
)

@Serializable
private data class CreateCommentRequest(
	val text: String,
	@SerialName("reply_to_id") val replyToId: Long? = null
)

@Serializable
private data class UpdateCommentRequest(
	val text: String
)

class CommentsApiImpl(
	private val config: ApiConfig,
	private val logger: Logger,
	private val authenticationManager: AuthenticationManager,
	httpClientProvider: HttpClientProvider
) : CommentsApi {
	private companion object {
		private const val MAX_COMMENTS_CACHE_ENTRIES = 64
	}

	private data class CachedCommentsPage(
		val etag: String,
		val payload: ApiCommentsListResponse
	)

	private val httpClient = httpClientProvider.getClientWithoutHttpCache()
	private val cacheMutex = Mutex()
	private val commentsCache = LinkedHashMap<String, CachedCommentsPage>()
	private val commentToPostId = mutableMapOf<String, String>()

	override suspend fun getComments(postId: String, cursor: String?, limit: Int): GetCommentsResponse {
		return safeApiCall(errorResponse = GetCommentsResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall GetCommentsResponse.Error
			val cacheKey = buildCommentsCacheKey(postId, cursor, limit)
			val cachedPage = cacheMutex.withLock { commentsCache[cacheKey] }

			var response = requestCommentsPage(
				authToken = authToken,
				postId = postId,
				cursor = cursor,
				limit = limit,
				ifNoneMatch = cachedPage?.etag
			)

			logger.logKtorRequest("CommentsApiImpl getComments", response.call.request)

			if (response.status == HttpStatusCode.NotModified) {
				if (cachedPage != null) {
					val items = cachedPage.payload.items.map { it.toDomainItem() }
					return@safeApiCall GetCommentsResponse.Success(
						items = items,
						nextCursor = cachedPage.payload.nextCursor
					)
				}

				// Fallback safety: if server returns 304 but in-memory snapshot is gone,
				// immediately re-fetch full payload without validator.
				response = requestCommentsPage(
					authToken = authToken,
					postId = postId,
					cursor = cursor,
					limit = limit,
					ifNoneMatch = null
				)
				logger.logKtorRequest("CommentsApiImpl getComments fallback", response.call.request)
			}

			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("CommentsApiImpl getComments", response.status, bodyText)
				return@safeApiCall GetCommentsResponse.Error
			}

			val payload = runCatching { JsonSerializer.decodeFromString<ApiCommentsListResponse>(bodyText) }
				.getOrNull() ?: return@safeApiCall GetCommentsResponse.Error

			val items = payload.items.map { it.toDomainItem() }
			cacheMutex.withLock {
				val etag = response.headers[HttpHeaders.ETag]?.trim()
				if (!etag.isNullOrBlank()) {
					commentsCache[cacheKey] = CachedCommentsPage(etag = etag, payload = payload)
					while (commentsCache.size > MAX_COMMENTS_CACHE_ENTRIES) {
						val firstKey = commentsCache.keys.firstOrNull() ?: break
						commentsCache.remove(firstKey)
					}
				}
				for (item in payload.items) {
					commentToPostId[item.id] = item.postId
				}
			}
			GetCommentsResponse.Success(items = items, nextCursor = payload.nextCursor)
		}
	}

	override suspend fun createComment(data: CreateCommentData): CreateCommentResponse {
		return safeApiCall(errorResponse = CreateCommentResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall CreateCommentResponse.Error

			val requestBody = CreateCommentRequest(text = data.text, replyToId = data.replyToId)
			val startedAt = System.currentTimeMillis()

			val response = httpClient.post("${config.apiUrl}/posts/${data.postId}/comments") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(JsonSerializer.encodeToString(requestBody))
			}

			logger.logKtorRequest("CommentsApiImpl createComment", response.call.request)

			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				val durationMs = System.currentTimeMillis() - startedAt
				logger.d("CommentsApiImpl createComment", "Failed in ${durationMs}ms with status=${response.status.value}")
				logger.logFailureResponse("CommentsApiImpl createComment", response.status, bodyText)
				return@safeApiCall CreateCommentResponse.Error
			}

			val comment = runCatching { JsonSerializer.decodeFromString<ApiComment>(bodyText) }
				.getOrNull() ?: return@safeApiCall CreateCommentResponse.Error
			val durationMs = System.currentTimeMillis() - startedAt
			logger.d("CommentsApiImpl createComment", "Success in ${durationMs}ms")
			invalidateCommentsCacheForPost(data.postId)

			CreateCommentResponse.Success(comment = comment.toDomainItem())
		}
	}

	override suspend fun updateComment(commentId: String, text: String): UpdateCommentResponse {
		return safeApiCall(errorResponse = UpdateCommentResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall UpdateCommentResponse.Error

			val response = httpClient.patch("${config.apiUrl}/comments/$commentId") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(JsonSerializer.encodeToString(UpdateCommentRequest(text = text)))
			}

			logger.logKtorRequest("CommentsApiImpl updateComment", response.call.request)

			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("CommentsApiImpl updateComment", response.status, bodyText)
				return@safeApiCall UpdateCommentResponse.Error
			}
			invalidateCommentsCacheByCommentId(commentId)

			UpdateCommentResponse.Success
		}
	}

	override suspend fun deleteComment(commentId: String): DeleteCommentResponse {
		return safeApiCall(errorResponse = DeleteCommentResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall DeleteCommentResponse.Error

			val response = httpClient.delete("${config.apiUrl}/comments/$commentId") {
				addAuthTokenHeader(authToken)
			}

			logger.logKtorRequest("CommentsApiImpl deleteComment", response.call.request)

			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("CommentsApiImpl deleteComment", response.status, bodyText)
				return@safeApiCall DeleteCommentResponse.Error
			}
			invalidateCommentsCacheByCommentId(commentId)

			DeleteCommentResponse.Success
		}
	}

	private fun buildCommentsCacheKey(postId: String, cursor: String?, limit: Int): String {
		return "$postId|${cursor.orEmpty()}|$limit"
	}

	private suspend fun requestCommentsPage(
		authToken: String,
		postId: String,
		cursor: String?,
		limit: Int,
		ifNoneMatch: String?
	): HttpResponse {
		return httpClient.get("${config.apiUrl}/posts/$postId/comments") {
			addAuthTokenHeader(authToken)
			ifNoneMatch?.let { header(HttpHeaders.IfNoneMatch, it) }
			url {
				parameters.append("limit", limit.toString())
				if (!cursor.isNullOrBlank()) {
					parameters.append("cursor", cursor)
				}
			}
		}
	}

	private suspend fun invalidateCommentsCacheForPost(postId: String) {
		cacheMutex.withLock {
			val keysToDelete = commentsCache.keys.filter { key -> key.startsWith("$postId|") }
			for (key in keysToDelete) {
				commentsCache.remove(key)
			}
			commentToPostId.entries.removeAll { entry -> entry.value == postId }
		}
	}

	private suspend fun invalidateCommentsCacheByCommentId(commentId: String) {
		cacheMutex.withLock {
			val postId = commentToPostId[commentId]
			if (postId == null) {
				commentsCache.clear()
				commentToPostId.clear()
				return
			}

			val keysToDelete = commentsCache.keys.filter { key -> key.startsWith("$postId|") }
			for (key in keysToDelete) {
				commentsCache.remove(key)
			}
			commentToPostId.entries.removeAll { entry -> entry.value == postId }
		}
	}
}

private fun ApiComment.toDomainItem(): CommentItem {
	return CommentItem(
		id = id,
		postId = postId,
		authorId = author.id,
		authorUsername = author.username,
		authorName = author.name,
		authorAvatarUrl = author.avatar?.ifBlank { null },
		text = text,
		createdAt = createdAt,
		updatedAt = updatedAt,
		replyTo = replyTo?.let {
			CommentReplyItem(
				id = it.id,
				text = it.text,
				authorId = it.authorId,
				authorName = it.authorName
			)
		}
	)
}
