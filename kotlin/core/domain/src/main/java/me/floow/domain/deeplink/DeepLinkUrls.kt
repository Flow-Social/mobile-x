package me.floow.domain.deeplink

object DeepLinkUrls {
    const val BASE_URL = "https://flow-social.github.io"
    const val LEGACY_BASE_URL = "https://floow.me"
    val SUPPORTED_BASE_URLS = listOf(BASE_URL, LEGACY_BASE_URL)

    fun profileUrl(username: String): String = "$BASE_URL/$username"

    /**
     * Returns an Android Intent URL that opens the Flow app directly when scanned from a
     * QR reader (e.g. the default Camera app), instead of going through a browser. If the
     * app is not installed, falls back to opening the regular https profile URL.
     */
    fun profileQrIntentUrl(username: String, packageName: String = "me.floow.app"): String {
        val webUrl = profileUrl(username)
        val fallbackEncoded = encodeIntentParam(webUrl)
        val pathAndHost = webUrl.removePrefix("https://")
        return "intent://$pathAndHost#Intent;scheme=https;package=$packageName;" +
            "S.browser_fallback_url=$fallbackEncoded;end"
    }

    fun postUrl(postId: String, username: String?): String {
        val safeUser = username.orEmpty()
        return "$BASE_URL/$safeUser/$postId"
    }

    private fun encodeIntentParam(value: String): String {
        // Minimal URL-encoder for Intent URL params (supports ASCII characters used in profile URLs).
        val sb = StringBuilder(value.length)
        for (ch in value) {
            when {
                ch.isLetterOrDigit() || ch in "-._~" -> sb.append(ch)
                else -> {
                    for (b in ch.toString().encodeToByteArray()) {
                        sb.append('%')
                        sb.append("0123456789ABCDEF"[(b.toInt() shr 4) and 0xF])
                        sb.append("0123456789ABCDEF"[b.toInt() and 0xF])
                    }
                }
            }
        }
        return sb.toString()
    }

    fun isSupportedWebUrl(url: String): Boolean {
        return SUPPORTED_BASE_URLS.any(url::startsWith)
    }
}
