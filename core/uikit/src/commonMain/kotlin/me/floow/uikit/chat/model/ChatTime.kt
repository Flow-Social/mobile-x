package me.floow.uikit.chat.model

fun currentChatTimeMillis(): Long = platformCurrentChatTimeMillis()

fun chatLocalDayStartMillis(epochMillis: Long): Long = platformChatLocalDayStartMillis(epochMillis)

fun formatChatClockTime(epochMillis: Long): String = platformFormatChatClockTime(epochMillis)

fun isChatLocalToday(dayStartMillis: Long): Boolean = platformIsChatLocalToday(dayStartMillis)

fun formatChatDayLabel(dayStartMillis: Long): String = platformFormatChatDayLabel(dayStartMillis)

internal expect fun platformCurrentChatTimeMillis(): Long

internal expect fun platformChatLocalDayStartMillis(epochMillis: Long): Long

internal expect fun platformFormatChatClockTime(epochMillis: Long): String

internal expect fun platformIsChatLocalToday(dayStartMillis: Long): Boolean

internal expect fun platformFormatChatDayLabel(dayStartMillis: Long): String
