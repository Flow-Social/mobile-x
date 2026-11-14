package me.floow.database.localstore

/**
 * Resolves the merged micro-thumbnail for media items (avatars, profile backgrounds, post media)
 * when updating locally cached Room entities from incoming server DTOs:
 *
 * 1. If [newUrl] is null or blank -> returns null (user deleted the media, preview must be cleared).
 * 2. If [newThumb] is not null or blank -> returns [newThumb] (fresh preview arrived from server).
 * 3. If [newUrl] == [cachedUrl] -> returns [cachedThumb] (partial DTO for the same image without thumb field, retain cache).
 * 4. Otherwise -> returns null (image changed to a new URL without preview, old preview cannot be shown).
 */
fun resolveMergedThumb(
    newUrl: String?,
    newThumb: String?,
    cachedUrl: String?,
    cachedThumb: String?
): String? {
    if (newUrl.isNullOrBlank()) return null
    if (!newThumb.isNullOrBlank()) return newThumb
    if (newUrl == cachedUrl) return cachedThumb
    return null
}
