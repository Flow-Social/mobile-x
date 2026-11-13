package me.floow.shared.config

import me.floow.api.config.AppBuildConfig

/**
 * Backward compatibility alias for [AppBuildConfig].
 */
@Deprecated("Use me.floow.api.config.AppBuildConfig instead", ReplaceWith("AppBuildConfig", "me.floow.api.config.AppBuildConfig"))
object AppConfig {
    val BASE_URL: String get() = AppBuildConfig.BASE_URL
}
