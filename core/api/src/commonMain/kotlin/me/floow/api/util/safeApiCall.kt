package me.floow.api.util

import io.ktor.client.plugins.cache.InvalidCacheStateException
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.io.EOFException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

suspend fun <T> safeApiCall(errorResponse: T, apiCall: suspend () -> T): T {
	return try {
		apiCall()
	} catch (ex: UnresolvedAddressException) {
		errorResponse
	} catch (ex: TimeoutCancellationException) {
		errorResponse
	} catch (ex: EOFException) {
		errorResponse
	} catch (ex: IOException) {
		errorResponse
	} catch (ex: SerializationException) {
		errorResponse
	} catch (ex: InvalidCacheStateException) {
		errorResponse
	} catch (ex: Throwable) {
		if (ex.isConnectFailure()) {
			errorResponse
		} else {
			throw ex
		}
	}
}

private fun Throwable.isConnectFailure(): Boolean {
	return generateSequence(this) { it.cause }
		.any { throwable ->
			throwable::class.qualifiedName == "java.net.ConnectException" ||
				throwable::class.simpleName == "ConnectException"
		}
}
