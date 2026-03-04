package me.floow.api.util

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.io.IOException

class HttpClientProvider {
	private companion object {
		private const val CONNECT_TIMEOUT_MS = 10_000L
		private const val REQUEST_TIMEOUT_MS = 20_000L
		private const val SOCKET_TIMEOUT_MS = 20_000L
	}

	private val lazyClient: HttpClient by lazy {
		createClient(enableHttpCache = true)
	}

	private val lazyClientWithoutHttpCache: HttpClient by lazy {
		createClient(enableHttpCache = false)
	}

	private fun createClient(enableHttpCache: Boolean): HttpClient {
		return HttpClient(CIO) {
			expectSuccess = false

				install(HttpTimeout) {
					connectTimeoutMillis = CONNECT_TIMEOUT_MS
					requestTimeoutMillis = REQUEST_TIMEOUT_MS
					socketTimeoutMillis = SOCKET_TIMEOUT_MS
				}

				if (enableHttpCache) {
					install(HttpCache)
				}

				install(HttpRequestRetry) {
					maxRetries = 2
					retryOnExceptionIf { request, throwable ->
						request.method == HttpMethod.Get &&
							(
								throwable is TimeoutCancellationException ||
									throwable is IOException ||
									throwable is InvalidCacheStateException
								)
					}
				retryIf { request, response ->
					request.method == HttpMethod.Get && response.status.value in 500..599
				}
				exponentialDelay(base = 2.0, baseDelayMs = 400, maxDelayMs = 2_000)
			}

			install(ContentNegotiation) {
				json(JsonSerializer)
			}

			install(WebSockets) {
				pingIntervalMillis = 20_000L
			}
		}
	}

	fun getClient(): HttpClient {
		return lazyClient
	}

	fun getClientWithoutHttpCache(): HttpClient {
		return lazyClientWithoutHttpCache
	}
}
