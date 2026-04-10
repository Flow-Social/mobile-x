package me.floow.shared.chats.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val chatTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val chatDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

internal actual fun formatChatClockTime(epochMillis: Long): String {
	return Instant.ofEpochMilli(epochMillis)
		.atZone(ZoneId.systemDefault())
		.format(chatTimeFormatter)
}

internal actual fun formatChatDayLabel(epochMillis: Long, nowEpochMillis: Long): String {
	val zoneId = ZoneId.systemDefault()
	val messageDate = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
	val nowDate = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
	return when (messageDate) {
		nowDate -> "Сегодня"
		nowDate.minusDays(1) -> "Вчера"
		else -> Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(chatDayFormatter)
	}
}

internal actual fun currentChatEpochMillis(): Long = System.currentTimeMillis()
