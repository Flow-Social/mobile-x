package me.floow.uikit.util

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970

private val timeFormatter = NSDateFormatter().apply {
    dateFormat = "HH:mm"
}

actual fun formatLastSeen(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return ""
    val date = NSDate.dateWithTimeIntervalSince1970(timestamp / 1000.0)
    return timeFormatter.stringFromDate(date)
}
