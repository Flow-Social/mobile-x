package me.floow.shared.feed.ui

import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970
import kotlin.math.max

private val monthDayFormatter = NSDateFormatter().apply { dateFormat = "d MMMM" }

internal actual fun formatFeedPostTime(
    timestamp: Long,
    nowMillis: Long,
): String {
    if (timestamp <= 0L) return ""

    val calendar = NSCalendar.currentCalendar
    val postDate = NSDate.dateWithTimeIntervalSince1970(timestamp / 1000.0)
    val nowDate = NSDate.dateWithTimeIntervalSince1970(nowMillis / 1000.0)

    val postMidnight = calendar.startOfDayForDate(postDate)
    val nowMidnight = calendar.startOfDayForDate(nowDate)

    val diffSeconds = nowMidnight.timeIntervalSince1970 - postMidnight.timeIntervalSince1970
    val daysBetween = max(0L, (diffSeconds / 86400.0).toLong())
    val minutesBetween = max(0L, (nowMillis - timestamp) / 60_000L)

    if (timestamp > nowMillis) {
        return monthDayFormatter.stringFromDate(postDate)
    }

    return when {
        daysBetween == 0L -> when {
            minutesBetween < 1L -> "только что"
            minutesBetween < 60L -> "${minutesBetween} мин назад"
            minutesBetween < 120L -> "час назад"
            else -> "${minutesBetween / 60L} ч назад"
        }
        daysBetween == 1L -> "вчера"
        daysBetween in 2L..6L -> "${daysBetween} дн назад"
        daysBetween in 7L..13L -> "неделю назад"
        else -> monthDayFormatter.stringFromDate(postDate)
    }
}

internal actual fun currentFeedEpochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
