package me.floow.shared.profile.auth

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.floow.shared.login.auth.flowAuthReadLocalStorage
import me.floow.shared.profile.uilogic.ProfileImageVariant
import me.floow.shared.profile.uilogic.ProfilePayload
import me.floow.shared.profile.uilogic.ProfilePost
import me.floow.shared.profile.uilogic.ProfileRepository
import me.floow.shared.profile.auth.flowWasmApiRequest
import kotlin.js.JsName
import kotlin.coroutines.resume

@JsName("flowProfileApiGet")
external fun flowProfileApiGet(
    path: String,
    apiUrl: String,
    authToken: String,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit,
)

class WasmProfileRepository : ProfileRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getProfile(userId: String?): ProfilePayload {
        val authToken = requireNotNull(
            flowAuthReadLocalStorage(SESSION_TOKEN_KEY)?.takeIf(String::isNotBlank)
        ) { "Missing session token" }
        val targetUserId = userId?.takeIf { it.isNotBlank() && it != "me" && it != "self" }
        val profile = if (targetUserId == null) {
            apiGet<ProfileResponse>("/profile", authToken)
        } else {
            apiGet<ProfileResponse>("/users/$targetUserId", authToken)
        }
        val resolvedUserId = targetUserId ?: profile.id
        val postsResult = runCatching {
            apiGetPosts(resolvedUserId, authToken, offset = 0, limit = POSTS_PAGE_SIZE)
        }
        val presence = runCatching {
            apiGetPresence(resolvedUserId, authToken)
        }.getOrNull()
        val posts = postsResult.getOrDefault(emptyList())

        return ProfilePayload(
            id = resolvedUserId,
            shortUsername = profile.username,
            avatarUrl = profile.avatar.toAbsUrl(),
            backgroundUrl = profile.background.toAbsUrl(),
            displayName = profile.name?.takeIf(String::isNotBlank),
            description = profile.bio?.takeIf(String::isNotBlank),
            totalLikesReceived = profile.totalLikesReceived,
            isSelf = targetUserId == null,
            isOnline = presence?.isOnline == true,
            lastSeenAtMillis = presence?.lastSeenAt,
            posts = posts,
            arePostsError = postsResult.isFailure,
            canLoadMorePosts = posts.size >= POSTS_PAGE_SIZE,
        )
    }

    override suspend fun getMorePosts(
        userId: String?,
        offset: Int,
        limit: Int,
    ): List<ProfilePost> {
        val authToken = requireNotNull(
            flowAuthReadLocalStorage(SESSION_TOKEN_KEY)?.takeIf(String::isNotBlank)
        ) { "Missing session token" }
        val resolvedUserId = userId?.takeIf { it.isNotBlank() && it != "self" } ?: "me"
        return apiGetPosts(resolvedUserId, authToken, offset = offset, limit = limit)
    }

    override suspend fun deletePost(postId: String): Result<Unit> {
        val authToken = requireNotNull(
            flowAuthReadLocalStorage(SESSION_TOKEN_KEY)?.takeIf(String::isNotBlank)
        ) { "Missing session token" }
        return runCatching {
            apiRequest(
                method = "DELETE",
                path = "/posts/$postId",
                authToken = authToken,
            )
            Unit
        }
    }

    private suspend fun apiGetPosts(
        userId: String,
        authToken: String,
        offset: Int,
        limit: Int,
    ): List<ProfilePost> {
        val payload = apiGet<String>(
            path = "/users/$userId/posts?limit=$limit&offset=$offset",
            authToken = authToken,
            serializer = { raw -> raw }
        )
        val posts = runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(ApiPostDto.serializer()),
                payload
            )
        }.getOrNull() ?: runCatching {
            json.decodeFromString(ApiPostsListResponse.serializer(), payload).posts
        }.getOrDefault(emptyList())
        return posts.map { it.toProfilePost() }
    }

    private suspend fun apiGetPresence(
        userId: String,
        authToken: String,
    ): PresenceItemDto? {
        val response = apiGet<PresenceResponse>(
            path = "/presence?user_ids=$userId",
            authToken = authToken
        )
        return response.items.firstOrNull()
    }

    private suspend fun <T> apiGet(
        path: String,
        authToken: String,
        serializer: (String) -> T,
    ): T {
        val body = suspendCancellableCoroutine<String> { continuation ->
            flowProfileApiGet(
                path = path,
                apiUrl = FLOW_API_URL,
                authToken = authToken,
                onSuccess = { raw ->
                    if (continuation.isActive) continuation.resume(raw)
                },
                onError = { message ->
                    if (continuation.isActive) continuation.resume("""{"error":"$message"}""")
                }
            )
        }
        val errorMessage = runCatching {
            json.decodeFromString(ErrorResponse.serializer(), body).error
        }.getOrNull()
        if (!errorMessage.isNullOrBlank()) {
            throw IllegalStateException("$path -> $errorMessage")
        }
        return serializer(body)
    }

    private suspend inline fun <reified T> apiGet(
        path: String,
        authToken: String,
    ): T = apiGet(path, authToken) { raw ->
        json.decodeFromString(raw)
    }

    private suspend fun apiRequest(
        method: String,
        path: String,
        authToken: String?,
        contentType: String? = null,
        body: String? = null,
    ): String {
        return suspendCancellableCoroutine<String> { continuation ->
            flowWasmApiRequest(
                method = method,
                path = path,
                apiUrl = FLOW_API_URL,
                authToken = authToken,
                contentType = contentType,
                body = body,
                onSuccess = { continuation.resume(it) },
                onError = { message ->
                    continuation.resume("""{"error":"$message"}""")
                }
            )
        }.also { raw ->
            val error = runCatching { json.decodeFromString(ErrorResponse.serializer(), raw).error }.getOrNull()
            if (!error.isNullOrBlank()) error(error)
        }
    }

    private fun ApiPostDto.toProfilePost(): ProfilePost {
        return ProfilePost(
            id = id,
            description = content.description,
            imageVariants = content.imageVariants.map { variant ->
                ProfileImageVariant(
                    lqUrl = variant.lqUrl.toAbsUrl(),
                    previewUrl = variant.previewUrl.toAbsUrl(),
                    fullUrl = variant.fullUrl.toAbsUrl(),
                )
            },
            imageUrls = content.imageUrls.mapNotNull { it.toAbsUrl() },
            likesCount = likesCount,
            commentsCount = commentsCount,
            createdAtMillis = 0L,
        )
    }

    private fun String?.toAbsUrl(): String? {
        if (this == null || this.isBlank()) return null
        if (startsWith("http")) return this
        val baseUrl = "https://45.66.228.158.nip.io"
        return if (startsWith("/")) "$baseUrl$this" else "$baseUrl/$this"
    }

    companion object {
        const val FLOW_API_URL = "https://45.66.228.158.nip.io/api/v1"
        const val SESSION_TOKEN_KEY = "flow.auth.session_token"
        const val POSTS_PAGE_SIZE = 20
    }
}

@Serializable
private data class ProfileResponse(
    val id: String,
    val username: String? = null,
    val name: String? = null,
    @SerialName("avatar")
    val avatar: String? = null,
    @SerialName("background")
    val background: String? = null,
    @SerialName("bio")
    val bio: String? = null,
    @SerialName("total_likes_received")
    val totalLikesReceived: Int = 0,
)

@Serializable
private data class PresenceResponse(
    val items: List<PresenceItemDto> = emptyList(),
)

@Serializable
private data class PresenceItemDto(
    @SerialName("user_id")
    val userId: Long,
    @SerialName("is_online")
    val isOnline: Boolean,
    @SerialName("last_seen_at")
    val lastSeenAt: Long? = null,
)

@Serializable
private data class ApiPostDto(
    val id: String,
    val content: ApiPostContentDto,
    @SerialName("likes_count")
    val likesCount: Int = 0,
    @SerialName("comments_count")
    val commentsCount: Int = 0,
)

@Serializable
private data class ApiPostsListResponse(
    val posts: List<ApiPostDto> = emptyList(),
)

@Serializable
private data class ApiPostContentDto(
    @SerialName("image_urls")
    val imageUrls: List<String> = emptyList(),
    @SerialName("image_variants")
    val imageVariants: List<ApiImageVariantDto> = emptyList(),
    val description: String? = null,
)

@Serializable
private data class ApiImageVariantDto(
    @SerialName("lq_url")
    val lqUrl: String? = null,
    @SerialName("preview_url")
    val previewUrl: String? = null,
    @SerialName("full_url")
    val fullUrl: String? = null,
)

@Serializable
private data class ErrorResponse(
    val error: String? = null,
)
