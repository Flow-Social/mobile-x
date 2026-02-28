package me.floow.profile.ui.profile

import android.net.Uri

internal fun resolveProfileListUrls(rawUrl: String?): Pair<String?, String?> {
    val normalized = rawUrl?.trim().orEmpty()
    if (normalized.isBlank()) return null to null

    val uri = Uri.parse(normalized)
    val path = uri.path.orEmpty()
    val suffix = "_full.jpg"
    if (!path.endsWith(suffix, ignoreCase = true)) {
        return null to normalized
    }

    val stemPath = path.dropLast(suffix.length)
    val lqUrl = uri.buildUpon().path("${stemPath}_lq.jpg").build().toString()
    val previewUrl = uri.buildUpon().path("${stemPath}_preview.jpg").build().toString()
    return lqUrl to previewUrl
}
