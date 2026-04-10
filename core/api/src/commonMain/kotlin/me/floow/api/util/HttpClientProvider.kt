package me.floow.api.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.io.IOException

expect fun platformHttpClientEngineFactory(): HttpClientEngineFactory<*>

class HttpClientProvider {
    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val REQUEST_TIMEOUT_MS = 20_000L
        const val SOCKET_TIMEOUT_MS = 20_000L
    }

    private val lazyClient: HttpClient by lazy {
        createClient(enableHttpCache = true)
    }

    private val lazyClientWithoutHttpCache: HttpClient by lazy {
        createClient(enableHttpCache = false)
    }

    private fun createClient(enableHttpCache: Boolean): HttpClient {
        return HttpClient(platformHttpClientEngineFactory()) {
            expectSuccess = false

            install(HttpTimeout) {
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                socketTimeoutMillis = SOCKET_TIMEOUT_MS
            }

            install(HttpRequestRetry) {
                maxRetries = 2
                retryOnExceptionIf { request, throwable ->
                    request.method == HttpMethod.Get &&
                        (
                            throwable is TimeoutCancellationException ||
                                throwable is IOException
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
