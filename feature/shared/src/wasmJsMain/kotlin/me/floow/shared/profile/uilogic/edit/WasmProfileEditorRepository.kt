package me.floow.shared.profile.uilogic.edit

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.floow.shared.login.auth.flowAuthReadLocalStorage
import me.floow.shared.profile.auth.WasmProfileRepository.Companion.FLOW_API_URL
import me.floow.shared.profile.auth.flowWasmApiRequest
import kotlin.coroutines.resume

class WasmProfileEditorRepository : ProfileEditorRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun checkUsernameAvailability(username: String): Boolean {
        return runCatching {
            val raw = apiRequest(
                method = "GET",
                path = "/auth/check-username?username=$username",
                authToken = null,
            )
            json.decodeFromString<CheckUsernameResponse>(raw).available
        }.getOrDefault(false)
    }

    override suspend fun updateProfile(
        name: String,
        username: String,
        bio: String,
    ): Result<Unit> {
        val authToken = requireAuthToken()
        return runCatching {
            apiRequest(
                method = "PUT",
                path = "/profile",
                authToken = authToken,
                contentType = "application/json",
                body = json.encodeToString(
                    EditProfileRequest(
                        name = name,
                        username = username,
                        bio = bio,
                        avatar = "",
                    )
                ),
            )
            Unit
        }
    }

    override suspend fun updateProfileMedia(kind: String, mediaUrl: String): Result<Unit> {
        val authToken = requireAuthToken()
        return runCatching {
            apiRequest(
                method = "PATCH",
                path = "/profile/media",
                authToken = authToken,
                contentType = "application/json",
                body = json.encodeToString(
                    UpdateMediaRequest(
                        kind = kind,
                        mediaUrl = mediaUrl,
                    )
                ),
            )
            Unit
        }
    }

    private suspend fun apiRequest(
        method: String,
        path: String,
        authToken: String?,
        contentType: String? = null,
        body: String? = null,
    ): String {
        return suspendCancellableCoroutine { continuation ->
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
            val error = runCatching { json.decodeFromString<ApiErrorResponse>(raw).error }.getOrNull()
            if (!error.isNullOrBlank()) error(error)
        }
    }

    private fun requireAuthToken(): String {
        return requireNotNull(
            flowAuthReadLocalStorage(SESSION_TOKEN_KEY)?.takeIf(String::isNotBlank)
        ) { "Missing session token" }
    }

    private companion object {
        const val SESSION_TOKEN_KEY = "flow.auth.session_token"
    }
}

@Serializable
private data class EditProfileRequest(
    val name: String,
    val username: String,
    val bio: String,
    val avatar: String = "",
)

@Serializable
private data class UpdateMediaRequest(
    val kind: String,
    @SerialName("media_url")
    val mediaUrl: String,
)

@Serializable
private data class CheckUsernameResponse(
    val available: Boolean,
)

@Serializable
private data class ApiErrorResponse(
    val error: String? = null,
)
