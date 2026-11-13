package me.floow.uikit.util

import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import kotlin.math.max

private val timeFormatter = NSDateFormatter().apply { dateFormat = "HH:mm" }
private val monthDayFormatter = NSDateFormatter().apply { dateFormat = "d MMMM" }

internal actual fun currentPresenceStatusEpochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

internal actual fun platformFormatPresenceStatusLabel(
    isOnline: Boolean,
    lastSeenAtMillis: Long?,
    nowMillis: Long,
): String {
    if (isOnline) return "В сети"
    val normalizedLastSeenAt = lastSeenAtMillis?.takeIf { it > 0L } ?: return "Оффлайн"

    val calendar = NSCalendar.currentCalendar
    val nowDate = NSDate.dateWithTimeIntervalSince1970(nowMillis / 1000.0)
    val seenDate = NSDate.dateWithTimeIntervalSince1970(normalizedLastSeenAt / 1000.0)

    val nowStart = calendar.startOfDayForDate(nowDate)
    val seenStart = calendar.startOfDayForDate(seenDate)
    val seenTime = timeFormatter.stringFromDate(seenDate)

    val diffSeconds = nowStart.timeIntervalSince1970 - seenStart.timeIntervalSince1970
    val daysDiff = max(0L, (diffSeconds / 86400.0).toLong())

    return when {
        daysDiff == 0L -> "Был(а) в $seenTime"
        daysDiff == 1L -> "Был(а) вчера в $seenTime"
        daysDiff in 2L..6L -> "Был(а) на этой неделе"
        daysDiff in 7L..30L -> "Был(а) в этом месяце"
        else -> {
            val dateLabel = monthDayFormatter.stringFromDate(seenDate)
            "Был(а) $dateLabel в $seenTime"
        }
    }
}
