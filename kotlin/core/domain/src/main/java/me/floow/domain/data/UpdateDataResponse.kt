package me.floow.domain.data

import me.floow.domain.models.Post

enum class FailureError {
	Other,
	UsernameAlreadyExists
}

sealed interface UpdateDataResponse {
	data class Success(
		val post: Post? = null
	) : UpdateDataResponse

	data class Failure(
		val failureError: FailureError = FailureError.Other,
		val message: String? = null
	) : UpdateDataResponse
}
