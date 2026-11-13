package me.floow.api.config

/**
 * Backward compatibility alias for [AppBuildConfig].
 */
@Deprecated("Use AppBuildConfig instead", ReplaceWith("AppBuildConfig"))
object AppConfig {
    val BASE_URL: String get() = AppBuildConfig.BASE_URL
}
