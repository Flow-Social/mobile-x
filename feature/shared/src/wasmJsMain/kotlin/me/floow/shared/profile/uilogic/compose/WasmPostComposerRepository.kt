package me.floow.shared.profile.uilogic.compose

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.floow.shared.login.auth.flowAuthReadLocalStorage
import me.floow.shared.profile.auth.WasmProfileRepository.Companion.FLOW_API_URL
import me.floow.shared.profile.auth.btoa
import me.floow.shared.profile.auth.flowWasmApiRequest
import me.floow.shared.profile.auth.flowWasmUploadBase64
import me.floow.shared.profile.image.LocalImageFile
import me.floow.shared.profile.uilogic.ProfileImageVariant
import me.floow.shared.profile.uilogic.ProfilePost
import kotlin.coroutines.resume

class WasmPostComposerRepository : PostComposerRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun loadCategories(): Result<List<PostCategory>> {
        val authToken = requireAuthToken()
        return runCatching {
            val raw = apiRequest(
                method = "GET",
                path = "/categories",
                authToken = authToken,
            )
            json.decodeFromString<List<ApiCategoryItem>>(raw).map { item ->
                PostCategory(
                    code = item.code,
                    title = item.code.toDisplayCategoryTitle(),
                )
            }
        }
    }

    override suspend fun uploadImages(
        files: List<LocalImageFile>,
        kind: UploadKind,
        onProgress: (uploaded: Int, total: Int) -> Unit,
    ): Result<List<String>> {
        val authToken = requireAuthToken()
        return runCatching {
            val uploadedUrls = mutableListOf<String>()
            files.forEachIndexed { index, file ->
                val presignResponse = apiRequest(
                    method = "POST",
                    path = "/uploads/presign",
                    authToken = authToken,
                    contentType = "application/json",
                    body = json.encodeToString(
                        CreateUploadPresignRequest(
                            fileName = file.fileName,
                            contentType = file.contentType,
                            sizeBytes = file.sizeBytes,
                            kind = kind.toBackendKind(),
                        )
                    ),
                )
                val presign = json.decodeFromString<CreateUploadPresignApiResponse>(presignResponse)
                uploadFileToPresignedUrl(
                    uploadUrl = presign.uploadUrl,
                    contentType = file.contentType,
                    bytes = file.bytes,
                )
                uploadedUrls += requireNotNull(presign.fileUrl.toAbsoluteMediaUrl())
                onProgress(index + 1, files.size)
            }
            uploadedUrls
        }
    }

    override suspend fun createPost(
        description: String?,
        imageUrls: List<String>,
        categoryCode: String,
    ): Result<ProfilePost> {
        val authToken = requireAuthToken()
        return runCatching {
            val raw = apiRequest(
                method = "POST",
                path = "/posts",
                authToken = authToken,
                contentType = "application/json",
                body = json.encodeToString(
                    CreatePostRequest(
                        imageUrls = imageUrls,
                        description = description,
                        category = categoryCode,
                    )
                ),
            )
            json.decodeFromString<ApiPostDto>(raw).toProfilePost()
        }
    }

    override suspend fun updatePost(
        postId: String,
        description: String?,
        imageUrls: List<String>,
    ): Result<ProfilePost> {
        val authToken = requireAuthToken()
        return runCatching {
            val raw = apiRequest(
                method = "PUT",
                path = "/posts/$postId",
                authToken = authToken,
                contentType = "application/json",
                body = json.encodeToString(
                    UpdatePostRequest(
                        imageUrls = imageUrls,
                        description = description,
                    )
                ),
            )
            json.decodeFromString<ApiPostDto>(raw).toProfilePost()
        }
    }

    private suspend fun apiRequest(
        method: String,
        path: String,
        authToken: String,
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
        }.let { raw ->
            val error = runCatching { json.decodeFromString<ApiErrorResponse>(raw).error }.getOrNull()
            if (!error.isNullOrBlank()) error(error)
            raw
        }
    }

    private suspend fun uploadFileToPresignedUrl(
        uploadUrl: String,
        contentType: String,
        bytes: ByteArray,
    ) {
        val base64 = bytes.encodeBase64()
        val errorMessage = suspendCancellableCoroutine<String?> { continuation ->
            flowWasmUploadBase64(
                uploadUrl = uploadUrl,
                contentType = contentType,
                base64 = base64,
                onSuccess = { continuation.resume(null) },
                onError = { message -> continuation.resume(message) }
            )
        }
        if (errorMessage != null) error(errorMessage)
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
private data class ApiCategoryItem(
    val code: String,
)

@Serializable
private data class CreatePostRequest(
    @SerialName("image_urls")
    val imageUrls: List<String>,
    val description: String? = null,
    val category: String,
)

@Serializable
private data class UpdatePostRequest(
    @SerialName("image_urls")
    val imageUrls: List<String>,
    val description: String? = null,
)

@Serializable
private data class CreateUploadPresignRequest(
    @SerialName("file_name")
    val fileName: String,
    @SerialName("content_type")
    val contentType: String,
    @SerialName("size_bytes")
    val sizeBytes: Long,
    val kind: String,
)

@Serializable
private data class CreateUploadPresignApiResponse(
    @SerialName("upload_url")
    val uploadUrl: String,
    @SerialName("file_url")
    val fileUrl: String,
)

@Serializable
private data class ApiErrorResponse(
    val error: String? = null,
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

private fun ApiPostDto.toProfilePost(): ProfilePost {
    return ProfilePost(
        id = id,
        description = content.description,
        imageVariants = content.imageVariants.map { variant ->
            ProfileImageVariant(
                lqUrl = variant.lqUrl.toAbsoluteMediaUrl(),
                previewUrl = variant.previewUrl.toAbsoluteMediaUrl(),
                fullUrl = variant.fullUrl.toAbsoluteMediaUrl(),
            )
        },
        imageUrls = content.imageUrls.mapNotNull(String::toAbsoluteMediaUrl),
        likesCount = likesCount,
        commentsCount = commentsCount,
    )
}

private fun UploadKind.toBackendKind(): String {
    return when (this) {
        UploadKind.Post -> "post"
        UploadKind.Avatar -> "avatar"
        UploadKind.Background -> "background"
    }
}

private fun String.toDisplayCategoryTitle(): String {
    return split('_', '-')
        .filter(String::isNotBlank)
        .joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { char -> char.uppercase() }
        }
        .ifBlank { this }
}

private fun ByteArray.encodeBase64(): String {
    val binary = buildString(capacity = size) {
        for (byte in this@encodeBase64) {
            append((byte.toInt() and 0xFF).toChar())
        }
    }
    return btoa(binary)
}

private fun String?.toAbsoluteMediaUrl(): String? {
    if (this == null || isBlank()) return null
    if (startsWith("http")) return this
    val baseUrl = "https://45.66.228.158.nip.io"
    return if (startsWith("/")) "$baseUrl$this" else "$baseUrl/$this"
}
