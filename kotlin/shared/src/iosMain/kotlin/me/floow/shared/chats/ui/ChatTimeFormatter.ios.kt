package me.floow.shared.chats.ui

import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

private val timeFormatter = NSDateFormatter().apply { dateFormat = "HH:mm" }
private val dayFormatter = NSDateFormatter().apply { dateFormat = "d MMM" }

internal actual fun formatChatClockTime(epochMillis: Long): String {
    val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)
    return timeFormatter.stringFromDate(date)
}

internal actual fun formatChatDayLabel(epochMillis: Long, nowEpochMillis: Long): String {
    val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)
    val now = NSDate.dateWithTimeIntervalSince1970(nowEpochMillis / 1000.0)
    val calendar = NSCalendar.currentCalendar

    val dateStart = calendar.startOfDayForDate(date)
    val nowStart = calendar.startOfDayForDate(now)

    val diffSeconds = nowStart.timeIntervalSince1970 - dateStart.timeIntervalSince1970
    val daysDiff = (diffSeconds / 86400.0).toLong()

    return when (daysDiff) {
        0L -> "Сегодня"
        1L -> "Вчера"
        else -> dayFormatter.stringFromDate(date)
    }
}

internal actual fun currentChatEpochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
