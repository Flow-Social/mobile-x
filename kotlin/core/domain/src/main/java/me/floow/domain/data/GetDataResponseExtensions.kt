package me.floow.domain.data

fun <T> GetDataResponse<T>.orFallback(
	fallbackData: T?,
	fallbackError: GetDataError = GetDataError.Other
): GetDataResponse<T> {
	return when (this) {
		is GetDataResponse.Success -> this
		is GetDataResponse.Error -> {
			if (fallbackData != null) {
				GetDataResponse.Success(fallbackData)
			} else {
				GetDataResponse.Error(fallbackError)
			}
		}
	}
}
