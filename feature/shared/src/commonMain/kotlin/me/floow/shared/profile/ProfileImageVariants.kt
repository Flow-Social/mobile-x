package me.floow.shared.profile

fun resolveProfileListUrls(rawUrl: String?): Pair<String?, String?> {
    val normalized = rawUrl?.trim().orEmpty()
    if (normalized.isBlank()) return null to null

    val (withoutFragment, fragment) = normalized.splitAtFirst('#')
    val (pathWithHost, query) = withoutFragment.splitAtFirst('?')

    val stem = when {
        pathWithHost.endsWith("_full.jpg", ignoreCase = true) -> {
            pathWithHost.dropLast("_full.jpg".length)
        }
        pathWithHost.endsWith("_preview.jpg", ignoreCase = true) -> {
            pathWithHost.dropLast("_preview.jpg".length)
        }
        pathWithHost.endsWith("_lq.jpg", ignoreCase = true) -> {
            pathWithHost.dropLast("_lq.jpg".length)
        }
        else -> {
            return null to normalized
        }
    }

    val lqUrl = rebuildUrl(stem = stem, suffix = "_lq.jpg", query = query, fragment = fragment)
    val previewUrl = rebuildUrl(stem = stem, suffix = "_preview.jpg", query = query, fragment = fragment)
    return lqUrl to previewUrl
}

private fun rebuildUrl(
    stem: String,
    suffix: String,
    query: String?,
    fragment: String?,
): String {
    val queryPart = query?.let { "?$it" }.orEmpty()
    val fragmentPart = fragment?.let { "#$it" }.orEmpty()
    return "$stem$suffix$queryPart$fragmentPart"
}

private fun String.splitAtFirst(delimiter: Char): Pair<String, String?> {
    val index = indexOf(delimiter)
    if (index < 0) return this to null
    return substring(0, index) to substring(index + 1)
}
