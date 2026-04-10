package me.floow.uikit.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

actual fun formatLastSeen(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return ""
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}
