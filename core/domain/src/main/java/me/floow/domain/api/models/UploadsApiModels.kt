package me.floow.domain.api.models

data class CreateUploadPresignData(
	val fileName: String,
	val contentType: String,
	val sizeBytes: Long,
	val kind: String = "post"
)

sealed interface CreateUploadPresignResponse {
	data class Success(
		val uploadUrl: String,
		val fileUrl: String,
		val objectKey: String,
		val expiresInSeconds: Long,
		val maxSizeBytes: Long
	) : CreateUploadPresignResponse

	data object Error : CreateUploadPresignResponse
}
