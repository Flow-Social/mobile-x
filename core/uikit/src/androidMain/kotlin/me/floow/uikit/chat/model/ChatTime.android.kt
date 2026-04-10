package me.floow.uikit.chat.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CHAT_ZONE_ID: ZoneId = ZoneId.systemDefault()
private val CHAT_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val CHAT_DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yy")

internal actual fun platformCurrentChatTimeMillis(): Long = System.currentTimeMillis()

internal actual fun platformChatLocalDayStartMillis(epochMillis: Long): Long {
	val localDate = Instant.ofEpochMilli(epochMillis)
		.atZone(CHAT_ZONE_ID)
		.toLocalDate()
	return localDate
		.atStartOfDay(CHAT_ZONE_ID)
		.toInstant()
		.toEpochMilli()
}

internal actual fun platformFormatChatClockTime(epochMillis: Long): String {
	return Instant.ofEpochMilli(epochMillis)
		.atZone(CHAT_ZONE_ID)
		.toLocalTime()
		.format(CHAT_TIME_FORMATTER)
}

internal actual fun platformIsChatLocalToday(dayStartMillis: Long): Boolean {
	val date = Instant.ofEpochMilli(dayStartMillis)
		.atZone(CHAT_ZONE_ID)
		.toLocalDate()
	return date == LocalDate.now(CHAT_ZONE_ID)
}

internal actual fun platformFormatChatDayLabel(dayStartMillis: Long): String {
	return Instant.ofEpochMilli(dayStartMillis)
		.atZone(CHAT_ZONE_ID)
		.toLocalDate()
		.format(CHAT_DAY_FORMATTER)
}
