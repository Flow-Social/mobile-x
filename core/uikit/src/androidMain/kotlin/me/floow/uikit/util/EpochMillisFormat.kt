package me.floow.uikit.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HOUR_MINUTE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun formatEpochMillisToHourMinuteLabel(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    return HOUR_MINUTE_FORMATTER.format(
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    )
}
