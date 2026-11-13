package me.floow.shared.feed.ui

internal expect fun formatFeedPostTime(
    timestamp: Long,
    nowMillis: Long = currentFeedEpochMillis(),
): String

internal expect fun currentFeedEpochMillis(): Long
