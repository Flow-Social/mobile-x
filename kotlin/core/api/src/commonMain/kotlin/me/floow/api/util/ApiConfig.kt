package me.floow.api.util

import me.floow.api.config.AppBuildConfig

data class ApiConfig(
    val apiUrl: String = AppBuildConfig.BASE_URL
)
