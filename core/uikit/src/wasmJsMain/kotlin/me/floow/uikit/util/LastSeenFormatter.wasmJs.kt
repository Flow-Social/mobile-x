package me.floow.uikit.util

@JsName("Date")
private external class JsDate {
    constructor(timestamp: Double)
    fun getHours(): Int
    fun getMinutes(): Int
}

actual fun formatLastSeen(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return ""
    val date = JsDate(timestamp.toDouble())
    val hours = date.getHours().toString().padStart(2, '0')
    val minutes = date.getMinutes().toString().padStart(2, '0')
    return "$hours:$minutes"
}
