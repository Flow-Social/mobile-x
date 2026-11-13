package me.floow.domain.data

sealed interface GetDataResponse<T> {
	data class Success<T>(val data: T) : GetDataResponse<T>

	data class Error<T>(val error: GetDataError) : GetDataResponse<T>
}