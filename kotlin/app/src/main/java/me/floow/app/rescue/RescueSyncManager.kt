package me.floow.app.rescue

import android.content.Context
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.floow.api.util.ApiConfig
import me.floow.database.AppDatabase
import me.floow.domain.utils.Logger

@Serializable
data class RescueProfilePayload(
	@SerialName("user_id") val userId: String,
	@SerialName("email") val email: String = "",
	@SerialName("username") val username: String? = null,
	@SerialName("name") val name: String? = null,
	@SerialName("avatar_url") val avatarUrl: String? = null,
	@SerialName("background_url") val backgroundUrl: String? = null,
	@SerialName("bio") val bio: String? = null,
	@SerialName("total_likes_received") val totalLikesReceived: Long = 0
)

@Serializable
data class RescuePostPayload(
	@SerialName("post_id") val postId: String,
	@SerialName("author_id") val authorId: String,
	@SerialName("author_name") val authorName: String? = null,
	@SerialName("author_username") val authorUsername: String? = null,
	@SerialName("author_avatar_url") val authorAvatarUrl: String? = null,
	@SerialName("description") val description: String? = null,
	@SerialName("category") val category: String,
	@SerialName("image_urls") val imageUrls: List<String> = emptyList(),
	@SerialName("created_at") val createdAt: Long,
	@SerialName("likes_count") val likesCount: Int = 0,
	@SerialName("comments_count") val commentsCount: Int = 0
)

@Serializable
data class RescueSyncRequest(
	@SerialName("old_token") val oldToken: String = "",
	@SerialName("profile") val profile: RescueProfilePayload? = null,
	@SerialName("posts") val posts: List<RescuePostPayload> = emptyList()
)

class RescueSyncManager(
	private val context: Context,
	private val database: AppDatabase,
	private val httpClient: HttpClient,
	private val apiConfig: ApiConfig,
	private val logger: Logger
) {
	companion object {
		private const val RESCUE_PREFS = "flowme.rescue_sync"
		private const val KEY_RESCUE_COMPLETED = "is_synced_v2"
		private const val KEY_MIGRATION_COMPLETED = "is_migration_completed_v2"
		private const val AUTH_PREFS = "flowme.auth"
		private const val AUTH_TOKEN_KEY = "authToken"
		private const val AUTH_USER_ID_KEY = "authUserId"
	}

	private val json = Json { ignoreUnknownKeys = true }

	fun isMigrationNeeded(): Boolean {
		val rescuePrefs = context.getSharedPreferences(RESCUE_PREFS, Context.MODE_PRIVATE)
		if (rescuePrefs.getBoolean(KEY_MIGRATION_COMPLETED, false)) {
			return false
		}
		if (rescuePrefs.getBoolean(KEY_RESCUE_COMPLETED, false)) {
			// Rescue sync is already completed, no migration prompt needed
			rescuePrefs.edit().putBoolean(KEY_MIGRATION_COMPLETED, true).apply()
			return false
		}
		return true
	}

	fun markMigrationCompleted() {
		val rescuePrefs = context.getSharedPreferences(RESCUE_PREFS, Context.MODE_PRIVATE)
		rescuePrefs.edit().putBoolean(KEY_MIGRATION_COMPLETED, true).apply()
	}

	suspend fun syncIfNeeded(): Boolean = withContext(Dispatchers.IO) {
		val rescuePrefs = context.getSharedPreferences(RESCUE_PREFS, Context.MODE_PRIVATE)
		if (rescuePrefs.getBoolean(KEY_RESCUE_COMPLETED, false)) {
			return@withContext true
		}

		try {
			val authPrefs = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
			val oldToken = authPrefs.getString(AUTH_TOKEN_KEY, "") ?: ""
			val authUserId = authPrefs.getString(AUTH_USER_ID_KEY, "") ?: ""

			val profileEntity = if (authUserId.isNotBlank()) {
				database.profileDao().getProfile(authUserId)
			} else {
				database.profileDao().getAnyProfile()
			}

			val currentUserId = authUserId.ifBlank { profileEntity?.userId.orEmpty() }
			val postsEntities = database.postsDao().getAllPosts()
			val userPosts = if (currentUserId.isNotBlank()) {
				postsEntities.filter { it.authorId == currentUserId }
			} else {
				emptyList()
			}

			if (profileEntity == null && userPosts.isEmpty() && oldToken.isBlank()) {
				rescuePrefs.edit()
					.putBoolean(KEY_RESCUE_COMPLETED, true)
					.putBoolean(KEY_MIGRATION_COMPLETED, true)
					.apply()
				return@withContext true
			}

			val profilePayload = profileEntity?.let {
				RescueProfilePayload(
					userId = it.userId,
					username = it.username,
					name = it.name,
					avatarUrl = it.avatarUrl,
					backgroundUrl = it.backgroundUrl,
					bio = it.description,
					totalLikesReceived = it.totalLikesReceived.toLong()
				)
			} ?: if (authUserId.isNotBlank()) {
				RescueProfilePayload(userId = authUserId)
			} else null

			val postsPayload = userPosts.map { post ->
				RescuePostPayload(
					postId = post.postId,
					authorId = post.authorId,
					authorName = post.authorName,
					authorUsername = post.authorUsername,
					authorAvatarUrl = post.authorAvatarUrl,
					description = post.description,
					category = post.category,
					imageUrls = post.imageUrls,
					createdAt = post.createdAt,
					likesCount = post.likesCount,
					commentsCount = post.commentsCount
				)
			}

			val request = RescueSyncRequest(
				oldToken = oldToken,
				profile = profilePayload,
				posts = postsPayload
			)

			val url = "${apiConfig.apiUrl.trimEnd('/')}/rescue/sync"
			val response = httpClient.post(url) {
				contentType(ContentType.Application.Json)
				setBody(json.encodeToString(request))
			}

			if (response.status.isSuccess()) {
				logger.d("RescueSyncManager", "Successfully synced ${postsPayload.size} posts and profile to new server")
				rescuePrefs.edit()
					.putBoolean(KEY_RESCUE_COMPLETED, true)
					.putBoolean(KEY_MIGRATION_COMPLETED, true)
					.apply()
				return@withContext true
			} else {
				logger.d("RescueSyncManager", "Failed with status ${response.status}")
				return@withContext false
			}
		} catch (e: Throwable) {
			logger.d("RescueSyncManager", "Rescue sync error: ${e.message}")
			return@withContext false
		}
	}
}
