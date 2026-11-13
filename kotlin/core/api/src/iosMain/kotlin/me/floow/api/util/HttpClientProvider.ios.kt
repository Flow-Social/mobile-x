package me.floow.api.util

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

actual fun platformHttpClientEngineFactory(): HttpClientEngineFactory<*> = Darwin
