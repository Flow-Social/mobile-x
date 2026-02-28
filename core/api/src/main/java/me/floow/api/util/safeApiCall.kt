package me.floow.api.util

import io.ktor.client.plugins.cache.InvalidCacheStateException
import io.ktor.util.network.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.SerializationException
import kotlinx.io.EOFException
import kotlinx.io.IOException

import java.net.ConnectException

suspend fun <T> safeApiCall(errorResponse: T, apiCall: suspend () -> T): T {
	return try {
		apiCall()
	} catch (ex: UnresolvedAddressException) {
		errorResponse
	} catch (ex: TimeoutCancellationException) {
		errorResponse
	} catch (ex: EOFException) {
		errorResponse
	} catch (ex: ConnectException) {
		errorResponse
	} catch (ex: IOException) {
		errorResponse
	} catch (ex: SerializationException) {
		errorResponse
	} catch (ex: InvalidCacheStateException) {
		errorResponse
	}
}
