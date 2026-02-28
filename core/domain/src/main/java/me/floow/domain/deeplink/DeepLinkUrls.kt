package me.floow.domain.deeplink

object DeepLinkUrls {
    const val BASE_URL = "https://flow-social.github.io"

    fun profileUrl(username: String): String = "$BASE_URL/$username"

    fun postUrl(postId: String, username: String?): String {
        val safeUser = username.orEmpty()
        return "$BASE_URL/$safeUser/$postId"
    }
}
