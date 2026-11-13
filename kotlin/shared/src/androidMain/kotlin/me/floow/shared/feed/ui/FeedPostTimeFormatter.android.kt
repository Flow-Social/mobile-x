package me.floow.shared.feed.ui

import me.floow.uikit.util.FlowTimeFormatters

internal actual fun formatFeedPostTime(
    timestamp: Long,
    nowMillis: Long,
): String = FlowTimeFormatters.formatRelativeTime(timestamp, nowMillis)

internal actual fun currentFeedEpochMillis(): Long = System.currentTimeMillis()
