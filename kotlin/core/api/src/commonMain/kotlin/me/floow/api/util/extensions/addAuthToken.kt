package me.floow.api.util.extensions

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header

fun HttpRequestBuilder.addAuthTokenHeader(authToken: String) {
	header("X-Authorization-Token", "$authToken")
}
