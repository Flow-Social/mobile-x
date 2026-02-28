package me.floow.domain.data.repos

import me.floow.domain.data.GetDataResponse

data class UploadImageData(
	val fileName: String,
	val contentType: String,
	val sizeBytes: Long,
	val bytes: ByteArray
)

interface UploadsRepository {
	suspend fun uploadImages(
		data: List<UploadImageData>,
		kind: String = "post",
		onProgress: ((uploaded: Int, total: Int) -> Unit)? = null
	): GetDataResponse<List<String>>
}
