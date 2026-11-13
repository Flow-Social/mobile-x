package me.floow.uikit.chat.model

import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

private val timeFormatter = NSDateFormatter().apply { dateFormat = "HH:mm" }
private val dayFormatter = NSDateFormatter().apply { dateFormat = "d MMMM" }

internal actual fun platformCurrentChatTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

internal actual fun platformChatLocalDayStartMillis(epochMillis: Long): Long {
    val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)
    val calendar = NSCalendar.currentCalendar
    val startOfDay = calendar.startOfDayForDate(date)
    return (startOfDay.timeIntervalSince1970 * 1000.0).toLong()
}

internal actual fun platformFormatChatClockTime(epochMillis: Long): String {
    val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)
    return timeFormatter.stringFromDate(date)
}

internal actual fun platformIsChatLocalToday(dayStartMillis: Long): Boolean {
    val calendar = NSCalendar.currentCalendar
    val todayStart = calendar.startOfDayForDate(NSDate())
    return (todayStart.timeIntervalSince1970 * 1000.0).toLong() == dayStartMillis
}

internal actual fun platformFormatChatDayLabel(dayStartMillis: Long): String {
    val calendar = NSCalendar.currentCalendar
    val date = NSDate.dateWithTimeIntervalSince1970(dayStartMillis / 1000.0)
    val todayStart = calendar.startOfDayForDate(NSDate())
    val todayStartMillis = (todayStart.timeIntervalSince1970 * 1000.0).toLong()
    return when (dayStartMillis) {
        todayStartMillis -> "сегодня"
        todayStartMillis - 86400000L -> "вчера"
        else -> dayFormatter.stringFromDate(date)
    }
}
