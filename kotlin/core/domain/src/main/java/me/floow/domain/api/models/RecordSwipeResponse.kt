package me.floow.domain.api.models

sealed interface RecordSwipeResponse {
	data object Success : RecordSwipeResponse
	data object Error : RecordSwipeResponse
}

