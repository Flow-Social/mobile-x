package me.floow.data.repos

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.floow.domain.api.UploadsApi
import me.floow.domain.api.models.CreateUploadPresignData
import me.floow.domain.api.models.CreateUploadPresignResponse
import me.floow.domain.data.GetDataError
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.UploadImageData
import me.floow.domain.data.repos.UploadsRepository
import me.floow.domain.utils.Logger

class UploadsRepositoryImpl(
	private val logger: Logger,
	private val uploadsApi: UploadsApi
) : UploadsRepository {
	override suspend fun uploadImages(
		data: List<UploadImageData>,
		kind: String,
		onProgress: ((uploaded: Int, total: Int) -> Unit)?
	): GetDataResponse<List<String>> = coroutineScope {
		if (data.isEmpty()) {
			return@coroutineScope GetDataResponse.Success(emptyList())
		}

		val maxParallelUploads = 3
		val semaphore = Semaphore(maxParallelUploads)
		val uploadedCount = AtomicInteger(0)
		val uploadResults = data.mapIndexed { index, item ->
			async {
				semaphore.withPermit {
					val presign = uploadsApi.createPresign(
						CreateUploadPresignData(
							fileName = item.fileName,
							contentType = item.contentType,
							sizeBytes = item.sizeBytes,
							kind = kind,
						)
					)

					if (presign !is CreateUploadPresignResponse.Success) {
						logger.d("UploadsRepositoryImpl.uploadImages", "Presign failed at index=$index")
						return@withPermit UploadResult.Error(index = index)
					}

					val uploaded = uploadsApi.uploadFile(
						uploadUrl = presign.uploadUrl,
						contentType = item.contentType,
						bytes = item.bytes,
					)
					if (!uploaded) {
						logger.d("UploadsRepositoryImpl.uploadImages", "Upload failed at index=$index")
						return@withPermit UploadResult.Error(index = index)
					}

					val completed = uploadedCount.incrementAndGet()
					onProgress?.invoke(completed, data.size)
					UploadResult.Success(index = index, url = presign.fileUrl)
				}
			}
		}

		val resolved = uploadResults.awaitAll()
		val hasError = resolved.any { it is UploadResult.Error }
		if (hasError) {
			return@coroutineScope GetDataResponse.Error(GetDataError.Other)
		}

		val orderedUrls = resolved
			.map { it as UploadResult.Success }
			.sortedBy { it.index }
			.map { it.url }
		return@coroutineScope GetDataResponse.Success(orderedUrls)
	}
}

private sealed interface UploadResult {
	data class Success(val index: Int, val url: String) : UploadResult
	data class Error(val index: Int) : UploadResult
}
