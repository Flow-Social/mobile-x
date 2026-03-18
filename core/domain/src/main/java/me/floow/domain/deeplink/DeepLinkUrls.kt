package me.floow.domain.deeplink

object DeepLinkUrls {
    const val BASE_URL = "https://flow-social.github.io"
    const val LEGACY_BASE_URL = "https://floow.me"
    val SUPPORTED_BASE_URLS = listOf(BASE_URL, LEGACY_BASE_URL)

    fun profileUrl(username: String): String = "$BASE_URL/$username"

    fun postUrl(postId: String, username: String?): String {
        val safeUser = username.orEmpty()
        return "$BASE_URL/$safeUser/$postId"
    }

    fun isSupportedWebUrl(url: String): Boolean {
        return SUPPORTED_BASE_URLS.any(url::startsWith)
    }
}
