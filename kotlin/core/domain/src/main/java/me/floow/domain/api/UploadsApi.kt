package me.floow.domain.api

import me.floow.domain.api.models.CreateUploadPresignData
import me.floow.domain.api.models.CreateUploadPresignResponse

interface UploadsApi {
	suspend fun createPresign(data: CreateUploadPresignData): CreateUploadPresignResponse

	suspend fun uploadFile(
		uploadUrl: String,
		contentType: String,
		bytes: ByteArray,
		onProgress: ((Float) -> Unit)? = null,
	): Boolean

	suspend fun uploadFilePath(
		uploadUrl: String,
		contentType: String,
		filePath: String,
		onProgress: ((Float) -> Unit)? = null,
	): Boolean
}
