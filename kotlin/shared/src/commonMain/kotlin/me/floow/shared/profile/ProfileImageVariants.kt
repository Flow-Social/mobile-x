package me.floow.shared.profile

fun resolveProfileListUrls(rawUrl: String?): Pair<String?, String?> =
    null to rawUrl?.trim()?.takeIf(String::isNotEmpty)
