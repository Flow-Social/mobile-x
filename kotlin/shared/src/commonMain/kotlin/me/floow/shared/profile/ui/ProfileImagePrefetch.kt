package me.floow.shared.profile.ui

import me.floow.shared.profile.ui.model.ProfilePostItem

internal fun ProfilePostItem.prefetchUrlsForProfile(): List<String> {
    return buildList {
        lqUrl?.takeIf(String::isNotBlank)?.let(::add)
        previewUrl?.takeIf(String::isNotBlank)?.let(::add)
        fullUrl?.takeIf(String::isNotBlank)?.let(::add)
    }
}
