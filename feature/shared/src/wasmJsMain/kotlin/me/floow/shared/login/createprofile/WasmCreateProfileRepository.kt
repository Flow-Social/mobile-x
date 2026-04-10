package me.floow.shared.login.createprofile

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.floow.domain.api.models.EditProfileData
import me.floow.domain.auth.AuthenticationManager
import me.floow.shared.chats.uilogic.encodeURIComponent
import me.floow.shared.login.auth.flowAuthWriteLocalStorage
import me.floow.shared.login.uilogic.createprofile.CreateProfileRepository
import me.floow.shared.login.uilogic.createprofile.CreateProfileSubmissionResult
import me.floow.shared.profile.auth.flowWasmApiRequest
import kotlin.coroutines.resume

class WasmCreateProfileRepository(
    private val authenticationManager: AuthenticationManager,
) : CreateProfileRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun submitProfile(data: EditProfileData): CreateProfileSubmissionResult {
        val pendingToken = authenticationManager.getPendingRegistrationTokenOrNull()
            ?: return CreateProfileSubmissionResult.Failure
        val payload = apiRequest(
            path = "/auth/google/complete",
            body = buildFormBody(
                "pending_token" to pendingToken,
                "name" to data.name.value,
                "username" to data.username.value,
                "bio" to data.description.value,
                "avatar" to "",
            ),
        )

        if (payload.contains("USERNAME_ALREADY_EXISTS")) {
            return CreateProfileSubmissionResult.UsernameAlreadyExists
        }

        val response = runCatching {
            json.decodeFromString(WasmCompleteRegistrationResponse.serializer(), payload)
        }.getOrNull() ?: return CreateProfileSubmissionResult.Failure
        val token = response.token?.takeIf(String::isNotBlank)
            ?: return CreateProfileSubmissionResult.Failure

        response.user?.id?.takeIf(String::isNotBlank)?.let(authenticationManager::saveSelfUserId)
        authenticationManager.writeAuthToken(token)
        clearPendingInitialData()
        return CreateProfileSubmissionResult.Success
    }

    private suspend fun apiRequest(
        path: String,
        body: String,
    ): String {
        return suspendCancellableCoroutine { continuation ->
            flowWasmApiRequest(
                method = "POST",
                path = path,
                apiUrl = FLOW_API_URL,
                authToken = null,
                contentType = FORM_URLENCODED,
                body = body,
                onSuccess = { continuation.resume(it) },
                onError = { message -> continuation.resume(message) },
            )
        }
    }

    private fun buildFormBody(vararg entries: Pair<String, String>): String {
        return entries.joinToString("&") { (key, value) ->
            "${encodeURIComponent(key)}=${encodeURIComponent(value)}"
        }
    }

    private fun clearPendingInitialData() {
        flowAuthWriteLocalStorage(PENDING_NAME_KEY, "")
        flowAuthWriteLocalStorage(PENDING_USERNAME_KEY, "")
        flowAuthWriteLocalStorage(PENDING_DESCRIPTION_KEY, "")
    }

    private companion object {
        const val FLOW_API_URL = "https://45.66.228.158.nip.io/api/v1"
        const val FORM_URLENCODED = "application/x-www-form-urlencoded"
        const val PENDING_NAME_KEY = "flow.auth.pending.name"
        const val PENDING_USERNAME_KEY = "flow.auth.pending.username"
        const val PENDING_DESCRIPTION_KEY = "flow.auth.pending.description"
    }
}

@Serializable
private data class WasmCompleteRegistrationResponse(
    val token: String? = null,
    val user: WasmCompleteRegistrationUser? = null,
)

@Serializable
private data class WasmCompleteRegistrationUser(
    val id: String? = null,
)
