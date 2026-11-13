package me.floow.shared.profile

fun resolveProfileListUrls(rawUrl: String?): Pair<String?, String?> {
    val normalized = rawUrl?.trim().orEmpty()
    if (normalized.isBlank()) return null to null
    return null to normalized
}
