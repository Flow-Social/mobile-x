package me.floow.mock.data

import kotlinx.coroutines.delay
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.UploadImageData
import me.floow.domain.data.repos.UploadsRepository

class MockUploadsRepository : UploadsRepository {
	override suspend fun uploadImages(
		data: List<UploadImageData>,
		kind: String,
		onProgress: ((uploaded: Int, total: Int) -> Unit)?
	): GetDataResponse<List<String>> {
		delay(150)
		val urls = data.mapIndexed { index, _ ->
			onProgress?.invoke(index + 1, data.size)
			"https://picsum.photos/seed/mock-upload-$kind-$index/800/1200"
		}
		return GetDataResponse.Success(urls)
	}
}
