package me.floow.api.util

import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AuthRefreshConfig {
    var refreshToken: (suspend () -> Boolean)? = null
    var onRefreshFailed: (suspend () -> Unit)? = null
}

val AuthRefreshPlugin = createClientPlugin("AuthRefreshPlugin", ::AuthRefreshConfig) {
    val mutex = Mutex()
    val refreshAction = pluginConfig.refreshToken
    val onFailed = pluginConfig.onRefreshFailed

    on(Send) { request ->
        val originalCall = proceed(request)
        if (originalCall.response.status == HttpStatusCode.Unauthorized) {
            val hasAuthHeader = request.headers.contains("X-Authorization-Token") ||
                request.headers.contains(HttpHeaders.Authorization)
            if (!hasAuthHeader) {
                return@on originalCall
            }

            val refreshed = mutex.withLock {
                refreshAction?.invoke() ?: false
            }
            if (refreshed) {
                proceed(request)
            } else {
                onFailed?.invoke()
                originalCall
            }
        } else {
            originalCall
        }
    }
}
