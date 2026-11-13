package me.floow.api.util

import me.floow.api.config.AppConfig

data class ApiConfig(
    val apiUrl: String = AppConfig.BASE_URL
)
